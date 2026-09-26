package com.coffeejournal.ui.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Anthropic Messages API with the server-side web_search tool, over plain HTTP (the user chose no SDK). Each cited
 * text block carries its web_search_result_location citations with the cited text (≤ 150 characters, verbatim).
 */
object ClaudeApi {
    const val MESSAGES = "https://api.anthropic.com/v1/messages"
    const val MODELS = "https://api.anthropic.com/v1/models"
    const val VERSION = "2023-06-01"

    /** Claude Opus 5 only: a request its safety classifiers decline is rerun on another model server-side. */
    const val FALLBACK_MODEL = "claude-opus-5"
    const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

    const val MAX_CONTINUATIONS = 3

    fun headers(key: String, model: String? = null, json: Boolean = true): Map<String, String> = buildMap {
        put("x-api-key", key)
        put("anthropic-version", VERSION)
        if (json) put("content-type", "application/json")
        if (model == FALLBACK_MODEL) put("anthropic-beta", FALLBACK_BETA)
    }

    /**
     * web_search_20260209 (dynamic filtering) on the models that support it (Opus 4.6 and later, Sonnet 4.6 and later,
     * Fable / Mythos); web_search_20250305 (basic) on Haiku and anything older or unrecognised.
     */
    fun searchToolType(model: String): String {
        val m = Regex("""claude-(opus|sonnet|haiku|fable|mythos)-(\d+)(?:-(\d{1,2}))?(?:-|$)""").find(model.trim().lowercase())
            ?: return "web_search_20250305"
        val family = m.groupValues[1]
        val major = m.groupValues[2].toInt()
        val minor = m.groupValues[3].toIntOrNull() ?: 0
        val dynamic = family != "haiku" && (major > 4 || (major == 4 && minor >= 6))
        return if (dynamic) "web_search_20260209" else "web_search_20250305"
    }

    /**
     * No thinking field (Opus 5 thinks adaptively by default, Haiku 4.5 not at all) and no temperature. [assistant]:
     * the content returned so far after a pause_turn, sent back unchanged so the server resumes the turn.
     */
    fun body(model: String, question: String, assistant: List<JsonElement> = emptyList()): String = buildJsonObject {
        put("model", model)
        put("max_tokens", 16000)
        put("system", NoteHelperPrompts.SEARCH_SYSTEM)
        putJsonArray("messages") {
            addJsonObject { put("role", "user"); put("content", question) }
            if (assistant.isNotEmpty()) addJsonObject { put("role", "assistant"); put("content", JsonArray(assistant)) }
        }
        putJsonArray("tools") {
            addJsonObject {
                put("type", searchToolType(model))
                put("name", "web_search")
                put("max_uses", 3)
                putJsonObject("user_location") { put("type", "approximate"); put("country", "KR") }
            }
        }
        if (model == FALLBACK_MODEL) put("fallbacks", "default")
    }.toString()

    fun request(key: String, model: String, question: String, assistant: List<JsonElement> = emptyList()) =
        AiHttpRequest(MESSAGES, headers(key, model), body(model, question, assistant))

    fun modelsRequest(key: String) = AiHttpRequest(MODELS, headers(key, json = false))

    data class Reply(val stopReason: String?, val content: List<JsonElement>, val refusal: String?)

    /** Null when [body] is not a message. */
    fun parse(body: String): Reply? {
        val root = AiJson.parseObject(body) ?: return null
        if (root["content"] == null && root["stop_reason"] == null) return null
        val refusal = root["stop_details"]["explanation"].str ?: root["stop_details"]["category"].str
        return Reply(root["stop_reason"].str, root["content"].arr, refusal)
    }

    /**
     * The answer from every content block of the turn (all continuations): text blocks in order; a text block with
     * citations gets [n] marks at its end. Sources: the cited pages in the order first cited, then the other search
     * results. Search errors come back as an error object instead of a result list; unknown blocks (thinking, code
     * execution of dynamic filtering) are skipped.
     */
    fun answer(content: List<JsonElement>, model: String, notes: List<String> = emptyList()): GroundedAnswer {
        val texts = content.filter { it["type"].str == "text" }
        val order = LinkedHashMap<String, String>() // url → title
        texts.forEach { b -> b["citations"].arr.forEach { c -> c["url"].str?.let { url -> order.getOrPut(url) { c["title"].str ?: "" } } } }
        content.filter { it["type"].str == "web_search_tool_result" }.forEach { r ->
            // a list of results, or one error object ({"type":"web_search_tool_result_error","error_code":…})
            (r["content"] as? JsonArray)?.forEach { item ->
                if (item["type"].str == "web_search_result") item["url"].str?.let { url -> order.getOrPut(url) { item["title"].str ?: "" } }
            }
        }
        val numbers = order.keys.withIndex().associate { (i, url) -> url to i + 1 }
        val sources = order.entries.mapIndexed { i, (url, title) -> AnswerSource(i + 1, title.ifBlank { SourceKinds.domainOf(url) }, url, SourceKinds.domainOf(url)) }
        val text = StringBuilder()
        val citations = mutableListOf<Citation>()
        texts.forEach { b ->
            val start = text.length
            text.append(b["text"].str ?: "")
            val cs = b["citations"].arr.filter { it["url"].str != null }
            if (cs.isNotEmpty() && text.length > start) {
                citations += Citation(
                    start, text.length, cs.mapNotNull { numbers[it["url"].str] }.distinct().sorted(), Citation.Anchor.EXACT,
                    evidence = cs.mapNotNull { it["cited_text"].str },
                )
            }
        }
        // the uncited search results stay listed after the cited ones
        return GroundedAnswer(AiProvider.CLAUDE, model, AnswerComposer.compose(text.toString(), citations, missingQuote = null), sources, notes = notes)
    }

    /** The error codes of search attempts that failed (for the "no sources" message). */
    fun searchErrors(content: List<JsonElement>): List<String> = content.filter { it["type"].str == "web_search_tool_result" }
        .mapNotNull { r -> (r["content"] as? JsonObject)?.let { it["error_code"].str } }
}
