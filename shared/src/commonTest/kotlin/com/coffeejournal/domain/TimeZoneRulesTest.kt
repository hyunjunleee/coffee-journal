@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.coffeejournal.domain

import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.CalendarRanges
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import com.coffeejournal.domain.rules.PantryRules
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Gap #14: the app runs in Asia/Seoul (UTC+9) while the build runs in UTC. Records made between 00:00 and 09:00 local
 * time fall on the previous UTC day, so every rule is checked with the zone passed explicitly.
 */
class TimeZoneRulesTest {
    private val seoul = TimeZone.of("Asia/Seoul")

    private fun kst(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Instant = LocalDateTime(y, m, d, h, min).toInstant(seoul)

    private class FixedClock(val at: Instant) : Clock { override fun now(): Instant = at }

    @Test fun recordsAfterMidnightBeforeNineBelongToTheLocalDay() {
        val early = kst(2026, 9, 25, 0, 30).toEpochMilliseconds()
        assertEquals(LocalDate(2026, 9, 25), Dates.toLocalDate(early, seoul))
        assertEquals(LocalDate(2026, 9, 24), Dates.toLocalDate(early, TimeZone.UTC), "the same instant is still the 24th in UTC")
        assertEquals(kst(2026, 9, 25, 0).toEpochMilliseconds(), Dates.startOfDayMillis(LocalDate(2026, 9, 25), seoul))
        assertEquals(early, Dates.toMillis(LocalDate(2026, 9, 25), 0, 30, seoul))
        assertEquals(LocalDate(2026, 9, 25), Dates.today(seoul, FixedClock(kst(2026, 9, 25, 8, 59))))
    }

    @Test fun ddayCountsLocalDays() {
        val start = LocalDate(2026, 1, 1)
        // 00:30 on Sep 25 in Seoul is day 268; UTC would still say day 267
        val today = Dates.today(seoul, FixedClock(kst(2026, 9, 25, 0, 30)))
        assertEquals(268, DdayRules.dayCount(start, today))
        assertEquals("Coffee D-268", DdayRules.label(start, today))
        assertEquals(267, DdayRules.dayCount(start, Dates.today(TimeZone.UTC, FixedClock(kst(2026, 9, 25, 0, 30)))))
        // the start day itself is D-1 from its local midnight on
        assertEquals("Coffee D-1", DdayRules.label(LocalDate(2026, 9, 25), today))
    }

    @Test fun calendarRangesUseLocalDays() {
        val entries = listOf(
            Entry(id = "a", createdAt = kst(2026, 9, 20, 8).toEpochMilliseconds(), name = "게이샤"),
            Entry(id = "b", createdAt = kst(2026, 9, 30, 1).toEpochMilliseconds(), name = "게이샤"),
        )
        val ranges = CalendarRanges.compute(entries)
        assertEquals(1, ranges.size, "9 days 17 hours apart: one range")
        assertEquals(1, CalendarRanges.rangesOn(ranges, LocalDate(2026, 9, 20), seoul).size)
        assertEquals(1, CalendarRanges.rangesOn(ranges, LocalDate(2026, 9, 30), seoul).size, "a 01:00 record still marks the 30th")
        assertEquals(0, CalendarRanges.rangesOn(ranges, LocalDate(2026, 10, 1), seoul).size)
        assertEquals(0, CalendarRanges.rangesOn(ranges, LocalDate(2026, 9, 19), seoul).size)
        assertEquals(1, CalendarRanges.rangesOn(ranges, Dates.startOfDayMillis(LocalDate(2026, 9, 30), seoul), seoul).size)
        assertEquals(0, CalendarRanges.rangesOn(ranges, Dates.startOfDayMillis(LocalDate(2026, 10, 1), seoul), seoul).size)
        // read in UTC the 01:00 record lands on the 29th — the reason the zone matters
        assertEquals(0, CalendarRanges.rangesOn(ranges, LocalDate(2026, 9, 30), TimeZone.UTC).size)
    }

    @Test fun pantryPeakWindowOpensAtLocalMidnight() {
        val bag = PantryItem(id = "p", name = "케냐", roastLevel = "라이트", roastDate = "2026-09-11", createdAt = 0)
        val (from, to) = PantryRules.peakWindow(bag)!!
        assertEquals(LocalDate(2026, 9, 25) to LocalDate(2026, 10, 26), from to to)
        val justAfterMidnight = Dates.today(seoul, FixedClock(kst(2026, 9, 25, 0, 5)))
        assertTrue(justAfterMidnight in from..to, "the peak has started in Seoul")
        assertTrue(Dates.today(TimeZone.UTC, FixedClock(kst(2026, 9, 25, 0, 5))) < from)
        assertEquals("예상 피크 2026.9.25 ~ 2026.10.26 · 라이트 기준", PantryRules.drinkWindowText(bag))
    }

    @Test fun todayFlowReEmitsRightAfterLocalMidnight() = runTest {
        val start = kst(2026, 9, 24, 23, 59)
        val clock = object : Clock { override fun now(): Instant = start + testScheduler.currentTime.milliseconds }
        Dates.todayFlow(zone = { seoul }, clock = clock).take(2).toList().let { dates ->
            assertEquals(listOf(LocalDate(2026, 9, 24), LocalDate(2026, 9, 25)), dates)
        }
        val elapsed = testScheduler.currentTime
        assertTrue(elapsed in 60_000L..60_100L, "second date arrives just after midnight, not later ($elapsed ms)")
    }

    @Test fun todayFlowWaitsHoursWithoutRepeatingTheSameDate() = runTest {
        val start = kst(2026, 9, 24, 21, 0)
        val clock = object : Clock { override fun now(): Instant = start + testScheduler.currentTime.milliseconds }
        val dates = Dates.todayFlow(zone = { seoul }, clock = clock).take(3).toList()
        assertEquals(listOf(LocalDate(2026, 9, 24), LocalDate(2026, 9, 25), LocalDate(2026, 9, 26)), dates)
        val elapsed = testScheduler.currentTime.milliseconds
        assertTrue(elapsed >= 27.hours && elapsed < 27.hours + 1_000.milliseconds, "re-emits at each local midnight ($elapsed)")
    }
}
