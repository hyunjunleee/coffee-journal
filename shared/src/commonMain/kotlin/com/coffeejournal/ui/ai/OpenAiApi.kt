package com.coffeejournal.ui.ai

import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** OpenAI Responses API with the web_search tool (search forced, sources included, location Korea). */
object OpenAiApi {
    const val RESPONSES = "https://api.openai.com/v1/responses"
    const val MODELS = "https://api.openai.com/v1/models"

    fun headers(key: String, json: Boolean = true): Map<String, String> =
        if (json) mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json") else mapOf("Authorization" to "Bearer $key")

    fun body(model: String, instructions: String, input: String): String = buildJsonObject {
        put("model", model)
        put("instructions", instructions)
        put("input", input)
        putJsonArray("tools") {
            addJsonObject {
                put("type", "web_search")
                // without it the tool assumes the United States
                putJsonObject("user_location") { put("type", "approximate"); put("country", "KR") }
            }
        }
        put("tool_choice", "required")
        putJsonArray("include") { add("web_search_call.action.sources") }
    }.toString()

    fun request(key: String, model: String, question: String) =
        AiHttpRequest(RESPONSES, headers(key), body(model, NoteHelperPrompts.SEARCH_SYSTEM, question))

    fun modelsRequest(key: String) = AiHttpRequest(MODELS, headers(key, json = false))

    /** The model ids of a GET /v1/models reply (Anthropic's has the same "data" list). */
    fun modelIds(body: String): List<String>? = AiJson.parseObject(body)?.let { root -> root["data"].arr.mapNotNull { it["id"].str } }

    data class Annotation(val start: Int, val end: Int, val url: String, val title: String)

    /** Null when [body] is not a response object. */
    fun parse(body: String): Pair<String, List<Annotation>>? {
        val root = AiJson.parseObject(body) ?: return null
        if (root["output"] == null) return null
        val text = StringBuilder()
        val annotations = mutableListOf<Annotation>()
        root["output"].arr.filter { it["type"].str == "message" }.forEach { message ->
            message["content"].arr.filter { it["type"].str == "output_text" }.forEach { part ->
                if (text.isNotEmpty()) text.append("\n\n")
                val offset = text.length
                val t = part["text"].str ?: ""
                text.append(t)
                part["annotations"].arr.filter { it["type"].str == "url_citation" }.forEach { a ->
                    val url = a["url"].str ?: return@forEach
                    val s = (a["start_index"].int ?: 0).coerceIn(0, t.length)
                    val e = (a["end_index"].int ?: s).coerceIn(s, t.length)
                    annotations += Annotation(offset + s, offset + e, url, a["title"].str ?: "")
                }
            }
        }
        return text.toString() to annotations
    }

    /** "([sca.coffee](https://…))" style links the model writes inline, which the app shows as [n] marks instead. */
    private val INLINE_LINK = Regex("""\[[^\]\n]*]\(https?://[^)\s]*\)""")

    /**
     * The answer with its url_citation annotations as [n] marks at the end of the sentence each one ends in; sources
     * are numbered in the order they are first cited. An annotated inline markdown link is taken out of the text.
     */
    fun answer(text: String, annotations: List<Annotation>, model: String, notes: List<String> = emptyList()): GroundedAnswer {
        // character ranges to take out (the inline link, its parentheses and the space before it)
        val removed = annotations.mapNotNull { a ->
            val covered = text.substring(a.start, a.end)
            if (!INLINE_LINK.containsMatchIn(covered)) return@mapNotNull null
            var s = a.start
            var e = a.end
            if (s > 0 && text[s - 1] == '(' && e < text.length && text[e] == ')') { s--; e++ }
            while (s > 0 && text[s - 1] == ' ') s--
            s until e
        }.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, r ->
            val last = acc.lastOrNull()
            if (last != null && r.first <= last.last + 1) acc[acc.lastIndex] = last.first..maxOf(last.last, r.last) else acc += r
            acc
        }
        fun mapIndex(p: Int): Int {
            var shift = 0
            for (r in removed) {
                if (p <= r.first) break
                shift += if (p > r.last) r.last - r.first + 1 else p - r.first
            }
            return p - shift
        }
        val clean = StringBuilder()
        var last = 0
        removed.forEach { r -> clean.appendRange(text, last, r.first); last = r.last + 1 }
        clean.appendRange(text, last, text.length)

        val numbers = LinkedHashMap<String, Int>()
        val sources = mutableListOf<AnswerSource>()
        val citations = annotations.map { a ->
            val key = sourceKey(a.url)
            val n = numbers.getOrPut(key) {
                (numbers.size + 1).also { sources += AnswerSource(it, a.title.ifBlank { SourceKinds.domainOf(a.url) }, a.url, SourceKinds.domainOf(a.url)) }
            }
            val at = mapIndex(a.end)
            Citation(at, at, listOf(n), Citation.Anchor.SENTENCE_END)
        }
        return GroundedAnswer(AiProvider.OPENAI, model, AnswerComposer.compose(clean.toString(), citations, missingQuote = null), sources, notes = notes)
    }

    /** One source per page: OpenAI adds utm_source=openai to the links. */
    private fun sourceKey(url: String): String = url.replace(Regex("""[?&]utm_source=openai"""), "").trimEnd('/', '?')
}
