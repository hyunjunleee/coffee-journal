package com.coffeejournal.ui.bean.b

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.BlendBeans
import com.coffeejournal.domain.rules.Dates

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

    /**
     * "이름 12g (40%)" per component; the percentage only when any grams were entered, whole percents that add up to
     * 100 ([BlendBeans.sharesFromGrams]). "NaN" / "Infinity" / "1e999" grams count as missing.
     */
    fun componentLines(components: List<BlendComponent>): List<String> {
        val shares = BlendBeans.sharesFromGrams(components.map { it.grams })
        val any = shares.any { it != null }
        return components.mapIndexed { i, c ->
            val g = c.grams.trim()
            c.name + (if (g.isNotEmpty()) " ${g}g" else "") + (if (any) " (${shares[i] ?: 0}%)" else "")
        }
    }

    /** A café blend record's beans, "브라질 Cerrado 60%" each; nothing when its beans were never entered. */
    fun cafeBlendLines(entry: Entry): List<String> =
        if (!BlendBeans.hasBeans(entry)) emptyList()
        else BlendBeans.beans(entry).mapIndexed { i, b -> BlendBeans.label(b, i) + (BlendBeans.percentText(b.percent)?.let { " $it" } ?: "") }

    /**
     * Web entryBlendCardHtml "상업 블렌드 · {roastery || source}": the roastery field, else the roastery in the name's
     * trailing parenthesis ("하우스 블렌드 (프릳츠)"); without either, no dangling "·".
     */
    fun commercialLine(entry: Entry): String {
        val shop = entry.roastery.trim().ifEmpty { BeanNames.parseNameRoastery(entry.name).trim() }
        return if (shop.isEmpty()) "상업 블렌드" else "상업 블렌드 · $shop"
    }

    /**
     * Web `<input type="number" step="0.1">` for grams: digits with at most one decimal point (a comma counts as the
     * point). Returns the text to keep, or null when [typed] is not a number being typed and the field keeps its value.
     */
    fun gramsInput(typed: String): String? = com.coffeejournal.ui.theme.InputFilters.decimal(typed)

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
