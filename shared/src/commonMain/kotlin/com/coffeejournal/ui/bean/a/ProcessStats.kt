package com.coffeejournal.ui.bean.a

import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.reference.Processes
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.CountryLookup

/** Pure matching and aggregation behind the 가공 방식 view (web processRecordMatchesName & friends). */
internal object ProcessStats {
    private val honeyHint = Regex("허니|honey", RegexOption.IGNORE_CASE)

    /** Web processRecordMatchesName: compare the primary (before parenthesis) part; 허니 also matches "레드 허니" etc. */
    fun matches(record: BeanRecord, itemName: String, seg: String?): Boolean {
        val raw = record.process
        val primary = BeanNames.parseFarmProducer(raw).farm.ifEmpty { raw }
        if (seg != null) {
            if (primary == seg) return true
            if (seg == "허니" && honeyHint.containsMatchIn("$raw ${record.processOther}")) return true
            return false
        }
        val value = primary.trim().lowercase()
        val target = itemName.lowercase()
        if (value == target) return true
        if (primary == "기타") {
            val otherPrimary = BeanNames.parseFarmProducer(record.processOther).farm.trim().lowercase()
            if (otherPrimary == target) return true
        }
        return false
    }

    /** Web honeySubtypeForRecord: colour regexes, then the text in parentheses, else 세부 미기록. */
    fun honeySubtype(record: BeanRecord): String {
        val raw = "${record.process} ${record.processOther}".trim()
        Processes.honeySubtypes.firstOrNull { it.regex.containsMatchIn(raw) }?.let { return it.label }
        val producer = BeanNames.parseFarmProducer(record.process).producer
        return producer.ifEmpty { "세부 미기록" }
    }

    /** Web processSearchLabel: process + processOther combined for grouping. */
    fun searchLabel(record: BeanRecord): String {
        val process = record.process.trim()
        val other = record.processOther.trim()
        if (process.isEmpty()) return other
        if (process == "기타") return other.ifEmpty { process }
        if (other.isEmpty() || process.lowercase().contains(other.lowercase())) return process
        return "$process · $other"
    }

    data class SearchGroup(val label: String, val records: List<BeanRecord>)

    /** Web renderProcessSearch: label groups (most records, then 가나다) plus known methods without records. */
    fun search(records: List<BeanRecord>, query: String): List<SearchGroup> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val groups = LinkedHashMap<String, MutableList<BeanRecord>>()
        records.forEach { record ->
            val label = searchLabel(record)
            val haystack = listOf(record.process, record.processOther, label).filter { it.isNotBlank() }.joinToString(" ").lowercase()
            if (label.isEmpty() || !haystack.contains(q)) return@forEach
            groups.getOrPut(label) { mutableListOf() } += record
        }
        (Processes.main4 + Processes.etc)
            .filter { "${it.name} ${it.en ?: ""}".lowercase().contains(q) }
            .forEach { groups.getOrPut(it.name) { mutableListOf() } }
        return groups.entries
            .map { (label, list) -> SearchGroup(label, list.sortedByDescending { it.createdAt }) }
            .sortedWith(compareByDescending<SearchGroup> { it.records.size }.thenBy { it.label })
    }

    data class Breakdown(
        val countries: List<Pair<String, Int>>,
        val varieties: List<Pair<String, Int>>,
        val subs: List<Pair<String, Int>>,
        val matching: List<BeanRecord>,
    )

    /** Web processBreakdown; countries are shown bilingual so "Ethiopia" and "에티오피아" merge. */
    fun breakdown(records: List<BeanRecord>, name: String, seg: String?): Breakdown {
        if (name.isBlank()) return Breakdown(emptyList(), emptyList(), emptyList(), emptyList())
        val matching = records.filter { matches(it, name, seg) }
        val byCountry = LinkedHashMap<String, Int>()
        val byVariety = LinkedHashMap<String, Int>()
        val bySub = LinkedHashMap<String, Int>()
        matching.forEach { r ->
            val c = r.country.trim().takeIf { it.isNotEmpty() }?.let { CountryLookup.bilingual(it) } ?: "국가 미상"
            byCountry[c] = (byCountry[c] ?: 0) + 1
            val v = r.variety.trim().ifEmpty { "품종 미상" }
            byVariety[v] = (byVariety[v] ?: 0) + 1
            val sub = subtype(r, seg)
            if (sub.isNotEmpty()) bySub[sub] = (bySub[sub] ?: 0) + 1
        }
        val desc = compareByDescending<Pair<String, Int>> { it.second }
        return Breakdown(
            byCountry.toList().sortedWith(desc),
            byVariety.toList().sortedWith(desc),
            bySub.toList().sortedWith(desc),
            matching,
        )
    }

    /** The 세부 종류 of a matching record, depending on how the method was selected. */
    fun subtype(record: BeanRecord, seg: String?): String = when {
        seg == "허니" -> honeySubtype(record)
        seg == "기타" -> record.processOther.trim()
        BeanNames.parseFarmProducer(record.process).farm == "기타" -> BeanNames.parseFarmProducer(record.processOther).producer
        else -> BeanNames.parseFarmProducer(record.process).producer
    }

    /** Web renderProcessDetail honey branch: subtype → records, most first then 가나다. */
    fun honeyGroups(matching: List<BeanRecord>): List<Pair<String, List<BeanRecord>>> =
        matching.groupBy { honeySubtype(it) }
            .map { (k, v) -> k to v.sortedByDescending { it.createdAt } }
            .sortedWith(compareByDescending<Pair<String, List<BeanRecord>>> { it.second.size }.thenBy { it.first })

    data class BrewGroup(val displayName: String, val visits: List<BeanRecord>)

    /** Web byBrewName: home brews grouped by core name, in first-seen order. */
    fun brewGroups(matching: List<BeanRecord>): List<BrewGroup> =
        matching.filter { it.category.ifBlank { Category.BEAN } == Category.BEAN }
            .groupBy { BeanFormat.beanKey(it) }
            .values.map { BrewGroup(BeanFormat.displayName(it.first()), it) }

    fun isTried(records: List<BeanRecord>, process: Processes.Process): Boolean =
        records.any { matches(it, process.name, process.seg) }
}
