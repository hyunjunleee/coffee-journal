package com.coffeejournal.ui.calendar

import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.ClassType
import com.coffeejournal.domain.model.CoffeeClass
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RoadmapPhase
import com.coffeejournal.domain.rules.Dates
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarGridTest {
    private val sep = YearMonth(2026, 9)
    private val today = LocalDate(2026, 9, 25)

    private fun entry(date: LocalDate, name: String = "A", category: String = Category.BEAN, hour: Int = 9, id: String = "$name-$date-$hour", extra: Entry.() -> Entry = { this }) =
        Entry(id = id, createdAt = Dates.toMillis(date, hour, 0), category = category, name = name).extra()

    private fun cell(grid: MonthGrid, day: Int): DayCell = grid.cells[day - 1]

    @Test fun septemberGridStartsOnTuesdayAndHasThirtyCells() {
        val grid = CalendarGrid.build(sep, emptyList(), emptyList(), CalFilter.ALL, null, today)
        assertEquals(2, grid.leadingBlanks)
        assertEquals(30, grid.cells.size)
        assertEquals(30, sep.dayCount)
        assertTrue(cell(grid, 25).isToday)
        assertFalse(cell(grid, 24).isToday)
        assertEquals(YearMonth(2026, 10), sep.next())
        assertEquals(YearMonth(2025, 12), YearMonth(2026, 1).prev())
        assertEquals("2026년 9월", sep.label)
    }

    @Test fun rangeCapsAtRangeEndsAndWeekEdges() {
        // same bean 9/8 (Tue) → 9/15 (Tue): one continuous range
        val entries = listOf(entry(LocalDate(2026, 9, 8)), entry(LocalDate(2026, 9, 11)), entry(LocalDate(2026, 9, 15)))
        val grid = CalendarGrid.build(sep, entries, emptyList(), CalFilter.CAFE, null, today)
        // ranges ignore the filter: the band is drawn even though 카페 hides the records
        assertTrue(cell(grid, 8).capLeft); assertFalse(cell(grid, 8).capRight)
        assertFalse(cell(grid, 10).capLeft); assertFalse(cell(grid, 10).capRight)
        assertTrue(cell(grid, 12).capRight)   // Saturday
        assertTrue(cell(grid, 13).capLeft)    // Sunday
        assertTrue(cell(grid, 15).capRight); assertFalse(cell(grid, 15).capLeft)
        assertNull(cell(grid, 16).range)
        assertEquals(1, grid.visibleRanges.size)
        assertEquals(3, grid.visibleRanges.first().count)
        assertTrue(cell(grid, 8).entries.isEmpty())
    }

    @Test fun cupBadgeNeedsTwoBrewsOrBlends() {
        val d = LocalDate(2026, 9, 3)
        val blend = Blend(id = "b1", name = "실험 1", date = "2026-09-03", createdAt = 0L)
        val one = CalendarGrid.build(sep, listOf(entry(d)), emptyList(), CalFilter.ALL, null, today)
        assertFalse(cell(one, 3).showCupBadge)
        assertTrue(cell(one, 3).opensDirectly)
        val withBlend = CalendarGrid.build(sep, listOf(entry(d)), listOf(blend), CalFilter.ALL, null, today)
        assertEquals(2, cell(withBlend, 3).cupCount)
        assertTrue(cell(withBlend, 3).showCupBadge)
        assertFalse(cell(withBlend, 3).opensDirectly)
        assertEquals(listOf(Category.BEAN), cell(withBlend, 3).categories)
        assertEquals(listOf(Category.BEAN, Category.BEAN), cell(withBlend, 3).dotCategories)
        // cafe records never count as cups, and blends disappear under the 카페 filter
        val cafe = CalendarGrid.build(sep, listOf(entry(d, category = Category.CAFE), entry(d, category = Category.CAFE, hour = 10)), listOf(blend), CalFilter.CAFE, null, today)
        assertEquals(0, cell(cafe, 3).cupCount)
        assertTrue(cell(cafe, 3).blends.isEmpty())
        assertEquals(listOf(Category.CAFE), cell(cafe, 3).categories)
    }

    @Test fun milestoneBadgesEveryTenDays() {
        val start = LocalDate(2026, 6, 3)
        val grid = CalendarGrid.build(sep, emptyList(), emptyList(), CalFilter.ALL, start, today)
        val hundred = cell(grid, 10).milestone
        assertNotNull(hundred); assertEquals("100일", hundred.text); assertTrue(hundred.big)
        val hundredTen = cell(grid, 20).milestone
        assertNotNull(hundredTen); assertEquals("110일", hundredTen.text); assertFalse(hundredTen.big)
        assertNull(cell(grid, 11).milestone)
        assertNull(CalendarGrid.build(sep, emptyList(), emptyList(), CalFilter.ALL, null, today).cells[9].milestone)
    }

    @Test fun cuppingListFiltersByEffectiveType() {
        val explicitPublic = entry(LocalDate(2026, 9, 2), name = "퍼블릭 커핑", category = Category.CUPPING) { copy(cuppingType = CuppingType.PUBLIC) }
        val inferredHome = entry(LocalDate(2026, 9, 5), name = "집에서 커핑", category = Category.CUPPING)
        val classCupping = entry(LocalDate(2026, 8, 30), name = "센서리 수업", category = Category.CUPPING) { copy(cuppingType = CuppingType.CLASS) }
        val cafe = entry(LocalDate(2026, 9, 6), name = "카페", category = Category.CAFE)
        val all = listOf(explicitPublic, inferredHome, classCupping, cafe, entry(LocalDate(2026, 9, 7)))
        assertEquals(listOf(explicitPublic.id), CalendarGrid.cafeCuppingList(all, CalFilter.CUPPING, CuppingType.PUBLIC, true, sep).map { it.id })
        assertEquals(listOf(inferredHome.id), CalendarGrid.cafeCuppingList(all, CalFilter.CUPPING, CuppingType.HOME, true, sep).map { it.id })
        assertTrue(CalendarGrid.cafeCuppingList(all, CalFilter.CUPPING, CuppingType.CLASS, true, sep).isEmpty())
        assertEquals(listOf(classCupping.id), CalendarGrid.cafeCuppingList(all, CalFilter.CUPPING, CuppingType.CLASS, false, sep).map { it.id })
        assertEquals(listOf(cafe.id), CalendarGrid.cafeCuppingList(all, CalFilter.CAFE, CuppingType.PUBLIC, true, sep).map { it.id })
        assertEquals(1, CalendarGrid.brewList(all, sep).size)
    }

    @Test fun classesSortByDateThenCreation() {
        val recurring = CoffeeClass(id = "r", createdAt = 1, title = "주간", classType = ClassType.RECURRING, startDate = "2026-08-01", endDate = "2026-09-30")
        val oneday = CoffeeClass(id = "o", createdAt = 2, title = "원데이", classType = ClassType.ONEDAY, date = "2026-09-10")
        val sameDayOlder = CoffeeClass(id = "s", createdAt = 1, title = "같은 날", classType = ClassType.ONEDAY, date = "2026-09-10")
        val undated = CoffeeClass(id = "u", createdAt = 99, title = "미정", classType = "")
        val sorted = ClassLists.sorted(listOf(undated, sameDayOlder, recurring, oneday))
        assertEquals(listOf("o", "s", "r", "u"), sorted.map { it.id })
        assertEquals(listOf("o", "s", "u"), ClassLists.filter(sorted, ClassType.ONEDAY).map { it.id })
        assertEquals("2026년 8월 1일 ~ 2026년 9월 30일", ClassLists.dateLabel(recurring))
        assertEquals("2026년 9월 10일", ClassLists.dateLabel(oneday))
        assertEquals("원데이 클래스", ClassLists.typeLabel(undated))
    }

    @Test fun reviewsAndRoadmapHelpers() {
        val longNotes = "가".repeat(120)
        assertEquals("가".repeat(115) + "…", CalendarGrid.reviewExcerpt(longNotes))
        assertEquals("한 줄 리뷰", CalendarGrid.reviewExcerpt("  한 줄\n리뷰 "))
        val reviewed = entry(LocalDate(2026, 9, 1), category = Category.CUPPING) { copy(notes = "좋았다") }
        val silent = entry(LocalDate(2026, 9, 2), category = Category.CUPPING)
        assertEquals(listOf(reviewed.id), CalendarGrid.cuppingReviews(listOf(silent, reviewed)).map { it.id })
        val phases = listOf(RoadmapPhase("a", 0, "A", "", 0, 30), RoadmapPhase("b", 1, "B", "", 30, 60))
        assertEquals("a", CalendarGrid.currentPhase(phases, 29)?.id)
        assertEquals("b", CalendarGrid.currentPhase(phases, 30)?.id)
        assertNull(CalendarGrid.currentPhase(phases, 60))
        assertNull(CalendarGrid.currentPhase(phases, null))
    }
}
