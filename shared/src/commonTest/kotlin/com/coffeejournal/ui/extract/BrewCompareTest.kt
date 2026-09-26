package com.coffeejournal.ui.extract

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.reference.CvaForm
import com.coffeejournal.domain.rules.CvaAssessment
import com.coffeejournal.domain.rules.CvaScoring
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.extract.compare.BrewCompare
import com.coffeejournal.ui.extract.compare.CompareCell
import com.coffeejournal.ui.extract.compare.CompareReference
import com.coffeejournal.ui.extract.compare.CompareTable
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrewCompareTest {
    private fun at(d: Int) = Dates.toMillis(LocalDate(2026, 9, d), 8, 30)
    private fun brew(id: String, d: Int, dose: String = "15", water: String = "240", temp: String = "92", grind: String = "24클릭", time: String = "2:30", flavor: Double? = null) = Entry(
        id = id, createdAt = at(d), category = Category.BEAN, name = "에티오피아 예가체프 (리브레)", dose = dose, water = water, temp = temp,
        grind = grind, time = time, dripper = "V60", actualNotes = "자스민",
        attributes = ScaScoring.defaultAttributes() + (flavor?.let { mapOf("flavor" to it) } ?: emptyMap()),
    )

    private val key = "에티오피아 예가체프"
    private val records = listOf(
        brew("a", 21, flavor = 8.0),
        brew("b", 23, temp = "91", flavor = 8.5, time = "2:40"),
        brew("c", 25, dose = "16", water = "250", grind = "22클릭"),
        Entry(id = "cafe", createdAt = at(24), category = Category.CAFE, name = "에티오피아 예가체프"),
        Entry(id = "other", createdAt = at(24), category = Category.BEAN, name = "케냐"),
    )

    private fun CompareTable.row(label: String) = rows.single { it.label == label }.cells

    @Test fun bestRecipeLeadsAndDifferencesAreMarked() {
        val t = BrewCompare.build(records, key, bestId = "a")
        assertEquals(CompareReference.BEST, t.reference)
        assertEquals(listOf("a", "c", "b"), t.columns.map { it.entryId }, "reference first, then newest first; cafés and other beans left out")
        assertEquals(listOf(true, false, false), t.columns.map { it.isReference })
        assertEquals("에티오피아 예가체프", t.beanName)
        assertEquals(listOf("날짜", "원두량", "물량", "비율", "온도", "분쇄도", "총시간", "드리퍼", "점수", "노트"), t.rows.map { it.label })

        assertEquals(listOf(CompareCell("15g"), CompareCell("16g", true, "+1g"), CompareCell("15g")), t.row("원두량"))
        assertEquals(CompareCell("1:15.6", true, "−0.4"), t.row("비율")[1])
        assertEquals(CompareCell("91°C", true, "−1°C"), t.row("온도")[2])
        assertEquals(CompareCell("22클릭", differs = true), t.row("분쇄도")[1])
        assertEquals(CompareCell("2:40", true, "+10s"), t.row("총시간")[2])
        assertEquals(CompareCell("38.50", true, "+0.5"), t.row("점수")[2])
        assertEquals(CompareCell("-", differs = true), t.row("점수")[1], "no score against a scored reference")
        assertFalse(t.row("노트").any { it.differs }, "notes are shown, not compared")
        assertFalse(t.row("날짜").any { it.differs })
    }

    @Test fun withoutABestTheTopScoreIsTheReference() {
        val t = BrewCompare.build(records, key, bestId = null)
        assertEquals(CompareReference.TOP_SCORE, t.reference)
        assertEquals("b", t.columns.first().entryId)
        // a best id that is not one of this bean's brews does not count
        assertEquals(CompareReference.TOP_SCORE, BrewCompare.build(records, key, bestId = "other").reference)
    }

    @Test fun cvaScoresAreComparedOnlyWithCva() {
        val cva = CvaScoring.toScores(CvaAssessment(affective = CvaForm.sectionKeys.associateWith { 7 }))
        val list = listOf(
            brew("x", 20).copy(attributes = cva),
            brew("y", 21).copy(attributes = CvaScoring.toScores(CvaAssessment(affective = CvaForm.sectionKeys.associateWith { 6 }))),
            brew("z", 22, flavor = 8.0),
        )
        val t = BrewCompare.build(list, key, bestId = "x")
        assertEquals("CVA 89.50", t.row("점수")[0].text)
        assertEquals(CompareCell("CVA 84.25", true, "−5.25"), t.row("점수")[2])
        assertEquals(CompareCell("38.00", differs = true), t.row("점수")[1], "a 2004 total is never set against a CVA score")
        // with no 2004 score anywhere, the top CVA score is the reference
        assertEquals(CompareReference.TOP_CVA, BrewCompare.build(list.take(2), key, null).reference)
    }

    @Test fun noScoreNoBest_noReference() {
        val t = BrewCompare.build(listOf(brew("p", 1), brew("q", 2, temp = "90")), key, null)
        assertNull(t.reference)
        assertTrue(t.rows.all { r -> r.cells.none { it.differs } })
    }

    @Test fun customBlendsJoinEachComponentsTable() {
        val blend = Entry(
            id = "bl", createdAt = at(26), category = Category.BEAN, beanMode = BeanMode.CUSTOM_BLEND, name = "블렌드",
            blendComponents = listOf(BlendComponent("에티오피아 예가체프", "10"), BlendComponent("케냐", "5")), dose = "15",
        )
        assertEquals(listOf("bl", "c", "b", "a"), BrewCompare.records(records + blend, key).map { it.id })
    }

    @Test fun numberCells() {
        assertEquals(CompareCell("-"), BrewCompare.numberCell(null, null, null, "g"))
        assertEquals(CompareCell("15g", differs = true), BrewCompare.numberCell("15g", 15.0, null, "g"))
        assertEquals(CompareCell("15g"), BrewCompare.numberCell("15g", 15.0, 15.0, "g"))
    }
}
