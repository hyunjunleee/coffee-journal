package com.coffeejournal.ui.bean

import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.PantryItem

/** Everything the 원두 tab views derive their statistics from; computed once by the tab view model. */
data class BeanData(
    /** False until the repositories have emitted once (the header shows no count before that). */
    val loaded: Boolean = false,
    val entries: List<Entry> = emptyList(),
    /** BeanRecords.flatten(entries): one row per tasted bean, cupping sessions expanded. */
    val records: List<BeanRecord> = emptyList(),
    /** [records] plus a café blend's other beans, one row each: for the views that count origins (map, variety, process). */
    val originRecords: List<BeanRecord> = emptyList(),
    val miscItems: List<MiscItem> = emptyList(),
    val blends: List<Blend> = emptyList(),
    val pantry: List<PantryItem> = emptyList(),
)
