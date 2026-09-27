package com.coffeejournal.ui.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A Tavily search result: [content] is its chunks (≤ 500 characters each, joined by " [...] "), [rawContent] the page text. */
data class TavilyResult(val title: String, val url: String, val content: String, val rawContent: String? = null, val score: Double? = null) {
    val domain: String get() = SourceKinds.domainOf(url)
}

/** One Tavily search of a question: [depth] "basic" (1 credit) or "advanced" (2); [domains] limits it to those sites. */
data class TavilySearch(val query: String, val depth: String, val domains: List<String> = emptyList()) {
    val people: Boolean get() = domains.isNotEmpty()
}

/**
 * Tavily /search (Bearer key): the searches of one question ([TavilyPlan]), and one basic search with one result for
 * the key check (1 credit). Every search asks for 5 results with 3 passages each, as the evaluation measured them
 * (Tavily accepts chunks_per_source on basic too).
 */
object TavilyApi {
    const val URL = "https://api.tavily.com/search"

    /** At most this many pages go to Gemini (three searches of five). */
    const val MAX_SOURCES = 15

    fun headers(key: String) = mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json")

    fun searchBody(search: TavilySearch): String = buildJsonObject {
        put("query", search.query)
        put("search_depth", search.depth)
        put("max_results", 5)
        put("chunks_per_source", 3)
        put("include_raw_content", "text")
        put("include_answer", false)
        if (search.domains.isNotEmpty()) putJsonArray("include_domains") { search.domains.forEach { add(it) } }
    }.toString()

    fun checkBody(): String = buildJsonObject {
        put("query", "coffee tasting notes")
        put("search_depth", "basic")
        put("max_results", 1)
    }.toString()

    fun request(key: String, search: TavilySearch) = AiHttpRequest(URL, headers(key), searchBody(search))
    fun checkRequest(key: String) = AiHttpRequest(URL, headers(key), checkBody())

    /** The searches' results in the given order, each page once (trailing slash and case aside), at most [cap]. */
    fun merge(searches: List<List<TavilyResult>>, cap: Int = MAX_SOURCES): List<TavilyResult> {
        val seen = HashSet<String>()
        return searches.flatten().filter { seen.add(it.url.trimEnd('/').lowercase()) }.take(cap)
    }

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

/**
 * What Tavily is asked for one question (Gemini 무료 + Tavily), from 설정 › 검색 and 사람들 의견 (evaluation runs
 * #14-#16, plan 12.3): the main query at the chosen depth(s), then with 사람들 의견 one basic search limited to Korean
 * blogs, in Korean.
 */
object TavilyPlan {
    /** Where home-café posts are: Korean blogs and a café community. */
    val BLOGS = listOf("blog.naver.com", "tistory.com", "brunch.co.kr", "cafe.naver.com")

    fun searches(main: String, q: NoteQuestion, depth: SearchDepth, people: Boolean): List<TavilySearch> =
        depth.depths.map { TavilySearch(main, it) } + if (people) listOf(TavilySearch(koreanQuery(q), "basic", BLOGS)) else emptyList()

    /** The blog search's words (eval.py korean_query): the note's Korean name, or the description, as posts word it. */
    fun koreanQuery(q: NoteQuestion): String = when (q.mode) {
        NoteMode.NOTE -> "커피 원두 ${NoteHelperPrompts.koreanName(q.query)} 노트 후기"
        NoteMode.DESCRIBE -> "커피 원두 후기 ${q.query.trim()}"
    }

    /** Questions a month on Tavily's free 1,000 credits, rounded down to tens. */
    fun questionsPerMonth(credits: Int): Int = 1000 / credits.coerceAtLeast(1) / 10 * 10

    /**
     * The pages in the order Gemini gets them: advanced results first, then basic's new pages, then the blogs; each
     * page once, at most [TavilyApi.MAX_SOURCES].
     */
    fun order(found: List<Pair<TavilySearch, List<TavilyResult>>>): List<TavilyResult> {
        val main = found.filter { !it.first.people }.sortedBy { if (it.first.depth == "advanced") 0 else 1 }
        return TavilyApi.merge((main + found.filter { it.first.people }).map { it.second })
    }
}
