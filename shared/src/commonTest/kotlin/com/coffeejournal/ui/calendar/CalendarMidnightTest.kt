package com.coffeejournal.ui.calendar

import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.CalendarRanges
import com.coffeejournal.domain.rules.Dates
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gap #14: calendar squares match bean ranges by local calendar day. "Start of day + 24 h" is wrong on the two
 * daylight-saving days: the 25-hour autumn day lost a record made in its last hour, and the 23-hour spring day picked
 * up a record made just after the following midnight.
 */
class CalendarMidnightTest {
    private val berlin = TimeZone.of("Europe/Berlin")

    private fun brew(date: LocalDate, hour: Int, minute: Int) =
        Entry(id = "$date-$hour", createdAt = Dates.toMillis(date, hour, minute, berlin), name = "케냐 기통가 AA")

    private fun cell(date: LocalDate, entries: List<Entry>) = CalendarGrid.buildCell(
        date, entries.filter { Dates.toLocalDate(it.createdAt, berlin) == date }, emptyList(),
        CalendarRanges.compute(entries), ddayStart = null, today = LocalDate(2026, 1, 1), zone = berlin,
    )

    @Test fun autumnDstDay_recordInItsLastHourKeepsItsBand() {
        val autumn = LocalDate(2026, 10, 25) // 25 hours long in Berlin
        val late = listOf(brew(autumn, 23, 30))
        val c = cell(autumn, late)
        assertNotNull(c.range, "the 23:30 brew's band is on its own day")
        assertTrue(c.capLeft && c.capRight, "a one-day band is capped on both sides")
        assertNull(cell(LocalDate(2026, 10, 26), late).range)
    }

    @Test fun springDstDay_doesNotTakeTheNextDaysRecord() {
        val spring = LocalDate(2026, 3, 29) // 23 hours long in Berlin
        val next = LocalDate(2026, 3, 30)
        val justAfterMidnight = listOf(brew(next, 0, 30))
        assertNull(cell(spring, justAfterMidnight).range, "00:30 on the 30th is not on the 29th")
        assertNotNull(cell(next, justAfterMidnight).range)
    }

    @Test fun rangeSpanningTheDstChange_isContinuousAndCappedAtItsEnds() {
        val entries = listOf(brew(LocalDate(2026, 10, 24), 9, 0), brew(LocalDate(2026, 10, 27), 23, 45))
        val days = (24..27).map { cell(LocalDate(2026, 10, it), entries) }
        assertTrue(days.all { it.range != null })
        // 24 Oct is a Saturday: capped there (week edge); 25 Oct is a Sunday: capped left (week edge)
        assertEquals(listOf(true, true, false, false), days.map { it.capLeft })
        assertEquals(listOf(true, false, false, true), days.map { it.capRight })
    }

    @Test fun squareDescription_readsDateTodayAndCounts() {
        val date = LocalDate(2026, 9, 21)
        val entries = listOf(brew(date, 8, 0), brew(date, 15, 0))
        val c = CalendarGrid.buildCell(date, entries, emptyList(), emptyList(), ddayStart = LocalDate(2026, 9, 2), today = date, zone = berlin)
        assertEquals("9월 21일, 오늘, 20일, 기록 2개", com.coffeejournal.ui.calendar.components.dayCellDescription(c))
        val empty = CalendarGrid.buildCell(LocalDate(2026, 9, 22), emptyList(), emptyList(), emptyList(), null, today = date, zone = berlin)
        assertEquals("9월 22일", com.coffeejournal.ui.calendar.components.dayCellDescription(empty))
    }
}
