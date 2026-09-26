package com.coffeejournal.android

import com.coffeejournal.ui.ai.AiConnectionException
import com.coffeejournal.ui.ai.AiHttp
import com.coffeejournal.ui.ai.AiHttpRequest
import com.coffeejournal.ui.ai.AiHttpResponse
import com.coffeejournal.ui.ai.AiKeySlot
import com.coffeejournal.ui.ai.AiPrefs
import com.coffeejournal.ui.ai.AiProvider
import com.coffeejournal.ui.ai.NoteHelperService
import com.coffeejournal.ui.ai.SecretStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.koin.core.context.GlobalContext
import java.util.Collections

/**
 * The AI services for the flow tests: every request is answered by the first rule whose URL part matches (added
 * order) and recorded; nothing reaches the network. Bound by [testPlatformModule].
 */
class FakeAiHttp : AiHttp {
    val requests: MutableList<AiHttpRequest> = Collections.synchronizedList(mutableListOf())
    private val rules = Collections.synchronizedList(mutableListOf<Pair<String, (AiHttpRequest) -> AiHttpResponse>>())

    fun on(urlPart: String, status: Int = 200, body: () -> String) = apply { rules += urlPart to { _ -> AiHttpResponse(status, body()) } }

    fun clear() = apply { rules.clear(); requests.clear() }

    override suspend fun send(request: AiHttpRequest): AiHttpResponse {
        requests += request
        val rule = synchronized(rules) { rules.firstOrNull { request.url.contains(it.first) } }
            ?: throw AiConnectionException("no fake reply for ${request.url}")
        return rule.second(request)
    }
}

/** The key store without the Android Keystore (Robolectric has none). */
class MemorySecretStore : SecretStore {
    val values: MutableMap<String, String> = Collections.synchronizedMap(mutableMapOf())
    override val supported: Boolean = true
    override suspend fun get(name: String): String? = values[name]
    override suspend fun put(name: String, value: String) { values[name] = value }
    override suspend fun delete(name: String) { values.remove(name) }
}

object AiSetup {
    private val koin get() = GlobalContext.get()
    val http: FakeAiHttp get() = koin.get<AiHttp>() as FakeAiHttp
    val secrets: MemorySecretStore get() = koin.get<SecretStore>() as MemorySecretStore

    fun key(slot: AiKeySlot, value: String = "test-${slot.name.lowercase()}-a1b2") { secrets.values[NoteHelperService.secretName(slot)] = value }

    /** [p] chosen with its keys saved and, when [consented], its notice already confirmed. */
    fun ready(p: AiProvider, consented: Boolean = true, model: String? = null) = runBlocking {
        val prefs = koin.get<AiPrefs>()
        prefs.setProvider(p)
        if (model != null) prefs.setModel(p, model)
        if (consented) prefs.setConsent(p, true)
        p.keys.forEach { key(it) }
    }

    /** Tavily's five pages and Gemini's answer from them (mode A), or Gemini's answer to a described taste (mode B). */
    fun tavilyAnswers(geminiText: String = AiReplies.NOTE_ANSWER) {
        http.on("api.tavily.com") { AiReplies.TAVILY }.on("generativelanguage") { AiReplies.gemini(geminiText) }
    }
}

/** SYNTHETIC service replies in the documented shapes (2026-09-26); every page, title and text is made up. */
object AiReplies {
    private fun q(text: String) = JsonPrimitive(text).toString()

    const val TAVILY = """
    {"query": "q", "results": [
      {"url": "https://sca.coffee/sca-news/flavor-notes-jasmine", "title": "Flavor notes: jasmine",
       "content": "Jasmine is a sweet floral aroma. [...] Washed Ethiopian coffees often show jasmine.",
       "score": 0.9, "raw_content": "Flavor notes: jasmine\nJasmine is a sweet floral aroma, like jasmine tea.\nWashed Ethiopian coffees often show jasmine."},
      {"url": "https://www.example-roaster.com/products/yirgacheffe-konga", "title": "Yirgacheffe Konga",
       "content": "Tasting notes: Jasmine, Bergamot, Black Tea.",
       "score": 0.8, "raw_content": "Yirgacheffe Konga. Tasting notes: Jasmine,  Bergamot, Black Tea. Washed. Plum, dark chocolate."},
      {"url": "https://blog.naver.com/someone/1234", "title": "자스민 향 커피 후기",
       "content": "개인적으로 자스민은 “꽃차 같은 향”이라고 느꼈다.", "score": 0.5, "raw_content": null}
    ], "response_time": 1.2}
    """

    const val NOTE_SENTENCE_1 = "한 줄 뜻: SCA는 자스민을 \"a sweet floral aroma\"라고 설명한다."
    const val NOTE_SENTENCE_3 = "한 로스터리는 \"honey sweetness\"도 적었다."
    const val UNCITED = "비슷한 표현은 찾지 못했어요."
    const val NOTE_ANSWER =
        "- $NOTE_SENTENCE_1[1]\n" +
            "- 한 로스터리는 예가체프에 \"Jasmine, Bergamot, Black Tea\"라고 적었다 [2].\n" +
            "- $NOTE_SENTENCE_3[2]\n" +
            "- 한 블로그는 \"꽃차 같은 향\"이라고 적었다(개인 의견).[3]\n" +
            "- $UNCITED"

    const val DESCRIBE_ANSWER =
        "- Plum: 한 로스터리는 \"Plum, dark chocolate\"이라고 적었다.[2]\n" +
            "- Dark Chocolate: 같은 페이지에 나온다.[2]\n" +
            "- 자두처럼 달고 끝이 쌉쌀한 맛은 다크 초콜릿에 가깝다고 적혀 있다.[2]\n" +
            "- Jasmine: SCA는 \"a sweet floral aroma\"라고 설명한다.[1]"

    fun gemini(text: String) = """{"candidates": [{"content": {"role": "model", "parts": [{"text": ${q(text)}}]}, "finishReason": "STOP"}]}"""

    const val GEMINI_404 = """{"error": {"code": 404, "message": "This model models/gemini-2.5-flash is no longer available to new users. Please update your code to use a newer model for the latest features and improvements.", "status": "NOT_FOUND"}}"""
    const val GEMINI_429 = """{"error": {"code": 429, "message": "You exceeded your current quota, please check your plan and billing details. For more information on this error, head to: https://ai.google.dev/gemini-api/docs/rate-limits.", "status": "RESOURCE_EXHAUSTED"}}"""
    const val TAVILY_401 = """{"detail": {"error": "Unauthorized: missing or invalid API key."}}"""

    /** A Google-grounded answer (GEMINI_SEARCH), segments given by their text. */
    val GROUNDED = run {
        val text = "- 자스민은 꽃향을 가리킨다.\n- 출처 없는 문장이다."
        """{"candidates": [{"content": {"parts": [{"text": ${q(text)}}]}, "finishReason": "STOP",
          "groundingMetadata": {"webSearchQueries": ["jasmine coffee"],
            "searchEntryPoint": {"renderedContent": "<div class=\"chips\"><a href=\"https://www.google.com/search?q=jasmine+coffee\">jasmine coffee</a></div>"},
            "groundingChunks": [{"web": {"uri": "https://vertexaisearch.cloud.google.com/grounding-api-redirect/A1", "title": "sca.coffee"}}],
            "groundingSupports": [{"segment": {"startIndex": 2, "endIndex": 16, "text": "자스민은 꽃향을 가리킨다."}, "groundingChunkIndices": [0]}]}}]}"""
    }

    const val CLAUDE = """
    {"id": "msg_synthetic", "type": "message", "role": "assistant", "model": "claude-opus-5", "stop_reason": "end_turn",
     "content": [
       {"type": "server_tool_use", "id": "srvtoolu_01", "name": "web_search", "input": {"query": "jasmine coffee tasting note"}},
       {"type": "web_search_tool_result", "tool_use_id": "srvtoolu_01", "content": [
         {"type": "web_search_result", "url": "https://sca.coffee/sca-news/flavor-notes-jasmine", "title": "Flavor notes: jasmine", "encrypted_content": "Eq1"},
         {"type": "web_search_result", "url": "https://www.reddit.com/r/Coffee/comments/x/jasmine", "title": "Jasmine in coffee?", "encrypted_content": "Eq2"}
       ]},
       {"type": "text", "text": "- 한 줄 뜻: "},
       {"type": "text", "text": "SCA는 자스민을 \"a sweet floral aroma\"라고 설명한다.", "citations": [
         {"type": "web_search_result_location", "url": "https://sca.coffee/sca-news/flavor-notes-jasmine", "title": "Flavor notes: jasmine", "encrypted_index": "Eo1", "cited_text": "Jasmine is a sweet floral aroma, like jasmine tea."}
       ]},
       {"type": "text", "text": "\n- 출처 없이 덧붙인 문장이다."}
     ],
     "usage": {"input_tokens": 4000, "output_tokens": 200, "server_tool_use": {"web_search_requests": 1}}}
    """
}
