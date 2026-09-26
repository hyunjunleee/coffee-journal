package com.coffeejournal.domain

import com.coffeejournal.domain.reference.CvaForm
import com.coffeejournal.domain.rules.CvaAssessment
import com.coffeejournal.domain.rules.CvaScoring
import com.coffeejournal.domain.rules.ScaScoring
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The CVA affective score against the SCA's own worked numbers: SCA-104 §4.2 (100 at all 9s, 79 at all 5s), the §7.1
 * two-way table (sum of the eight sections → score before deductions) and the calculator's deductions (2 per non-uniform
 * cup, 4 per defective cup).
 */
class CvaScoringTest {
    /** Eight affective ratings adding up to [sum] (fragrance … overall). */
    private fun withSum(sum: Int, u: Int = 0, d: Int = 0, defects: List<String> = emptyList()): CvaAssessment {
        require(sum in 8..72)
        val base = sum / 8
        val extra = sum % 8
        val ratings = CvaForm.sectionKeys.mapIndexed { i, k -> k to base + if (i < extra) 1 else 0 }.toMap()
        return CvaAssessment(affective = ratings, nonUniformCups = u, defectiveCups = d, defects = defects)
    }

    @Test fun sectionsAndScalesAreTheStandards() {
        assertEquals(listOf("fragrance", "aroma", "flavor", "aftertaste", "acidity", "sweetness", "mouthfeel", "overall"), CvaForm.sectionKeys)
        assertEquals(7, CvaForm.intensitySections.size, "every section but overall has a descriptive intensity")
        assertEquals(0 to 15, CvaForm.INTENSITY_MIN to CvaForm.INTENSITY_MAX)
        assertEquals(1 to 9, CvaForm.AFFECTIVE_MIN to CvaForm.AFFECTIVE_MAX)
        assertEquals(24, CvaForm.olfactoryFlat.size, "9 categories with 15 narrower boxes on the SCA-103 form")
        assertEquals(listOf("salty", "sour", "sweet", "bitter", "umami"), CvaForm.mainTastes.map { it.id })
        assertEquals(5, CvaForm.mouthfeel.size)
        assertEquals(listOf("moldy", "phenolic", "potato"), CvaForm.defects.map { it.id })
        assertEquals("극히 낮음", CvaForm.qualityLabel(1))
        assertEquals("높지도 낮지도 않음", CvaForm.qualityLabel(5))
        assertEquals("극히 높음", CvaForm.qualityLabel(9))
    }

    @Test fun definitionPoints_allNinesAre100_allFivesAre79() {
        assertEquals(100.0, CvaScoring.affectiveScore(withSum(72)))
        assertEquals(79.0, CvaScoring.affectiveScore(withSum(40)))
        assertEquals(58.0, CvaScoring.affectiveScore(withSum(8)))
    }

    @Test fun matchesTheTwoWayTableOfSca104() {
        // SCA-104 §7.1, column A → column B, including the rows that round a half up
        val table = mapOf(
            8 to 58.00, 9 to 58.75, 10 to 59.25, 11 to 60.00, 12 to 60.75, 13 to 61.25, 15 to 62.50, 18 to 64.50, 23 to 67.75,
            26 to 69.75, 31 to 73.00, 36 to 76.50, 39 to 78.25, 41 to 79.75, 44 to 81.75, 45 to 82.25, 47 to 83.50, 52 to 87.00,
            53 to 87.50, 55 to 88.75, 60 to 92.25, 63 to 94.00, 66 to 96.00, 69 to 98.00, 71 to 99.25, 72 to 100.00,
        )
        table.forEach { (sum, expected) -> assertEquals(expected, CvaScoring.affectiveScore(withSum(sum)), "Σ $sum") }
        // every row of the table follows from the formula
        for (sum in 8..72) {
            val s = CvaScoring.roundQuarter(0.65625 * sum + 52.75)
            assertEquals(s, CvaScoring.affectiveScore(withSum(sum)))
        }
    }

    @Test fun deductsTwoPerNonUniformAndFourPerDefectiveCup() {
        // Σ 52 → 87.00; one non-uniform, one defective (phenolic) cup: 87 − 2 − 4 = 81
        assertEquals(81.0, CvaScoring.affectiveScore(withSum(52, u = 1, d = 1, defects = listOf("phenolic"))))
        assertEquals(85.0, CvaScoring.affectiveScore(withSum(52, u = 1)))
        assertEquals(77.0, CvaScoring.affectiveScore(withSum(52, u = 5)))
        assertEquals(57.0, CvaScoring.affectiveScore(withSum(52, u = 5, d = 5, defects = listOf("moldy"))))
    }

    @Test fun defectiveCupsNeedADefectType() {
        // SCA-104 §5.4.1: without the defect type the coffee is not counted as defective
        assertEquals(87.0, CvaScoring.affectiveScore(withSum(52, d = 2)))
        assertEquals(79.0, CvaScoring.affectiveScore(withSum(52, d = 2, defects = listOf("potato"))))
    }

    @Test fun noScoreUntilAllEightSectionsAreRated() {
        val partial = withSum(40).let { it.copy(affective = it.affective - "overall") }
        assertEquals(7, CvaScoring.affectiveCount(partial))
        assertNull(CvaScoring.affectiveScore(partial))
        assertNull(CvaScoring.affectiveScore(CvaAssessment()))
        // out-of-scale ratings do not count as rated
        assertNull(CvaScoring.affectiveScore(withSum(40).let { it.copy(affective = it.affective + ("overall" to 10)) }))
    }

    @Test fun storesInCvaKeysAndReadsBack() {
        val a = CvaAssessment(
            intensity = mapOf("fragrance" to 8, "aroma" to 0, "mouthfeel" to 15),
            affective = withSum(55).affective,
            aromaDescriptors = listOf("floral", "berry", "citrusFruit"),
            flavorDescriptors = listOf("fruity", "cocoa"),
            mainTastes = listOf("sour", "sweet"),
            mouthfeel = listOf("smooth"),
            notes = mapOf("aroma" to "자스민", "overall" to "밝고 깨끗"),
            nonUniformCups = 1, defectiveCups = 1, defects = listOf("phenolic"),
        )
        val scores = CvaScoring.toScores(a)
        val texts = CvaScoring.toTexts(a)
        assertTrue(scores.keys.all { it.startsWith("cva.") } && texts.keys.all { it.startsWith("cva.") })
        assertEquals(0.0, scores["cva.intensity.aroma"], "a 0 intensity is a real rating")
        assertEquals("floral,berry,citrusFruit", texts["cva.cata.aroma"])
        assertEquals(a, CvaScoring.fromMaps(scores, texts))
        assertTrue(CvaScoring.present(scores, texts))
        assertEquals(82.75, CvaScoring.scoreOf(scores, texts))
        // the defect type is in the text map: without it the defective cup does not count
        assertEquals(86.75, CvaScoring.scoreOf(scores, emptyMap()))
    }

    @Test fun readingDropsValuesOutOfRangeAndUnknownIds() {
        val read = CvaScoring.fromMaps(
            mapOf("cva.intensity.fragrance" to 16.0, "cva.intensity.aroma" to 7.5, "cva.affective.flavor" to 0.0, "cva.affective.acidity" to 6.0, "cva.cups.nonUniform" to 9.0),
            mapOf("cva.cata.aroma" to "floral, nope,floral,berry,cocoa,spice,woody,burnt", "cva.cata.mainTastes" to "salty,sweet,bitter", "cva.defects" to "moldy,rust"),
        )
        assertEquals(emptyMap(), read.intensity)
        assertEquals(mapOf("acidity" to 6), read.affective)
        assertEquals(0, read.nonUniformCups)
        assertEquals(listOf("floral", "berry", "cocoa", "spice", "woody"), read.aromaDescriptors, "at most five, known ids, no repeats")
        assertEquals(listOf("salty", "sweet"), read.mainTastes, "at most two")
        assertEquals(listOf("moldy"), read.defects)
    }

    @Test fun scoreTextLabelsCvaApartFrom2004() {
        val sca = ScaScoring.defaultAttributes() + mapOf("flavor" to 8.0)
        assertEquals("38.00 / 100", CvaScoring.scoreText(sca, emptyMap()))
        assertEquals("CVA 87.00 / 100", CvaScoring.scoreText(CvaScoring.toScores(withSum(52)), emptyMap()))
        assertNull(CvaScoring.scoreText(CvaScoring.toScores(CvaAssessment(intensity = mapOf("flavor" to 9))), emptyMap()))
        // CVA keys never reach the 2004 total
        assertNull(ScaScoring.effectiveTotal(CvaScoring.toScores(withSum(72))))
        assertFalse(CvaScoring.present(sca, mapOf("flavor" to "달콤")))
    }
}
