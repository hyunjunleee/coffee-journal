package com.coffeejournal.ui.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The four services' requests and replies (fixtures: AiFixtures, synthetic). */
class AiParsersTest {
    private fun obj(body: String?): JsonObject = AiJson.parseObject(body!!)!!

    /** The answer text with its marks as the screen shows them ("…이다.[1]"), grey runs in ‹›. */
    private fun rendered(a: GroundedAnswer): String = a.paragraphs.joinToString("\n") { p ->
        p.runs.joinToString("") { r ->
            val t = if (!r.gap && !r.cited) "‹${r.text}›" else r.text
            t + r.markers.joinToString("") { "[$it]" }
        }
    }

    // ───────────── Gemini ─────────────

    @Test fun gemini_request_systemContentsTemperature_andSearchOnlyWhenAsked() {
        val r = GeminiApi.request("AQ.secret", "gemini-3.5-flash", "SYS", "Q", search = true)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent", r.url)
        assertEquals("AQ.secret", r.headers["x-goog-api-key"])
        assertEquals("application/json", r.headers["Content-Type"])
        assertFalse("AQ.secret" in r.toString(), "the key never shows in a log line")
        val b = obj(r.body)
        assertEquals("SYS", b["system_instruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("user", b["contents"]!!.jsonArray[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Q", b["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(0.2, b["generationConfig"]!!.jsonObject["temperature"]!!.jsonPrimitive.content.toDouble())
        assertEquals("""[{"google_search":{}}]""", b["tools"].toString())
        assertNull(obj(GeminiApi.body("SYS", "Q", search = false))["tools"])
        // the key check: no system prompt, 8 tokens at most
        val check = obj(GeminiApi.body(null, "안녕", search = false, maxOutputTokens = 8))
        assertNull(check["system_instruction"])
        assertEquals("8", check["generationConfig"]!!.jsonObject["maxOutputTokens"]!!.jsonPrimitive.content)
        // a typed model name is a path segment
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/my%20model:generateContent", GeminiApi.url("models/my model"))
    }

    @Test fun gemini_plainReply_textOfTheNonThoughtParts() {
        val body = """{"candidates": [{"content": {"parts": [{"text": "생각", "thought": true}, {"text": "답 "}, {"text": "이어서"}]}, "finishReason": "MAX_TOKENS"}]}"""
        val r = GeminiApi.parse(body)!!
        assertEquals("답 이어서", r.text)
        assertNull(r.grounding)
        assertEquals(listOf("답이 길어 끝이 잘렸어요."), GeminiApi.truncationNotes(r.finishReason))
        assertNull(GeminiApi.blocked(r))
        assertEquals("SAFETY", GeminiApi.blocked(GeminiApi.parse(AiFixtures.GEMINI_BLOCKED)!!))
        assertNull(GeminiApi.parse("not json"))
    }

    @Test fun gemini_grounded_sourcesMarksAndSuggestions_textUnchanged() {
        val reply = GeminiApi.parse(AiFixtures.geminiGrounded())!!
        val g = reply.grounding!!
        assertEquals(listOf("bergamot coffee tasting note", "베르가못 커피 노트"), g.queries)
        val a = GeminiApi.groundedAnswer(reply, "gemini-3.5-flash")
        assertEquals(AiProvider.GEMINI_SEARCH, a.provider)
        // the redirect link opens the page; the title carries the domain
        assertEquals(listOf("sca.coffee", "blog.naver.com"), a.sources.map { it.domain })
        assertTrue(a.sources[0].url.startsWith("https://vertexaisearch.cloud.google.com/"))
        assertEquals(listOf(SourceKind.INSTITUTION, SourceKind.PERSONAL), a.sources.map { it.kind })
        assertEquals(AiFixtures.SUGGESTIONS_HTML, a.searchSuggestionsHtml)
        // Google's text as it came, a mark after each grounded segment, the rest grey
        assertEquals(AiFixtures.GEMINI_TEXT, a.plainText)
        assertEquals(
            "- ${AiFixtures.GEMINI_LINE_1}[1]\n- ${AiFixtures.GEMINI_LINE_2}[1][2]\n- ‹${AiFixtures.GEMINI_LINE_3}›",
            rendered(a),
        )
        assertTrue(a.paragraphs.all { it.quotes.isEmpty() }, "no quote check without page text")
        // the searches Google ran, shown under the answer
        assertEquals(listOf("bergamot coffee tasting note", "베르가못 커피 노트"), a.queries)
    }

    @Test fun gemini_queryStep_jsonSchemaNoToolsTemperatureZero() {
        val r = GeminiApi.queryRequest("AQ.k", "gemini-3.5-flash-lite", NoteHelperPrompts.QUERY_SYSTEM, "맛 묘사: \"자두\"")
        assertTrue(r.url.endsWith("/gemini-3.5-flash-lite:generateContent"))
        val b = obj(r.body)
        assertNull(b["tools"])
        assertEquals(NoteHelperPrompts.QUERY_SYSTEM, b["system_instruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(
            """{"temperature":0,"maxOutputTokens":1024,"responseMimeType":"application/json",""" +
                """"responseSchema":{"type":"OBJECT","properties":{"queries":{"type":"ARRAY","items":{"type":"STRING"}}},"required":["queries"]}}""",
            b["generationConfig"].toString(),
        )
    }

    @Test fun searchQueries_parsedAndChecked_elseNull() {
        // one search per question: only the first query, spaces tidied
        assertEquals(listOf("ripe plum bitter finish tasting notes specialty coffee"),
            SearchQueries.parse("""{"queries": [" ripe plum  bitter finish tasting notes specialty coffee ", "plum bittersweet tasting notes"]}"""))
        // quotes around a whole query come off; a fenced reply is read
        assertEquals(listOf("a tasting notes specialty coffee"),
            SearchQueries.parse("```json\n{\"queries\": [\"\\\"a tasting notes specialty coffee\\\"\", \"b\"]}\n```"))
        // the partly quoted fallback stays as it is
        assertEquals(listOf("\"bergamot\" coffee tasting notes"), SearchQueries.parse("""{"queries": ["\"bergamot\" coffee tasting notes"]}"""))
        for (bad in listOf(null, "", "not json", "{}", """{"queries": []}""", """{"queries": ["  ", ""]}""", """{"queries": "plum"}""",
            """{"queries": ["${"x".repeat(201)}"]}""", """["plum coffee"]""")) {
            assertNull(SearchQueries.parse(bad), bad.toString())
        }
        assertEquals(listOf("plum tasting notes specialty coffee"), SearchQueries.fromTyped(" \n  plum  tasting notes specialty coffee \n cocoa coffee"))
        assertEquals(200, SearchQueries.fromTyped("y".repeat(300)).single().length)
        assertTrue(SearchQueries.fromTyped(" \n ").isEmpty())
    }

    @Test fun gemini_segmentsWithoutText_placedByUtf8ByteOffsets_inKoreanText() {
        val bytes = GeminiApi.groundedAnswer(GeminiApi.parse(AiFixtures.geminiGrounded(segmentText = false, byteOffsets = true))!!, "m")
        assertEquals("- ${AiFixtures.GEMINI_LINE_1}[1]\n- ${AiFixtures.GEMINI_LINE_2}[1][2]\n- ‹${AiFixtures.GEMINI_LINE_3}›", rendered(bytes))
        // with the segment text present, character offsets (the other reading) land the same
        val chars = GeminiApi.groundedAnswer(GeminiApi.parse(AiFixtures.geminiGrounded(segmentText = true, byteOffsets = false))!!, "m")
        assertEquals(rendered(bytes), rendered(chars))
    }

    @Test fun gemini_utf8Offsets() {
        val t = "a가😀b"
        assertEquals(0, GeminiApi.utf8ToCharIndex(t, 0))
        assertEquals(1, GeminiApi.utf8ToCharIndex(t, 1))
        assertEquals(2, GeminiApi.utf8ToCharIndex(t, 4))
        assertEquals(4, GeminiApi.utf8ToCharIndex(t, 8), "past the 4-byte emoji (two chars)")
        assertEquals(5, GeminiApi.utf8ToCharIndex(t, 9))
        assertEquals(5, GeminiApi.utf8ToCharIndex(t, 99))
        assertEquals(2, GeminiApi.utf8ToCharIndex(t, 2), "inside 가 counts as past it")
    }

    // ───────────── Tavily ─────────────

    @Test fun tavily_request_fiveResultsThreeChunksRawText_atEitherDepth_andTheOneCreditCheck() {
        val r = TavilyApi.request("tvly-secret", TavilySearch("\"bergamot\" coffee flavor note meaning tasting notes", "advanced"))
        assertEquals("https://api.tavily.com/search", r.url)
        assertEquals("Bearer tvly-secret", r.headers["Authorization"])
        val b = obj(r.body)
        assertEquals("advanced", b["search_depth"]!!.jsonPrimitive.content)
        assertEquals("5", b["max_results"]!!.jsonPrimitive.content)
        assertEquals("3", b["chunks_per_source"]!!.jsonPrimitive.content)
        assertEquals("text", b["include_raw_content"]!!.jsonPrimitive.content)
        assertEquals("false", b["include_answer"]!!.jsonPrimitive.content)
        val c = obj(TavilyApi.checkRequest("k").body)
        assertEquals("basic", c["search_depth"]!!.jsonPrimitive.content)
        assertEquals("1", c["max_results"]!!.jsonPrimitive.content)
        assertNull(b["include_domains"])
        // basic asks for the same (as the evaluation measured it; Tavily accepts chunks on basic too)
        val basic = obj(TavilyApi.request("k", TavilySearch("q", "basic")).body)
        assertEquals("basic", basic["search_depth"]!!.jsonPrimitive.content)
        assertEquals("5", basic["max_results"]!!.jsonPrimitive.content)
        assertEquals("3", basic["chunks_per_source"]!!.jsonPrimitive.content)
        assertEquals("text", basic["include_raw_content"]!!.jsonPrimitive.content)
        // the Korean-blog search is limited to the blogs
        val blogs = obj(TavilyApi.request("k", TavilySearch("커피 원두 베르가못 노트 후기", "basic", TavilyPlan.BLOGS)).body)
        assertEquals("""["blog.naver.com","tistory.com","brunch.co.kr","cafe.naver.com"]""", blogs["include_domains"].toString())
        assertEquals(listOf(1, 2, 3), SearchDepth.entries.map { it.credits })
        assertEquals(listOf("기본", "정밀", "정밀+기본"), SearchDepth.entries.map { it.label })
    }

    @Test fun tavilyPlan_searchesPerSetting_basicBeforeAdvanced_blogsLast_inKorean() {
        val note = NoteQuestion(NoteMode.NOTE, "베르가못 (bergamot)")
        fun plan(depth: SearchDepth, people: Boolean) = TavilyPlan.searches("bergamot tasting note specialty coffee", note, depth, people)
            .map { Triple(it.query, it.depth, it.people) }
        val main = "bergamot tasting note specialty coffee"
        val blog = Triple("커피 원두 베르가못 노트 후기", "basic", true)
        assertEquals(listOf(Triple(main, "basic", false)), plan(SearchDepth.BASIC, false))
        assertEquals(listOf(Triple(main, "advanced", false)), plan(SearchDepth.PRECISE, false))
        // the same query at basic first: an advanced search first would make the basic one return its cached result
        assertEquals(listOf(Triple(main, "basic", false), Triple(main, "advanced", false)), plan(SearchDepth.PRECISE_BASIC, false))
        assertEquals(listOf(Triple(main, "basic", false), blog), plan(SearchDepth.BASIC, true))
        assertEquals(listOf(Triple(main, "advanced", false), blog), plan(SearchDepth.PRECISE, true))
        assertEquals(listOf(Triple(main, "basic", false), Triple(main, "advanced", false), blog), plan(SearchDepth.PRECISE_BASIC, true))
        // eval.py korean_query: the Korean part of the label, or the description as it is
        assertEquals("커피 원두 자스민 노트 후기", TavilyPlan.koreanQuery(NoteQuestion(NoteMode.NOTE, " 자스민 ")))
        assertEquals("커피 원두 후기 잘 익은 자두 같아요", TavilyPlan.koreanQuery(NoteQuestion(NoteMode.DESCRIBE, " 잘 익은 자두 같아요 ")))
        // credits: 기본 1, 정밀 2, 정밀+기본 3, +1 with 사람들 의견; questions a month rounded down to tens
        assertEquals(listOf(1000, 500, 330, 250), (1..4).map(TavilyPlan::questionsPerMonth))
        assertEquals(3, AiSettings().tavilyCredits, "the defaults: 정밀 + 사람들 의견")
        assertEquals(4, AiSettings(searchDepth = SearchDepth.PRECISE_BASIC).tavilyCredits)
        assertEquals(1, AiSettings(searchDepth = SearchDepth.BASIC, people = false).tavilyCredits)
        assertEquals("질문 한 번에 약 3크레딧 · 무료 1,000크레딧이면 한 달 약 330번", AiTexts.credits(3))
        assertEquals("질문 한 번에 약 1크레딧 · 무료 1,000크레딧이면 한 달 약 1,000번", AiTexts.credits(1))
    }

    @Test fun tavilyPlan_order_advancedFirst_thenBasicsNewPages_thenBlogs_eachOnce_atMostFifteen() {
        val adv = TavilyApi.parse(AiFixtures.TAVILY_SEARCH)!!
        val basic = TavilyApi.parse(AiFixtures.TAVILY_BASIC)!!
        val blogs = TavilyApi.parse(AiFixtures.TAVILY_BLOGS)!!
        val found = listOf(TavilySearch("q", "basic") to basic, TavilySearch("q", "advanced") to adv, TavilySearch("k", "basic", TavilyPlan.BLOGS) to blogs)
        val ordered = TavilyPlan.order(found)
        assertEquals(
            listOf("sca.coffee", "example-roaster.com", "blog.naver.com", "roaster-a.example", "roaster-b.example", "m.blog.naver.com", "coffeelog.tistory.com"),
            ordered.map { it.domain },
        )
        val many = (1..20).map { TavilyResult("t$it", "https://site$it.example/p", "c") }
        assertEquals(15, TavilyPlan.order(listOf(TavilySearch("q", "advanced") to many)).size)
    }

    @Test fun tavily_results() {
        val results = TavilyApi.parse(AiFixtures.TAVILY_SEARCH)!!
        assertEquals(3, results.size)
        assertEquals("example-roaster.com", results[1].domain)
        assertTrue(results[1].rawContent!!.contains("Roast: light"))
        assertNull(results[2].rawContent)
        assertEquals(0.91, results[0].score)
        assertNull(TavilyApi.parse("""{"detail": {"error": "x"}}"""))
    }

    @Test fun tavilyGemini_marksToSources_quotesCheckedAgainstTheCitedPage() {
        val results = TavilyApi.parse(AiFixtures.TAVILY_SEARCH)!!
        val a = SourcedAnswer.answer(AiFixtures.TAVILY_ANSWER, results, "gemini-3.5-flash-lite")
        assertEquals(
            "- 한 줄 뜻: SCA는 베르가못을 \"the citrus aroma of Earl Grey tea\"라고 설명한다.[1]\n" +
                "- 한 로스터리는 예가체프에 \"bergamot, jasmine, black tea\"라고 적었다.[2]\n" +
                "- 한 블로그는 \"얼그레이 같은 향\"이라고 적었다(개인 의견).[3]\n" +
                "- 같은 로스터리는 \"honey sweetness\"도 적었다.[2]\n" +
                "- ‹비슷한 표현은 찾지 못했어요.›",
            rendered(a),
        )
        val quotes = a.paragraphs.flatMap { it.quotes }
        assertEquals(
            listOf(
                QuoteCheck("the citrus aroma of Earl Grey tea", QuoteStatus.FOUND, 1),
                // raw_content has "Bergamot,  Jasmine, Black Tea": case and spacing do not matter
                QuoteCheck("bergamot, jasmine, black tea", QuoteStatus.FOUND, 2),
                // no raw_content: the curly-quoted snippet (content) is searched
                QuoteCheck("얼그레이 같은 향", QuoteStatus.FOUND, 3),
                QuoteCheck("honey sweetness", QuoteStatus.NOT_FOUND, 2),
            ),
            quotes,
        )
        assertEquals((1..3).toList(), a.sources.map { it.number })
        assertEquals(SourceKind.PERSONAL, a.sources[2].kind)
    }

    // ───────────── OpenAI ─────────────

    @Test fun openAi_request_webSearchForcedInKorea_sourcesIncluded() {
        val r = OpenAiApi.request("sk-proj-secret", "gpt-5-nano", "Q")
        assertEquals("https://api.openai.com/v1/responses", r.url)
        assertEquals("Bearer sk-proj-secret", r.headers["Authorization"])
        val b = obj(r.body)
        assertEquals("gpt-5-nano", b["model"]!!.jsonPrimitive.content)
        assertEquals(NoteHelperPrompts.SEARCH_SYSTEM, b["instructions"]!!.jsonPrimitive.content)
        assertEquals("Q", b["input"]!!.jsonPrimitive.content)
        assertEquals("""[{"type":"web_search","user_location":{"type":"approximate","country":"KR"}}]""", b["tools"].toString())
        assertEquals("required", b["tool_choice"]!!.jsonPrimitive.content)
        assertEquals("""["web_search_call.action.sources"]""", b["include"].toString())
        assertEquals("GET", OpenAiApi.modelsRequest("k").method)
    }

    @Test fun openAi_urlCitations_inlineLinksBecomeMarksAtSentenceEnds_sourcesDeduplicated() {
        val (text, annotations) = OpenAiApi.parse(AiFixtures.openAi())!!
        assertEquals(3, annotations.size)
        val a = OpenAiApi.answer(text, annotations, "gpt-5-nano")
        assertEquals(
            "- 베르가못은 얼그레이 홍차의 시트러스 향이다.[1]\n" +
                "- ‹한 로스터리는 \"bergamot, jasmine\"이라고 적었다.› 두 번째 문장도 같은 출처다.[2]\n" +
                "- ‹출처 없는 문장이다.›",
            rendered(a),
        )
        // the ?utm_source=openai link and the bare one are one source
        assertEquals(2, a.sources.size)
        assertEquals("Flavor notes: bergamot", a.sources[0].title)
        assertEquals("example-roaster.com", a.sources[1].domain)
        assertNull(OpenAiApi.parse("""{"error": null}"""))
        assertEquals(listOf("gpt-5-nano", "gpt-5-nano-2025-08-07"), OpenAiApi.modelIds(AiFixtures.OPENAI_MODELS))
        // searches only: the open_page action is not a query
        assertEquals(listOf("bergamot coffee tasting note", "yirgacheffe bergamot jasmine roaster"), OpenAiApi.searchQueries(AiFixtures.openAi()))
    }

    // ───────────── Claude ─────────────

    @Test fun claude_request_toolVersionPerModel_fallbacksOnlyForOpus5_noThinkingNoTemperature() {
        val opus = ClaudeApi.request("sk-ant-secret", "claude-opus-5", "Q")
        assertEquals("https://api.anthropic.com/v1/messages", opus.url)
        assertEquals("sk-ant-secret", opus.headers["x-api-key"])
        assertEquals("2023-06-01", opus.headers["anthropic-version"])
        assertEquals("application/json", opus.headers["content-type"])
        assertEquals("server-side-fallback-2026-07-01", opus.headers["anthropic-beta"])
        val b = obj(opus.body)
        assertEquals("16000", b["max_tokens"]!!.jsonPrimitive.content)
        assertEquals(NoteHelperPrompts.SEARCH_SYSTEM, b["system"]!!.jsonPrimitive.content)
        assertEquals("""[{"role":"user","content":"Q"}]""", b["messages"].toString())
        assertEquals(
            """[{"type":"web_search_20260209","name":"web_search","max_uses":3,"user_location":{"type":"approximate","country":"KR"}}]""",
            b["tools"].toString(),
        )
        assertEquals("default", b["fallbacks"]!!.jsonPrimitive.content)
        assertNull(b["thinking"])
        assertNull(b["temperature"])

        val sonnet = ClaudeApi.request("k", "claude-sonnet-5", "Q")
        assertNull(sonnet.headers["anthropic-beta"])
        assertNull(obj(sonnet.body)["fallbacks"])
        assertEquals("web_search_20260209", ClaudeApi.searchToolType("claude-sonnet-5"))
        val haiku = obj(ClaudeApi.request("k", "claude-haiku-4-5", "Q").body)
        assertEquals("web_search_20250305", haiku["tools"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertNull(haiku["fallbacks"])
        assertEquals("web_search_20260209", ClaudeApi.searchToolType("claude-opus-4-6"))
        assertEquals("web_search_20250305", ClaudeApi.searchToolType("claude-sonnet-4-5"))
        assertEquals("web_search_20250305", ClaudeApi.searchToolType("something-else"))
        val models = ClaudeApi.modelsRequest("k")
        assertEquals("GET", models.method)
        assertEquals("2023-06-01", models.headers["anthropic-version"])
    }

    @Test fun claude_resumeSendsTheContentBackAsTheAssistantTurn() {
        val content = AiJson.parse(AiFixtures.CLAUDE_PAUSED_CONTENT)!!.jsonArray.toList()
        val b = obj(ClaudeApi.body("claude-opus-5", "Q", content))
        val messages = b["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("assistant", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals(AiJson.parse(AiFixtures.CLAUDE_PAUSED_CONTENT), messages[1].jsonObject["content"])
    }

    @Test fun claude_citedBlocksGetMarks_sourcesCitedFirst_quotesFoundInCitedTextOnly() {
        val reply = ClaudeApi.parse(AiFixtures.claude())!!
        assertEquals("end_turn", reply.stopReason)
        val a = ClaudeApi.answer(reply.content, "claude-opus-5")
        assertEquals(
            "- 한 줄 뜻: SCA는 베르가못을 \"the citrus aroma of Earl Grey tea\"라고 설명한다.[1]\n" +
                "- 한 로스터리는 \"bergamot, jasmine, black tea\"라고 적었고 \"stone fruit\"도 언급했다.[2]\n" +
                "- ‹출처 없이 덧붙인 문장이다.›",
            rendered(a),
        )
        assertEquals(
            listOf("sca.coffee" to SourceKind.INSTITUTION, "example-roaster.com" to SourceKind.OTHER, "reddit.com" to SourceKind.PERSONAL),
            a.sources.map { it.domain to it.kind },
        )
        // ✓ from the 150-character excerpts; a quote missing from them gets no badge at all (no proof of absence)
        assertEquals(
            listOf(QuoteCheck("the citrus aroma of Earl Grey tea", QuoteStatus.FOUND, 1), QuoteCheck("bergamot, jasmine, black tea", QuoteStatus.FOUND, 2)),
            a.paragraphs.flatMap { it.quotes },
        )
        assertEquals(listOf("max_uses_exceeded"), ClaudeApi.searchErrors(reply.content))
        // both searches, the one dynamic filtering ran from code execution ("caller") too
        assertEquals(listOf("bergamot coffee tasting note", "bergamot roaster notes"), a.queries)
        val refusal = ClaudeApi.parse(AiFixtures.CLAUDE_REFUSAL)!!
        assertEquals("refusal", refusal.stopReason)
        assertEquals("declined by a safety classifier", refusal.refusal)
        assertNotNull(ClaudeApi.parse(AiFixtures.claude(stopReason = "max_tokens")))
    }
}
