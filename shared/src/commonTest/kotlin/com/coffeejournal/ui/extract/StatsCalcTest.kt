package com.coffeejournal.ui.extract

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.reference.CvaForm
import com.coffeejournal.domain.rules.CvaAssessment
import com.coffeejournal.domain.rules.CvaScoring
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.extract.stats.Ranked
import com.coffeejournal.ui.extract.stats.StatsCalc
import com.coffeejournal.ui.extract.stats.StatsPeriod
import com.coffeejournal.ui.extract.stats.StatsText
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsCalcTest {
    private val today = LocalDate(2026, 9, 26)
    private fun at(y: Int, m: Int, d: Int, h: Int = 9) = Dates.toMillis(LocalDate(y, m, d), h, 0)
    private fun brew(id: String, date: Long, name: String = "케냐 AA", dose: String = "15", water: String = "240", temp: String = "92", price: String = "", bag: String = "", score: Double? = null, cva: Boolean = false, country: String = "케냐", process: String = "워시드", variety: String = "SL28", roastery: String = "프릳츠") = Entry(
        id = id, createdAt = date, category = Category.BEAN, name = name, dose = dose, water = water, temp = temp, price = price, bagWeight = bag,
        country = country, process = process, variety = variety, roastery = roastery,
        attributes = when {
            score == null -> ScaScoring.defaultAttributes()
            cva -> CvaScoring.toScores(CvaAssessment(affective = CvaForm.sectionKeys.associateWith { score.toInt() }))
            else -> ScaScoring.defaultAttributes() + ("flavor" to score)
        },
    )

    private val journal = listOf(
        brew("b1", at(2026, 9, 3), price = "18000", bag = "200", score = 8.0),
        brew("b2", at(2026, 9, 3, 15), temp = "90", score = 8.5),
        brew("b3", at(2026, 8, 20), name = "에티오피아 구지", country = "에티오피아", process = "허니(화이트)", variety = "74158", roastery = "리브레", price = "20000", bag = "100"),
        brew("b4", at(2026, 6, 1), name = "콜롬비아", country = "Colombia", process = "기타", variety = "Geisha, Caturra", score = 7.0, cva = true),
        brew("b5", at(2025, 12, 31), name = "브라질", country = "브라질"),
        Entry(id = "c1", createdAt = at(2026, 9, 10), category = Category.CAFE, name = "게이샤", price = "8,500", country = "파나마"),
        Entry(
            id = "k1", createdAt = at(2026, 9, 12), category = Category.CUPPING, name = "커핑",
            cuppingBeans = listOf(CuppingBean(name = "케냐 키리냐가", country = "케냐", process = "워시드"), CuppingBean(name = "과테말라", country = "과테말라", process = "내추럴")),
        ),
    )

    @Test fun periodsStartWhereTheyShould() {
        assertEquals(LocalDate(2026, 9, 1), StatsCalc.range(StatsPeriod.MONTH, today, journal).first)
        assertEquals(LocalDate(2026, 7, 1), StatsCalc.range(StatsPeriod.QUARTER, today, journal).first)
        assertEquals(LocalDate(2026, 1, 1), StatsCalc.range(StatsPeriod.YEAR, today, journal).first)
        assertEquals(LocalDate(2025, 12, 1), StatsCalc.range(StatsPeriod.ALL, today, journal).first)
        assertNull(StatsCalc.range(StatsPeriod.ALL, today, emptyList()).first)
        // January: 3개월 reaches back into last year
        assertEquals(LocalDate(2025, 11, 1), StatsCalc.range(StatsPeriod.QUARTER, LocalDate(2026, 1, 15), journal).first)
    }

    @Test fun thisMonthCountsCupsPerDay() {
        val s = StatsCalc.compute(journal, emptyList(), StatsPeriod.MONTH, today)
        assertEquals(2, s.brewCount)
        assertEquals(1, s.cafeCount)
        assertEquals(1, s.cuppingCount)
        assertEquals(3, s.cups)
        assertTrue(s.daily)
        assertEquals(30, s.bars.size, "every day of September")
        assertEquals(2, s.bars[2].brew)
        assertEquals(1, s.bars[9].cafe)
        assertEquals(30.0, s.gramsUsed)
    }

    @Test fun monthlyBarsCoverThePeriod() {
        val s = StatsCalc.compute(journal, emptyList(), StatsPeriod.ALL, today)
        assertEquals(listOf("25.12", "26.1", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월"), s.bars.map { it.label })
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 1, 0, 1, 3), s.bars.map { it.total })
        val year = StatsCalc.compute(journal, emptyList(), StatsPeriod.YEAR, today)
        assertEquals(9, year.bars.size)
        assertEquals(4, year.brewCount, "2025's brew is left out")
    }

    @Test fun spendingUsesBagPriceOverWeightTimesDose() {
        // b1: 18000/200 × 15 = 1350; b2: no price → its bean's first priced record → 1350; b3: 20000/100 × 15 = 3000
        val s = StatsCalc.compute(journal, emptyList(), StatsPeriod.QUARTER, today)
        assertEquals(5700.0, s.spending.beans, 1e-9)
        assertEquals(3, s.spending.brewsPriced)
        assertEquals(0, s.spending.brewsUnpriced)
        assertEquals(8500.0, s.spending.cafe)
        assertEquals("원두 5,700원 (3잔)", StatsText.beanSpendLine(s))
        // without any price the brew is left out and counted as unpriced; the pantry bag fills in for the bean
        val all = StatsCalc.compute(journal, emptyList(), StatsPeriod.ALL, today)
        assertEquals(2, all.spending.brewsUnpriced)
        val withPantry = StatsCalc.compute(journal, listOf(PantryItem(id = "p", name = "브라질", weight = "250", price = "12500", createdAt = 0L)), StatsPeriod.ALL, today)
        assertEquals(1, withPantry.spending.brewsUnpriced)
        assertEquals(5700.0 + 750.0, withPantry.spending.beans, 1e-9)
    }

    @Test fun customBlendsCostTheirComponents() {
        val blend = Entry(
            id = "x", createdAt = at(2026, 9, 20), category = Category.BEAN, beanMode = BeanMode.CUSTOM_BLEND, name = "케냐 AA + 에티오피아 구지",
            blendComponents = listOf(BlendComponent("케냐 AA", "10"), BlendComponent("에티오피아 구지", "5")), dose = "15",
        )
        val s = StatsCalc.spending(listOf(blend), emptyList(), journal + blend, emptyList())
        assertEquals(10 * 90.0 + 5 * 200.0, s.beans, 1e-9)
        assertEquals(1, s.brewsPriced)
    }

    @Test fun brokenNumbersCountAsMissing() {
        val odd = listOf(brew("n1", at(2026, 9, 5), dose = "NaN", water = "1e999", temp = "Infinity", price = "abc", bag = "0", score = 8.0))
        val s = StatsCalc.compute(odd, emptyList(), StatsPeriod.MONTH, today)
        assertEquals(0.0, s.gramsUsed)
        assertEquals(1, s.spending.brewsUnpriced)
        assertTrue(s.ratioVsScore.isEmpty())
        assertTrue(s.tempVsScore.isEmpty())
        assertEquals(1, s.scores.size)
    }

    @Test fun rankingsAndScoresKeepCvaApart() {
        val s = StatsCalc.compute(journal, emptyList(), StatsPeriod.YEAR, today)
        assertEquals(Ranked("케냐", 3), s.origins.first(), "two brews and a cupping bean")
        assertTrue(Ranked("콜롬비아", 1) in s.origins, "English country names are read too")
        assertEquals(listOf("워시드", "허니", "기타", "내추럴"), s.processes.map { it.name })
        assertTrue(s.varieties.any { it.name.contains("Caturra") })
        assertEquals("프릳츠", s.roasteries.first().name)
        assertEquals(listOf(true, false, false), s.scores.map { it.cva }, "oldest first: the June CVA tasting, then two 2004 scores")
        val cvaPoint = s.scores.single { it.cva }
        assertEquals(CvaScoring.roundQuarter(56 * 0.65625 + 52.75), cvaPoint.score)
        assertEquals(2, s.tempVsScore.count { !it.cva })
        assertEquals(setOf(92.0, 90.0), s.tempVsScore.filter { !it.cva }.map { it.x }.toSet())
    }

    @Test fun emptyPeriod() {
        val s = StatsCalc.compute(emptyList(), emptyList(), StatsPeriod.MONTH, today)
        assertTrue(s.isEmpty)
        assertTrue(s.bars.all { it.total == 0 })
        assertTrue(StatsCalc.compute(emptyList(), emptyList(), StatsPeriod.ALL, today).bars.isEmpty())
    }

    @Test fun topKeepsFirstSeenOrderOnTies() {
        assertEquals(listOf(Ranked("b", 2), Ranked("a", 1), Ranked("c", 1)), StatsCalc.top(listOf("a", "b", "c", "b")))
        assertEquals(2, StatsCalc.top(listOf("a", "b", "c"), limit = 2).size)
    }

    @Test fun descriptionsSummariseTheCharts() {
        val s = StatsCalc.compute(journal, emptyList(), StatsPeriod.QUARTER, today)
        val cups = StatsText.cupsDescription(s)
        assertTrue(cups.startsWith("월별 잔 수 막대 차트. 모두 4잔, 집 추출 3잔, 카페 1잔. 7월 0잔, 8월 1잔, 9월 3잔. 가장 많이 마신 달은 9월 3잔."), cups)
        val month = StatsText.cupsDescription(StatsCalc.compute(journal, emptyList(), StatsPeriod.MONTH, today))
        assertTrue(month.contains("가장 많이 마신 날은 9월 3일 2잔"), month)
        val scores = StatsText.scoresDescription(s.scores)
        assertTrue(scores.contains("SCA 2004 점수 2개, 최저 38.00, 최고 38.50"), scores)
    }
}
