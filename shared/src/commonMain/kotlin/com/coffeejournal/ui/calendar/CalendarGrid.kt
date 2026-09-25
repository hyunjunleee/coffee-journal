package com.coffeejournal.ui.calendar

import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RoadmapPhase
import com.coffeejournal.domain.rules.BeanRange
import com.coffeejournal.domain.rules.CalendarRanges
import com.coffeejournal.domain.rules.CuppingTypes
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import com.coffeejournal.domain.rules.Packages
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** Calendar filter values (web calFilter): 전체 or one of the record categories. */
internal object CalFilter {
    const val ALL = "전체"
    const val CUPPING = Category.CUPPING
    const val CAFE = Category.CAFE
    const val BEAN = Category.BEAN
    val all = listOf(ALL, CUPPING, CAFE, BEAN)
}

internal data class YearMonth(val year: Int, val month: Int) {
    val label: String get() = "${year}년 ${month}월"
    val first: LocalDate get() = LocalDate(year, month, 1)
    val nextFirst: LocalDate get() = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    val dayCount: Int get() = Dates.daysBetween(first, nextFirst)
    fun prev(): YearMonth = if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)
    fun next(): YearMonth = if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)
    fun contains(date: LocalDate): Boolean = date.year == year && date.month.number == month
    fun contains(epochMillis: Long): Boolean = contains(Dates.toLocalDate(epochMillis))

    companion object {
        fun of(date: LocalDate): YearMonth = YearMonth(date.year, date.month.number)
    }
}

/** Everything one calendar square needs to draw itself (web renderCalendar per-day block). */
internal data class DayCell(
    val date: LocalDate,
    val entries: List<Entry>,
    val blends: List<Blend>,
    /** Distinct categories in record order, blends counted as 원두 — the colour bar at the top. */
    val categories: List<String>,
    /** 원두 records + lab blends that day. */
    val cupCount: Int,
    val milestone: DdayRules.Milestone?,
    /** Up to three category dots (+ one 원두 dot for blends when there is room). */
    val dotCategories: List<String>,
    val range: BeanRange?,
    val capLeft: Boolean,
    val capRight: Boolean,
    val isToday: Boolean,
) {
    val hasContent: Boolean get() = entries.isNotEmpty() || blends.isNotEmpty()
    val showCupBadge: Boolean get() = cupCount >= 2
    /** Web: a single record and no blend opens the record directly instead of the day panel. */
    val opensDirectly: Boolean get() = entries.size == 1 && blends.isEmpty()
}

internal data class MonthGrid(
    val yearMonth: YearMonth,
    /** Empty squares before the 1st so that Sunday is the first column. */
    val leadingBlanks: Int,
    val cells: List<DayCell>,
    /** Bean ranges overlapping the visible month, for the legend. */
    val visibleRanges: List<BeanRange>,
)

/** Pure calendar computations ported from the web renderCalendar / list renderers. */
internal object CalendarGrid {
    val weekdays = listOf("일", "월", "화", "수", "목", "금", "토")

    fun category(entry: Entry): String = entry.category.ifBlank { Category.BEAN }

    fun entriesForFilter(entries: List<Entry>, filter: String): List<Entry> =
        if (filter == CalFilter.ALL) entries else entries.filter { category(it) == filter }

    /** Lab blends sit on their own date when set, otherwise on the day they were created. */
    fun blendDate(blend: Blend): LocalDate = Dates.parseIsoDate(blend.date) ?: Dates.toLocalDate(blend.createdAt)

    fun build(
        yearMonth: YearMonth,
        entries: List<Entry>,
        blends: List<Blend>,
        filter: String,
        ddayStart: LocalDate?,
        today: LocalDate,
        ranges: List<BeanRange> = CalendarRanges.compute(entries),
    ): MonthGrid {
        // oldest first within a day, like the web (its records array is in creation order): panel rows, dots, colour bar
        val byDay = entriesForFilter(entries, filter).sortedBy { it.createdAt }.groupBy { Dates.toLocalDate(it.createdAt) }
        val includeBlends = filter == CalFilter.ALL || filter == CalFilter.BEAN
        val blendsByDay = if (includeBlends) blends.groupBy(::blendDate) else emptyMap()
        val cells = (1..yearMonth.dayCount).map { day ->
            val date = LocalDate(yearMonth.year, yearMonth.month, day)
            buildCell(date, byDay[date].orEmpty(), blendsByDay[date].orEmpty(), ranges, ddayStart, today)
        }
        val monthStart = Dates.startOfDayMillis(yearMonth.first)
        val monthEnd = Dates.startOfDayMillis(yearMonth.nextFirst) - 1
        val visible = ranges.filter { monthStart <= it.end && monthEnd >= it.start }
        return MonthGrid(yearMonth, Dates.sundayFirstIndex(yearMonth.first.dayOfWeek), cells, visible)
    }

    fun buildCell(
        date: LocalDate,
        dayEntries: List<Entry>,
        dayBlends: List<Blend>,
        ranges: List<BeanRange>,
        ddayStart: LocalDate?,
        today: LocalDate,
    ): DayCell {
        val dayStart = Dates.startOfDayMillis(date)
        val dayEnd = dayStart + Dates.DAY_MS - 1
        // several ranges may overlap one day; the web draws only the first (earliest) one
        val range = ranges.firstOrNull { dayStart <= it.end && dayEnd >= it.start }
        val dow = date.dayOfWeek
        val capLeft = range != null && (dayStart <= range.start || dow == DayOfWeek.SUNDAY)
        val capRight = range != null && (dayEnd >= range.end || dow == DayOfWeek.SATURDAY)
        val categories = LinkedHashSet(dayEntries.map(::category))
        if (dayBlends.isNotEmpty()) categories += Category.BEAN
        val cupCount = dayEntries.count { Packages.isBrew(it) } + dayBlends.size
        val milestone = ddayStart?.let { DdayRules.calendarBadge(DdayRules.dayCount(it, date)) }
        val dots = dayEntries.take(3).map(::category).toMutableList()
        if (dayBlends.isNotEmpty() && dots.size < 3) dots += Category.BEAN
        return DayCell(
            date = date, entries = dayEntries, blends = dayBlends, categories = categories.toList(), cupCount = cupCount,
            milestone = milestone, dotCategories = dots, range = range, capLeft = capLeft, capRight = capRight,
            isToday = date == today,
        )
    }

    /** Web renderCafeCuppingList filter: non-원두 records, optional category, cupping type and month scope. */
    fun cafeCuppingList(
        entries: List<Entry>,
        filter: String,
        cuppingType: String,
        monthOnly: Boolean,
        yearMonth: YearMonth,
    ): List<Entry> = entries.filter { en ->
        val cat = category(en)
        when {
            cat == Category.BEAN -> false
            (filter == CalFilter.CAFE || filter == CalFilter.CUPPING) && cat != filter -> false
            cat == Category.CUPPING && CuppingTypes.effective(en) != cuppingType -> false
            else -> !monthOnly || yearMonth.contains(en.createdAt)
        }
    }.sortedByDescending { it.createdAt }

    /** Web renderCalendarBrewList: this month's 원두 records, newest first. */
    fun brewList(entries: List<Entry>, yearMonth: YearMonth): List<Entry> =
        entries.filter { Packages.isBrew(it) && yearMonth.contains(it.createdAt) }.sortedByDescending { it.createdAt }

    /** Web "Today" box: the most recently recorded 원두. */
    fun latestBrewName(entries: List<Entry>): String? =
        entries.filter { Packages.isBrew(it) }.maxByOrNull { it.createdAt }?.let { it.name.ifBlank { "이름 없음" } }

    /** Web renderBeansToTry: cupping records with an overall review, newest first. */
    fun cuppingReviews(entries: List<Entry>): List<Entry> =
        entries.filter { it.isCupping && it.notes.isNotBlank() }.sortedByDescending { it.createdAt }

    /** Web cuppingOverallReviewExcerpt: collapse whitespace, cut at 115 characters. */
    fun reviewExcerpt(notes: String): String {
        val clean = notes.replace(Regex("\\s+"), " ").trim()
        return if (clean.length > 115) clean.take(115).trimEnd() + "…" else clean
    }

    /**
     * Web findCurrentRoadmapPhase: the phase whose [dayStart, dayEnd) contains the D-day count. When phases overlap
     * (the starter phase spans every day, so any phase added by hand overlaps it) the narrowest one wins, earlier
     * position on ties — the same answer as the web whenever phases do not overlap.
     */
    fun currentPhase(phases: List<RoadmapPhase>, day: Int?): RoadmapPhase? =
        day?.let { d -> phases.filter { d >= it.dayStart && d < it.dayEnd }.minByOrNull { it.dayEnd.toLong() - it.dayStart } }
}
