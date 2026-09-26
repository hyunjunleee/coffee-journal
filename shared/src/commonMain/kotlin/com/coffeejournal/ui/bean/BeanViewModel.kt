package com.coffeejournal.ui.bean

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.ui.theme.deriveOffMain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Sub views of the 원두 tab in the web's order; ids match the web data-bean-view values. */
object BeanViews {
    const val NOTES = "notes"
    const val PROCESS = "process"
    const val ROAST = "roast"
    const val VARIETY = "variety"
    const val BLEND = "blend"
    const val SOURCE = "source"
    const val SELECTION = "selection"
    const val MAP = "map"
    const val SPECIALTY = "specialty"

    val all: List<String> = listOf(NOTES, PROCESS, ROAST, VARIETY, BLEND, SOURCE, SELECTION, MAP, SPECIALTY)
    val labels: Map<String, String> = mapOf(
        NOTES to "커피 노트", PROCESS to "가공 방식", ROAST to "배전도", VARIETY to "품종", BLEND to "블렌드",
        SOURCE to "로스터리", SELECTION to "생두 수입사", MAP to "커피 지도 + 농장(생산자)", SPECIALTY to "✦ Competition Lots",
    )
    const val DEFAULT = MAP
}

/** Combines every repository the 원두 tab reads into one [BeanData] and keeps the selected sub view. */
class BeanViewModel(
    entries: EntryRepository,
    misc: MiscRepository,
    blends: BlendRepository,
    pantry: PantryRepository,
) : ViewModel() {
    /** Flattened off the main thread, newest input winning (gap #10). */
    val data: StateFlow<BeanData> = combine(
        entries.observeAll(), misc.observeAll(), blends.observeAll(), pantry.observeAll(),
    ) { entryList, miscItems, blendList, pantryItems -> BeanData(loaded = true, entries = entryList, miscItems = miscItems, blends = blendList, pantry = pantryItems) }
        .deriveOffMain { it.copy(records = BeanRecords.flatten(it.entries)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BeanData())

    private val _selectedView = MutableStateFlow(BeanViews.DEFAULT)
    val selectedView: StateFlow<String> = _selectedView.asStateFlow()

    fun selectView(view: String) {
        if (view in BeanViews.all) _selectedView.value = view
    }
}
