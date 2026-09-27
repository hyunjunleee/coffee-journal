package com.coffeejournal.ui.ai

import kotlinx.serialization.json.JsonPrimitive

/**
 * SYNTHETIC replies, hand-written in the shapes the services document (checked 2026-09-26): none of them was recorded
 * from a real call, and every URL, title and text is made up for the tests.
 */
object AiFixtures {
    private fun q(text: String) = JsonPrimitive(text).toString()

    // ───────────── Gemini, Google Search grounding ─────────────

    const val GEMINI_LINE_1 = "한 줄 뜻: 베르가못은 얼그레이 홍차에 쓰이는 시트러스 향을 가리킨다."
    const val GEMINI_LINE_2 = "한 로스터리는 \"bergamot, jasmine, black tea\"라고 적었다."
    const val GEMINI_LINE_3 = "출처와 연결되지 않은 문장이다."
    const val GEMINI_TEXT = "- $GEMINI_LINE_1\n- $GEMINI_LINE_2\n- $GEMINI_LINE_3"
    const val SUGGESTIONS_HTML = "<style>.chip{border:1px solid #ccc}</style><div class=\"carousel\"><a class=\"chip\" href=\"https://www.google.com/search?q=bergamot+coffee\">bergamot coffee</a></div>"

    private fun utf8(text: String, chars: Int) = text.substring(0, chars).encodeToByteArray().size

    /**
     * A grounded reply. [segmentText] false leaves the segments' text out, so only their indices place them;
     * [byteOffsets] writes those indices as UTF-8 byte offsets (one of the two readings the docs give).
     */
    fun geminiGrounded(segmentText: Boolean = true, byteOffsets: Boolean = true): String {
        val s1 = GEMINI_TEXT.indexOf(GEMINI_LINE_1)
        val e1 = s1 + GEMINI_LINE_1.length
        val s2 = GEMINI_TEXT.indexOf(GEMINI_LINE_2)
        val e2 = s2 + GEMINI_LINE_2.length
        fun at(i: Int) = if (byteOffsets) utf8(GEMINI_TEXT, i) else i
        fun seg(text: String) = if (segmentText) ""","text": ${q(text)}""" else ""
        return """
        {
          "candidates": [{
            "content": {"role": "model", "parts": [{"text": ${q(GEMINI_TEXT)}}]},
            "finishReason": "STOP",
            "groundingMetadata": {
              "webSearchQueries": ["bergamot coffee tasting note", "베르가못 커피 노트"],
              "searchEntryPoint": {"renderedContent": ${q(SUGGESTIONS_HTML)}},
              "groundingChunks": [
                {"web": {"uri": "https://vertexaisearch.cloud.google.com/grounding-api-redirect/AAA111", "title": "sca.coffee"}},
                {"web": {"uri": "https://vertexaisearch.cloud.google.com/grounding-api-redirect/BBB222", "title": "blog.naver.com"}}
              ],
              "groundingSupports": [
                {"segment": {"startIndex": ${at(s1)}, "endIndex": ${at(e1)}${seg(GEMINI_LINE_1)}}, "groundingChunkIndices": [0]},
                {"segment": {"startIndex": ${at(s2)}, "endIndex": ${at(e2)}${seg(GEMINI_LINE_2)}}, "groundingChunkIndices": [1, 0]}
              ]
            }
          }],
          "usageMetadata": {"promptTokenCount": 120, "candidatesTokenCount": 80, "totalTokenCount": 200},
          "modelVersion": "gemini-3.5-flash"
        }
        """.trimIndent()
    }

    /** A plain reply (no tools) with [text]. */
    fun geminiPlain(text: String, finishReason: String = "STOP") = """
        {"candidates": [{"content": {"role": "model", "parts": [{"text": ${q(text)}}]}, "finishReason": "$finishReason", "index": 0}],
         "usageMetadata": {"promptTokenCount": 900, "candidatesTokenCount": 150}, "modelVersion": "gemini-3.5-flash-lite"}
    """.trimIndent()

    const val GEMINI_BLOCKED = """{"promptFeedback": {"blockReason": "SAFETY"}, "usageMetadata": {"promptTokenCount": 10}}"""

    /** Measured 2026-09-26 with a new key: the message of the 404 for gemini-2.5-flash. */
    const val GEMINI_404_NEW_USERS = """{"error": {"code": 404, "message": "This model models/gemini-2.5-flash is no longer available to new users. Please update your code to use a newer model for the latest features and improvements.", "status": "NOT_FOUND"}}"""

    /** Measured 2026-09-26: every 3.x model with google_search on a free project. */
    const val GEMINI_429_BILLING = """{"error": {"code": 429, "message": "You exceeded your current quota, please check your plan and billing details. For more information on this error, head to: https://ai.google.dev/gemini-api/docs/rate-limits.", "status": "RESOURCE_EXHAUSTED"}}"""

    const val GEMINI_400_KEY = """{"error": {"code": 400, "message": "API key not valid. Please pass a valid API key.", "status": "INVALID_ARGUMENT", "details": [{"@type": "type.googleapis.com/google.rpc.ErrorInfo", "reason": "API_KEY_INVALID", "domain": "googleapis.com"}]}}"""
    const val GEMINI_403 = """{"error": {"code": 403, "message": "Method doesn't allow unregistered callers.", "status": "PERMISSION_DENIED"}}"""
    const val GEMINI_402 = """{"error": {"code": 402, "message": "Your prepayment credits are depleted.", "status": "FAILED_PRECONDITION"}}"""
    const val GEMINI_503 = """{"error": {"code": 503, "message": "The model is overloaded. Please try again later.", "status": "UNAVAILABLE"}}"""

    // ───────────── Tavily + Gemini (sources given) ─────────────

    const val TAVILY_SEARCH = """
    {
      "query": "\"bergamot\" coffee flavor note meaning tasting notes",
      "follow_up_questions": null,
      "answer": null,
      "images": [],
      "results": [
        {"url": "https://sca.coffee/sca-news/flavor-notes-bergamot", "title": "Flavor notes: bergamot",
         "content": "Bergamot is the citrus aroma of Earl Grey tea. [...] Washed Ethiopian coffees often show bergamot.",
         "score": 0.91, "raw_content": "Flavor notes: bergamot\nBergamot is the citrus aroma of Earl Grey tea. Tasters use it for washed coffees.\nWashed Ethiopian coffees often show bergamot."},
        {"url": "https://www.example-roaster.com/products/yirgacheffe-konga", "title": "Yirgacheffe Konga",
         "content": "Tasting notes: Bergamot, Jasmine, Black Tea. Washed.",
         "score": 0.84, "raw_content": "Yirgacheffe Konga — Tasting notes: Bergamot,  Jasmine, Black Tea. Washed. Roast: light. Lychee, white peach, red currant."},
        {"url": "https://blog.naver.com/someone/223344", "title": "베르가못 향 커피 후기",
         "content": "개인적으로 베르가못은 “얼그레이 같은 향”이라고 느꼈다.",
         "score": 0.52, "raw_content": null}
      ],
      "response_time": 1.67,
      "request_id": "synthetic-0001"
    }
    """

    /** What Gemini wrote from the three pages above: marks in several forms, a quote not on its page, a line with no mark. */
    const val TAVILY_ANSWER =
        "- 한 줄 뜻: SCA는 베르가못을 \"the citrus aroma of Earl Grey tea\"라고 설명한다.[1]\n" +
            "- 한 로스터리는 예가체프에 \"bergamot, jasmine, black tea\"라고 적었다 [2].\n" +
            "- 한 블로그는 \"얼그레이 같은 향\"이라고 적었다(개인 의견).[3]\n" +
            "- 같은 로스터리는 \"honey sweetness\"도 적었다.[2][9]\n" +
            "- 비슷한 표현은 찾지 못했어요."

    /** The query step's request is the one Gemini request asking for JSON. */
    const val QUERY_STEP = "responseMimeType"

    /** The query step's reply: Gemini's JSON text in the usual reply. */
    fun geminiQueries(json: String) = geminiPlain(json)

    const val TAVILY_401 = """{"detail": {"error": "Unauthorized: missing or invalid API key."}}"""
    const val TAVILY_432 = """{"detail": {"error": "This request exceeds your plan's set usage limit. Please upgrade your plan or contact support@tavily.com"}}"""

    // ───────────── OpenAI Responses ─────────────

    const val OPENAI_TEXT =
        "- 베르가못은 얼그레이 홍차의 시트러스 향이다 ([sca.coffee](https://sca.coffee/sca-news/flavor-notes-bergamot?utm_source=openai)).\n" +
            "- 한 로스터리는 \"bergamot, jasmine\"이라고 적었다. 두 번째 문장도 같은 출처다 ([example-roaster.com](https://www.example-roaster.com/products/yirgacheffe-konga?utm_source=openai)).\n" +
            "- 출처 없는 문장이다."

    fun openAi(): String {
        val a1s = OPENAI_TEXT.indexOf("[sca.coffee]")
        val a1e = OPENAI_TEXT.indexOf(")", a1s) + 1
        val a2s = OPENAI_TEXT.indexOf("[example-roaster.com]")
        val a2e = OPENAI_TEXT.indexOf(")", a2s) + 1
        val a3s = OPENAI_TEXT.indexOf("시트러스 향")
        val a3e = a3s + "시트러스 향".length
        return """
        {
          "id": "resp_synthetic", "object": "response", "status": "completed", "model": "gpt-5-nano",
          "output": [
            {"type": "reasoning", "id": "rs_1", "summary": []},
            {"type": "web_search_call", "id": "ws_1", "status": "completed",
             "action": {"type": "search", "query": "bergamot coffee tasting note",
                        "sources": [{"type": "url", "url": "https://sca.coffee/sca-news/flavor-notes-bergamot"}, {"type": "url", "url": "https://www.example-roaster.com/products/yirgacheffe-konga"}]}},
            {"type": "web_search_call", "id": "ws_2", "status": "completed", "action": {"type": "open_page", "url": "https://sca.coffee/sca-news/flavor-notes-bergamot"}},
            {"type": "web_search_call", "id": "ws_3", "status": "completed", "action": {"type": "search", "query": "yirgacheffe bergamot jasmine roaster"}},
            {"type": "message", "id": "msg_1", "status": "completed", "role": "assistant", "content": [
              {"type": "output_text", "text": ${q(OPENAI_TEXT)}, "logprobs": [], "annotations": [
                {"type": "url_citation", "start_index": $a1s, "end_index": $a1e, "url": "https://sca.coffee/sca-news/flavor-notes-bergamot?utm_source=openai", "title": "Flavor notes: bergamot"},
                {"type": "url_citation", "start_index": $a2s, "end_index": $a2e, "url": "https://www.example-roaster.com/products/yirgacheffe-konga?utm_source=openai", "title": "Yirgacheffe Konga"},
                {"type": "url_citation", "start_index": $a3s, "end_index": $a3e, "url": "https://sca.coffee/sca-news/flavor-notes-bergamot", "title": "Flavor notes: bergamot"}
              ]}
            ]}
          ],
          "usage": {"input_tokens": 3000, "output_tokens": 400}
        }
        """.trimIndent()
    }

    const val OPENAI_401 = """{"error": {"message": "Incorrect API key provided: sk-proj-****abcd.", "type": "invalid_request_error", "param": null, "code": "invalid_api_key"}}"""
    const val OPENAI_429_QUOTA = """{"error": {"message": "You exceeded your current quota, please check your plan and billing details.", "type": "insufficient_quota", "param": null, "code": "insufficient_quota"}}"""
    const val OPENAI_429_RATE = """{"error": {"message": "Rate limit reached for gpt-5-nano.", "type": "requests", "param": null, "code": "rate_limit_exceeded"}}"""
    const val OPENAI_MODELS = """{"object": "list", "data": [{"id": "gpt-5-nano", "object": "model", "created": 1, "owned_by": "system"}, {"id": "gpt-5-nano-2025-08-07", "object": "model", "created": 1, "owned_by": "system"}]}"""

    // ───────────── Anthropic Messages with web search ─────────────

    /** Search results, a dynamic-filtering search from code execution that failed (error object, "caller"), text blocks. */
    val CLAUDE_CONTENT = """
      [
        {"type": "thinking", "thinking": "", "signature": "EqoBCkgIAxgC"},
        {"type": "server_tool_use", "id": "srvtoolu_01", "name": "web_search", "input": {"query": "bergamot coffee tasting note"}},
        {"type": "web_search_tool_result", "tool_use_id": "srvtoolu_01", "content": [
          {"type": "web_search_result", "url": "https://sca.coffee/sca-news/flavor-notes-bergamot", "title": "Flavor notes: bergamot", "encrypted_content": "EqQBCioIAhgB", "page_age": "March 1, 2025"},
          {"type": "web_search_result", "url": "https://www.example-roaster.com/products/yirgacheffe-konga", "title": "Yirgacheffe Konga", "encrypted_content": "Eq2BCioIAhgB", "page_age": null},
          {"type": "web_search_result", "url": "https://www.reddit.com/r/Coffee/comments/abc/what_does_bergamot_taste_like", "title": "What does bergamot taste like?", "encrypted_content": "Eq3BCioIAhgB"}
        ]},
        {"type": "code_execution_tool_result", "tool_use_id": "srvtoolu_02", "content": {"type": "code_execution_result", "stdout": "", "stderr": "", "return_code": 0}},
        {"type": "server_tool_use", "id": "srvtoolu_03", "name": "web_search", "input": {"query": "bergamot roaster notes"}, "caller": {"type": "code_execution_20260120", "tool_id": "srvtoolu_02"}},
        {"type": "web_search_tool_result", "tool_use_id": "srvtoolu_03", "caller": {"type": "code_execution_20260120", "tool_id": "srvtoolu_02"},
         "content": {"type": "web_search_tool_result_error", "error_code": "max_uses_exceeded"}},
        {"type": "text", "text": "- 한 줄 뜻: "},
        {"type": "text", "text": "SCA는 베르가못을 \"the citrus aroma of Earl Grey tea\"라고 설명한다.", "citations": [
          {"type": "web_search_result_location", "url": "https://sca.coffee/sca-news/flavor-notes-bergamot", "title": "Flavor notes: bergamot", "encrypted_index": "Eo8BCioIAhgB", "cited_text": "Bergamot is the citrus aroma of Earl Grey tea. Tasters use it for washed coffees."}
        ]},
        {"type": "text", "text": "\n- 한 로스터리는 "},
        {"type": "text", "text": "\"bergamot, jasmine, black tea\"라고 적었고 \"stone fruit\"도 언급했다.", "citations": [
          {"type": "web_search_result_location", "url": "https://www.example-roaster.com/products/yirgacheffe-konga", "title": "Yirgacheffe Konga", "encrypted_index": "Ep1BCioIAhgB", "cited_text": "Tasting notes: Bergamot, Jasmine, Black Tea. Washed."}
        ]},
        {"type": "text", "text": "\n- 출처 없이 덧붙인 문장이다."}
      ]
    """.trimIndent()

    fun claude(content: String = CLAUDE_CONTENT, stopReason: String = "end_turn") = """
        {"id": "msg_synthetic", "type": "message", "role": "assistant", "model": "claude-opus-5",
         "content": $content, "stop_reason": "$stopReason", "stop_sequence": null,
         "usage": {"input_tokens": 5120, "output_tokens": 310, "server_tool_use": {"web_search_requests": 2}}}
    """.trimIndent()

    /** The first half of a paused turn: a search ran, no text yet. */
    val CLAUDE_PAUSED_CONTENT = """
      [
        {"type": "server_tool_use", "id": "srvtoolu_01", "name": "web_search", "input": {"query": "bergamot coffee"}},
        {"type": "web_search_tool_result", "tool_use_id": "srvtoolu_01", "content": [
          {"type": "web_search_result", "url": "https://sca.coffee/sca-news/flavor-notes-bergamot", "title": "Flavor notes: bergamot", "encrypted_content": "EqQB", "page_age": null}
        ]}
      ]
    """.trimIndent()

    val CLAUDE_RESUMED_CONTENT = """
      [
        {"type": "text", "text": "SCA는 베르가못을 시트러스 향이라고 설명한다.", "citations": [
          {"type": "web_search_result_location", "url": "https://sca.coffee/sca-news/flavor-notes-bergamot", "title": "Flavor notes: bergamot", "encrypted_index": "Eo8B", "cited_text": "Bergamot is the citrus aroma of Earl Grey tea."}
        ]}
      ]
    """.trimIndent()

    const val CLAUDE_REFUSAL = """{"id": "msg_r", "type": "message", "role": "assistant", "model": "claude-opus-5", "content": [{"type": "text", "text": "부분"}], "stop_reason": "refusal", "stop_details": {"type": "refusal", "category": "cyber", "explanation": "declined by a safety classifier"}, "usage": {"input_tokens": 10, "output_tokens": 1}}"""

    const val CLAUDE_MODELS = """{"data": [{"type": "model", "id": "claude-opus-5", "display_name": "Claude Opus 5", "created_at": "2026-01-01T00:00:00Z"}, {"type": "model", "id": "claude-sonnet-5", "display_name": "Claude Sonnet 5", "created_at": "2026-01-01T00:00:00Z"}], "has_more": false, "first_id": "claude-opus-5", "last_id": "claude-sonnet-5"}"""

    fun claudeError(type: String, message: String) = """{"type": "error", "error": {"type": "$type", "message": ${q(message)}}, "request_id": "req_synthetic"}"""
}
