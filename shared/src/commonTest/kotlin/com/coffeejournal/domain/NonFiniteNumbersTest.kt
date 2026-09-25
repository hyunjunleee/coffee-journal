package com.coffeejournal.domain

import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.reference.ScaForm
import com.coffeejournal.domain.rules.Numbers
import com.coffeejournal.domain.rules.PantryRules
import com.coffeejournal.domain.rules.RecipeSteps
import com.coffeejournal.domain.rules.ScaScoring
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * home-1 and the crash class around it: Kotlin's toDoubleOrNull() accepts "NaN" / "Infinity" and turns "1e999" into
 * Infinity, and roundToInt() throws on NaN. Such text (typed, pasted or restored from a backup) must count as missing.
 */
class NonFiniteNumbersTest {
    private val nonFinite = listOf("NaN", " NaN ", "Infinity", "-Infinity", "1e999", "-1e999")

    @Test fun parserTreatsNonFiniteAsMissing() {
        nonFinite.forEach { assertNull(Numbers.parse(it), it) }
        assertEquals(0.0, Numbers.parse("-0")!!, 0.0, "\"-0\" is a valid zero")
        assertEquals(15.5, Numbers.parse(" 15.5 "))
        assertNull(Numbers.parse("abc"))
        assertNull(Numbers.finite(Double.NaN))
    }

    @Test fun pantryPriceTextsIgnoreNonFiniteWeights() {
        for (w in nonFinite + "-0" + "0") {
            assertEquals("", PantryRules.priceText(w, "18,000"), "weight '$w'")
            assertEquals("", PantryRules.unitPriceText(w, "18,000"), "weight '$w'")
        }
        // a price of 400 digits parses to Infinity
        assertEquals("", PantryRules.priceText("200", "9".repeat(400)))
        assertEquals("200g · 18,000원 · 100g 환산 9,000원", PantryRules.priceText("200", "18,000"))
        assertEquals("100g 환산가 · 9,000원", PantryRules.unitPriceText("200", "18,000"))
    }

    @Test fun remainingGramsSkipsNonFiniteDosesAndGrams() {
        val key = "예가체프"
        val brews = (nonFinite + "-0").mapIndexed { i, d -> Entry(id = "e$i", createdAt = i.toLong(), name = "예가체프", dose = d) } +
            Entry(id = "ok", createdAt = 99, name = "예가체프", dose = "15") +
            Entry(id = "blend", createdAt = 100, name = "커스텀", beanMode = "customBlend", blendComponents = listOf(BlendComponent("예가체프", "NaN"), BlendComponent("예가체프", "Infinity")))
        val blends = listOf(Blend(id = "b", beans = listOf(BlendComponent("예가체프", "-Infinity"), BlendComponent("예가체프", "5")), createdAt = 0))
        assertEquals(180.0 to 200.0, PantryRules.remainingGrams(key, 200.0, brews, blends))
        assertEquals(80.0 to 100.0, PantryRules.remainingGrams(key, Double.NaN, brews, blends))
        assertEquals(80.0 to 100.0, PantryRules.remainingGrams(key, Double.POSITIVE_INFINITY, brews, blends))
    }

    @Test fun stepsSummaryTreatsNonFiniteWaitAndWaterAsMissing() {
        for (bad in nonFinite) {
            val s = RecipeSteps.summary(listOf(RecipeStep("0:00", "50", "10", "뜸"), RecipeStep("0:40", bad, bad, "2차")))
            assertEquals(40, s.totalTimeSec, "wait '$bad'")
            assertEquals(50.0, s.totalWater, "water '$bad'")
            assertEquals(2, s.pourCount)
        }
        val negZero = RecipeSteps.summary(listOf(RecipeStep("0:30", "-0", "-0", "")))
        assertEquals(30, negZero.totalTimeSec)
        assertEquals("총 1차 추출 · 합계 물량 0g · 총 시간 0:30", RecipeSteps.summaryLine(negZero))
        // minutes beyond Int range used to throw NumberFormatException in toInt()
        assertNull(RecipeSteps.parseTimeToSec("99999999999:00"))
        assertNull(RecipeSteps.parseTimeToSec("999999999999999999999:00"))
        assertNull(RecipeSteps.summary(listOf(RecipeStep("0:10", "", "1e300", ""))).totalTimeSec)
    }

    @Test fun stepsWarningsAndDiffIgnoreNonFiniteTargets() {
        val s = RecipeSteps.summary(listOf(RecipeStep("0:00", "240", "150", "")))
        nonFinite.forEach { assertTrue(RecipeSteps.warnings(s, it, "NaN").isEmpty(), it) }
        val ref = RecipeRef("r", listOf(RecipeStep("0:00", "NaN", "Infinity", "뜸"), RecipeStep("0:30", "0", "10", "1차")))
        val cur = listOf(RecipeStep("0:00", "40", "30", "뜸"), RecipeStep("0:30", "-0", "1e999", "1차"))
        assertTrue(RecipeSteps.diff(cur, ref).isEmpty(), "NaN / Infinity / -0 never produce a diff line")
    }

    @Test fun scaTotalsIgnoreNonFiniteScores() {
        val fresh = ScaScoring.defaultAttributes()
        val bad = fresh + mapOf("flavor" to Double.NaN, "acidity" to Double.POSITIVE_INFINITY, "body" to 8.0)
        assertEquals(38.0, ScaScoring.total(bad))
        assertEquals(38.0, ScaScoring.effectiveTotal(bad))
        assertNull(ScaScoring.effectiveTotal(fresh + mapOf("flavor" to Double.NaN)))
        assertNull(ScaScoring.total(mapOf("flavor" to Double.NaN)))
        assertEquals("–", ScaScoring.format2(Double.NaN))
        assertEquals("–", ScaScoring.format1(Double.NEGATIVE_INFINITY))
        ScaForm.attrs.forEach { assertEquals("–", ScaScoring.readout(it, Double.NaN)) }
    }
}
