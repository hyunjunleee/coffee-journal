package com.coffeejournal.ui.bean.a

import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.rules.NoteCanon

/** Pure aggregation behind the 커피 노트 view (web renderNoteCloud / renderNoteDetail). */
internal object NoteStats {
    const val EXPECTED = "expected"
    const val ACTUAL = "actual"
    const val SORT_COUNT = "count"
    const val SORT_ALPHA = "alpha"

    data class CloudEntry(val key: String, val label: String, val beans: Set<String>, val records: List<BeanRecord>) {
        val beanCount: Int get() = beans.size
    }

    data class Combination(val labels: List<String>, val records: List<BeanRecord>) {
        val beanCount: Int get() = records.map { BeanFormat.beanKey(it) }.toSet().size
    }

    fun description(kind: String): String =
        if (kind == ACTUAL) "내가 실제로 느껴 기록한 노트만 모았어요." else "원두 봉투나 공식 정보에 적힌 예상 노트만 모았어요."

    fun emptyText(kind: String, query: String): String = when {
        query.isNotBlank() -> "검색 결과가 없어요."
        kind == ACTUAL -> "아직 내가 기록한 노트가 없어요."
        else -> "아직 모인 예상 노트가 없어요."
    }

    /** The note text of a record for the selected kind. */
    fun text(record: BeanRecord, kind: String): String =
        (if (kind == ACTUAL) record.actualNotes else record.expectedNotes).trim()

    /** Web noteExplorerRecords: only records that carry a note of this kind. */
    fun explorerRecords(records: List<BeanRecord>, kind: String): List<BeanRecord> =
        records.filter { text(it, kind).isNotEmpty() }

    /** Web renderNoteCloud counts: key = spacing-free lowercase label, value = label + unique beans + records. */
    fun aggregate(records: List<BeanRecord>, kind: String): Map<String, CloudEntry> {
        val out = LinkedHashMap<String, Pair<String, Pair<LinkedHashSet<String>, MutableList<BeanRecord>>>>()
        explorerRecords(records, kind).forEach { record ->
            NoteCanon.split(text(record, kind)).forEach { label ->
                val key = NoteCanon.key(label)
                val slot = out.getOrPut(key) { label to (LinkedHashSet<String>() to mutableListOf()) }
                slot.second.first += BeanFormat.beanKey(record)
                slot.second.second += record
            }
        }
        return out.mapValues { (key, v) -> CloudEntry(key, v.first, v.second.first, v.second.second) }
    }

    /** Search over label, both note fields, bean name and place; sort by unique beans or 가나다. */
    fun filterAndSort(entries: Collection<CloudEntry>, query: String, sort: String): List<CloudEntry> {
        val q = query.trim().lowercase()
        val filtered = entries.filter { info ->
            if (q.isEmpty()) return@filter true
            if (info.label.lowercase().contains(q)) return@filter true
            info.records.any { r ->
                listOf(r.expectedNotes, r.actualNotes, r.name, BeanFormat.notePlace(r))
                    .filter { it.isNotBlank() }.joinToString(" ").lowercase().contains(q)
            }
        }
        return if (sort == SORT_ALPHA) filtered.sortedBy { it.label } else filtered.sortedByDescending { it.beanCount }
    }

    /** Web renderNoteDetail: group the linked records by their full note combination, most records first. */
    fun combinations(entry: CloudEntry, kind: String): List<Combination> {
        val groups = LinkedHashMap<String, Pair<List<String>, MutableList<BeanRecord>>>()
        entry.records.forEach { record ->
            val labels = NoteCanon.split(text(record, kind))
            val key = labels.map { NoteCanon.key(it) }.sorted().joinToString("|")
            groups.getOrPut(key) { labels to mutableListOf() }.second += record
        }
        return groups.values
            .map { (labels, records) -> Combination(labels, records.sortedByDescending { it.createdAt }) }
            .sortedByDescending { it.records.size }
    }
}
