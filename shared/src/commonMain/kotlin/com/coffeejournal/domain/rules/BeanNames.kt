package com.coffeejournal.domain.rules

import com.coffeejournal.domain.model.Entry

data class NameParens(val roastery: String, val source: String, val farm: String, val producer: String)

data class FarmProducer(val farm: String, val producer: String)

/** Name normalisation rules ported from the web app; the core name is the identity of a bean. */
object BeanNames {
    private val trailingParens = Regex("\\s*\\([^)]*\\)\\s*$")

    /** Web coreBeanName: strip a trailing "(…)" then trim and lower-case. */
    fun coreBeanName(name: String?): String = (name ?: "").replace(trailingParens, "").trim().lowercase()

    /** Web displayBeanName: strip the trailing parenthesis; fall back to the raw name. */
    fun displayName(name: String?): String {
        val raw = name?.takeIf { it.isNotBlank() } ?: return "이름 없음"
        val stripped = raw.replace(trailingParens, "").trim()
        return stripped.ifEmpty { raw }
    }

    /** Web parseNameParens: "(로스터리, 출처, 농장, 생산자)" at the end of a name. */
    fun parseNameParens(name: String?): NameParens? {
        val m = Regex("\\(([^)]+)\\)\\s*$").find(name ?: "") ?: return null
        val parts = m.groupValues[1].split(',').map { it.trim() }
        return NameParens(
            roastery = parts.getOrElse(0) { "" },
            source = parts.getOrElse(1) { "" },
            farm = parts.getOrElse(2) { "" },
            producer = parts.getOrElse(3) { "" },
        )
    }

    fun parseNameRoastery(name: String?): String = parseNameParens(name)?.roastery ?: ""

    /** Web selectionShortName: drop a trailing "셀렉션". */
    fun selectionShortName(name: String?): String = (name ?: "").replace(Regex("\\s*셀렉션\\s*$"), "").trim()

    fun entrySelection(entry: Entry): String =
        selectionShortName(entry.selection.ifBlank { parseNameParens(entry.name)?.source ?: "" })

    /** Web parseFarmProducer: "농장(생산자)" → farm + producer; also used for "허니(세부)" processes. */
    fun parseFarmProducer(value: String?): FarmProducer {
        val raw = (value ?: "").trim()
        val m = Regex("^(.*?)\\(([^)]*)\\)\\s*$").find(raw)
        return if (m != null) FarmProducer(m.groupValues[1].trim(), m.groupValues[2].trim()) else FarmProducer(raw, "")
    }

    fun formatFarmProducer(farm: String?, producer: String?): String {
        val f = (farm ?: "").trim()
        val p = (producer ?: "").trim()
        return when {
            f.isNotEmpty() && p.isNotEmpty() -> "$f($p)"
            f.isNotEmpty() -> f
            else -> p
        }
    }

    /** Web varietyPrimary / varietySub. */
    fun varietyPrimary(value: String?): String = parseFarmProducer(value).farm
    fun varietySub(value: String?): String = parseFarmProducer(value).producer

    /** Web splitVarietyValues: split on , & / outside parentheses. */
    fun splitVarietyValues(value: String?): List<String> {
        val raw = (value ?: "").trim()
        if (raw.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        for (ch in raw) {
            when {
                ch == '(' -> { depth++; cur.append(ch) }
                ch == ')' -> { depth = maxOf(0, depth - 1); cur.append(ch) }
                depth == 0 && (ch == ',' || ch == '&' || ch == '/') -> { if (cur.isNotBlank()) out += cur.toString().trim(); cur.clear() }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotBlank()) out += cur.toString().trim()
        return out
    }

    /** Web normalizedVarietyKey. */
    fun normalizedVarietyKey(raw: String?): String {
        var k = (raw ?: "").trim().lowercase()
        k = k.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").replace(Regex("\\s*외\\s*$"), "").trim()
        if (k.startsWith("heirloom") || k.contains("에티오피아") && k.contains("재래")) k = "ethiopian heirloom"
        if (k.startsWith("catimor")) k = "catimor"
        if (k.startsWith("robusta")) k = "robusta"
        if (k.startsWith("liberica")) k = "liberica"
        return k
    }

    fun isEthiopianSelectionNumber(key: String): Boolean = Regex("^74\\d{3}$").matches(key.trim())

    /** Web isDecafEntry: process, processOther or name mentions 디카페인. */
    fun isDecaf(name: String?, process: String?, processOther: String?): Boolean =
        listOf(name, process, processOther).any { (it ?: "").contains("디카페인") }

    /** Web isKnownBlendName: the name itself says blend. */
    fun nameSaysBlend(name: String?): Boolean = Regex("블렌드|blend", RegexOption.IGNORE_CASE).containsMatchIn(name ?: "")
}
