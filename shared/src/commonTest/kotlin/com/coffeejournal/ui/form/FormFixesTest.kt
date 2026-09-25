package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.nav.FormMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Audit fixes of the record form: hidden fields, blend leftovers, repeat beans, the draft id, bad numbers, saved state. */
class FormFixesTest {
    private val now = Dates.toMillis(LocalDate(2026, 9, 25), 12, 0)

    private fun entryOf(state: FormState, existing: Entry? = null) = FormMapper.toEntry(state, "id1", existing, emptyList(), now)

    // form-3: the cupping form never shows the recipe or bag fields, so it must not save the seeded 2:10 or 100 g
    @Test fun cuppingRecordSavesNoHiddenRecipeFields() {
        val s = FormMapper.newState(FormMode.CUPPING, null, now).copy(
            cuppingBeans = listOf(CuppingBeanForm(name = "A")),
            dose = "15", water = "240", temp = "92", dripper = "V60", filter = "표백",
        )
        assertEquals("2:10", s.time, "the fresh form still seeds the example steps (category can switch to 원두)")
        val en = entryOf(s)
        assertEquals(listOf("", "", "", "", "", "", ""), listOf(en.time, en.dose, en.water, en.temp, en.dripper, en.filter, en.bagWeight))
        assertFalse(EntryDisplay.infoRows(en).any { it.first == "총 시간" })
        val older = Entry(id = "old", createdAt = now, category = Category.CUPPING, time = "2:10")
        assertFalse(EntryDisplay.infoRows(older).any { it.first == "총 시간" }, "an invented time stored earlier is not shown either")

        val cafe = entryOf(FormMapper.newState(FormMode.CUPPING, null, now).copy(category = Category.CAFE, name = "케냐", dripper = "V60", dose = "15"))
        assertEquals("", cafe.time, "카페 hides 총 추출시간 too")
        assertEquals("", cafe.dose)
        assertEquals("V60", cafe.dripper, "카페 shows 드리퍼")

        val brew = entryOf(FormMapper.newState(FormMode.CUPPING, null, now).copy(category = Category.BEAN, name = "케냐", dose = "15"))
        assertEquals("2:10", brew.time, "switching the cupping form to 원두 keeps the brew fields")
        assertEquals("15", brew.dose)
        assertEquals("100", brew.bagWeight)
    }

    // form-9: 직접 블렌드 left over after switching to 카페 neither blocks saving nor is stored
    @Test fun customBlendLeftOverInCafeIsIgnored() {
        val s = FormMapper.newState(FormMode.CAFE, null, now).copy(
            category = Category.CAFE, beanMode = BeanMode.CUSTOM_BLEND, name = "케냐", blendRows = listOf(BlendRowForm("A", "10"), BlendRowForm()),
        )
        assertFalse(s.isCustomBlend)
        assertNull(FormMapper.validate(s))
        val en = entryOf(s)
        assertEquals(BeanMode.SINGLE, en.beanMode)
        assertTrue(en.blendComponents.isEmpty())
        assertEquals(FormField.NAME, FormMapper.validate(s.copy(name = ""))?.field)
        // still a blend when switched back to 원두
        assertEquals(FormField.BLEND_ROWS, FormMapper.validate(s.copy(category = Category.BEAN))?.field)
        assertEquals(BeanMode.COMMERCIAL_BLEND, entryOf(s.copy(beanMode = BeanMode.COMMERCIAL_BLEND)).beanMode)
    }

    // form-8: any earlier record of the bean locks the bag info (banner) and hides the bag-photo slots
    @Test fun repeatBeanShowsBannerEvenWhenNothingWasFilled() {
        val first = Entry(id = "e1", createdAt = 1_000, name = "벤사")
        val s = FormMapper.newState(FormMode.EXTRACT, null, now).copy(name = "벤사")
        val same = FormMapper.autofill(s, listOf(first))
        assertEquals(s.copy(autofillBanner = true, repeatBean = true), same)
        val fresh = FormMapper.autofill(same, emptyList())
        assertFalse(fresh.autofillBanner)
        assertFalse(fresh.repeatBean)

        val later = Entry(id = "e2", createdAt = 2_000, name = "벤사 (리브레)")
        val cupping = Entry(id = "c", createdAt = 500, category = Category.CUPPING, name = "벤사")
        assertTrue(FormMapper.hasEarlierSameBean(later, listOf(first, later)))
        assertFalse(FormMapper.hasEarlierSameBean(first, listOf(first, later)), "the first registration may edit its bag info")
        assertFalse(FormMapper.hasEarlierSameBean(first, listOf(first, cupping)), "cupping records do not count")
    }

    // form-4: a new form carries one id for its whole session
    @Test fun newFormsGetTheirOwnDraftId() {
        val a = FormMapper.newState(FormMode.EXTRACT, null, now)
        val b = FormMapper.newState(FormMode.EXTRACT, null, now)
        assertTrue(a.draftId.isNotBlank())
        assertNotEquals(a.draftId, b.draftId)
        assertEquals("fixed", FormMapper.newState(FormMode.CAFE, null, now, draftId = "fixed").draftId)
    }

    // NaN / Infinity typed or pasted into number fields must not reach roundToInt
    @Test fun nonFiniteNumbersCountAsMissing() {
        val steps = GenericStepsForm.example().toMutableList()
        steps[steps.lastIndex] = steps.last().copy(wait = "NaN")
        val s = FormMapper.withStepsTime(FormMapper.newState(FormMode.EXTRACT, null, now).copy(name = "x", steps = steps))
        assertEquals("1:35", s.time, "the NaN wait is ignored instead of crashing")
        assertEquals("", s.steps.last().toStep().wait)
        assertEquals("", StepForm(time = "0:00", pour = true, water = "Infinity").toStep().water)
        assertEquals("", StepForm(time = "0:00", wait = "1e999").toStep().wait)
        assertEquals("", StepForm(time = "99999999999:00", wait = "5").toStep().time)
        assertEquals("abc", StepForm(wait = "abc").toStep().wait, "free text is kept as typed")

        val en = entryOf(s.copy(dose = "NaN", water = "Infinity", temp = "92", bagWeight = "-Infinity"))
        assertEquals(listOf("", "", "92", ""), listOf(en.dose, en.water, en.temp, en.bagWeight))
        assertEquals("", en.steps.last().wait)

        val blend = entryOf(
            FormMapper.newState(FormMode.EXTRACT, null, now).copy(
                beanMode = BeanMode.CUSTOM_BLEND, blendRows = listOf(BlendRowForm("A", "10"), BlendRowForm("B", "Infinity")),
            )
        )
        assertEquals("10", blend.dose)
        assertEquals("", blend.blendComponents[1].grams)

        val stored = FormNumbers.finiteSteps(listOf(RecipeStep("1:00", "NaN", "Infinity", "")))
        assertEquals(RecipeStep("1:00", "", "", ""), stored.single())
        assertEquals(RecipeStep("0:10", "", "", ""), FormNumbers.finiteRef(RecipeRef("r", listOf(RecipeStep("0:10", "", "NaN", ""))))!!.steps.single())
        val edited = FormMapper.fromEntry(Entry(id = "e", createdAt = now, attributes = mapOf("flavor" to Double.NaN)), FormMode.EXTRACT)
        assertEquals(10.0, edited.attributes["uniformity"], "a record with only NaN scores opens with the defaults")
    }

    // platform-7: the form survives process death through its SavedStateHandle string
    @Test fun formStateRoundTripsWithoutPhotoBytes() {
        val s = FormMapper.newState(FormMode.EXTRACT, null, now).copy(
            name = "복원 원두", dose = "17", expectedNotes = listOf("자스민"), actualInput = "복숭아",
            steps = listOf(StepForm("0:00", "10", true, "30", "1차")), blendRows = listOf(BlendRowForm("A", "5")),
            cuppingBeans = listOf(CuppingBeanForm(name = "c", evaluationScores = mapOf("aroma" to 8.0))),
            appliedRecipeRef = RecipeRef("R", listOf(RecipeStep("0:00", "30"))),
            bagPhotos = listOf(PhotoSlot(existingName = "a.jpg"), PhotoSlot(pending = byteArrayOf(1, 2))),
            attributes = mapOf("flavor" to 8.25), openLauncher = RecipeLauncher.MINE, repeatBean = true,
            saving = true, error = FormError(FormField.NAME, "x"),
        )
        val back = FormStateCodec.decode(FormStateCodec.encode(s))!!
        assertEquals(s.copy(saving = false, error = null, bagPhotos = listOf(PhotoSlot("a.jpg"), PhotoSlot())), back)
        assertEquals(s.draftId, back.draftId)
        assertNull(FormStateCodec.decode("not json"))
    }
}

/** The generic example steps as editable rows (what every fresh brew form starts with). */
private object GenericStepsForm {
    fun example(): List<StepForm> = com.coffeejournal.domain.reference.GenericSteps.example.map(StepForm::from)
}
