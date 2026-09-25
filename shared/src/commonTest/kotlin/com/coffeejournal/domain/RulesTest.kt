package com.coffeejournal.domain

import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.CalendarRanges
import com.coffeejournal.domain.rules.CountryLookup
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import com.coffeejournal.domain.rules.NoteCanon
import com.coffeejournal.domain.rules.PantryRules
import com.coffeejournal.domain.rules.RecipeSteps
import com.coffeejournal.domain.rules.RegionHierarchy
import com.coffeejournal.domain.rules.RoastFamily
import com.coffeejournal.domain.rules.ScaScoring
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {
    @Test fun coreBeanNameStripsTrailingParens() {
        assertEquals("에티오피아 예가체프", BeanNames.coreBeanName("에티오피아 예가체프 (커피리브레, 노르딕)"))
        assertEquals("kenya aa", BeanNames.coreBeanName("  Kenya AA "))
        val p = BeanNames.parseNameParens("벤사 (리브레, 노르딕, 농장, 생산자)")!!
        assertEquals("리브레", p.roastery); assertEquals("노르딕", p.source); assertEquals("농장", p.farm); assertEquals("생산자", p.producer)
    }

    @Test fun farmProducerAndVarietySplitting() {
        assertEquals("허니", BeanNames.parseFarmProducer("허니(더블 퍼멘티드)").farm)
        assertEquals("더블 퍼멘티드", BeanNames.parseFarmProducer("허니(더블 퍼멘티드)").producer)
        assertEquals(listOf("Heirloom(74110, 74112)", "Mundo Novo"), BeanNames.splitVarietyValues("Heirloom(74110, 74112), Mundo Novo"))
        assertEquals("라 에스메랄다(페드로)", BeanNames.formatFarmProducer("라 에스메랄다", "페드로"))
    }

    @Test fun calendarRangesSplitOnTenDayGap() {
        val d0 = Dates.startOfDayMillis(LocalDate(2026, 7, 1))
        val e = { days: Int, name: String -> Entry(id = "$name$days", createdAt = d0 + days * Dates.DAY_MS, name = name) }
        val ranges = CalendarRanges.compute(listOf(e(0, "A"), e(3, "A"), e(20, "A"), e(1, "B (x)"), e(2, "b")))
        assertEquals(3, ranges.size)
        val a = ranges.filter { it.name == "A" }
        assertEquals(2, a.size)
        assertEquals(2, a[0].count)
        assertEquals(1, a[1].count)
        assertEquals(2, ranges.first { it.name == "B (x)" }.count)
    }

    @Test fun ddayCountsStartDayAsOne() {
        assertEquals(1, DdayRules.dayCount(LocalDate(2026, 1, 1), LocalDate(2026, 1, 1)))
        assertEquals(268, DdayRules.dayCount(LocalDate(2026, 1, 1), LocalDate(2026, 9, 25)))
        assertEquals("Coffee D-268", DdayRules.label(LocalDate(2026, 1, 1), LocalDate(2026, 9, 25)))
        assertTrue(DdayRules.milestone(100)!!.big)
        assertEquals("30일 기념", DdayRules.milestone(30)!!.text)
        assertNull(DdayRules.milestone(31))
    }

    @Test fun pantryPeakWindows() {
        val base = PantryItem(id = "p", name = "x", createdAt = 0, roastDate = "2026-09-01")
        assertEquals("예상 피크 2026.9.15 ~ 2026.10.16 · 라이트 기준", PantryRules.drinkWindowText(base.copy(roastLevel = "라이트")))
        assertEquals("예상 피크 2026.9.8 ~ 2026.10.1 · 미디엄 기준", PantryRules.drinkWindowText(base.copy(roastLevel = "미디엄")))
        assertEquals("예상 피크 2026.9.5 ~ 2026.9.22 · 다크 기준", PantryRules.drinkWindowText(base.copy(roastLevel = "미디엄 다크")))
        assertEquals(PantryRules.RoastGroup.LIGHT, PantryRules.roastGroup("미디엄 라이트"))
        assertEquals("예상 피크 2026.9.10 ~ 2026.9.20", PantryRules.drinkWindowText(base.copy(peakStart = "2026-09-10", peakEnd = "2026-09-20")))
        assertEquals("200g · 18,000원 · 100g 환산 9,000원", PantryRules.priceText("200", "18,000"))
    }

    @Test fun scaTotals() {
        val fresh = ScaScoring.defaultAttributes()
        assertEquals(30.0, ScaScoring.total(fresh))
        assertNull(ScaScoring.effectiveTotal(fresh))
        val scored = fresh + mapOf("flavor" to 8.25, "fragranceIntensity" to 3.5)
        assertEquals(38.25, ScaScoring.effectiveTotal(scored))
        assertEquals("38.25", ScaScoring.format2(38.25))
    }

    @Test fun roastFamilyAndNotes() {
        assertEquals(RoastFamily.DARK, RoastFamily.of("미디엄 다크"))
        assertEquals(RoastFamily.LIGHT, RoastFamily.of("라이트 미디엄"))
        assertEquals("자스민", NoteCanon.canonical("재스민."))
        assertEquals("브라운슈거", NoteCanon.canonical("Brown Sugar"))
        assertEquals(listOf("자스민", "오렌지"), NoteCanon.split("재스민, orange"))
        assertEquals(listOf("오렌지", "자몽"), NoteCanon.addChips(listOf("오렌지"), "자몽, 오렌지"))
    }

    @Test fun countryAndRegionLookup() {
        assertEquals("Colombia", CountryLookup.lookup("columbia")!!.en)
        assertEquals("Ethiopia", CountryLookup.lookup("에티오피아 예가체프")!!.en)
        assertEquals("케냐(Kenya)", CountryLookup.bilingual("Kenya"))
        val r = RegionHierarchy.parse("예가체프, Gedeb, Worka")
        assertEquals("Yirgacheffe", r.primary); assertEquals("Gedeb, Worka", r.sub)
    }

    @Test fun recipeStepsSummaryAndDiff() {
        val steps = listOf(RecipeStep("0:00", "50", "10", "1차"), RecipeStep("0:10", "", "40", "대기"), RecipeStep("0:50", "190", "30", "2차"), RecipeStep("1:20", "", "70", "드로우다운"))
        val s = RecipeSteps.summary(steps)
        assertEquals(240.0, s.totalWater); assertEquals(150, s.totalTimeSec); assertEquals(2, s.pourCount)
        assertEquals("총 2차 추출 · 합계 물량 240g · 총 시간 2:30", RecipeSteps.summaryLine(s))
        assertEquals(1, RecipeSteps.warnings(s, "250", "2:35").size)
        val ref = RecipeRef("유어홈", steps)
        assertTrue(RecipeSteps.diff(steps, ref).isEmpty())
        val changed = steps.toMutableList().also { it[2] = it[2].copy(water = "200") }
        assertEquals(listOf("2차: 물량 190g→200g (+10 g)"), RecipeSteps.diff(changed, ref))
    }
}
