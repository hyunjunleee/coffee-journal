package com.coffeejournal.ui.ai

import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Gemini generateContent (v1beta, key in the x-goog-api-key header): the request, the reply, and for Google Search
 * grounding the sources and where the answer is tied to them.
 */
object GeminiApi {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    /** The endpoint for [model] ("models/" prefix optional; anything outside [A-Za-z0-9._-] is percent-encoded). */
    fun url(model: String): String = BASE + encodePath(model.trim().removePrefix("models/")) + ":generateContent"

    fun headers(key: String) = mapOf("Content-Type" to "application/json", "x-goog-api-key" to key)

    /** [search]: add the google_search tool (GEMINI_SEARCH). [maxOutputTokens]: the key check's tiny request. */
    fun body(system: String?, user: String, search: Boolean, maxOutputTokens: Int? = null): String = buildJsonObject {
        if (system != null) putJsonObject("system_instruction") { putJsonArray("parts") { addJsonObject { put("text", system) } } }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") { addJsonObject { put("text", user) } }
            }
        }
        if (search) putJsonArray("tools") { addJsonObject { putJsonObject("google_search") { } } }
        putJsonObject("generationConfig") {
            if (maxOutputTokens != null) put("maxOutputTokens", maxOutputTokens) else put("temperature", 0.2)
        }
    }.toString()

    fun request(key: String, model: String, system: String?, user: String, search: Boolean, maxOutputTokens: Int? = null) =
        AiHttpRequest(url(model), headers(key), body(system, user, search, maxOutputTokens))

    /**
     * The query step (GEMINI_TAVILY): no tools, temperature 0, a short JSON answer {"queries": [...]} held to a schema
     * (structured output without tools works on the free tier).
     */
    fun queryBody(system: String, user: String): String = buildJsonObject {
        putJsonObject("system_instruction") { putJsonArray("parts") { addJsonObject { put("text", system) } } }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") { addJsonObject { put("text", user) } }
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", 0)
            put("maxOutputTokens", 200)
            put("responseMimeType", "application/json")
            putJsonObject("responseSchema") {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("queries") {
                        put("type", "ARRAY")
                        putJsonObject("items") { put("type", "STRING") }
                    }
                }
                putJsonArray("required") { add("queries") }
            }
        }
    }.toString()

    fun queryRequest(key: String, model: String, system: String, user: String) = AiHttpRequest(url(model), headers(key), queryBody(system, user))

    data class Chunk(val uri: String, val title: String)
    data class Support(val text: String, val startIndex: Int, val endIndex: Int, val chunks: List<Int>)
    data class Grounding(val queries: List<String>, val entryPointHtml: String?, val chunks: List<Chunk>, val supports: List<Support>)
    data class Reply(val text: String, val finishReason: String?, val blockReason: String?, val grounding: Grounding?)

    /** Null when [body] is not a generateContent reply at all. */
    fun parse(body: String): Reply? {
        val root = AiJson.parseObject(body) ?: return null
        val blockReason = root["promptFeedback"]["blockReason"].str
        // no candidate at all: the prompt was blocked (promptFeedback) or nothing came back
        val cand = root["candidates"].arr.firstOrNull() ?: return Reply("", null, blockReason ?: "NO_CANDIDATE", null)
        // thought parts (only sent with includeThoughts) are not the answer
        val text = cand["content"]["parts"].arr.filter { it["thought"].bool != true }.mapNotNull { it["text"].str }.joinToString("")
        val gm = cand["groundingMetadata"]
        val grounding = gm?.let {
            Grounding(
                queries = gm["webSearchQueries"].arr.mapNotNull { it.str },
                entryPointHtml = gm["searchEntryPoint"]["renderedContent"].str?.takeIf { it.isNotBlank() },
                chunks = gm["groundingChunks"].arr.map { c -> Chunk(c["web"]["uri"].str ?: "", c["web"]["title"].str ?: "") },
                supports = gm["groundingSupports"].arr.map { s ->
                    val seg = s["segment"]
                    Support(seg["text"].str ?: "", seg["startIndex"].int ?: 0, seg["endIndex"].int ?: 0, s["groundingChunkIndices"].arr.mapNotNull { it.int })
                },
            )
        }
        return Reply(text, cand["finishReason"].str, blockReason, grounding)
    }

    private val BLOCKING = setOf("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY")

    /** The reply was stopped by a filter and has no answer to show. */
    fun blocked(reply: Reply): String? = when {
        reply.blockReason != null -> reply.blockReason
        reply.text.isBlank() && reply.finishReason in BLOCKING -> reply.finishReason
        else -> null
    }

    /**
     * A Google-grounded reply as a [GroundedAnswer]: every source the grounding lists, in its order, and a mark after
     * each grounded segment, like Google's own citation example. The answer text itself is left as it is.
     */
    fun groundedAnswer(reply: Reply, model: String): GroundedAnswer {
        val g = reply.grounding
        val sources = g?.chunks?.mapIndexed { i, c ->
            AnswerSource(i + 1, c.title.ifBlank { SourceKinds.domainOf(c.uri) }, c.uri, SourceKinds.domainOfTitleOrUrl(c.title, c.uri))
        } ?: emptyList()
        var cursor = 0
        val citations = g?.supports?.mapNotNull { s ->
            val range = locate(reply.text, s, cursor) ?: return@mapNotNull null
            cursor = range.last + 1
            val numbers = s.chunks.filter { it in sources.indices }.map { it + 1 }.distinct()
            Citation(range.first, range.last + 1, numbers, Citation.Anchor.EXACT)
        } ?: emptyList()
        return GroundedAnswer(
            AiProvider.GEMINI_SEARCH, model, AnswerComposer.compose(reply.text, citations, missingQuote = null), sources,
            searchSuggestionsHtml = g?.entryPointHtml, notes = truncationNotes(reply.finishReason),
            queries = g?.queries?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList(),
        )
    }

    fun truncationNotes(finishReason: String?): List<String> =
        if (finishReason == "MAX_TOKENS") listOf("답이 길어 끝이 잘렸어요.") else emptyList()

    /**
     * Where a grounding segment sits in [text], as character indices. The docs disagree on whether startIndex / endIndex
     * count characters or UTF-8 bytes, so the segment's own text is looked for first (from [cursor] on, then from the
     * start); only when it is not found are the indices read as UTF-8 byte offsets.
     */
    fun locate(text: String, s: Support, cursor: Int = 0): IntRange? {
        if (s.text.isNotEmpty()) {
            val at = text.indexOf(s.text, cursor.coerceIn(0, text.length)).takeIf { it >= 0 } ?: text.indexOf(s.text)
            if (at >= 0) return at until at + s.text.length
        }
        val start = utf8ToCharIndex(text, s.startIndex)
        val end = utf8ToCharIndex(text, s.endIndex)
        return if (end > start) start until end else null
    }

    /** The character index at UTF-8 byte offset [byteOffset] of [text] (clamped; a split character counts as passed). */
    fun utf8ToCharIndex(text: String, byteOffset: Int): Int {
        if (byteOffset <= 0) return 0
        var bytes = 0
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val (width, chars) = when {
                ch.code < 0x80 -> 1 to 1
                ch.code < 0x800 -> 2 to 1
                ch.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> 4 to 2
                else -> 3 to 1
            }
            if (bytes + width > byteOffset) return if (bytes == byteOffset) i else i + chars
            bytes += width
            i += chars
            if (bytes == byteOffset) return i
        }
        return text.length
    }

    private fun encodePath(segment: String): String = buildString {
        segment.encodeToByteArray().forEach { b ->
            val c = b.toInt().toChar()
            if (b >= 0 && (c.isLetterOrDigit() || c in "._-")) append(c)
            else append('%').append(((b.toInt() and 0xFF) or 0x100).toString(16).substring(1).uppercase())
        }
    }
}
