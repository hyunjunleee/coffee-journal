package com.coffeejournal.ui.ai

import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.ui.notify.MemorySettingsDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Each option end to end against a fake HTTP layer: requests sent, answers built, failures reported. */
class NoteHelperServiceTest {
    private val http = FakeAiHttp()
    private val secrets = MemorySecretStore()
    private val service = NoteHelperService(http, secrets)
    private val note = NoteQuestion(NoteMode.NOTE, "베르가못 (bergamot)")

    private suspend fun keys(vararg slots: AiKeySlot) = slots.forEach { secrets.put(NoteHelperService.secretName(it), "key-${it.name}") }

    private suspend fun fails(block: suspend () -> Unit): AiError = assertFailsWith<AiFailure> { block() }.error

    @Test fun geminiTavily_searchesOnce_thenAsksGeminiWithTheNumberedPages() = runTest {
        keys(AiKeySlot.GEMINI, AiKeySlot.TAVILY)
        http.on("api.tavily.com") { AiFixtures.TAVILY_SEARCH }
            .on("generativelanguage") { AiFixtures.geminiPlain(AiFixtures.TAVILY_ANSWER) }
        val a = service.ask(note, AiProvider.GEMINI_TAVILY, "gemini-3.5-flash-lite")
        assertEquals(2, http.requests.size)
        val search = AiJson.parseObject(http.requests[0].body!!)!!
        assertEquals("\"bergamot\" coffee flavor note meaning tasting notes", search["query"]!!.jsonPrimitive.content)
        assertEquals("Bearer key-TAVILY", http.requests[0].headers["Authorization"])
        val gemini = http.requests[1]
        assertTrue(gemini.url.endsWith("/gemini-3.5-flash-lite:generateContent"))
        assertEquals("key-GEMINI", gemini.headers["x-goog-api-key"])
        val body = AiJson.parseObject(gemini.body!!)!!
        assertNull(body["tools"], "no Google Search on the free tier")
        assertEquals(NoteHelperPrompts.SOURCES_SYSTEM, body["system_instruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val user = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(user.startsWith(NoteHelperPrompts.question(note)))
        assertTrue(user.contains("\n\n출처\n[1] Flavor notes: bergamot — sca.coffee\nhttps://sca.coffee/sca-news/flavor-notes-bergamot\nBergamot is the citrus aroma"))
        assertTrue(user.contains("[3] 베르가못 향 커피 후기 — blog.naver.com"))
        assertFalse(user.contains("Roast: light"), "the page text (raw_content) is kept for the quote check, not sent")
        assertEquals(AiProvider.GEMINI_TAVILY, a.provider)
        assertEquals(3, a.sources.size)
        assertEquals(4, a.citedRuns)
    }

    @Test fun geminiTavily_noResultsOrNoMarks_isNoSources() = runTest {
        keys(AiKeySlot.GEMINI, AiKeySlot.TAVILY)
        http.sequence("api.tavily.com", 200 to """{"query": "q", "results": []}""", 200 to AiFixtures.TAVILY_SEARCH)
            .on("generativelanguage") { AiFixtures.geminiPlain("출처에 없어서 찾지 못했어요.") }
        assertEquals(AiErrorKind.NO_SOURCES, fails { service.ask(note, AiProvider.GEMINI_TAVILY, "m") }.kind)
        assertEquals(1, http.requests.size, "Gemini is not asked without pages")
        assertEquals(AiErrorKind.NO_SOURCES, fails { service.ask(note, AiProvider.GEMINI_TAVILY, "m") }.kind)
    }

    @Test fun geminiSearch_groundingTool_andTheFreeProject429() = runTest {
        keys(AiKeySlot.GEMINI)
        http.sequence("generativelanguage", 200 to AiFixtures.geminiGrounded(), 429 to AiFixtures.GEMINI_429_BILLING)
        val a = service.ask(note, AiProvider.GEMINI_SEARCH, "gemini-3.5-flash")
        assertEquals("""[{"google_search":{}}]""", AiJson.parseObject(http.requests[0].body!!)!!["tools"].toString())
        assertEquals(AiFixtures.SUGGESTIONS_HTML, a.searchSuggestionsHtml)
        val e = fails { service.ask(note, AiProvider.GEMINI_SEARCH, "gemini-3.5-flash") }
        assertEquals(AiErrorKind.BILLING_NEEDED, e.kind)
        assertEquals(AiErrors.SEARCH_BILLING, e.message)
    }

    @Test fun openAi_andClaude_answer() = runTest {
        keys(AiKeySlot.OPENAI, AiKeySlot.CLAUDE)
        http.on("api.openai.com/v1/responses") { AiFixtures.openAi() }.on("api.anthropic.com/v1/messages") { AiFixtures.claude() }
        assertEquals(2, service.ask(note, AiProvider.OPENAI, "gpt-5-nano").sources.size)
        assertEquals("Bearer key-OPENAI", http.requests[0].headers["Authorization"])
        val c = service.ask(note, AiProvider.CLAUDE, "claude-opus-5")
        assertEquals(3, c.sources.size)
        assertEquals("key-CLAUDE", http.requests[1].headers["x-api-key"])
        assertEquals("server-side-fallback-2026-07-01", http.requests[1].headers["anthropic-beta"])
    }

    @Test fun claude_pauseTurn_resumedWithTheContentSentBack_thenAnswered() = runTest {
        keys(AiKeySlot.CLAUDE)
        http.sequence(
            "api.anthropic.com/v1/messages",
            200 to AiFixtures.claude(AiFixtures.CLAUDE_PAUSED_CONTENT, stopReason = "pause_turn"),
            200 to AiFixtures.claude(AiFixtures.CLAUDE_RESUMED_CONTENT),
        )
        val a = service.ask(note, AiProvider.CLAUDE, "claude-sonnet-5")
        assertEquals(2, http.requests.size)
        val second = AiJson.parseObject(http.requests[1].body!!)!!["messages"]!!.jsonArray
        assertEquals(listOf("user", "assistant"), second.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        assertEquals(AiJson.parse(AiFixtures.CLAUDE_PAUSED_CONTENT), second[1].jsonObject["content"])
        assertEquals(1, a.citedRuns)
        assertEquals("sca.coffee", a.sources.single().domain)
        assertTrue(a.notes.isEmpty())
    }

    @Test fun claude_pausesStopAfterThreeResumes_refusalAndNoCitations() = runTest {
        keys(AiKeySlot.CLAUDE)
        http.sequence("api.anthropic.com/v1/messages", 200 to AiFixtures.claude(AiFixtures.CLAUDE_PAUSED_CONTENT, stopReason = "pause_turn"))
        // only search results, never text: no sources after the first request and three resumes
        assertEquals(AiErrorKind.NO_SOURCES, fails { service.ask(note, AiProvider.CLAUDE, "claude-opus-5") }.kind)
        assertEquals(1 + ClaudeApi.MAX_CONTINUATIONS, http.requests.size)

        val refusing = NoteHelperService(FakeAiHttp().on("anthropic") { AiFixtures.CLAUDE_REFUSAL }, secrets)
        val e = fails { refusing.ask(note, AiProvider.CLAUDE, "claude-opus-5") }
        assertEquals(AiErrorKind.REFUSED, e.kind)
        assertEquals("요청이 거절됐어요. 다른 말로 물어봐 주세요.", e.message)

        val truncated = NoteHelperService(FakeAiHttp().on("anthropic") { AiFixtures.claude(stopReason = "max_tokens") }, secrets)
        assertEquals(listOf("답이 길어 끝이 잘렸어요."), truncated.ask(note, AiProvider.CLAUDE, "claude-opus-5").notes)
    }

    @Test fun missingKeys_offline_unsupported() = runTest {
        keys(AiKeySlot.GEMINI)
        assertEquals(listOf(AiKeySlot.TAVILY), service.missingKeys(AiProvider.GEMINI_TAVILY))
        assertEquals(AiErrorKind.MISSING_KEY, fails { service.ask(note, AiProvider.GEMINI_TAVILY, "m") }.kind)
        assertTrue(http.requests.isEmpty(), "nothing is sent without every key")
        http.offline("generativelanguage")
        assertEquals(AiErrorKind.NETWORK, fails { service.ask(note, AiProvider.GEMINI_SEARCH, "m") }.kind)
        val ios = NoteHelperService(FakeAiHttp(), MemorySecretStore(supported = false))
        assertEquals(AiErrorKind.UNSUPPORTED, fails { ios.ask(note, AiProvider.GEMINI_SEARCH, "m") }.kind)
    }

    @Test fun keyCheck_cheapestRequestPerService() = runTest {
        keys(AiKeySlot.GEMINI, AiKeySlot.TAVILY, AiKeySlot.OPENAI, AiKeySlot.CLAUDE)
        http.on("generativelanguage") { AiFixtures.geminiPlain("네") }
            .on("api.tavily.com", status = 401) { AiFixtures.TAVILY_401 }
            .on("api.openai.com/v1/models") { AiFixtures.OPENAI_MODELS }
            .on("api.anthropic.com/v1/models") { AiFixtures.CLAUDE_MODELS }
        val settings = AiSettings(provider = AiProvider.GEMINI_SEARCH)
        val gemini = service.checkKey(AiKeySlot.GEMINI, settings)
        assertTrue(gemini.ok)
        assertEquals("정상", gemini.label)
        val geminiBody = AiJson.parseObject(http.requests.last().body!!)!!
        assertNull(geminiBody["tools"])
        assertEquals("8", geminiBody["generationConfig"]!!.jsonObject["maxOutputTokens"]!!.jsonPrimitive.content)
        assertTrue(http.requests.last().url.contains("/gemini-3.5-flash:"), "the chosen option's model")

        val tavily = service.checkKey(AiKeySlot.TAVILY, settings)
        assertEquals("키가 틀려요", tavily.label)
        assertEquals("basic", AiJson.parseObject(http.requests.last().body!!)!!["search_depth"]!!.jsonPrimitive.content)

        assertTrue(service.checkKey(AiKeySlot.OPENAI, settings).ok)
        assertEquals("GET", http.requests.last().method)
        // a model the key cannot list
        val gpt = service.checkKey(AiKeySlot.OPENAI, AiSettings(models = mapOf(AiProvider.OPENAI to "gpt-9")))
        assertEquals("이 모델은 쓸 수 없어요", gpt.label)
        assertTrue(service.checkKey(AiKeySlot.CLAUDE, settings).ok)
        assertEquals("이 모델은 쓸 수 없어요", service.checkKey(AiKeySlot.CLAUDE, AiSettings(models = mapOf(AiProvider.CLAUDE to "claude-haiku-4-5"))).label)

        secrets.delete(NoteHelperService.secretName(AiKeySlot.OPENAI))
        assertEquals("키가 틀려요", service.checkKey(AiKeySlot.OPENAI, settings).label)
    }

    @Test fun prefs_deviceKeys_defaults_consentPerOption() = runTest {
        val dao = MemorySettingsDao()
        val prefs = AiPrefs(SettingsRepository(dao))
        val first = prefs.load()
        assertEquals(AiProvider.GEMINI_TAVILY, first.provider)
        assertEquals("gemini-3.5-flash-lite", first.model())
        assertEquals("claude-opus-5", first.model(AiProvider.CLAUDE))
        prefs.setProvider(AiProvider.OPENAI)
        prefs.setModel(AiProvider.OPENAI, "  ")
        prefs.setModel(AiProvider.CLAUDE, "claude-sonnet-5")
        prefs.setConsent(AiProvider.OPENAI, true)
        val s = prefs.observe().first()
        assertEquals(AiProvider.OPENAI, s.provider)
        assertEquals("gpt-5-nano", s.model(), "blank means the default")
        assertEquals("claude-sonnet-5", s.model(AiProvider.CLAUDE))
        assertEquals(setOf(AiProvider.OPENAI), s.consents)
        assertTrue(dao.rows.value.keys.all { SettingsRepository.isDeviceKey(it) && it.startsWith("device.ai.") }, dao.rows.value.keys.toString())
        prefs.setConsent(AiProvider.OPENAI, false)
        assertTrue(prefs.load().consents.isEmpty())
    }

    @Test fun prompts_questionTemplatesAndTavilyQueries() {
        val a = NoteHelperPrompts.question(note)
        assertTrue(a.startsWith("노트 설명. 향미 노트: \"베르가못 (bergamot)\"\n형식:\n- 한 줄 뜻:"))
        val b = NoteHelperPrompts.question(NoteQuestion(NoteMode.DESCRIBE, "잘 익은 자두 같아요"))
        assertTrue(b.startsWith("맛 묘사로 노트 찾기. 마신 사람의 묘사: \"잘 익은 자두 같아요\"\n"))
        assertTrue(b.contains("플레이버 휠 용어: Black Tea, Floral, Chamomile"))
        assertTrue(b.contains("앱의 한국어 노트 분류: 베리류, 감귤류"))
        assertEquals("bergamot", NoteHelperPrompts.searchName("베르가못 (bergamot)"))
        assertEquals("자스민", NoteHelperPrompts.searchName("자스민"))
        assertEquals("흑설탕", NoteHelperPrompts.searchName("흑설탕 (갈색 설탕)"))
        assertEquals("\"자스민\" coffee flavor note meaning tasting notes", NoteHelperPrompts.tavilyQuery(NoteQuestion(NoteMode.NOTE, "자스민")))
        assertEquals("coffee tasting notes 잘 익은 자두 같아요", NoteHelperPrompts.tavilyQuery(NoteQuestion(NoteMode.DESCRIBE, " 잘 익은 자두 같아요 ")))
        assertTrue(NoteHelperPrompts.SOURCES_SYSTEM.contains("[n]"))
        assertTrue(NoteHelperPrompts.SEARCH_SYSTEM.startsWith("너는 스페셜티 커피의 향미 표현을 조사하는 도우미다."))
    }
}
