package com.coffeejournal.ui.ai

/**
 * An answer tied to web sources, whatever service wrote it: the answer's text in paragraphs of runs (sentences and
 * the gaps between them) with [n] source numbers after the cited ones, the numbered sources, and Google's Search
 * Suggestions for a Google-grounded answer. The text is the service's own; the app only adds the numbers.
 */
data class GroundedAnswer(
    val provider: AiProvider,
    val model: String,
    val paragraphs: List<AnswerParagraph>,
    val sources: List<AnswerSource>,
    /** searchEntryPoint.renderedContent (GEMINI_SEARCH only), to be shown with the answer. */
    val searchSuggestionsHtml: String? = null,
    /** Short notes about the answer itself ("답이 길어 끝이 잘렸어요"). */
    val notes: List<String> = emptyList(),
) {
    /** The answer's text without the [n] numbers (term matching reads this). */
    val plainText: String get() = paragraphs.joinToString("\n") { p -> p.runs.joinToString("") { it.text } }
    val citedRuns: Int get() = paragraphs.sumOf { p -> p.runs.count { it.cited } }
}

/** One line of the answer (a list item or a paragraph). */
data class AnswerParagraph(val runs: List<AnswerRun>, val quotes: List<QuoteCheck> = emptyList())

/**
 * A piece of a line: a sentence (or the part of it up to a citation mark) or the gap before / between sentences
 * ([gap]: spaces, a list bullet). [cited] runs belong to a sentence tied to a source; the others are shown grey.
 * [markers] are the 1-based source numbers shown right after this run.
 */
data class AnswerRun(val text: String, val cited: Boolean, val gap: Boolean = false, val markers: List<Int> = emptyList())

enum class QuoteStatus { FOUND, NOT_FOUND }

/** A double-quoted expression in a cited sentence and whether the cited source's text contains it word for word. */
data class QuoteCheck(val quote: String, val status: QuoteStatus, val source: Int? = null)

enum class SourceKind { INSTITUTION, PERSONAL, OTHER }

data class AnswerSource(val number: Int, val title: String, val url: String, val domain: String) {
    val kind: SourceKind get() = SourceKinds.of(domain)
}

/** Institution / personal-page badges for the source list (same lists as tools/ai-eval/eval.py). */
object SourceKinds {
    val INSTITUTIONS = listOf(
        "sca.coffee", "worldcoffeeresearch.org", "coffeeinstitute.org", "allianceforcoffeeexcellence.org",
        "cupofexcellence.org", "ico.org", "ncausa.org",
    )
    val PERSONAL = listOf(
        "blog.naver.com", "cafe.naver.com", "tistory.com", "brunch.co.kr", "velog.io", "medium.com", "reddit.com",
        "quora.com", "instagram.com", "youtube.com", "facebook.com", "x.com", "twitter.com",
    )

    fun of(domain: String): SourceKind {
        val d = domain.lowercase().removePrefix("www.")
        fun within(list: List<String>) = list.any { d == it || d.endsWith(".$it") }
        return when {
            within(INSTITUTIONS) -> SourceKind.INSTITUTION
            within(PERSONAL) -> SourceKind.PERSONAL
            else -> SourceKind.OTHER
        }
    }

    /** The host of [url] without "www." ("" when it has none). */
    fun domainOf(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').substringBefore(':')
        return host.lowercase().removePrefix("www.")
    }

    /** Grounding chunk links are redirects; their title is usually the site's domain (eval.py domain_of). */
    fun domainOfTitleOrUrl(title: String, url: String): String {
        val t = title.trim().lowercase()
        return if (Regex("[a-z0-9.-]+\\.[a-z]{2,}").matches(t)) t.removePrefix("www.") else domainOf(url)
    }
}

/**
 * Where a service ties part of the answer to sources: a character range of the answer text ([start] == [end] is a
 * point) with 1-based source numbers. [evidence] is the text the quotes in the tied sentences are checked against
 * (Tavily: the pages; Claude: the cited excerpts); empty means no quote check.
 */
data class Citation(
    val start: Int,
    val end: Int,
    val sources: List<Int>,
    val anchor: Anchor = Anchor.SENTENCE_END,
    val evidence: List<String> = emptyList(),
) {
    enum class Anchor {
        /** The numbers go after the sentence the citation ends in (OpenAI, the [n] marks Gemini wrote after Tavily). */
        SENTENCE_END,

        /** The numbers go exactly at [end] (Gemini's grounding segments, Claude's cited text blocks). */
        EXACT,
    }
}

/** Builds [AnswerParagraph]s from an answer text and its [Citation]s: sentence-level grey / cited, [n] marks, quotes. */
object AnswerComposer {
    /**
     * [missingQuote]: the status of a quote the evidence does not contain (Tavily: NOT_FOUND; Claude: null, no badge,
     * since its 150-character excerpts are no proof of absence).
     */
    fun compose(text: String, citations: List<Citation>, missingQuote: QuoteStatus? = QuoteStatus.NOT_FOUND): List<AnswerParagraph> {
        val spans = Sentences.split(text)
        val sentenceIdx = spans.indices.filter { !spans[it].gap }
        val citedBy = HashMap<Int, MutableList<Citation>>()
        // (kotlin.collections only: this is common code, the sorted JVM collections do not exist on iOS)
        val markers = HashMap<Int, MutableSet<Int>>()

        fun pointSentence(p: Int): Int? = sentenceIdx.lastOrNull { spans[it].start < p } ?: sentenceIdx.firstOrNull()

        for (c in citations) {
            if (c.sources.isEmpty()) continue
            val s = c.start.coerceIn(0, text.length)
            val e = c.end.coerceIn(s, text.length)
            val tied = if (s < e) sentenceIdx.filter { spans[it].start < e && s < spans[it].end } else listOfNotNull(pointSentence(s))
            val tiedOrPoint = tied.ifEmpty { listOfNotNull(pointSentence(e)) }
            tiedOrPoint.forEach { citedBy.getOrPut(it) { mutableListOf() } += c }
            val at = when (c.anchor) {
                Citation.Anchor.EXACT -> trimTrailingSpace(text, e)
                Citation.Anchor.SENTENCE_END -> tiedOrPoint.lastOrNull()?.let { spans[it].end } ?: e
            }
            markers.getOrPut(at) { mutableSetOf() } += c.sources
        }

        // cut the text at sentence edges, marks and line breaks
        val cuts = mutableSetOf(0, text.length)
        spans.forEach { cuts += it.start; cuts += it.end }
        cuts += markers.keys
        val paragraphs = mutableListOf<AnswerParagraph>()
        var line = mutableListOf<AnswerRun>()
        var lineQuotes = mutableListOf<QuoteCheck>()
        val quotedSentences = HashSet<Int>()
        fun endLine() {
            if (line.any { it.text.isNotBlank() }) paragraphs += AnswerParagraph(line, lineQuotes)
            line = mutableListOf(); lineQuotes = mutableListOf()
        }
        val points = cuts.sorted()
        for (k in 0 until points.size - 1) {
            val a = points[k]
            val b = points[k + 1]
            if (a >= b) continue
            val spanIndex = spans.indexOfFirst { it.start <= a && a < it.end }
            val span = spans.getOrNull(spanIndex)
            val gap = span?.gap ?: true
            val cited = !gap && citedBy.containsKey(spanIndex)
            // a gap may hold a line break: split it there
            var from = a
            val piece = text.substring(a, b)
            piece.forEachIndexed { i, ch ->
                if (ch == '\n') {
                    if (a + i > from) line += AnswerRun(text.substring(from, a + i), cited, gap)
                    endLine()
                    from = a + i + 1
                }
            }
            val marks = markers[b]?.sorted() ?: emptyList()
            if (b > from) line += AnswerRun(text.substring(from, b), cited, gap, marks)
            else if (marks.isNotEmpty()) {
                // a mark right after a line break: keep it on the line it follows
                val last = paragraphs.lastOrNull()
                if (line.isEmpty() && last != null) {
                    val runs = last.runs.toMutableList()
                    runs[runs.lastIndex] = runs.last().let { it.copy(markers = (it.markers + marks).distinct().sorted()) }
                    paragraphs[paragraphs.lastIndex] = last.copy(runs = runs)
                }
            }
            if (cited && spanIndex !in quotedSentences) {
                quotedSentences += spanIndex
                lineQuotes += QuoteChecks.check(text.substring(span!!.start, span.end), citedBy.getValue(spanIndex), missingQuote)
            }
        }
        endLine()
        return paragraphs
    }

    /** A mark placed at [end] goes before trailing spaces or a line break, next to the last word. */
    private fun trimTrailingSpace(text: String, end: Int): Int {
        var e = end
        while (e > 0 && text[e - 1].isWhitespace()) e--
        return e
    }
}

/** Splits an answer into sentences and gaps (whitespace, list bullets), never inside a quotation. */
object Sentences {
    data class Span(val start: Int, val end: Int, val gap: Boolean)

    private val BULLET = Regex("""^[ \t]*(?:[-*•·]|\d{1,2}[.)])[ \t]+""")
    private const val ENDS = ".!?。！？"
    private const val CLOSERS = "\"'”’)]）」』"

    fun split(text: String): List<Span> {
        val out = mutableListOf<Span>()
        var i = 0
        while (i < text.length) {
            val lineEnd = text.indexOf('\n', i).let { if (it < 0) text.length else it }
            splitLine(text, i, lineEnd, out)
            if (lineEnd < text.length) out += Span(lineEnd, lineEnd + 1, gap = true)
            i = lineEnd + 1
        }
        return out
    }

    private fun splitLine(text: String, from: Int, to: Int, out: MutableList<Span>) {
        var i = from
        BULLET.find(text.substring(from, to))?.let { m -> out += Span(from, from + m.value.length, gap = true); i = from + m.value.length }
        while (i < to) {
            // leading whitespace is a gap
            var s = i
            while (s < to && text[s].isWhitespace()) s++
            if (s > i) out += Span(i, s, gap = true)
            if (s >= to) return
            var j = s
            var inStraight = false
            var curlyDepth = 0
            var end = to
            while (j < to) {
                val ch = text[j]
                when (ch) {
                    '"' -> inStraight = !inStraight
                    '“' -> curlyDepth++
                    '”' -> if (curlyDepth > 0) curlyDepth--
                }
                if (ch in ENDS && !inStraight && curlyDepth == 0) {
                    var k = j + 1
                    while (k < to && text[k] in ENDS) k++
                    while (k < to && text[k] in CLOSERS && text[k] != '"') k++
                    // a decimal point ("1.5") or an abbreviation joined to the next word does not end a sentence
                    if (k >= to || text[k].isWhitespace()) { end = k; break }
                }
                j++
            }
            out += Span(s, end, gap = false)
            i = end
        }
    }
}

/** Finds the double-quoted expressions of a sentence and checks each against the citation's evidence. */
object QuoteChecks {
    private val QUOTE = Regex("""["“]([^"“”\n]{2,200})["”]""")

    fun quotesIn(sentence: String): List<String> = QUOTE.findAll(sentence).map { it.groupValues[1].trim() }.filter { it.length >= 2 }.toList()

    fun check(sentence: String, citations: List<Citation>, missing: QuoteStatus?): List<QuoteCheck> {
        val withEvidence = citations.filter { it.evidence.isNotEmpty() }
        if (withEvidence.isEmpty()) return emptyList()
        return quotesIn(sentence).mapNotNull { quote ->
            val q = normalize(quote.trimEnd('.', '…', ' ').ifEmpty { quote })
            if (q.isEmpty()) return@mapNotNull null
            val hit = withEvidence.firstOrNull { c -> c.evidence.any { normalize(it).contains(q) } }
            when {
                hit != null -> QuoteCheck(quote, QuoteStatus.FOUND, hit.sources.firstOrNull())
                missing != null -> QuoteCheck(quote, missing, withEvidence.first().sources.firstOrNull())
                else -> null
            }
        }
    }

    /** NFC, lower case, one space for any run of whitespace, straight quotes for curly ones. */
    fun normalize(text: String): String = normalizeNfc(text)
        .replace(Regex("[“”„‟″]"), "\"")
        .replace(Regex("[‘’‚‛′]"), "'")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
}
