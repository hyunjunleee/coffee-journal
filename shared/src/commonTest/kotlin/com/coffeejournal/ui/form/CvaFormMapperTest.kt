package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.reference.CvaForm
import com.coffeejournal.domain.reference.GenericSteps
import com.coffeejournal.domain.rules.CvaAssessment
import com.coffeejournal.domain.rules.CvaScoring
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.nav.FormMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The record form keeps one cupping form per tasting / cupping bean, storing CVA under `cva.` keys (feature-plan-v2 §2.3). */
class CvaFormMapperTest {
    private val now = Dates.toMillis(LocalDate(2026, 9, 25), 12, 0)
    private val cva = CvaAssessment(
        intensity = mapOf("fragrance" to 9, "acidity" to 11),
        affective = CvaForm.sectionKeys.associateWith { 7 },
        aromaDescriptors = listOf("floral", "citrusFruit"),
        mainTastes = listOf("sour"),
        notes = mapOf("overall" to "깨끗"),
    )

    @Test fun cvaTastingSavesOnlyCvaKeys() {
        val s = FormMapper.newState(FormMode.EXTRACT, null, now).copy(
            name = "케냐", scoreForm = ScoreForm.CVA, cva = cva,
            attributes = ScaScoring.defaultAttributes() + ("flavor" to 8.0), attributeNotes = mapOf("flavor" to "달콤"),
        )
        val en = FormMapper.toEntry(s, "id1", null, emptyList(), now)
        assertTrue(en.attributes.keys.all(CvaScoring::isCvaKey), "no SCA 2004 keys: ${en.attributes.keys}")
        assertTrue(en.attributeNotes.keys.all(CvaScoring::isCvaKey))
        assertNull(ScaScoring.effectiveTotal(en.attributes))
        assertEquals(56 * 0.65625 + 52.75, CvaScoring.scoreOf(en)) // Σ 56 → 89.50
        assertEquals("CVA 89.50 / 100", EntryDisplay.scaTotalText(en))

        // reopening shows the CVA sheet with the same values, and a fresh 2004 sheet behind it
        val back = FormMapper.fromEntry(en, FormMode.EXTRACT)
        assertEquals(ScoreForm.CVA, back.scoreForm)
        assertEquals(cva, back.cva)
        assertEquals(ScaScoring.defaultAttributes(), back.attributes)
        assertTrue(back.attributeNotes.isEmpty())
    }

    @Test fun sca2004TastingDropsAnyCvaAssessment() {
        val s = FormMapper.newState(FormMode.EXTRACT, null, now).copy(name = "케냐", cva = cva, attributes = ScaScoring.defaultAttributes() + ("flavor" to 8.0))
        val en = FormMapper.toEntry(s, "id1", null, emptyList(), now)
        assertFalse(CvaScoring.present(en.attributes, en.attributeNotes))
        assertEquals(38.0, ScaScoring.effectiveTotal(en.attributes))
        assertEquals(ScoreForm.SCA2004, FormMapper.fromEntry(en, FormMode.EXTRACT).scoreForm)
    }

    @Test fun cuppingBeanKeepsItsOwnForm() {
        val bean = CuppingBeanForm(name = "파나마 게이샤", scoreForm = ScoreForm.CVA, cva = cva, evaluation = mapOf("aroma" to "꽃"), evaluationScores = mapOf("aroma" to 8.0))
        val model = FormMapper.cuppingBeanModel(bean)
        assertTrue(model.evaluationScores.keys.all(CvaScoring::isCvaKey))
        assertEquals("floral,citrusFruit", model.evaluation["cva.cata.aroma"])
        assertFalse("aroma" in model.evaluation, "the 2004 memo of a CVA bean is not saved")
        val form = FormMapper.cuppingBeanForm(model)
        assertEquals(ScoreForm.CVA, form.scoreForm)
        assertEquals(cva, form.cva)
        // a 2004 bean is untouched
        val plain = FormMapper.cuppingBeanForm(CuppingBean(name = "케냐", evaluationScores = mapOf("acidity" to 9.0)))
        assertEquals(ScoreForm.SCA2004, plain.scoreForm)
        assertEquals(mapOf("acidity" to 9.0), FormMapper.cuppingBeanModel(plain).evaluationScores)
    }

    @Test fun cuppingEntryDisplaysNoTastingScore() {
        val en = Entry(id = "c", createdAt = now, category = Category.CUPPING, cuppingBeans = listOf(FormMapper.cuppingBeanModel(CuppingBeanForm(name = "a", scoreForm = ScoreForm.CVA, cva = cva))))
        assertNull(EntryDisplay.scaTotalText(en))
    }

    @Test fun ownStepLogIsWhatTheTimerAsksAbout() {
        val fresh = FormMapper.newState(FormMode.EXTRACT, null, now)
        assertEquals(GenericSteps.example.size, fresh.steps.size)
        assertFalse(FormMapper.hasOwnStepLog(fresh), "the example every form starts with")
        assertFalse(FormMapper.hasOwnStepLog(fresh.copy(steps = emptyList())))
        assertFalse(FormMapper.hasOwnStepLog(fresh.copy(steps = listOf(StepForm()))), "blank rows")
        assertTrue(FormMapper.hasOwnStepLog(fresh.copy(steps = listOf(StepForm.from(RecipeStep("0:00", "50", "30", "뜸"))))))
    }
}
