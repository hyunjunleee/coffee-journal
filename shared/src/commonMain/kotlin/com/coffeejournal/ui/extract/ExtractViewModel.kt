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
import com.coffeejournal.ui.theme.DerivationDispatcher
import com.coffeejournal.ui.theme.deriveOffMain
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate

/** Transient UI choices on the home tab (the search text and the 총정리 draft are kept apart, see [ExtractViewModel]). */
data class ExtractControls(
    val filterMode: String = ExtractFilter.ALL,
    val smallMode: String = SmallPackFilter.ALL,
    /** Explicit expand/collapse overrides; every group starts collapsed, like the web. */
    val expanded: Map<String, Boolean> = emptyMap(),
    val editingSummaryKey: String? = null,
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

/**
 * The home tab. Grouping, search and the 마시는 중 card are derived off the main thread (gap #10): the lists only
 * depend on the records, the filter, the search and the date, and are recomputed on [DerivationDispatcher] with the
 * newest input winning; opening a group, the 총정리 editor or the best-recipe picker only re-maps the finished groups.
 * The search text itself is state of its own, so the field never waits for a regroup, and the search runs
 * [SEARCH_DEBOUNCE_MS] after the last keystroke. [today] moves on at midnight, so the D-day pill and the
 * "N일째" text follow the date while the tab stays open (gap #14).
 */
class ExtractViewModel(
    private val entries: EntryRepository,
    private val pantry: PantryRepository,
    private val blends: BlendRepository,
    private val misc: MiscRepository,
    private val beanMeta: BeanMetaRepository,
    private val settings: SettingsRepository,
    today: Flow<LocalDate> = Dates.todayFlow(),
) : ViewModel() {

    private data class Sources(val entries: List<Entry>, val pantry: List<PantryItem>, val blends: List<Blend>, val misc: List<MiscItem>)
    private data class Meta(val summaries: Map<String, BeanSummary>, val best: Map<String, String>, val dday: LocalDate?, val count: Int)
    private data class ListInputs(val sources: Sources, val filterMode: String, val smallMode: String, val query: String, val today: LocalDate)
    private data class Lists(
        val today: LocalDate,
        val groups: List<BeanGroup>,
        val openedSmallPacks: List<SmallPackCard>,
        val search: SearchResult?,
        val drinking: DrinkingState,
        val emptyText: String?,
    )

    private val controls = MutableStateFlow(ExtractControls())
    private val queryText = MutableStateFlow("")
    private val draft = MutableStateFlow("")

    /** What the search field shows: every keystroke at once, independent of the (debounced) search. */
    val query: StateFlow<String> = queryText.asStateFlow()

    /** The 총정리 being written; kept out of [state] so typing it does not re-map the group list. */
    val summaryDraft: StateFlow<String> = draft.asStateFlow()

    private val sources = combine(entries.observeAll(), pantry.observeAll(), blends.observeAll(), misc.observeAll()) { e, p, b, m -> Sources(e, p, b, m) }
    private val meta = combine(beanMeta.observeSummaries(), beanMeta.observeBest(), settings.observeDdayStart(), entries.observeCount()) { s, b, d, c -> Meta(s, b, d, c) }

    @OptIn(FlowPreview::class)
    private val searchQuery: Flow<String> = queryText
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()

    private val lists: Flow<Lists> = combine(
        sources,
        controls.map { it.filterMode to it.smallMode }.distinctUntilChanged(),
        searchQuery,
        today.distinctUntilChanged(),
    ) { s, (filter, small), q, d -> ListInputs(s, filter, small, q, d) }
        .deriveOffMain(::buildLists)

    val state: StateFlow<ExtractUiState> = combine(lists, meta, controls) { l, m, c -> Triple(l, m, c) }
        .deriveOffMain { (l, m, c) -> assemble(l, m, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExtractUiState())

    private suspend fun buildLists(i: ListInputs): Lists {
        val s = i.sources
        val selection = ExtractGrouping.selectEntries(s.entries, s.pantry, i.filterMode, i.smallMode)
        val listed = selection.brewEntries.map { ListedEntry(it) } + ExtractGrouping.projectHomeCuppings(s.entries, selection.brewEntries)
        val groups = ExtractGrouping.buildGroups(listed, s.entries)
        yield()
        val search = ExtractGrouping.search(s.entries, i.query)
        yield()
        val empty = selection.brewEntries.isEmpty() && selection.openedSmallPacks.isEmpty()
        return Lists(
            today = i.today,
            groups = groups,
            openedSmallPacks = selection.openedSmallPacks.map { ExtractGrouping.smallPackCard(it) },
            search = search,
            drinking = ExtractGrouping.drinking(s.entries, s.pantry, s.blends, s.misc, i.today),
            emptyText = if (empty) ExtractGrouping.emptyText(i.filterMode, i.smallMode) else null,
        )
    }

    private fun assemble(l: Lists, m: Meta, c: ExtractControls): ExtractUiState {
        val dayCount = m.dday?.let { DdayRules.dayCount(it, l.today) }
        val groupUis = l.groups.map { g ->
            val bestId = m.best[g.key]
            GroupUi(
                group = g,
                expanded = c.expanded[g.key] ?: false,
                summary = m.summaries[g.key]?.text,
                bestEntry = bestId?.let { id -> g.recipeEntries.firstOrNull { it.id == id } },
                bestEntryId = bestId,
                editingSummary = c.editingSummaryKey == g.key,
                pickerOpen = c.pickerKey == g.key,
            )
        }
        return ExtractUiState(
            loaded = true,
            entryCount = m.count,
            ddayStart = m.dday,
            ddayLabel = DdayRules.label(m.dday, l.today),
            ddayMilestone = dayCount?.let { DdayRules.milestone(it) },
            drinking = l.drinking,
            controls = c,
            search = l.search,
            openedSmallPacks = l.openedSmallPacks,
            groups = groupUis,
            emptyText = l.emptyText,
        )
    }

    fun setFilterMode(mode: String) = controls.update { it.copy(filterMode = mode) }
    fun setSmallMode(mode: String) = controls.update { it.copy(smallMode = mode) }
    fun setQuery(query: String) { queryText.value = query }

    fun toggleGroup(key: String, currentlyExpanded: Boolean) = controls.update { it.copy(expanded = it.expanded + (key to !currentlyExpanded)) }

    fun startEditSummary(key: String, current: String?) {
        draft.value = current ?: ""
        controls.update { it.copy(editingSummaryKey = key, pickerKey = null) }
    }
    fun updateSummaryDraft(text: String) { draft.value = text }
    fun cancelEditSummary() {
        controls.update { it.copy(editingSummaryKey = null) }
        draft.value = ""
    }
    fun saveSummary(key: String) {
        val text = draft.value
        viewModelScope.launch {
            beanMeta.putSummary(key, text)
            controls.update { it.copy(editingSummaryKey = null) }
            draft.value = ""
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

    companion object {
        /** The home search waits this long after the last keystroke (the field itself never waits). */
        const val SEARCH_DEBOUNCE_MS = 120L
    }
}
