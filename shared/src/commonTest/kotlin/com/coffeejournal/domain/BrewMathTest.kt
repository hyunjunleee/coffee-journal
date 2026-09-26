package com.coffeejournal.domain

import com.coffeejournal.domain.rules.BrewMath
import com.coffeejournal.domain.rules.BrewMath.Edited
import com.coffeejournal.domain.rules.BrewMath.RatioFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrewMathTest {
    @Test fun ratioIsWaterOverDose() {
        assertEquals(16.0, BrewMath.ratio(15.0, 240.0))
        assertEquals("1:16", BrewMath.ratioText("15", "240"))
        assertEquals("1:16.7", BrewMath.ratioText("15", "250"))
        assertNull(BrewMath.ratioText("0", "240"))
        assertNull(BrewMath.ratioText("15", ""))
        assertNull(BrewMath.ratioText("NaN", "240"))
        assertNull(BrewMath.ratioText("1e999", "240"))
    }

    @Test fun extractionYieldIsTdsTimesBeverageOverDose() {
        // 1.35% × 200 g ÷ 15 g = 18%
        assertEquals(18.0, BrewMath.extractionYield(1.35, 200.0, 15.0)!!, 1e-9)
        // 1.30% × 280 g ÷ 18 g = 20.22%
        assertEquals(20.2222, BrewMath.extractionYield(1.30, 280.0, 18.0)!!, 1e-4)
        assertNull(BrewMath.extractionYield(0.0, 200.0, 15.0))
        assertNull(BrewMath.extractionYield(1.35, 200.0, 0.0))
        assertNull(BrewMath.extractionYield(null, 200.0, 15.0))
        assertNull(BrewMath.extractionYield(Double.NaN, 200.0, 15.0))
    }

    @Test fun classicChartBox() {
        assertEquals(BrewMath.Zone.BELOW, BrewMath.zone(17.9, 18.0, 22.0))
        assertEquals(BrewMath.Zone.INSIDE, BrewMath.zone(18.0, 18.0, 22.0))
        assertEquals(BrewMath.Zone.INSIDE, BrewMath.zone(22.0, 18.0, 22.0))
        assertEquals(BrewMath.Zone.ABOVE, BrewMath.zone(22.1, 18.0, 22.0))
        assertEquals("18% 미만 · 차트의 UNDER-DEVELOPED 쪽", BrewMath.eyVerdict(17.0))
        assertEquals("22% 초과 · 차트의 BITTER 쪽", BrewMath.eyVerdict(23.0))
        assertEquals("1.15~1.35% 안", BrewMath.tdsVerdict(1.2))
        assertEquals("1.15% 미만 · 차트의 WEAK 쪽", BrewMath.tdsVerdict(1.1))
        assertEquals("1.35% 초과 · 차트의 STRONG 쪽", BrewMath.tdsVerdict(1.4))
    }

    @Test fun solveFillsTheThirdField() {
        assertEquals(RatioFields("15", "16", "240"), BrewMath.solve(RatioFields("15", "16", ""), Edited.RATIO))
        assertEquals(RatioFields("20", "16", "320"), BrewMath.solve(RatioFields("20", "16", "240"), Edited.DOSE), "a new dose keeps the ratio")
        assertEquals(RatioFields("15", "16.7", "250"), BrewMath.solve(RatioFields("15", "16", "250"), Edited.WATER), "new water re-derives the ratio")
        assertEquals(RatioFields("15", "16", "240"), BrewMath.solve(RatioFields("", "16", "240"), Edited.WATER), "no dose: water ÷ ratio")
        assertEquals(RatioFields("15", "16", "240"), BrewMath.solve(RatioFields("", "16", "240"), Edited.RATIO))
        assertEquals(RatioFields("15", "16", "240"), BrewMath.solve(RatioFields("15", "", "240"), Edited.DOSE), "no ratio: water ÷ dose")
        assertEquals(RatioFields("15", "", ""), BrewMath.solve(RatioFields("15", "", ""), Edited.DOSE), "one field alone decides nothing")
        assertEquals(RatioFields("0", "16", ""), BrewMath.solve(RatioFields("0", "16", ""), Edited.DOSE), "zero is no value")
    }

    @Test fun formats() {
        assertEquals("16", BrewMath.fmt1(16.0))
        assertEquals("16.7", BrewMath.fmt1(16.666))
        assertEquals("1.35", BrewMath.fmt2(1.35))
        assertEquals("1.3", BrewMath.fmt2(1.30))
        assertEquals("20", BrewMath.fmt2(20.0))
        assertEquals("20.22", BrewMath.fmt2(20.2222))
        assertEquals("-2.5", BrewMath.fmt2(-2.5))
    }
}
