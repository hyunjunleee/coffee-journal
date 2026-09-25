package com.coffeejournal.ui.extract

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.repo.BeanMetaRepository
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.BeanSummary
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Transient UI choices on the home tab. */
data class ExtractControls(
    val filterMode: String = ExtractFilter.ALL,
    val smallMode: String = SmallPackFilter.ALL,
    val query: String = "",
    /** Explicit expand/collapse overrides; the first group is open by default. */
    val expanded: Map<String, Boolean> = emptyMap(),
    val editingSummaryKey: String? = null,
    val summaryDraft: String = "",
    val pickerKey: String? = null,
)

data class GroupUi(
    val group: BeanGroup,
    val expanded: Boolean,
    val summary: String?,
    val bestEntry: Entry?,
    val bestEntryId: String?,
    val editingSummary: Boolean,
    val pickerOpen: Boolean,
) {
    val showPickButton: Boolean get() = bestEntry == null && group.recipeEntries.size >= 5
}

data class ExtractUiState(
    val loaded: Boolean = false,
    val entryCount: Int = 0,
    val ddayStart: LocalDate? = null,
    val ddayLabel: String? = null,
    val ddayMilestone: DdayRules.Milestone? = null,
    val drinking: DrinkingState = DrinkingState.None,
    val controls: ExtractControls = ExtractControls(),
    val search: SearchResult? = null,
    val openedSmallPacks: List<SmallPackCard> = emptyList(),
    val groups: List<GroupUi> = emptyList(),
    val emptyText: String? = null,
)

class ExtractViewModel(
    private val entries: EntryRepository,
    private val pantry: PantryRepository,
    private val blends: BlendRepository,
    private val misc: MiscRepository,
    private val beanMeta: BeanMetaRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private data class Sources(val entries: List<Entry>, val pantry: List<PantryItem>, val blends: List<Blend>, val misc: List<MiscItem>)
    private data class Meta(val summaries: Map<String, BeanSummary>, val best: Map<String, String>, val dday: LocalDate?, val count: Int)

    private val controls = MutableStateFlow(ExtractControls())

    private val sources = combine(entries.observeAll(), pantry.observeAll(), blends.observeAll(), misc.observeAll()) { e, p, b, m -> Sources(e, p, b, m) }
    private val meta = combine(beanMeta.observeSummaries(), beanMeta.observeBest(), settings.observeDdayStart(), entries.observeCount()) { s, b, d, c -> Meta(s, b, d, c) }

    val state: StateFlow<ExtractUiState> = combine(sources, meta, controls) { s, m, c -> build(s, m, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExtractUiState())

    private fun build(s: Sources, m: Meta, c: ExtractControls): ExtractUiState {
        val today = Dates.today()
        val dayCount = m.dday?.let { DdayRules.dayCount(it, today) }
        val selection = ExtractGrouping.selectEntries(s.entries, s.pantry, c.filterMode, c.smallMode)
        val listed = selection.brewEntries.map { ListedEntry(it) } + ExtractGrouping.projectHomeCuppings(s.entries, selection.brewEntries)
        val groups = ExtractGrouping.buildGroups(listed, s.entries)
        val groupUis = groups.mapIndexed { index, g ->
            val bestId = m.best[g.key]
            GroupUi(
                group = g,
                expanded = c.expanded[g.key] ?: (index == 0),
                summary = m.summaries[g.key]?.text,
                bestEntry = bestId?.let { id -> g.recipeEntries.firstOrNull { it.id == id } },
                bestEntryId = bestId,
                editingSummary = c.editingSummaryKey == g.key,
                pickerOpen = c.pickerKey == g.key,
            )
        }
        val empty = selection.brewEntries.isEmpty() && selection.openedSmallPacks.isEmpty()
        return ExtractUiState(
            loaded = true,
            entryCount = m.count,
            ddayStart = m.dday,
            ddayLabel = DdayRules.label(m.dday, today),
            ddayMilestone = dayCount?.let { DdayRules.milestone(it) },
            drinking = ExtractGrouping.drinking(s.entries, s.pantry, s.blends, s.misc, today),
            controls = c,
            search = ExtractGrouping.search(s.entries, c.query),
            openedSmallPacks = selection.openedSmallPacks.map { ExtractGrouping.smallPackCard(it) },
            groups = groupUis,
            emptyText = if (empty) ExtractGrouping.emptyText(c.filterMode, c.smallMode) else null,
        )
    }

    fun setFilterMode(mode: String) = controls.update { it.copy(filterMode = mode) }
    fun setSmallMode(mode: String) = controls.update { it.copy(smallMode = mode) }
    fun setQuery(query: String) = controls.update { it.copy(query = query) }

    fun toggleGroup(key: String, currentlyExpanded: Boolean) = controls.update { it.copy(expanded = it.expanded + (key to !currentlyExpanded)) }

    fun startEditSummary(key: String, current: String?) = controls.update { it.copy(editingSummaryKey = key, summaryDraft = current ?: "", pickerKey = null) }
    fun updateSummaryDraft(text: String) = controls.update { it.copy(summaryDraft = text) }
    fun cancelEditSummary() = controls.update { it.copy(editingSummaryKey = null, summaryDraft = "") }
    fun saveSummary(key: String) {
        val text = controls.value.summaryDraft
        viewModelScope.launch {
            beanMeta.putSummary(key, text)
            controls.update { it.copy(editingSummaryKey = null, summaryDraft = "") }
        }
    }

    fun openBestPicker(key: String) = controls.update { it.copy(pickerKey = key) }
    fun closeBestPicker() = controls.update { it.copy(pickerKey = null) }
    fun pickBest(key: String, entryId: String) {
        viewModelScope.launch {
            beanMeta.setBest(key, entryId)
            controls.update { it.copy(pickerKey = null) }
        }
    }

    fun saveDday(date: LocalDate) {
        viewModelScope.launch { settings.setDdayStart(date) }
    }
}
