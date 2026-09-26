package com.coffeejournal.ui.notify

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import com.coffeejournal.ui.extract.DrinkingState
import com.coffeejournal.ui.extract.ExtractGrouping
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The home-screen widget says what the home tab says: the D-day pill and the 마시는 중 card. */
class WidgetSnapshotTest {
    private val today = LocalDate(2026, 9, 26)
    private fun brew(id: String, name: String, dose: String, daysAgo: Int) = Entry(
        id = id, createdAt = Dates.startOfDayMillis(Dates.plusDays(today, -daysAgo)) + 9 * 3_600_000L,
        category = Category.BEAN, name = name, dose = dose, bagWeight = "200",
    )

    @Test fun emptyJournal_showsBothHints() {
        val s = WidgetSnapshots.build(emptyList(), emptyList(), emptyList(), null, today)
        assertNull(s.ddayLabel)
        assertEquals("커피 처음 마신 날을 기록해두면 며칠째인지 보여드려요.", s.ddayHint)
        assertNull(s.beanName)
        assertEquals("아직 마시는 중인 원두가 없어요. 오늘 내린 커피부터 남겨보세요.", s.emptyText)
    }

    @Test fun ddayMatchesThePill() {
        val start = LocalDate(2026, 6, 19)
        val s = WidgetSnapshots.build(emptyList(), emptyList(), emptyList(), start, today)
        assertEquals(DdayRules.label(start, today), s.ddayLabel)
        assertEquals("Coffee D-100", s.ddayLabel)
        assertEquals("2026.06.19 첫 추출", s.ddaySince)
        assertEquals("100일 기념 ✦", s.milestone)
        assertNull(s.ddayHint)
        assertNull(WidgetSnapshots.build(emptyList(), emptyList(), emptyList(), start, Dates.plusDays(today, 1)).milestone)
    }

    @Test fun recentBean_matchesTheCard() {
        val entries = listOf(brew("a", "에티오피아 구지 (리브레)", "15", 3), brew("b", "에티오피아 구지", "18", 1))
        val card = assertIs<DrinkingState.RecentBean>(ExtractGrouping.drinking(entries, emptyList(), emptyList(), emptyList(), today)).card
        val s = WidgetSnapshots.build(entries, emptyList(), emptyList(), null, today)
        assertEquals("에티오피아 구지", s.beanName)
        assertEquals(card.remainingLine, s.remainingLine)
        assertEquals("잔여량 167g/200g", s.remainingLine)
        assertEquals(card.eyebrow, s.beanEyebrow)
        assertNull(s.emptyText)
    }

    @Test fun openedBags_firstCardAndHowManyMore() {
        val first = PantryItem(id = "p1", name = "케냐 AA", weight = "250", roastDate = "2026-09-01", status = PantryItem.STATUS_OPENED, openedAt = 10L, createdAt = 1L)
        val second = PantryItem(id = "p2", name = "과테말라", weight = "200", roastDate = "2026-09-20", status = PantryItem.STATUS_OPENED, openedAt = 20L, createdAt = 2L)
        val entries = listOf(brew("a", "케냐 AA", "20", 2))
        val s = WidgetSnapshots.build(entries, listOf(second, first), emptyList(), null, today)
        // the card order: earliest expected peak first
        assertEquals("케냐 AA", s.beanName)
        assertEquals("잔여량 230g/250g", s.remainingLine)
        assertEquals("마시는 중 · ${Dates.ymdCompact(10L)}.~", s.beanEyebrow)
        assertEquals(1, s.moreBeans)
    }
}
