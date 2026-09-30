package com.coffeejournal.ui.extract

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.Dates
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The new-record chooser's "같은 커피 다시": the café coffees drunk last, each café and bean once. */
class NewRecordLogicTest {
    private fun at(day: Int) = Dates.toMillis(LocalDate(2026, 9, day), 15, 0)

    private fun cafe(id: String, day: Int, cafe: String, bean: String) =
        Entry(id = id, createdAt = at(day), category = Category.CAFE, cafeName = cafe, name = bean)

    @Test fun newestFirst_eachCafeAndBeanOnce_threeAtMost() {
        val entries = listOf(
            cafe("a", 10, "FELT 청계천", "브라질 세하도"),
            cafe("b", 20, "felt 청계천 ", "브라질 세하도 (프릳츠)"), // the same café and bean, spelt otherwise
            cafe("c", 15, "FELT 청계천", "에티오피아 구지"),
            cafe("d", 18, "모모스", "브라질 세하도"),
            cafe("e", 12, "프릳츠 원서", "콜롬비아 우일라"),
            Entry(id = "brew", createdAt = at(25), category = Category.BEAN, name = "케냐 키리냐가"),
            Entry(id = "cup", createdAt = at(26), category = Category.CUPPING, name = "퍼블릭 커핑"),
            cafe("noname", 27, "FELT 청계천", ""),
        )
        assertEquals(listOf("b", "d", "c"), NewRecordLogic.recentCafeCoffees(entries).map { it.id })
        assertEquals(listOf("b", "d", "c", "e"), NewRecordLogic.recentCafeCoffees(entries, limit = 5).map { it.id })
    }

    @Test fun noCafeRecord_nothingToOffer() {
        assertEquals(emptyList(), NewRecordLogic.recentCafeCoffees(listOf(Entry(id = "brew", createdAt = at(1), category = Category.BEAN, name = "케냐"))))
    }

    @Test fun theLineUnderACoffee_isItsCafeAndDay() {
        assertEquals("FELT 청계천 · 2026.09.18", NewRecordLogic.againLine(cafe("a", 18, " FELT 청계천 ", "브라질")))
        assertEquals("2026.09.18", NewRecordLogic.againLine(cafe("a", 18, "", "브라질")))
    }
}
