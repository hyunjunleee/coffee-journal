package com.coffeejournal.ui.bean.a

import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.reference.Varieties
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.CountryLookup
import com.coffeejournal.ui.bean.KoreanOrder

/** Pure index/profile logic behind the 품종 view (web renderVarietyLineageExplorer / renderVarietyByCountry). */
internal object VarietyStats {
    const val HEIRLOOM = "ethiopian heirloom"
    const val ARABICA = "arabica"
    const val ROBUSTA = "robusta"
    const val LIBERICA = "liberica"
    const val SORT_ALPHA = "alpha"
    const val SORT_LINEAGE = "lineage"

    val speciesTabs: List<Pair<String, String>> = listOf(ARABICA to "아라비카", LIBERICA to "리베리카", ROBUSTA to "로부스타")

    private val heirloomHead = Regex("^(?:ethiopian\\s+)?heirloom(?:\\s|$)")

    /** Web normalizedVarietyKey; also folds "Ethiopian Heirloom 74110"-style prefixes that the shared rule leaves alone. */
    fun key(raw: String?): String {
        val k = BeanNames.normalizedVarietyKey(raw)
        return if (heirloomHead.containsMatchIn(k)) HEIRLOOM else k
    }

    fun isHeirloomNumber(key: String): Boolean = Regex("^74\\d{3}$").matches(key.replace(Regex("\\s+"), ""))

    fun species(key: String): String = when (key) { ROBUSTA -> ROBUSTA; LIBERICA -> LIBERICA; else -> ARABICA }

    /** Web varietyKoreanLabel: "한국어 (원문)" when the Korean name is known, else the raw text. */
    fun koreanLabel(raw: String): String {
        val t = raw.trim()
        val ko = Varieties.koreanNames[t.lowercase()] ?: return t
        return "$ko ($t)"
    }

    fun label(group: Group): String = Varieties.displayNames[group.key] ?: koreanLabel(group.display)

    data class Group(val key: String, val display: String, val records: List<BeanRecord>)

    data class Index(val groups: Map<String, Group>, val heirloomSelections: List<Group>) {
        val all: List<Group> get() = groups.values.toList()
        operator fun get(key: String): Group? = groups[key]
    }

    /** Recorded variety keys (74xxx numbers folded into Heirloom) plus the 21 reference varieties. */
    fun index(records: List<BeanRecord>): Index {
        val groups = LinkedHashMap<String, Pair<String, MutableList<BeanRecord>>>()
        records.filter { it.variety.isNotBlank() }.forEach { r ->
            BeanNames.splitVarietyValues(r.variety).forEach { raw ->
                val display = BeanNames.varietyPrimary(raw).trim()
                if (display.isEmpty()) return@forEach
                groups.getOrPut(key(display)) { display to mutableListOf() }.second += r
            }
        }
        val selections = groups.filter { isHeirloomNumber(it.key) }
        if (selections.isNotEmpty()) {
            val heirloom = groups.getOrPut(HEIRLOOM) { "Heirloom" to mutableListOf() }
            selections.values.forEach { heirloom.second += it.second }
        }
        Varieties.genealogyOrder.forEach { k -> groups.getOrPut(k) { (Varieties.displayNames[k] ?: k) to mutableListOf() } }
        val built = groups.mapValues { (k, v) -> Group(k, v.first, v.second) }
        val heirloomGroups = built.values.filter { isHeirloomNumber(it.key) }
            .sortedWith(compareBy({ it.display.length }, { it.display }))
        return Index(built, heirloomGroups)
    }

    data class Explorer(val items: List<Group>, val species: String, val query: String)

    /** Web explorer filtering: 74xxx searches show only Heirloom; other matches switch to the matching species. */
    fun explorer(index: Index, selectedSpecies: String, query: String, sort: String): Explorer {
        val q = query.lowercase().replace(Regex("\\s+"), "")
        val fold = { s: String -> s.lowercase().replace(Regex("\\s+"), "") }
        val searchingNumber = q.isNotEmpty() && Regex("^74\\d*$").matches(q) &&
            index.heirloomSelections.any { fold("${it.key} ${it.display}").contains(q) }
        val matches = { g: Group -> fold("${g.key} ${g.display} ${Varieties.displayNames[g.key] ?: ""}").contains(q) }
        var species = selectedSpecies
        if (q.isNotEmpty() && !searchingNumber) {
            index.all.firstOrNull { !isHeirloomNumber(it.key) && matches(it) }?.let { species = species(it.key) }
        }
        val rank = { k: String -> Varieties.genealogyOrder.indexOf(k).let { if (it < 0) 999 else it } }
        val items = index.all.filter { g ->
            if (species(g.key) != species || isHeirloomNumber(g.key)) return@filter false
            if (q.isEmpty()) return@filter true
            if (searchingNumber) g.key == HEIRLOOM else matches(g)
        }.sortedWith(
            if (sort == SORT_LINEAGE) compareBy<Group>({ rank(it.key) }, { label(it).lowercase() })
            else compareBy { label(it).lowercase() }
        )
        return Explorer(items, species, q)
    }

    /** Cups per species for the tab labels (74xxx groups are already folded into Heirloom). */
    fun speciesCount(index: Index, species: String): Int =
        index.all.filter { !isHeirloomNumber(it.key) && species(it.key) == species }.sumOf { it.records.size }

    private val unknownLineage = Varieties.Lineage(
        "", "계보 정보 준비 중", listOf("기원 미상", "기록된 품종"), listOf("확인 가능한 자료 없음", "내 커피 기록"), "→",
        "이 품종의 신뢰할 수 있는 계보 설명은 아직 등록하지 않았어요. 마신 기록은 아래에서 그대로 확인할 수 있어요.", emptyList(), null,
    )

    fun lineage(key: String): Varieties.Lineage =
        Varieties.lineage[Varieties.aliases[key] ?: key] ?: unknownLineage

    fun uniqueRecords(group: Group): List<BeanRecord> {
        val map = LinkedHashMap<String, BeanRecord>()
        group.records.forEach { map["${it.parentEntryId ?: it.entryId}|${it.name}|${it.variety}"] = it }
        return map.values.sortedByDescending { it.createdAt }
    }

    /** Web "N origins": distinct bilingual countries, 국가 미상 counting as one. */
    fun origins(records: List<BeanRecord>): Int =
        records.map { CountryLookup.bilingual(it.country) }.toSet().size

    /** Web isSingleVarietyRecord: exactly one variety written and it is this one ("… 외" never counts). */
    fun isSingle(record: BeanRecord, key: String): Boolean {
        if (Regex("외\\s*$").containsMatchIn(record.variety)) return false
        val parts = BeanNames.splitVarietyValues(record.variety).map { key(BeanNames.varietyPrimary(it)) }.filter { it.isNotEmpty() }
        return parts.size == 1 && parts[0] == key
    }

    data class RecordGroup(val title: String, val records: List<BeanRecord>, val emptyText: String)

    /** 단일/복수 groups for a normal variety; per-number groups for Heirloom. Empty when nothing was recorded. */
    fun recordGroups(index: Index, group: Group): List<RecordGroup> {
        val unique = uniqueRecords(group)
        if (unique.isEmpty()) return emptyList()
        if (group.key != HEIRLOOM) {
            val name = label(group)
            return listOf(
                RecordGroup("$name 단일 품종", unique.filter { isSingle(it, group.key) }, "단일 품종으로 마신 기록은 아직 없어요."),
                RecordGroup("$name 포함 · 복수 품종", unique.filter { !isSingle(it, group.key) }, "다른 품종과 함께 표기된 기록은 아직 없어요."),
            )
        }
        val idOf = { r: BeanRecord -> "${r.parentEntryId ?: r.entryId}|${r.name}" }
        val numbered = index.heirloomSelections.map { sel ->
            val children = LinkedHashMap<String, BeanRecord>()
            sel.records.forEach { children[idOf(it)] = it }
            RecordGroup("Heirloom · ${sel.display}", children.values.sortedByDescending { it.createdAt }, "")
        }
        val numberedIds = index.heirloomSelections.flatMap { s -> s.records.map(idOf) }.toSet()
        val unspecified = unique.filter { idOf(it) !in numberedIds }
        return numbered + if (unspecified.isNotEmpty()) listOf(RecordGroup("Heirloom · 번호 미상", unspecified, "")) else emptyList()
    }

    data class CountryRow(val display: String, val records: List<BeanRecord>)
    data class CountryGroup(val country: String, val rows: List<CountryRow>)

    /** Web renderVarietyByCountry: country → "품종 (세부)" rows with their records. */
    fun byCountry(records: List<BeanRecord>): List<CountryGroup> {
        val map = LinkedHashMap<String, LinkedHashMap<String, MutableList<BeanRecord>>>()
        records.filter { it.variety.isNotBlank() }.forEach { r ->
            val country = CountryLookup.bilingual(r.country)
            BeanNames.splitVarietyValues(r.variety).forEach { raw ->
                val primary = BeanNames.varietyPrimary(raw)
                if (primary.isEmpty()) return@forEach
                val sub = BeanNames.varietySub(raw)
                val display = if (sub.isNotEmpty()) "$primary ($sub)" else primary
                map.getOrPut(country) { LinkedHashMap() }.getOrPut(display) { mutableListOf() } += r
            }
        }
        return map.entries.sortedWith(compareBy(KoreanOrder) { it.key }).map { (country, rows) ->
            CountryGroup(country, rows.entries.sortedBy { it.key.lowercase() }.map { CountryRow(it.key, it.value) })
        }
    }
}
