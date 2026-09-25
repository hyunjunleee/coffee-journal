package com.coffeejournal.domain.rules

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.PantryItem

object Packages {
    /** Web entryPackageType with the legacy isDripBag flag folded into packageType at import time. */
    fun entryPackageType(entry: Entry): String = when (entry.packageType) {
        PackageType.SAMPLE -> PackageType.SAMPLE
        PackageType.DRIPBAG -> PackageType.DRIPBAG
        else -> PackageType.STANDARD
    }

    /** Web pantryPackageType: legacy items with weight 20 are samples. */
    fun pantryPackageType(item: PantryItem): String = when (item.packageType) {
        PackageType.SAMPLE, PackageType.DRIPBAG, PackageType.STANDARD -> item.packageType
        else -> if (item.weight.trim().toDoubleOrNull() == 20.0) PackageType.SAMPLE else PackageType.STANDARD
    }

    /** Web isBlendRecord. */
    fun isBlend(entry: Entry): Boolean =
        entry.beanMode == BeanMode.BLEND || entry.beanMode == BeanMode.COMMERCIAL_BLEND ||
            entry.beanMode == BeanMode.CUSTOM_BLEND || entry.blendComponents.isNotEmpty()

    fun isDecaf(entry: Entry): Boolean = BeanNames.isDecaf(entry.name, entry.process, entry.processOther)

    fun isBrew(entry: Entry): Boolean = entry.category.ifBlank { Category.BEAN } == Category.BEAN
}
