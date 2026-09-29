package com.coffeejournal.ui.ai

import com.coffeejournal.domain.reference.FlavorWheel
import com.coffeejournal.domain.reference.FlavorWheelExtras
import com.coffeejournal.domain.reference.NoteCategories
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Mode B's "노트 후보": the app's own vocabulary found in an answer, in the order it appears, each once. English
 * flavor-wheel terms — the wheel's own and the app's unofficial ones ([FlavorWheelExtras], e.g. "Plum", which an
 * answer often quotes from a roaster) — match case-insensitively as whole words ("Black Tea", not "blackberry"); the
 * wheel's inner-tier labels ("Other", "Sweet") are left out as too common. Korean note categories
 * and their keywords match at the start of a word, the longest first ("블루베리" is not also "베리"). A one-syllable
 * keyword ("꿀", "풀", "흙") must also end its word or be followed by a particle, so "풀바디" is not "풀".
 * Left out as well: a note said not to be there ("not sour", "no bitterness", "without rubber"), and wheel words that
 * in a coffee text mostly mean something else ("roast date", the Bourbon variety, "fresh roast", "raw beans",
 * "mineral water").
 */
object NoteTerms {
    private class Term(val label: String, val regex: Regex)

    private val PARTICLES = "은는이가을를의와과도로에향맛내처같냄즙"

    /** Wheel words that in an answer mostly are not notes: "roast date", the Bourbon variety, "fresh roast", "raw". */
    private val NOT_FROM_ANSWERS = setOf("date", "bourbon", "fresh", "raw")

    /** What must not follow a word for it to be a note: "mineral water", "mineral content". */
    private val NOT_FOLLOWED_BY = mapOf("mineral" to Regex("(?i)^\\s+(?:water|content)"))

    /** The words before a note that say it is not there: "not (too) sour", "no bitterness", "without the rubber". */
    private val NEGATED = Regex("(?i)(?:\\bnot|\\bno|\\bwithout|\\bnever|\\bless|n't)\\s+(?:(?:too|very|overly|really|any|the|a|much)\\s+)*$")

    /** What after a Korean note says it is not there: "신맛이 없고", "꽃향은 아니고", "쓴맛은 느껴지지 않고" is left in. */
    private val NEGATED_AFTER_KO = Regex("^(?:향|맛)?[은는이가도을를]?\\s*(?:없|아니|아닌)")

    private val terms: List<Term> by lazy {
        val english = (FlavorWheel.allTerms + FlavorWheelExtras.allTerms).distinct().filter { it.lowercase() !in NOT_FROM_ANSWERS }.map { t ->
            Term(t, Regex("(?<![A-Za-z])${pattern(t)}(?![A-Za-z])", RegexOption.IGNORE_CASE))
        }
        val korean = NoteCategories.all.flatMap { c -> c.subs.flatMap { s -> listOf(s.name) + s.keywords } }.distinct().map { k ->
            val tail = if (k.length == 1) "(?:(?![가-힣])|(?=[$PARTICLES]))" else ""
            Term(k, Regex("(?<![가-힣])${pattern(k)}$tail"))
        }
        english + korean
    }

    /** [term] as a pattern: spaces optional ("다크 초콜릿" = "다크초콜릿"), "/" with or without spaces, "-" or a space. */
    private fun pattern(term: String): String = buildString {
        term.trim().forEach { ch ->
            when {
                ch.isWhitespace() -> if (!endsWith("\\s*")) append("\\s*")
                ch == '/' -> append("\\s*/\\s*")
                ch == '-' -> append("[-\\s]?")
                ch.isLetterOrDigit() -> append(ch)
                else -> append('\\').append(ch)
            }
        }
    }

    /** The terms in [text], in order of appearance; an overlapping shorter match loses to the longer one. */
    fun find(text: String): List<String> {
        val hits = terms.flatMap { t -> t.regex.findAll(text).map { m -> Triple(m.range.first, m.range.last + 1, t.label) } }
            .filterNot { (start, end, label) ->
                NEGATED.containsMatchIn(text.substring(maxOf(0, start - 24), start)) ||
                    (label.any { it in '가'..'힣' } && NEGATED_AFTER_KO.containsMatchIn(text.substring(end))) ||
                    NOT_FOLLOWED_BY[label.lowercase()]?.containsMatchIn(text.substring(end)) == true
            }
            .sortedWith(compareBy<Triple<Int, Int, String>> { it.first }.thenByDescending { it.second - it.first })
        val out = LinkedHashMap<String, String>()
        var taken = -1
        for ((start, end, label) in hits) {
            if (start < taken) continue
            taken = end
            out.getOrPut(label.lowercase()) { label }
        }
        return out.values.toList()
    }
}

/** The notes the helper hands back to the record form (the form's back stack entry, like BrewTimerResult). */
object NoteHelperResult {
    const val KEY = "noteHelperNotes"
    private val serializer = ListSerializer(String.serializer())

    fun encode(notes: List<String>): String = AiJson.json.encodeToString(serializer, notes)
    fun decode(text: String): List<String>? = runCatching { AiJson.json.decodeFromString(serializer, text) }.getOrNull()

    /** [added] after [notes], skipping any already there (case-insensitive) or repeated. */
    fun merge(notes: List<String>, added: List<String>): List<String> {
        val seen = notes.map { it.trim().lowercase() }.toMutableSet()
        return notes + added.map { it.trim() }.filter { it.isNotEmpty() && seen.add(it.lowercase()) }
    }
}
