package com.coffeejournal.ui.extract

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.nav.FormMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The new-record chooser's "같은 커피 다시": the coffees recorded last, of any kind, each kind, place and bean once. */
class NewRecordLogicTest {
    private fun at(day: Int) = Dates.toMillis(LocalDate(2026, 9, day), 15, 0)

    private fun cafe(id: String, day: Int, cafe: String, bean: String) =
        Entry(id = id, createdAt = at(day), category = Category.CAFE, cafeName = cafe, name = bean)

    private fun brew(id: String, day: Int, bean: String) = Entry(id = id, createdAt = at(day), category = Category.BEAN, name = bean)

    @Test fun newestFirst_eachKindPlaceAndBeanOnce_fourAtMost() {
        val entries = listOf(
            cafe("a", 10, "FELT 청계천", "브라질 세하도"),
            cafe("b", 20, "felt 청계천 ", "브라질 세하도 (프릳츠)"), // the same café and bean, spelt otherwise
            cafe("c", 15, "FELT 청계천", "에티오피아 구지"),
            cafe("d", 18, "모모스", "브라질 세하도"),
            brew("home", 19, "브라질 세하도"), // the same bean brewed at home is another coffee to record again
            brew("home-old", 11, "브라질 세하도 (프릳츠)"),
            Entry(id = "cup", createdAt = at(12), category = Category.CUPPING, name = "퍼블릭 커핑", cuppingPlace = "커피플랜트"),
            cafe("noname", 27, "FELT 청계천", ""),
        )
        assertEquals(listOf("b", "home", "d", "c"), NewRecordLogic.recentCoffees(entries).map { it.id })
        assertEquals(listOf("b", "home", "d", "c", "cup"), NewRecordLogic.recentCoffees(entries, limit = 9).map { it.id })
    }

    @Test fun aCupping_isCalledByItsName_elseByItsBeans() {
        val named = Entry(id = "c1", createdAt = at(1), category = Category.CUPPING, name = "홈커핑 3종")
        val unnamed = Entry(
            id = "c2", createdAt = at(2), category = Category.CUPPING,
            cuppingBeans = listOf(CuppingBean(name = "케냐 키리냐가"), CuppingBean(name = ""), CuppingBean(name = "파나마 게이샤")),
        )
        assertEquals("홈커핑 3종", NewRecordLogic.title(named))
        assertEquals("케냐 키리냐가, 파나마 게이샤", NewRecordLogic.title(unnamed))
        assertEquals("브라질 세하도", NewRecordLogic.title(brew("x", 3, "브라질 세하도 (프릳츠)")))
    }

    @Test fun nothingRecorded_nothingToOffer() {
        assertEquals(emptyList(), NewRecordLogic.recentCoffees(listOf(brew("blank", 1, ""))))
    }

    @Test fun theLineUnderACoffee_isItsKindPlaceAndDay_andItOpensItsKindsForm() {
        assertEquals("카페 · FELT 청계천 · 2026.09.18", NewRecordLogic.againLine(cafe("a", 18, " FELT 청계천 ", "브라질")))
        assertEquals("직접 내림 · 2026.09.18", NewRecordLogic.againLine(brew("h", 18, "브라질")))
        val cup = Entry(id = "c", createdAt = at(6), category = Category.CUPPING, name = "퍼블릭", cuppingPlace = "커피플랜트 성수")
        assertEquals("커핑 · 커피플랜트 성수 · 2026.09.06", NewRecordLogic.againLine(cup))
        assertEquals(FormMode.CAFE, NewRecordLogic.modeFor(cafe("a", 1, "x", "y")))
        assertEquals(FormMode.EXTRACT, NewRecordLogic.modeFor(brew("h", 1, "y")))
        assertEquals(FormMode.CUPPING, NewRecordLogic.modeFor(cup))
    }
}
