package com.coffeejournal.ui.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A Tavily search result: [content] is its chunks (≤ 500 characters each, joined by " [...] "), [rawContent] the page text. */
data class TavilyResult(val title: String, val url: String, val content: String, val rawContent: String? = null, val score: Double? = null) {
    val domain: String get() = SourceKinds.domainOf(url)
}

/**
 * Tavily /search (Bearer key): one search per question at the depth chosen in 설정 (정밀 = advanced, 2 credits; 기본 =
 * basic, 1 credit; a second query added a source but no more cited content in the evaluation), and one basic search
 * with one result for the key check (1 credit).
 */
object TavilyApi {
    const val URL = "https://api.tavily.com/search"

    fun headers(key: String) = mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json")

    fun searchBody(query: String, depth: SearchDepth = SearchDepth.DEFAULT): String = buildJsonObject {
        put("query", query)
        put("search_depth", depth.tavily)
        put("max_results", 5)
        // passages around the term per page: advanced only
        if (depth == SearchDepth.PRECISE) put("chunks_per_source", 3)
        put("include_raw_content", "text")
        put("include_answer", false)
    }.toString()

    fun checkBody(): String = buildJsonObject {
        put("query", "coffee tasting notes")
        put("search_depth", "basic")
        put("max_results", 1)
    }.toString()

    fun request(key: String, query: String, depth: SearchDepth = SearchDepth.DEFAULT) = AiHttpRequest(URL, headers(key), searchBody(query, depth))
    fun checkRequest(key: String) = AiHttpRequest(URL, headers(key), checkBody())

    /** Null when [body] is not a search reply; results without a URL are left out. */
    fun parse(body: String): List<TavilyResult>? {
        val root = AiJson.parseObject(body) ?: return null
        if (root["results"] == null) return null
        return root["results"].arr.mapNotNull { r ->
            val url = r["url"].str?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TavilyResult(r["title"].str ?: "", url, r["content"].str ?: "", r["raw_content"].str, (r["score"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull())
        }
    }
}

/**
 * GEMINI_TAVILY's answer: Gemini wrote [n] after the sentences that use page n (sources_prompt_ko.txt). The marks are
 * taken out of the text and shown by the app instead; every double-quoted expression in a cited sentence is looked up
 * in the page it cites (raw_content, else content).
 */
object SourcedAnswer {
    /** "[1]", "[1][3]", "[1, 2]", with the space before it when the mark stands apart ("…이다 [1]."). */
    private val MARK = Regex("""[ \t]*\[(\d{1,2}(?:\s*[,，]\s*\d{1,2})*)]""")

    data class Marked(val text: String, val marks: List<Pair<Int, List<Int>>>)

    /** Removes the [n] marks: the text without them, and where each stood (index in the new text) with its numbers. */
    fun stripMarks(text: String): Marked {
        val out = StringBuilder()
        val marks = mutableListOf<Pair<Int, List<Int>>>()
        var last = 0
        for (m in MARK.findAll(text)) {
            val next = text.getOrNull(m.range.last + 1)
            // keep the space when the mark sits between two words ("이다 [1]다음" is rare, but must not become "이다다음")
            val keepSpace = m.value.first().isWhitespace() && next != null && !next.isWhitespace() && next !in ".,;:!?)]}。、"
            out.appendRange(text, last, m.range.first)
            if (keepSpace) out.append(' ')
            val numbers = m.groupValues[1].split(',', '，').mapNotNull { it.trim().toIntOrNull() }
            marks += out.length to numbers
            last = m.range.last + 1
        }
        out.appendRange(text, last, text.length)
        return Marked(out.toString(), marks)
    }

    fun answer(geminiText: String, results: List<TavilyResult>, model: String, notes: List<String> = emptyList(), queries: List<String> = emptyList()): GroundedAnswer {
        val marked = stripMarks(geminiText)
        val sources = results.mapIndexed { i, r -> AnswerSource(i + 1, r.title.ifBlank { r.domain }, r.url, r.domain) }
        val citations = marked.marks.mapNotNull { (at, numbers) ->
            val valid = numbers.filter { it in 1..results.size }.distinct()
            if (valid.isEmpty()) return@mapNotNull null
            Citation(at, at, valid, Citation.Anchor.SENTENCE_END, evidence = valid.map { n -> results[n - 1].let { it.rawContent ?: it.content } })
        }
        val paragraphs = AnswerComposer.compose(marked.text, citations, missingQuote = QuoteStatus.NOT_FOUND)
        return GroundedAnswer(AiProvider.GEMINI_TAVILY, model, paragraphs, sources, notes = notes, queries = queries)
    }
}

/**
 * The query step's answer (GEMINI_TAVILY): one short English query in {"queries": [...]}, or the query the user
 * corrected on the answer screen.
 */
object SearchQueries {
    /** One search per question: a second one added a source but no more cited content (plan 12.3). */
    const val MAX = 1
    const val MAX_LENGTH = 200

    /**
     * The query in Gemini's JSON reply, or null when there is no usable one (not JSON, no list, only blank strings, a
     * query over [MAX_LENGTH] characters): the caller then searches with the fixed template instead. Only the first
     * query is kept; quotes around a whole query are taken off.
     */
    fun parse(reply: String?): List<String>? {
        val text = reply?.trim()?.removePrefix("```json")?.removePrefix("```")?.removeSuffix("```")?.trim() ?: return null
        val list = AiJson.parseObject(text)?.get("queries") as? kotlinx.serialization.json.JsonArray ?: return null
        val queries = list.mapNotNull { it.str }.map(::unquote).filter { it.isNotEmpty() }
        if (queries.isEmpty() || queries.any { it.length > MAX_LENGTH }) return null
        return queries.distinctBy { it.lowercase() }.take(MAX)
    }

    /** What the user typed under "검색어 고치기": the first non-blank line, spaces tidied, cut to [MAX_LENGTH]. */
    fun fromTyped(text: String): List<String> =
        text.lines().map { unquote(it).take(MAX_LENGTH).trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(MAX)

    private fun unquote(q: String): String {
        val t = q.trim().replace(Regex("\\s+"), " ")
        return if (t.length >= 2 && t.first() in "\"“" && t.last() in "\"”" && t.count { it == '"' || it == '“' || it == '”' } == 2) t.substring(1, t.length - 1).trim() else t
    }
}

