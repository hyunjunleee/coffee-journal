package com.coffeejournal.ui.bean

import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.PantryItem

/** Everything the 원두 tab views derive their statistics from; computed once by the tab view model. */
data class BeanData(
    val entries: List<Entry> = emptyList(),
    /** BeanRecords.flatten(entries): one row per tasted bean, cupping sessions expanded. */
    val records: List<BeanRecord> = emptyList(),
    val miscItems: List<MiscItem> = emptyList(),
    val blends: List<Blend> = emptyList(),
    val pantry: List<PantryItem> = emptyList(),
)
