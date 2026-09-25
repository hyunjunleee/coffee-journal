package com.coffeejournal.ui.bean.a

import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Dates

/** Small record-level formatting rules shared by the 원두 tab views (web displayBeanName, categoryBadgeHtml, ...). */
internal object BeanFormat {
    /** The record to open when a row is tapped: cupping beans open their parent session. */
    fun openEntryId(record: BeanRecord): String = record.parentEntryId ?: record.entryId

    /** Web categoryBadgeHtml label: 원두 → 직접 내림, otherwise the category itself. */
    fun categoryLabel(category: String): String = if (category.isBlank() || category == Category.BEAN) "직접 내림" else category

    /** Web displayBeanName: trailing parenthesis stripped, 이름 없음 when blank. */
    fun displayName(record: BeanRecord): String = BeanNames.displayName(record.name)

    /** Web noteRecordPlace: cafe name, cupping place, or 집 추출 for home brews. */
    fun notePlace(record: BeanRecord): String = when (record.category) {
        Category.CAFE -> record.cafeName
        Category.CUPPING -> record.cuppingPlace
        else -> "집 추출"
    }

    /** "직접 내림" / "카페 · 이름" / "커핑 · 장소" (web variety-record-kind). */
    fun kindWithPlace(record: BeanRecord): String {
        val place = record.place.trim()
        val label = categoryLabel(record.category)
        return if (place.isNotEmpty()) "$label · $place" else label
    }

    fun date(record: BeanRecord): String = Dates.ymdCompact(record.createdAt)

    /** Bean identity key used by counts of "unique beans"; falls back to the raw name (web coreBeanName || name || 이름없음). */
    fun beanKey(record: BeanRecord): String =
        BeanNames.coreBeanName(record.name).ifEmpty { record.name.ifEmpty { "이름없음" } }

    /** Web visitRowsHtml brew row: "첫날 ~ 마지막날 (N번) · 가공 · 품종". */
    fun brewRange(visits: List<BeanRecord>): String {
        val sorted = visits.sortedBy { it.createdAt }
        if (sorted.isEmpty()) return ""
        val first = Dates.ymdCompact(sorted.first().createdAt)
        val last = Dates.ymdCompact(sorted.last().createdAt)
        val range = if (first == last) first else "$first ~ $last"
        val processes = sorted.map { it.process.trim() }.filter { it.isNotEmpty() }.distinct()
        val varieties = sorted.map { it.variety.trim() }.filter { it.isNotEmpty() }.distinct()
        return buildString {
            append(range).append(" (").append(sorted.size).append("번)")
            if (processes.isNotEmpty()) append(" · ").append(processes.joinToString(", "))
            if (varieties.isNotEmpty()) append(" · ").append(varieties.joinToString(", "))
        }
    }

    /** Web visitLineHtml: "2026.7.15 · 카페 · OO카페 · 워시드 · 게이샤". */
    fun visitLine(record: BeanRecord): String = listOf(
        Dates.ymdCompact(record.createdAt),
        categoryLabel(record.category),
        record.place.trim(),
        record.process.trim(),
        record.variety.trim(),
    ).filter { it.isNotEmpty() }.joinToString(" · ")

    /** Web catBadgesWithPlaces: category → distinct places, in 원두/카페/커핑 order. */
    fun categoriesWithPlaces(records: List<BeanRecord>): List<Pair<String, List<String>>> {
        val byCat = LinkedHashMap<String, LinkedHashSet<String>>()
        records.forEach { r ->
            val cat = r.category.ifBlank { Category.BEAN }
            val set = byCat.getOrPut(cat) { LinkedHashSet() }
            r.place.trim().takeIf { it.isNotEmpty() }?.let { set += it }
        }
        return Category.all.filter { it in byCat }.map { it to byCat.getValue(it).toList() }
    }
}
