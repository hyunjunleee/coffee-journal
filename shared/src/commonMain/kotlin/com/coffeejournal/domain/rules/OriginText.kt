package com.coffeejournal.domain.rules

/**
 * The region as the bean form edits it: 지역 (the first part) and 세부 지역 (the rest, shown "벤사 › 코코세"). It is
 * stored as one text with commas, "시다모, 벤사, 코코세" — the web's regionHierarchy form — so backups, the web app and the
 * coffee map (which groups by the first part) read it as before.
 */
object RegionText {
    /** Between the parts of a 세부 지역 as the form and the record show them. */
    const val SHOWN = " › "

    /** What people type between the parts of a 세부 지역: › > -> → / , (and the spaces around them). */
    private val SEPARATORS = Regex("\\s*(?:->|→|›|»|>|/|,)\\s*")

    /** [stored] as (지역, 세부 지역): "시다모, 벤사, 코코세" → ("시다모", "벤사 › 코코세"). */
    fun split(stored: String): Pair<String, String> {
        val parts = parts(stored)
        return (parts.firstOrNull() ?: "") to parts.drop(1).joinToString(SHOWN)
    }

    /** The places of a typed 세부 지역, outermost first: "벤사 > 코코세", "벤사, 코코세" and "벤사 › 코코세" are the same. */
    fun subParts(sub: String): List<String> = sub.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }

    /** The text to store for 지역 [primary] and 세부 지역 [sub]: "시다모, 벤사, 코코세" (just the sub places when 지역 is empty). */
    fun join(primary: String, sub: String): String = (listOf(primary.trim()) + subParts(sub)).filter { it.isNotEmpty() }.joinToString(", ")

    /** A stored region for reading: "시다모, 벤사, 코코세" → "시다모 › 벤사 › 코코세". */
    fun display(stored: String): String = parts(stored).joinToString(SHOWN)

    private fun parts(stored: String): List<String> = stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * 재배 고도 in metres: the form takes the number (or a range, "1800-2000") and the record keeps it with its unit,
 * "1800-2000m". Older records written by hand ("1,950 masl", "약 2000m") open as they are.
 */
object Altitude {
    /** A stored altitude for the field: its number or range without the unit; anything else unchanged. */
    fun forField(stored: String): String {
        val t = stored.trim()
        val bare = t.replace(UNIT, "").trim()
        return if (NUMBERS.matches(bare)) bare else t
    }

    /** What the field holds, to store: a number or range gets "m"; empty stays empty; other text stays as typed. */
    fun stored(field: String): String {
        val t = field.trim()
        if (t.isEmpty()) return ""
        val bare = t.replace(UNIT, "").trim()
        return if (NUMBERS.matches(bare)) bare + "m" else t
    }

    /** Whether the field holds a number or a range as typed (or nothing yet), rather than an older free text. */
    fun isNumeric(field: String): Boolean = field.isBlank() || NUMBERS.matches(field.trim()) || PARTIAL.matches(field.trim())

    /** The field's input filter: digits, the thousands comma, a point and a range sign ("~", "-") only. */
    fun typing(typed: String): String = typed.filter { it.isDigit() || it in ",.~-– " }

    /** The unit at the end, as people write it: m, masl, m.a.s.l., meters, mts, 미터. */
    private val UNIT = Regex("(?i)\\s*(m\\.?\\s*a\\.?\\s*s\\.?\\s*l\\.?|masl|meters?|metres?|mts?|m|미터)\\s*$")

    /** A number ("1,950", "1950.5") or a range of two ("1800-2000", "1,800 ~ 2,000"). */
    private val NUMBERS = Regex("\\d[\\d,.]*(\\s*[~\\-–]\\s*\\d[\\d,.]*)?")

    /** A range being typed: its first number and the sign ("1800-", "1800 ~ "). */
    private val PARTIAL = Regex("\\d[\\d,.]*\\s*[~\\-–]?\\s*")
}

/**
 * English written in the entry fields: each space-separated word starts with a capital ("yellow bourbon" → "Yellow
 * Bourbon", "heirloom(74112)" → "Heirloom(74112)"). Only a lower-case a–z at a word's start changes, and a word stays
 * as typed when capitals would change what it says: one with a capital already ("iPhone", "pH", "SL28"), a unit
 * ("200 ml", "24 clicks"), a link or an address ("youtube.com/@x", "a@b.kr"), and — after the first word — the small
 * words names keep in lower case ("Sul de Minas", "Valle del Cauca", "Cup of Excellence"). Korean and digits stay.
 */
object EnglishCase {
    fun words(text: String): String {
        if (text.none { it in 'a'..'z' }) return text
        val out = StringBuilder(text.length)
        var i = 0
        var first = true
        while (i < text.length) {
            if (text[i] == ' ') { out.append(' '); i++; continue }
            var j = i
            while (j < text.length && text[j] != ' ') j++
            val word = text.substring(i, j)
            out.append(if (word[0] in 'a'..'z' && !keepsCase(word, first)) word[0].uppercaseChar() + word.substring(1) else word)
            first = false
            i = j
        }
        return out.toString()
    }

    private fun keepsCase(word: String, first: Boolean): Boolean {
        if (word.any { it.isUpperCase() }) return true
        if ('@' in word || "://" in word || word.startsWith("www.") || DOMAIN.containsMatchIn(word)) return true
        val bare = word.trimEnd(',', '.', ';', ':', ')', '!', '?').lowercase()
        return bare in UNITS || (!first && bare in SMALL_WORDS)
    }

    /** A dot between letters, as in a domain ("youtube.com"), not a sentence's full stop. */
    private val DOMAIN = Regex("[a-z0-9]\\.[a-z]{2,}")

    private val UNITS = setOf(
        "g", "gr", "kg", "mg", "ml", "l", "oz", "mm", "cm", "m", "km", "s", "sec", "secs", "min", "mins", "h", "hr",
        "ppm", "tds", "ph", "bar", "rpm", "click", "clicks", "masl", "ft",
    )

    private val SMALL_WORDS = setOf("de", "del", "da", "das", "do", "dos", "di", "du", "der", "den", "von", "van", "y", "e", "of", "and", "the")
}

/**
 * 품종 as the bean form edits it: the varieties ("Heirloom, Mundo Novo") and, apart, the Ethiopian selection numbers of
 * its Heirloom ("74112, 74158"). The record keeps them as one text the way it always had, "Heirloom(74112, 74158),
 * Mundo Novo" (BeanNames.splitVarietyValues keeps the parenthesis with its variety, and the variety view counts each
 * number under Heirloom). Only that parenthesis moves between the text and the numbers field: the rest of the text,
 * its separators and spacing, stays as typed.
 */
object VarietyText {
    /** A variety of the text and where it is (without the spaces around it). */
    private class Token(val text: String, val range: IntRange)

    /** The varieties of [text] with their places, split as [BeanNames.splitVarietyValues] does: , & / outside parentheses. */
    private fun tokens(text: String): List<Token> {
        val out = mutableListOf<Token>()
        var depth = 0
        var start = 0
        fun flush(end: Int) {
            var a = start
            var b = end
            while (a < b && text[a].isWhitespace()) a++
            while (b > a && text[b - 1].isWhitespace()) b--
            if (b > a) out += Token(text.substring(a, b), a until b)
        }
        text.forEachIndexed { i, ch ->
            when {
                ch == '(' -> depth++
                ch == ')' -> depth = maxOf(0, depth - 1)
                depth == 0 && (ch == ',' || ch == '&' || ch == '/') -> { flush(i); start = i + 1 }
            }
        }
        flush(text.length)
        return out
    }

    /**
     * [stored] as (varieties, Heirloom numbers): the first Heirloom whose parenthesis holds numbers only gives them to
     * the numbers field and keeps its place in the text; any other parenthesis stays where it is.
     */
    fun split(stored: String): Pair<String, String> {
        for (t in tokens(stored)) {
            if (!isHeirloom(t.text)) continue
            val paren = PAREN.find(t.text) ?: continue
            val inside = paren.groupValues[2]
            val list = numberList(inside)
            if (list.isEmpty() || !inside.all { it.isDigit() || it in ", /·" }) continue
            val varieties = stored.substring(0, t.range.first) + paren.groupValues[1].trimEnd() + stored.substring(t.range.last + 1)
            return varieties.trim() to list.joinToString(", ")
        }
        return stored.trim() to ""
    }

    /**
     * The text to store: [numbers] ("74112 74158", "74112,74158") go right after the first Heirloom without a parenthesis,
     * as "(74112, 74158)"; the rest stays as typed. Numbers without such a Heirloom are dropped, and the form only asks
     * for them when there is one ([hasHeirloom]).
     */
    fun join(varieties: String, numbers: String): String {
        val text = varieties.trim()
        val list = numberList(numbers)
        if (list.isEmpty()) return text
        val t = tokens(text).firstOrNull { isHeirloom(it.text) && '(' !in it.text } ?: return text
        return text.substring(0, t.range.last + 1) + "(${list.joinToString(", ")})" + text.substring(t.range.last + 1)
    }

    /** Whether [varieties] has a Heirloom (or 에티오피아 재래종) the numbers can go with, so the form asks for them. */
    fun hasHeirloom(varieties: String): Boolean = tokens(varieties).any { isHeirloom(it.text) && '(' !in it.text }

    /** The numbers typed, in order, each once: "74112, 74158 74112" → [74112, 74158]. */
    fun numberList(text: String): List<String> = Regex("\\d+").findAll(text).map { it.value }.distinct().toList()

    /** The numbers field's input filter: digits and the separators people use between them. */
    fun typingNumbers(typed: String): String = typed.filter { it.isDigit() || it in ", " }

    private fun isHeirloom(token: String) = BeanNames.normalizedVarietyKey(token) == "ethiopian heirloom"

    private val PAREN = Regex("^(.*?)\\s*\\(([^)]*)\\)\\s*$")
}

/**
 * A bean's 지역, 재배 고도 and 품종 to store: the text the record held ([loaded]) while the form's fields are still what
 * it opened as, so opening a record and saving it for another field changes none of them; otherwise the fields'
 * text as [RegionText], [Altitude] and [VarietyText] write it.
 */
object OriginKeep {
    fun region(loaded: String, primary: String, sub: String): String =
        if (loaded.isNotEmpty() && RegionText.split(loaded) == (primary to sub)) loaded else RegionText.join(primary, sub)

    fun altitude(loaded: String, field: String): String =
        if (loaded.isNotEmpty() && Altitude.forField(loaded) == field) loaded else Altitude.stored(field)

    fun variety(loaded: String, varieties: String, numbers: String): String =
        if (loaded.isNotEmpty() && VarietyText.split(loaded) == (varieties to numbers)) loaded else VarietyText.join(varieties, numbers)
}
