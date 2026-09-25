package com.coffeejournal.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RoadmapItem
import com.coffeejournal.domain.model.RoadmapPhase
import com.coffeejournal.domain.rules.CalendarRanges
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import com.coffeejournal.domain.rules.Ids
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Top chips of the tab: four calendar filters plus the study and classes views. */
internal object CalTabs {
    const val ALL = CalFilter.ALL
    const val STUDY = "스터디"
    const val CLASSES = "클래스"
    const val CUPPING = CalFilter.CUPPING
    const val CAFE = CalFilter.CAFE
    const val BEAN = CalFilter.BEAN
    val all = listOf(ALL, STUDY, CLASSES, CUPPING, CAFE, BEAN)
}

internal object StudyTabs {
    const val ROADMAP = "로드맵"
    const val REVIEWS = "커핑 리뷰 모음"
    val all = listOf(ROADMAP, REVIEWS)
}

internal object ListScope {
    const val MONTH = "월별 보기"
    const val ALL = "전체 보기"
    val all = listOf(MONTH, ALL)
}

internal data class CalendarControls(
    val yearMonth: YearMonth,
    val tab: String = CalTabs.ALL,
    val filter: String = CalFilter.ALL,
    val cuppingType: String = CuppingType.PUBLIC,
    val listScope: String = ListScope.MONTH,
    val studyTab: String = StudyTabs.ROADMAP,
    /** Phase the person opened by hand; until they touch one the current phase is open. */
    val openPhaseId: String? = null,
    val openTouched: Boolean = false,
    val selectedDate: LocalDate? = null,
)

internal data class CalendarUiState(
    val controls: CalendarControls,
    val grid: MonthGrid,
    val todayBean: String?,
    val cafeCuppingList: List<Entry>,
    val brewList: List<Entry>,
    val roadmap: List<RoadmapPhase>,
    val ddayCount: Int?,
    val currentPhaseId: String?,
    val openPhaseId: String?,
    val reviews: List<Entry>,
    val selectedCell: DayCell?,
) {
    val yearMonth: YearMonth get() = controls.yearMonth
    val filter: String get() = controls.filter
}

class CalendarViewModel(
    entries: EntryRepository,
    blends: BlendRepository,
    private val roadmapRepo: RoadmapRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val controls = MutableStateFlow(CalendarControls(YearMonth.of(Dates.today())))

    internal val state: StateFlow<CalendarUiState> = combine(
        entries.observeAll(), blends.observeAll(), roadmapRepo.observeAll(), settings.observeDdayStart(), controls,
    ) { e, b, r, d, c -> build(e, b, r, d, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(emptyList(), emptyList(), emptyList(), null, controls.value))

    init {
        viewModelScope.launch { roadmapRepo.ensureSeeded() }
    }

    private fun build(entries: List<Entry>, blends: List<Blend>, phases: List<RoadmapPhase>, ddayStart: LocalDate?, c: CalendarControls): CalendarUiState {
        val today = Dates.today()
        val ranges = CalendarRanges.compute(entries)
        val grid = CalendarGrid.build(c.yearMonth, entries, blends, c.filter, ddayStart, today, ranges)
        val ddayCount = ddayStart?.let { DdayRules.dayCount(it, today) }
        val current = CalendarGrid.currentPhase(phases, ddayCount)
        val open = if (c.openTouched) c.openPhaseId else current?.id
        return CalendarUiState(
            controls = c,
            grid = grid,
            todayBean = CalendarGrid.latestBrewName(entries),
            cafeCuppingList = CalendarGrid.cafeCuppingList(entries, c.filter, c.cuppingType, c.listScope == ListScope.MONTH, c.yearMonth),
            brewList = CalendarGrid.brewList(entries, c.yearMonth),
            roadmap = phases,
            ddayCount = ddayCount,
            currentPhaseId = current?.id,
            openPhaseId = open,
            reviews = CalendarGrid.cuppingReviews(entries),
            selectedCell = c.selectedDate?.let { date -> grid.cells.firstOrNull { it.date == date }?.takeIf { it.hasContent } },
        )
    }

    // ----- month / filter controls -----

    fun prevMonth() = controls.update { it.copy(yearMonth = it.yearMonth.prev(), selectedDate = null) }
    fun nextMonth() = controls.update { it.copy(yearMonth = it.yearMonth.next(), selectedDate = null) }
    fun goToday() = controls.update { it.copy(yearMonth = YearMonth.of(Dates.today()), selectedDate = null) }

    /** Top chip: calendar filters change [CalendarControls.filter]; 스터디/클래스 only switch the view. */
    fun setTab(tab: String) = controls.update { c ->
        if (tab in CalFilter.all) c.copy(tab = tab, filter = tab, selectedDate = null) else c.copy(tab = tab)
    }

    fun setCuppingType(type: String) = controls.update { it.copy(cuppingType = type) }
    fun setListScope(scope: String) = controls.update { it.copy(listScope = scope) }
    fun setStudyTab(tab: String) = controls.update { it.copy(studyTab = tab) }
    fun selectDate(date: LocalDate?) = controls.update { it.copy(selectedDate = date) }

    // ----- roadmap -----

    fun togglePhase(phaseId: String) = controls.update { c ->
        val currentlyOpen = if (c.openTouched) c.openPhaseId else state.value.openPhaseId
        c.copy(openTouched = true, openPhaseId = if (currentlyOpen == phaseId) null else phaseId)
    }

    private fun updatePhase(phaseId: String, transform: (RoadmapPhase) -> RoadmapPhase) {
        viewModelScope.launch {
            val phase = roadmapRepo.getAll().firstOrNull { it.id == phaseId } ?: return@launch
            roadmapRepo.upsert(transform(phase))
        }
    }

    fun toggleItem(phaseId: String, itemId: String) = updatePhase(phaseId) { p ->
        p.copy(items = p.items.map { if (it.id == itemId) it.copy(done = !it.done) else it })
    }

    /** Blank edits are ignored so the row falls back to its previous text (web commit()). */
    fun editItem(phaseId: String, itemId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        updatePhase(phaseId) { p -> p.copy(items = p.items.map { if (it.id == itemId) it.copy(text = clean) else it }) }
    }

    fun deleteItem(phaseId: String, itemId: String) = updatePhase(phaseId) { p -> p.copy(items = p.items.filter { it.id != itemId }) }

    fun addItem(phaseId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        updatePhase(phaseId) { p -> p.copy(items = p.items + RoadmapItem(id = Ids.newCustomId("custom-"), text = clean)) }
    }

    /** App addition: the web ships only the starter phase and has no way to add another. */
    fun addPhase(title: String, range: String, dayStart: Int, dayEnd: Int) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        viewModelScope.launch {
            val all = roadmapRepo.getAll()
            val id = Ids.newCustomId("phase-")
            roadmapRepo.upsert(
                RoadmapPhase(
                    id = id, position = (all.maxOfOrNull { it.position } ?: -1) + 1, title = cleanTitle, range = range.trim(),
                    dayStart = minOf(dayStart, dayEnd), dayEnd = maxOf(dayStart, dayEnd),
                )
            )
            controls.update { it.copy(openTouched = true, openPhaseId = id) }
        }
    }
}
