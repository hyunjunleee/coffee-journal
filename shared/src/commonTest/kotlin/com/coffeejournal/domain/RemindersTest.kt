package com.coffeejournal.domain

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.BeanStock
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ReminderKind
import com.coffeejournal.domain.rules.ReminderKinds
import com.coffeejournal.domain.rules.Reminders
import com.coffeejournal.ui.notify.ReminderInputs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Feature plan v2 §3: which reminders are due on a day, for the notification check. */
class RemindersTest {
    private val today = LocalDate(2026, 9, 26)
    private val zone = TimeZone.of("Asia/Seoul")
    private val all = ReminderKinds()

    private fun bag(id: String, name: String, block: PantryItem.() -> PantryItem = { this }) =
        PantryItem(id = id, name = name, createdAt = 1L).block()

    private fun brew(id: String, name: String, dose: String, daysAgo: Int = 1) = Entry(
        id = id, createdAt = Dates.startOfDayMillis(Dates.plusDays(today, -daysAgo), zone) + 9 * 3_600_000L,
        category = Category.BEAN, name = name, dose = dose,
    )

    private fun stock(remaining: Double, bag: Double = 200.0, key: String = "게이샤") =
        BeanStock(id = "pantry:p1", beanKey = key, name = "게이샤", remainingGrams = remaining, bagGrams = bag, remainingLine = "잔여량 ${remaining}g/${bag}g")

    // ── ① peak start ──

    @Test fun peak_dueOnTheDayTheExpectedWindowStarts() {
        // light roast: peak from roast date + 14 days (PantryRules)
        val light = bag("p1", "에티오피아 구지") { copy(roastDate = "2026-09-12", roastLevel = "라이트") }
        val reminders = Reminders.due(listOf(light), emptyList(), emptyList(), null, today, all)
        assertEquals(1, reminders.size)
        val r = reminders.single()
        assertEquals(ReminderKind.PEAK, r.kind)
        assertEquals("peak:p1:2026-09-26", r.key)
        assertEquals("피크 시작 · 에티오피아 구지", r.title)
        assertEquals("오늘부터 마시기 좋은 때예요. 예상 피크 2026.9.26 ~ 2026.10.27 · 라이트 기준", r.body)
        // the day before and the day after: nothing
        assertTrue(Reminders.due(listOf(light), emptyList(), emptyList(), null, Dates.plusDays(today, -1), all).isEmpty())
        assertTrue(Reminders.due(listOf(light), emptyList(), emptyList(), null, Dates.plusDays(today, 1), all).isEmpty())
    }

    @Test fun peak_manualWindowWins_andOpenedBagsCountToo() {
        val manual = bag("p2", "케냐 AA") { copy(roastDate = "2026-09-01", peakStart = "2026-09-26", peakEnd = "2026-10-20", status = PantryItem.STATUS_OPENED, openedAt = 5L) }
        val medium = bag("p3", "브라질") { copy(roastDate = "2026-09-19", roastLevel = "미디엄") } // +7 → today
        val dark = bag("p4", "만델링") { copy(roastDate = "2026-09-19", roastLevel = "다크") } // +4 → 09-23
        val noDate = bag("p5", "이름만")
        val due = Reminders.peakStarts(listOf(manual, medium, dark, noDate), today)
        assertEquals(listOf("peak:p2:2026-09-26", "peak:p3:2026-09-26"), due.map { it.key }.sorted())
        assertEquals("오늘부터 마시기 좋은 때예요. 예상 피크 2026.9.26 ~ 2026.10.20", due.first { it.key.startsWith("peak:p2") }.body)
    }

    // ── ② low stock ──

    @Test fun cupIsTheMedianDose_withA15gFallback() {
        val entries = listOf(brew("a", "게이샤", "14"), brew("b", "게이샤 (리브레)", "18"), brew("c", "게이샤", "15"), brew("d", "다른 원두", "30"))
        assertEquals(15.0, Reminders.cupGrams("게이샤", entries))
        assertEquals(16.0, Reminders.cupGrams("게이샤", entries.take(2)))
        // no dose, a blank or broken one, or a café record: the fallback cup
        val none = listOf(brew("x", "게이샤", ""), brew("y", "게이샤", "NaN"), brew("z", "게이샤", "20").copy(category = Category.CAFE))
        assertEquals(Reminders.FALLBACK_CUP_GRAMS, Reminders.cupGrams("게이샤", none))
    }

    @Test fun lowStock_boundaryIsTwoUsualCups() {
        val entries = listOf(brew("a", "게이샤", "14"), brew("b", "게이샤", "16"), brew("c", "게이샤", "15"))
        // two cups of 15 g = 30 g: exactly at the line is due, a hair above is not
        assertEquals(1, Reminders.lowStock(listOf(stock(30.0)), entries).size)
        assertTrue(Reminders.lowStock(listOf(stock(30.1)), entries).isEmpty())
        val r = Reminders.lowStock(listOf(stock(29.0)), entries).single()
        assertEquals("low:pantry:p1", r.key)
        assertEquals("원두 소진 임박 · 게이샤", r.title)
        assertEquals("잔여량 29.0g/200.0g · 평소 15g 기준 1잔 남았어요.", r.body)
        // no records with a dose: 15 g cups
        assertEquals(1, Reminders.lowStock(listOf(stock(30.0)), emptyList()).size)
        assertTrue(Reminders.lowStock(listOf(stock(31.0)), emptyList()).isEmpty())
        // a bigger usual dose moves the line
        val big = listOf(brew("d", "게이샤", "20"))
        assertEquals(1, Reminders.lowStock(listOf(stock(40.0)), big).size)
    }

    @Test fun lowStock_textForLessThanACupAndAnEmptyBag() {
        assertEquals("평소 15g 기준 한 잔이 안 되게 남았어요.", Reminders.cupsLeftText(9.0, 15.0))
        assertEquals("다 마셨어요. 다음 원두를 준비해 두세요.", Reminders.cupsLeftText(0.0, 15.0))
        assertEquals("평소 16.5g 기준 2잔 남았어요.", Reminders.cupsLeftText(33.0, 16.5))
    }

    // ── ③ D-day milestone ──

    @Test fun dday_milestoneDaysOnly() {
        // 2026-06-29 is D-1, so 2026-09-26 is D-90
        val start = LocalDate(2026, 6, 29)
        val r = Reminders.ddayMilestone(start, today)!!
        assertEquals(ReminderKind.DDAY, r.kind)
        assertEquals("dday:2026-06-29:90", r.key)
        assertEquals("Coffee D-90 · 90일 기념", r.title)
        assertEquals("커피 처음 마신 날부터 90일째 되는 날이에요.", r.body)
        assertNull(Reminders.ddayMilestone(start, Dates.plusDays(today, 1)))
        assertNull(Reminders.ddayMilestone(null, today))
        // D-100 is the big one (✦), like the home pill
        assertEquals("Coffee D-100 · 100일 기념 ✦", Reminders.ddayMilestone(start, Dates.plusDays(today, 10))!!.title)
        // a start date in the future is never a milestone
        assertNull(Reminders.ddayMilestone(Dates.plusDays(today, 30), today))
    }

    // ── settings and de-duplication ──

    @Test fun sentKeysAreNeverDueAgain() {
        val light = bag("p1", "에티오피아 구지") { copy(roastDate = "2026-09-12", roastLevel = "라이트") }
        val start = LocalDate(2026, 6, 29)
        val first = Reminders.due(listOf(light), emptyList(), listOf(stock(10.0)), start, today, all)
        assertEquals(setOf(ReminderKind.PEAK, ReminderKind.LOW_STOCK, ReminderKind.DDAY), first.map { it.kind }.toSet())
        val again = Reminders.due(listOf(light), emptyList(), listOf(stock(10.0)), start, today, all, sent = first.map { it.key }.toSet())
        assertTrue(again.isEmpty())
        // a new bag of the same bean is a new supply, so it is due again
        val newBag = stock(10.0).copy(id = "pantry:p9")
        assertEquals(listOf("low:pantry:p9"), Reminders.due(emptyList(), emptyList(), listOf(newBag), null, today, all, sent = first.map { it.key }.toSet()).map { it.key })
    }

    @Test fun switchedOffKindsAreLeftOut() {
        val light = bag("p1", "에티오피아 구지") { copy(roastDate = "2026-09-12", roastLevel = "라이트") }
        val start = LocalDate(2026, 6, 29)
        fun kinds(k: ReminderKinds) = Reminders.due(listOf(light), emptyList(), listOf(stock(10.0)), start, today, k).map { it.kind }.toSet()
        assertEquals(setOf(ReminderKind.LOW_STOCK, ReminderKind.DDAY), kinds(ReminderKinds(peak = false)))
        assertEquals(setOf(ReminderKind.PEAK, ReminderKind.DDAY), kinds(ReminderKinds(lowStock = false)))
        assertEquals(setOf(ReminderKind.PEAK, ReminderKind.LOW_STOCK), kinds(ReminderKinds(dday = false)))
        assertTrue(kinds(ReminderKinds(peak = false, lowStock = false, dday = false)).isEmpty())
        assertTrue(ReminderKinds(peak = false).allows(ReminderKind.DDAY))
    }

    // ── the home card's numbers feed the low-stock rule ──

    @Test fun inUse_isWhatTheHomeCardShows() {
        val opened = bag("p1", "게이샤 (리브레)") { copy(weight = "200", status = PantryItem.STATUS_OPENED, openedAt = 1L) }
        val entries = (1..11).map { brew("e$it", "게이샤", "16", daysAgo = it) } // 176 g brewed
        val stocks = ReminderInputs.inUse(entries, listOf(opened), emptyList(), today)
        val s = stocks.single()
        assertEquals("pantry:p1", s.id)
        assertEquals("게이샤", s.beanKey)
        assertEquals(24.0, s.remainingGrams)
        assertEquals(200.0, s.bagGrams)
        assertEquals("잔여량 24g/200g", s.remainingLine)
        val low = Reminders.due(emptyList(), entries, stocks, null, today, all).single()
        assertEquals("원두 소진 임박 · 게이샤 (리브레)", low.title)
        assertEquals("잔여량 24g/200g · 평소 16g 기준 1잔 남았어요.", low.body)

        // no opened bag: the most recent standard bean with the 100 g default bag, like the card
        val recent = listOf(brew("r1", "콜롬비아", "15", daysAgo = 3), brew("r2", "콜롬비아", "15", daysAgo = 2))
        val fallback = ReminderInputs.inUse(recent, emptyList(), emptyList(), today).single()
        assertEquals("bean:콜롬비아", fallback.id)
        assertEquals(70.0, fallback.remainingGrams)
        assertEquals("잔여량 70g/100g", fallback.remainingLine)
        // drip bags and samples are not "in use"
        val dripOnly = bag("d1", "드립백") { copy(packageType = PackageType.DRIPBAG, status = PantryItem.STATUS_OPENED, openedAt = 1L) }
        assertTrue(ReminderInputs.inUse(emptyList(), listOf(dripOnly), emptyList(), today).isEmpty())
    }
}
