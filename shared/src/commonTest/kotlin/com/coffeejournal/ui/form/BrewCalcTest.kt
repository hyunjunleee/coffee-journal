package com.coffeejournal.ui.form

import com.coffeejournal.domain.rules.BrewMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrewCalcTest {
    @Test fun openingTakesTheRecipeDoseAndWater() {
        assertEquals(CalcForm(dose = "15", ratio = "16", water = "240"), BrewCalc.opened(CalcForm(), "15", "240"))
        // typed values stay; free text in the recipe is not taken
        assertEquals(CalcForm(dose = "18", ratio = "", water = ""), BrewCalc.opened(CalcForm(dose = "18"), "15", "약 240"))
    }

    @Test fun editsSolveTheRatio() {
        val c = BrewCalc.edit(CalcForm(dose = "15"), BrewMath.Edited.RATIO, "15")
        assertEquals("225", c.water)
        assertEquals("16.7", BrewCalc.edit(c, BrewMath.Edited.WATER, "250").ratio)
    }

    @Test fun memoLineWithTheChartVerdict() {
        val calc = CalcForm(dose = "15", ratio = "16", water = "240", tds = "1.35", beverage = "200")
        assertEquals(18.0, BrewCalc.extractionYield(calc)!!, 1e-9)
        assertEquals(
            "[계산기] 원두 15g · 물 240g (1:16) · TDS 1.35% · 추출액 200g → 추출수율 18% (SCA 고전 추출 차트 18~22% 안)",
            BrewCalc.memoLine(calc),
        )
        assertEquals(
            "[계산기] 원두 15g · TDS 1.2% · 추출액 180g → 추출수율 14.4% (SCA 고전 추출 차트 18% 미만)",
            BrewCalc.memoLine(CalcForm(dose = "15", tds = "1.2", beverage = "180")),
        )
        assertEquals("[계산기] 원두 15g · 물 240g (1:16)", BrewCalc.memoLine(CalcForm(dose = "15", water = "240")))
        assertNull(BrewCalc.memoLine(CalcForm()))
    }

    @Test fun appendsOnANewLine() {
        assertEquals("줄", BrewCalc.appendTo("", "줄"))
        assertEquals("메모\n줄", BrewCalc.appendTo("메모\n", "줄"))
    }
}
