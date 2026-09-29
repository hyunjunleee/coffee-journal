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

    /** The field's input filter: digits, the thousands comma, a point and a range sign ("~", "-") only. */
    fun typing(typed: String): String = typed.filter { it.isDigit() || it in ",.~-– " }

    /** The unit at the end, as people write it: m, masl, m.a.s.l., meters, mts, 미터. */
    private val UNIT = Regex("(?i)\\s*(m\\.?\\s*a\\.?\\s*s\\.?\\s*l\\.?|masl|meters?|metres?|mts?|m|미터)\\s*$")

    /** A number ("1,950", "1950.5") or a range of two ("1800-2000", "1,800 ~ 2,000"). */
    private val NUMBERS = Regex("\\d[\\d,.]*(\\s*[~\\-–]\\s*\\d[\\d,.]*)?")
}

/**
 * English written in the entry fields: each space-separated word starts with a capital ("yellow bourbon" → "Yellow
 * Bourbon", "heirloom(74112)" → "Heirloom(74112)"). Only a lower-case a–z at a word's start changes: Korean, digits and
 * the rest of each word ("SL28", "iPhone"'s "P") stay as typed.
 */
object EnglishCase {
    fun words(text: String): String {
        if (text.none { it in 'a'..'z' }) return text
        val out = StringBuilder(text.length)
        var start = true
        for (ch in text) {
            out.append(if (start && ch in 'a'..'z') ch.uppercaseChar() else ch)
            start = ch == ' '
        }
        return out.toString()
    }
}

/**
 * 품종 as the bean form edits it: the varieties ("Heirloom, Mundo Novo") and, apart, the Ethiopian selection numbers of
 * its Heirloom ("74112, 74158"). The record keeps them as one text the way it always had, "Heirloom(74112, 74158),
 * Mundo Novo" (BeanNames.splitVarietyValues keeps the parenthesis with its variety, and the variety view counts each
 * number under Heirloom).
 */
object VarietyText {
    /** [stored] as (varieties, Heirloom numbers); a Heirloom parenthesis that is not numbers stays in its variety. */
    fun split(stored: String): Pair<String, String> {
        var numbers = ""
        val tokens = BeanNames.splitVarietyValues(stored).map { token ->
            val paren = PAREN.find(token)
            if (numbers.isEmpty() && isHeirloom(token) && paren != null) {
                val inside = numberList(paren.groupValues[2])
                if (inside.isNotEmpty() && paren.groupValues[2].all { it.isDigit() || it in ", /·" }) {
                    numbers = inside.joinToString(", ")
                    return@map paren.groupValues[1].trim()
                }
            }
            token
        }
        return tokens.joinToString(", ") to numbers
    }

    /**
     * The text to store: [numbers] ("74112 74158", "74112,74158") go after the first Heirloom as "(74112, 74158)";
     * the rest stays as typed. Numbers without a Heirloom to go with are dropped (the form only asks for them with one).
     */
    fun join(varieties: String, numbers: String): String {
        val text = varieties.trim()
        val list = numberList(numbers)
        if (list.isEmpty()) return text
        val heirloom = BeanNames.splitVarietyValues(text).firstOrNull { isHeirloom(it) && PAREN.find(it) == null } ?: return text
        return text.replaceFirst(heirloom, "$heirloom(${list.joinToString(", ")})")
    }

    /** Whether [varieties] names Heirloom (or 에티오피아 재래종), so the form asks for its numbers. */
    fun hasHeirloom(varieties: String): Boolean = BeanNames.splitVarietyValues(varieties).any(::isHeirloom)

    /** The numbers typed, in order, each once: "74112, 74158 74112" → [74112, 74158]. */
    fun numberList(text: String): List<String> = Regex("\\d+").findAll(text).map { it.value }.distinct().toList()

    /** The numbers field's input filter: digits and the separators people use between them. */
    fun typingNumbers(typed: String): String = typed.filter { it.isDigit() || it in ", " }

    private fun isHeirloom(token: String) = BeanNames.normalizedVarietyKey(token) == "ethiopian heirloom"

    private val PAREN = Regex("^(.*?)\\s*\\(([^)]*)\\)\\s*$")
}
