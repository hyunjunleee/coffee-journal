package com.coffeejournal.ui.extract

import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.Packages
import com.coffeejournal.domain.rules.PantryRules

/** Pure helpers behind the pantry screen (web renderBeanPantry). */
object PantryListing {
    const val SORT_REGISTERED = "registered"
    const val SORT_PEAK = "peak"

    /** Unopened bags in the chosen order: registration (newest first) or expected peak (earliest first). */
    fun unopened(items: List<PantryItem>, sortMode: String): List<PantryItem> {
        val list = items.filter { !it.isOpened }
        return if (sortMode == SORT_PEAK) {
            list.sortedWith(compareBy<PantryItem> { PantryRules.peakStartMillis(it) }.thenByDescending { it.createdAt })
        } else list.sortedByDescending { it.createdAt }
    }

    /** Opened bags, most recently opened first. */
    fun opened(items: List<PantryItem>): List<PantryItem> =
        items.filter { it.isOpened }.sortedByDescending { it.openedAt ?: it.createdAt }

    /** Web card meta: "형태 · 로스터리 · 가격문구 · 로스팅 {date} · 구매 {date}". */
    fun metaLine(item: PantryItem): String = listOf(
        PantryRules.packageLabel(item),
        item.roastery,
        PantryRules.priceText(item.weight, item.price).ifBlank { ExtractGrouping.weightText(item.weight) },
        if (item.roastDate.isNotBlank()) "로스팅 ${item.roastDate}" else "",
        if (item.purchaseDate.isNotBlank()) "구매 ${item.purchaseDate}" else "",
    ).filter { it.isNotBlank() }.joinToString(" · ")

    fun openConfirmText(item: PantryItem): String =
        if (Packages.pantryPackageType(item) != PackageType.STANDARD) "이 ${PantryRules.packageLabel(item)}를 개봉하고 드립백 / 소량으로 옮길까요?"
        else "이 원두를 개봉하고 위클리 원두로 옮길까요?"

    fun markOpened(item: PantryItem, now: Long): PantryItem =
        item.copy(status = PantryItem.STATUS_OPENED, openedAt = now, packageType = Packages.pantryPackageType(item))

    /** App improvement (design §2.3): undo an opening. */
    fun markUnopened(item: PantryItem): PantryItem = item.copy(status = PantryItem.STATUS_UNOPENED, openedAt = null)
}
