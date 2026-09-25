package com.coffeejournal.ui.bean.b

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Dates
import kotlin.math.roundToInt

/** One card in the 블렌드 view; [kind] matches the web filter values. */
sealed class BlendItem(val kind: String, val time: Long) {
    class Custom(val blend: Blend) : BlendItem(BlendSources.CUSTOM, BlendSources.blendTime(blend))
    class FromEntry(val entry: Entry) : BlendItem(if (entry.beanMode == BeanMode.CUSTOM_BLEND) BlendSources.CUSTOM else BlendSources.COMMERCIAL, entry.createdAt)
    class FromCupping(val entry: Entry, val bean: CuppingBean) : BlendItem(BlendSources.CUPPING, entry.createdAt)
}

/** Web renderBlendList: merges hand-made blends, blend records and cupped blends, newest first. */
object BlendSources {
    const val ALL = "all"
    const val CUPPING = "cupping"
    const val COMMERCIAL = "commercial"
    const val CUSTOM = "custom"
    val filters: List<String> = listOf(ALL, CUPPING, COMMERCIAL, CUSTOM)
    val filterLabels: Map<String, String> = mapOf(ALL to "전체", CUPPING to "커핑한 블렌드", COMMERCIAL to "카페 블렌드", CUSTOM to "내가 만든 블렌드")

    fun blendTime(blend: Blend): Long = Dates.parseIsoDate(blend.date)?.let { Dates.startOfDayMillis(it) } ?: blend.createdAt

    fun merge(blends: List<Blend>, entries: List<Entry>, filter: String = ALL): List<BlendItem> {
        val items = ArrayList<BlendItem>()
        blends.forEach { items += BlendItem.Custom(it) }
        entries.filter { it.beanMode == BeanMode.CUSTOM_BLEND || it.beanMode == BeanMode.COMMERCIAL_BLEND }.forEach { items += BlendItem.FromEntry(it) }
        entries.filter { it.isCupping }.forEach { en -> en.cuppingBeans.filter { it.beanMode == BeanMode.BLEND }.forEach { items += BlendItem.FromCupping(en, it) } }
        return items.filter { filter == ALL || it.kind == filter }.sortedByDescending { it.time }
    }

    /** "이름 12g (40%)" per component; the percentage only when any grams were entered. */
    fun componentLines(components: List<BlendComponent>): List<String> {
        val total = components.sumOf { it.grams.trim().toDoubleOrNull() ?: 0.0 }
        return components.map { c ->
            val g = c.grams.trim()
            val pct = if (total > 0) " (${((g.toDoubleOrNull() ?: 0.0) / total * 100).roundToInt()}%)" else ""
            c.name + (if (g.isNotEmpty()) " ${g}g" else "") + pct
        }
    }

    fun customTitle(blend: Blend): String =
        blend.name.trim().ifEmpty { blend.beans.joinToString(" + ") { it.name }.ifEmpty { "이름 없는 블렌드" } }

    fun customDate(blend: Blend): String = Dates.parseIsoDate(blend.date)?.let { Dates.ymdCompact(it) } ?: Dates.ymdCompact(blend.createdAt)

    /** Web populateBlendBeanDatalist: distinct display names, most recently had first. */
    fun recentBeanNames(records: List<BeanRecord>): List<String> {
        val seen = HashSet<String>()
        return records.filter { it.name.isNotBlank() }.sortedByDescending { it.createdAt }
            .mapNotNull { r -> if (seen.add(BeanNames.coreBeanName(r.name))) BeanNames.displayName(r.name) else null }
    }

    fun validRows(rows: List<BlendComponent>): List<BlendComponent> =
        rows.map { BlendComponent(it.name.trim(), it.grams.trim()) }.filter { it.name.isNotEmpty() }
}
