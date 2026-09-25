package com.coffeejournal.ui.misc

import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.reference.EquipmentTypes
import com.coffeejournal.domain.rules.Dates

/** Pure list rules of the web 기타 tab (renderMiscList): filtering, grouping, sorting and labels. */
object MiscListLogic {
    const val ALL = "all"
    const val SORT_FIXED = "fixed"
    const val SORT_RECENT = "registered"

    val typeTabs: List<String> = listOf(ALL) + EquipmentTypes.order
    val typeTabLabels: Map<String, String> = mapOf(ALL to "전체") + EquipmentTypes.order.associateWith { title(it) }
    val sortTabs: List<String> = listOf(SORT_FIXED, SORT_RECENT)
    val sortTabLabels: Map<String, String> = mapOf(SORT_FIXED to "지정순", SORT_RECENT to "최신순")

    /** A labelled block of cards; [emptyText] is shown when [items] is empty (per-type 보유/궁금 sections). */
    data class Section(val label: String?, val items: List<MiscItem>, val emptyText: String? = null)

    fun isEquipment(item: MiscItem): Boolean = item.type in EquipmentTypes.order

    /** Roasteries, importers, processes, varieties and farms live in the 원두 tab, never here. */
    fun equipmentOnly(items: List<MiscItem>): List<MiscItem> = items.filter(::isEquipment)

    fun title(type: String): String = if (type == ALL) EquipmentTypes.ALL_TITLE else EquipmentTypes.labels[type]?.title ?: type
    fun name(type: String): String = if (type == ALL) EquipmentTypes.ALL_NAME else EquipmentTypes.labels[type]?.name ?: type
    fun placeholder(type: String): String = EquipmentTypes.labels[type]?.placeholder ?: ""
    fun notesPlaceholder(type: String): String = EquipmentTypes.labels[type]?.notesPlaceholder ?: ""

    /** Web: `(status || '보유') === '보유'`. */
    fun isOwned(item: MiscItem): Boolean = item.status.ifBlank { MiscStatus.OWNED } == MiscStatus.OWNED
    fun isCurious(item: MiscItem): Boolean = item.status == MiscStatus.CURIOUS

    fun filtered(items: List<MiscItem>, type: String): List<MiscItem> =
        if (type == ALL) equipmentOnly(items) else items.filter { it.type == type }

    fun emptyText(type: String): String = "아직 등록한 ${title(type)}이(가) 없어요."

    /** Empty when nothing matches (the screen then shows [emptyText]). */
    fun sections(items: List<MiscItem>, type: String, sort: String): List<Section> {
        val list = filtered(items, type)
        if (list.isEmpty()) return emptyList()
        val newest = compareByDescending<MiscItem> { it.createdAt }
        return when {
            type == ALL && sort == SORT_RECENT -> listOf(Section(null, list.sortedWith(newest)))
            type == ALL -> EquipmentTypes.order.mapNotNull { t ->
                list.filter { it.type == t }.takeIf { it.isNotEmpty() }?.let { Section(name(t), it.sortedWith(newest)) }
            }
            else -> {
                val n = name(type)
                listOf(
                    Section("보유 $n", list.filter(::isOwned).sortedWith(newest), "아직 등록한 ${n}이(가) 없어요."),
                    Section("궁금한 $n", list.filter(::isCurious).sortedWith(newest), "궁금한 ${n}을(를) 추가해보세요."),
                )
            }
        }
    }

    /** Web: "2026.7.15부터 사용" from an ISO date; null when blank or malformed. */
    fun sinceLabel(since: String): String? = Dates.parseIsoDate(since)?.let { "${Dates.ymdCompact(it)}부터 사용" }

    /** The type a new item gets from the FAB: the current tab, or 드리퍼 on 전체. */
    fun formType(currentType: String): String = if (currentType == ALL) MiscType.DRIPPER else currentType
}
