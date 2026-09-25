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
import com.coffeejournal.domain.reference.RoadmapDefaults
import com.coffeejournal.domain.rules.CalendarRanges
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import com.coffeejournal.domain.rules.BeanRange
import com.coffeejournal.domain.rules.Ids
import com.coffeejournal.ui.theme.deriveOffMain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

data class CalendarControls(
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

data class CalendarUiState(
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
    /** All records, for the header's "N entries" (web #entry-count); null before the first load. */
    val entryCount: Int? = null,
) {
    val yearMonth: YearMonth get() = controls.yearMonth
    val filter: String get() = controls.filter
}

/**
 * The 커피 달력 tab. The month grid, ranges and lists are derived off the main thread with the newest input winning
 * (gap #10); the date comes from [today], which moves on at midnight, so the today cell and the D-day follow it
 * while the tab stays open (gap #14). Roadmap edits are read-modify-write of a whole phase; they run one at a time
 * and re-read the phase inside the lock, so rapid taps never undo each other (gap #11).
 */
class CalendarViewModel(
    entries: EntryRepository,
    blends: BlendRepository,
    private val roadmapRepo: RoadmapRepository,
    settings: SettingsRepository,
    today: Flow<LocalDate> = Dates.todayFlow(),
) : ViewModel() {
    private val controls = MutableStateFlow(CalendarControls(YearMonth.of(Dates.today())))

    /** Records oldest first with their bean ranges; recomputed only when the records change. */
    private class Prepared(val entries: List<Entry>, val ranges: List<BeanRange>)
    private class Inputs(val prepared: Prepared, val blends: List<Blend>, val phases: List<RoadmapPhase>, val ddayStart: LocalDate?, val controls: CalendarControls, val today: LocalDate)

    // the repository lists newest first; the web walks its records oldest first (day panel, dots, legend names)
    private val prepared = entries.observeAll().deriveOffMain { newestFirst ->
        val sorted = newestFirst.sortedBy { it.createdAt }
        Prepared(sorted, CalendarRanges.compute(sorted))
    }

    val state: StateFlow<CalendarUiState> = combine(
        prepared, blends.observeAll(), roadmapRepo.observeAll(), settings.observeDdayStart(),
        combine(controls, today.distinctUntilChanged()) { c, d -> c to d },
    ) { p, b, r, d, (c, day) -> Inputs(p, b, r, d, c, day) }
        .deriveOffMain { build(it.prepared, it.blends, it.phases, it.ddayStart, it.controls, it.today, entryCount = it.prepared.entries.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(Prepared(emptyList(), emptyList()), emptyList(), emptyList(), null, controls.value, Dates.today(), entryCount = null))

    /** One roadmap write at a time (see [updatePhase]). */
    private val roadmapLock = Mutex()

    init {
        viewModelScope.launch { roadmapLock.withLock { roadmapRepo.ensureSeeded() } }
    }

    private fun build(
        p: Prepared, blends: List<Blend>, phases: List<RoadmapPhase>, ddayStart: LocalDate?, c: CalendarControls, today: LocalDate, entryCount: Int?,
    ): CalendarUiState {
        val entries = p.entries
        val grid = CalendarGrid.build(c.yearMonth, entries, blends, c.filter, ddayStart, today, p.ranges)
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
            entryCount = entryCount,
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

    /**
     * Read-modify-write of one phase. Every roadmap write takes [roadmapLock] and reads the phase inside it, so two
     * quick taps (toggle one item, then another; add one, toggle another) both land instead of the second one writing
     * back the phase as it was before the first.
     */
    private fun updatePhase(phaseId: String, transform: (RoadmapPhase) -> RoadmapPhase) {
        viewModelScope.launch {
            roadmapLock.withLock {
                val phase = roadmapRepo.getAll().firstOrNull { it.id == phaseId } ?: return@withLock
                roadmapRepo.upsert(transform(phase))
            }
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

    /** App addition: removes a phase added by hand (the starter phase stays). */
    fun deletePhase(phaseId: String) {
        if (phaseId == RoadmapDefaults.STARTER_ID) return
        viewModelScope.launch {
            roadmapLock.withLock {
                val all = roadmapRepo.getAll()
                if (all.none { it.id == phaseId }) return@withLock
                roadmapRepo.replaceAll(all.filter { it.id != phaseId })
            }
            controls.update { c -> if (c.openPhaseId == phaseId) c.copy(openTouched = false, openPhaseId = null) else c }
        }
    }

    /** App addition: the web ships only the starter phase and has no way to add another. */
    fun addPhase(title: String, range: String, dayStart: Int, dayEnd: Int) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        viewModelScope.launch {
            val id = Ids.newCustomId("phase-")
            roadmapLock.withLock {
                val all = roadmapRepo.getAll()
                roadmapRepo.upsert(
                    RoadmapPhase(
                        id = id, position = (all.maxOfOrNull { it.position } ?: -1) + 1, title = cleanTitle, range = range.trim(),
                        dayStart = minOf(dayStart, dayEnd), dayEnd = maxOf(dayStart, dayEnd),
                    )
                )
            }
            controls.update { it.copy(openTouched = true, openPhaseId = id) }
        }
    }
}
