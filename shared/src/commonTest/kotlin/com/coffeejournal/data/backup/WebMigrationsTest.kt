package com.coffeejournal.data.backup

import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.rules.ScaScoring
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Gap #1: a web backup exported before the web's load-time migrations (script3.js 335-512) ran. */
class WebMigrationsTest {
    private val codec = BackupCodec()

    private val preMigrationBackup = """
    {
      "exportedAt": "2025-03-02T10:00:00.000Z",
      "data": {
        "entries": [
          {
            "id": "old-scale", "createdAt": 1740000000000, "category": "원두", "name": "케냐 옛 점수",
            "attributes": {
              "fragranceAroma": 4, "flavor": 3.5, "aftertaste": 5, "acidity": 4.25, "body": 3, "balance": 4,
              "uniformity": 4, "cleancup": 10, "sweetness": 2, "overall": 4.5,
              "fragranceIntensity": 9, "acidityIntensity": 15, "bodyIntensity": 3,
              "aroma": 3
            }
          },
          {
            "id": "fifteen-scale", "createdAt": 1740000000001, "category": "원두", "name": "강도 0-15",
            "attributes": {"flavor": 8, "fragranceIntensity": 7, "acidityIntensity": 6, "bodyIntensity": 14, "aroma": 7, "aromaIntensity": 4}
          },
          {
            "id": "yourhome-named", "createdAt": 1740000000002, "category": "원두", "name": "에티오피아 유어홈",
            "dose": "18", "water": "300", "time": "2:30",
            "steps": [
              {"time": "0:00", "water": "50", "wait": "10", "note": "1차 푸어"},
              {"time": "0:10", "water": "", "wait": "40", "note": "스월, 대기"},
              {"time": "0:50", "water": "140", "wait": "30", "note": "2차 푸어 (누적 190g)"},
              {"time": "1:20", "water": "", "wait": "70", "note": "드로우다운"}
            ],
            "recipeRef": {"name": "유어홈 (Your Home)", "steps": [
              {"time": "0:00", "water": "50", "wait": "10", "note": "1차 푸어"},
              {"time": "0:10", "water": "", "wait": "40", "note": "스월, 대기"},
              {"time": "0:50", "water": "140", "wait": "30", "note": "2차 푸어 (누적 190g)"},
              {"time": "1:20", "water": "", "wait": "70", "note": "드로우다운"}
            ]}
          },
          {
            "id": "yourhome-unnamed", "createdAt": 1740000000003, "category": "원두", "name": "콜롬비아",
            "dose": "18", "water": "300", "recipeRef": null,
            "steps": [
              {"time": "0:00", "water": "50", "wait": "10", "note": ""},
              {"time": "0:10", "water": "", "wait": "40", "note": ""},
              {"time": "0:50", "water": "140", "wait": "30", "note": "2차 푸어"},
              {"time": "1:20", "water": "", "wait": "70", "note": ""}
            ]
          }
        ]
      }
    }
    """.trimIndent()

    private fun decoded() = codec.decode(preMigrationBackup).entries.associateBy { it.id }

    @Test fun qualityScoresOnTheOldFivePointScaleMoveToSixToTen() {
        val a = decoded().getValue("old-scale").attributes
        assertEquals(9.0, a["fragranceAroma"])
        assertEquals(8.5, a["flavor"])
        assertEquals(10.0, a["aftertaste"])
        assertEquals(9.25, a["acidity"])
        assertEquals(9.5, a["overall"])
        // 0-10 rows in steps of 2: 4 and 2 are real scores on the current form, never rescaled
        assertEquals(4.0, a["uniformity"])
        assertEquals(2.0, a["sweetness"])
        assertEquals(10.0, a["cleancup"])
        assertEquals("9.00", ScaScoring.readout(com.coffeejournal.domain.reference.ScaForm.attrs.first(), a["fragranceAroma"]))
    }

    @Test fun intensitiesOnTheFifteenPointScaleMapToHalfSteps_currentOnesStay() {
        val old = decoded().getValue("old-scale").attributes
        assertEquals(3.0, old["fragranceIntensity"], "9 / 15 → 3")
        assertEquals(5.0, old["acidityIntensity"], "15 / 15 → 5")
        assertEquals(3.0, old["bodyIntensity"], "3 is already on the 1-5 scale")
        val fifteen = decoded().getValue("fifteen-scale").attributes
        assertEquals(2.5, fifteen["fragranceIntensity"], "round(7 / 3 * 2) / 2 = 2.5")
        assertEquals(2.0, fifteen["acidityIntensity"])
        assertEquals(4.5, fifteen["bodyIntensity"], "round(9.33) / 2 = 4.5")
        assertEquals(8.0, fifteen["flavor"])
    }

    @Test fun legacyAromaMovesToAromaIntensityUnlessThatIsAlreadySet() {
        val old = decoded().getValue("old-scale").attributes
        assertNull(old["aroma"])
        assertEquals(8.0, old["aromaIntensity"], "rescaled 3 → 8 first, as the web did")
        val fifteen = decoded().getValue("fifteen-scale").attributes
        assertEquals(7.0, fifteen["aroma"], "kept: aromaIntensity was already scored")
        assertEquals(4.0, fifteen["aromaIntensity"])
    }

    @Test fun yourHomeRecordsFromTheWrongRecipeAreCorrected() {
        val named = decoded().getValue("yourhome-named")
        assertEquals("15", named.dose)
        assertEquals("240", named.water)
        assertEquals(RecipeStep("0:50", "190", "30", "2차 푸어"), named.steps[2])
        assertEquals(RecipeStep("0:50", "190", "30", "2차 푸어"), named.recipeRef!!.steps[2])
        assertEquals(named.steps.filterIndexed { i, _ -> i != 2 }, named.recipeRef!!.steps.filterIndexed { i, _ -> i != 2 })
        val unnamed = decoded().getValue("yourhome-unnamed")
        assertEquals("15" to "240", unnamed.dose to unnamed.water)
        assertEquals("190", unnamed.steps[2].water)
    }

    @Test fun recordsThatAreNotLegacyYourHomeAreLeftAlone() {
        val current = com.coffeejournal.domain.model.Entry(
            id = "mine", createdAt = 1L, dose = "18", water = "300",
            steps = listOf(RecipeStep("0:00", "50", "10", "1차 푸어"), RecipeStep("0:50", "190", "30", "2차 푸어")),
            recipeRef = RecipeRef("유어홈 (Your Home)", listOf(RecipeStep("0:00", "50", "10", "1차 푸어"), RecipeStep("0:50", "190", "30", "2차 푸어"))),
        )
        assertEquals(current, WebMigrations.apply(current), "brewed at 18g / 300g on purpose with the current recipe")
        val other = current.copy(recipeRef = null, steps = listOf(RecipeStep("0:50", "140", "", "")))
        assertEquals(other, WebMigrations.apply(other), "a 140g pour at 0:50 without the 유어홈 recipe")
    }

    @Test fun currentScaleDataIsUntouched_andTheMigrationIsIdempotent() {
        val current = mapOf(
            "fragranceAroma" to 8.25, "flavor" to 6.0, "uniformity" to 10.0, "cleancup" to 0.0, "sweetness" to 6.0,
            "fragranceIntensity" to 3.5, "acidityIntensity" to 1.0, "bodyIntensity" to 5.0, "aromaIntensity" to 9.0,
        )
        assertEquals(current, WebMigrations.migrateAttributes(current))
        val once = decoded().values.toList()
        assertEquals(once, once.map(WebMigrations::apply), "a second pass changes nothing")
        // app backup → restore: encode and decode the migrated records again
        val again = codec.decode(codec.encode(BackupSnapshot(entries = once))).entries
        assertEquals(once.map { it.attributes }, again.map { it.attributes })
        assertEquals(once.map { it.steps to it.recipeRef }, again.map { it.steps to it.recipeRef })
        assertEquals(once.map { it.dose to it.water }, again.map { it.dose to it.water })
    }
}
