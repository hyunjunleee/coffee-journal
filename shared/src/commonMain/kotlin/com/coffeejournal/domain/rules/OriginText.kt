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
