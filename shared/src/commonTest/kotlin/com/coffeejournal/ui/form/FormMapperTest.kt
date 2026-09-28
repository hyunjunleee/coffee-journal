package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.reference.CafeRecipes
import com.coffeejournal.domain.reference.Champions
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.nav.FormMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormMapperTest {
    private val now = Dates.toMillis(LocalDate(2026, 9, 25), 12, 0)

    private fun entryOf(state: FormState, existing: Entry? = null, photos: List<String> = emptyList()) =
        FormMapper.toEntry(state, "id1", existing, photos, now)

    @Test fun newBrewFormSeedsGenericStepsAndComputesTime() {
        val s = FormMapper.newState(FormMode.EXTRACT, null, now, lastGrind = "20", lastWaterType = "정수기 물")
        assertEquals(Category.BEAN, s.category)
        assertEquals(6, s.steps.size)
        assertEquals("2:10", s.time)
        assertEquals("100", s.bagWeight)
        assertEquals(10.0, s.attributes["uniformity"])
        assertEquals("20", s.grind)
        val cafe = FormMapper.newState(FormMode.CAFE, null, now, lastGrind = "20")
        assertEquals(Category.CAFE, cafe.category)
        assertTrue(cafe.steps.isEmpty())
        assertEquals("", cafe.grind)
        assertEquals("수업", FormMapper.newState(FormMode.CUPPING, "수업", now).cuppingType)
        assertEquals(CuppingType.PUBLIC, FormMapper.newState(FormMode.CUPPING, "이상한값", now).cuppingType)
    }

    @Test fun brewEntryFollowsSaveRules() {
        val s = FormMapper.newState(FormMode.EXTRACT, null, now).copy(
            name = "에티오피아 예가체프 (리브레)", price = "18,000", process = "허니", processSub = "더블 퍼멘티드", roast = "라이트",
            packageType = PackageType.DRIPBAG, grind = "20", waterType = "정수기 물", dose = "15", water = "240",
            expectedNotes = listOf("자스민"), expectedInput = "오렌지, 자스민", actualInput = "복숭아",
            attributes = ScaScoring.defaultAttributes() + ("flavor" to 8.25), attributeNotes = mapOf("flavor" to " 밝다 ", "body" to " "),
        )
        val en = entryOf(s, photos = listOf("a.jpg"))
        assertEquals(Category.BEAN, en.category)
        assertEquals("허니(더블 퍼멘티드)", en.process)
        assertEquals("", en.processOther)
        assertEquals("18000", en.price)
        assertEquals(PackageType.DRIPBAG, en.packageType)
        assertEquals("자스민, 오렌지", en.expectedNotes)
        assertEquals("복숭아", en.actualNotes)
        assertEquals(6, en.steps.size)
        assertEquals("2:10", en.time)
        assertEquals(mapOf("flavor" to "밝다"), en.attributeNotes)
        assertEquals(8.25, en.attributes["flavor"])
        assertEquals(10.0, en.attributes["uniformity"])
        assertEquals(listOf("a.jpg"), en.bagPhotos)
        assertEquals("20", en.grind)
        assertEquals(now, en.createdAt)
    }

    @Test fun cafeEntryDropsBrewOnlyFields() {
        val s = FormMapper.newState(FormMode.CAFE, null, now).copy(
            name = "케냐 AA", cafeName = "OO카페", price = "6,500", grind = "x", waterType = "y", packageType = PackageType.SAMPLE,
            steps = listOf(StepForm(time = "0:00", pour = true, water = "30")), appliedRecipeRef = RecipeRef("r"),
        )
        val en = entryOf(s)
        assertEquals(Category.CAFE, en.category)
        assertEquals("OO카페", en.cafeName)
        assertEquals("6500", en.price)
        assertEquals("", en.grind)
        assertEquals("", en.waterType)
        assertTrue(en.steps.isEmpty())
        assertNull(en.recipeRef)
        assertEquals(PackageType.STANDARD, en.packageType)
    }

    @Test fun cuppingEntryUsesPlaceOrFirstBeanAndSkipsUnnamedBeans() {
        val beans = listOf(
            CuppingBeanForm(name = "A", process = "허니", processSub = "레드", price = "15,000", evaluationScores = mapOf("aroma" to 8.0), evaluation = mapOf("aroma" to "꽃", "body" to " ")),
            CuppingBeanForm(name = "   "),
            CuppingBeanForm(name = "B", process = "기타", processOther = "카보닉", beanMode = BeanMode.BLEND, blendComponentsText = "에티오피아 + 콜롬비아"),
            CuppingBeanForm(name = "C", process = "기타"),
        )
        val s = FormMapper.newState(FormMode.CUPPING, "수업", now).copy(cuppingBeans = beans, cuppingNotes = "좋았다", price = "1000", notes = "무시")
        val en = entryOf(s)
        assertEquals(Category.CUPPING, en.category)
        assertEquals("A", en.name)
        assertEquals("수업", en.cuppingType)
        assertEquals(listOf("A", "B", "C"), en.cuppingBeans.map { it.name })
        assertEquals("허니(레드)", en.cuppingBeans[0].process)
        assertEquals("15000", en.cuppingBeans[0].price)
        assertEquals(mapOf("aroma" to "꽃"), en.cuppingBeans[0].evaluation)
        assertEquals(mapOf("aroma" to 8.0), en.cuppingBeans[0].evaluationScores)
        assertEquals("카보닉", en.cuppingBeans[1].process)
        assertEquals(BeanMode.BLEND, en.cuppingBeans[1].beanMode)
        assertEquals("기타", en.cuppingBeans[2].process)
        assertEquals("", en.price)
        assertEquals("좋았다", en.notes)
        assertTrue(en.attributes.isEmpty())
        assertTrue(en.steps.isEmpty())
        assertEquals("FELT 청계천", entryOf(s.copy(cuppingPlace = "FELT 청계천")).name)
    }

    @Test fun customBlendAutoNameAndDoseSum() {
        val s = FormMapper.newState(FormMode.EXTRACT, null, now).copy(
            beanMode = BeanMode.CUSTOM_BLEND, blendRows = listOf(BlendRowForm("A", "10"), BlendRowForm("B", "20.5"), BlendRowForm("", "")), dose = "99",
        )
        assertNull(FormMapper.validate(s))
        val en = entryOf(s)
        assertEquals("A + B", en.name)
        assertEquals("30.5", en.dose)
        assertEquals(2, en.blendComponents.size)
        assertEquals("내 블렌드", entryOf(s.copy(name = "내 블렌드")).name)
        assertEquals(FormField.BLEND_ROWS, FormMapper.validate(s.copy(blendRows = listOf(BlendRowForm("A", "10"))))?.field)
        val noGrams = entryOf(s.copy(blendRows = listOf(BlendRowForm("A"), BlendRowForm("B"))))
        assertEquals("99", noGrams.dose)
    }

    @Test fun validationPointsAtTheRightField() {
        assertEquals(FormField.NAME, FormMapper.validate(FormMapper.newState(FormMode.EXTRACT, null, now))?.field)
        assertEquals(FormField.CUPPING_BEAN_NAME, FormMapper.validate(FormMapper.newState(FormMode.CUPPING, null, now))?.field)
        assertNull(FormMapper.validate(FormMapper.newState(FormMode.CAFE, null, now).copy(name = "x")))
        val pendingOnly = FormMapper.newState(FormMode.CUPPING, null, now).copy(cuppingBeans = listOf(CuppingBeanForm(name = "", expectedInput = "x")))
        assertEquals(FormField.CUPPING_BEAN_NAME, FormMapper.validate(pendingOnly)?.field)
    }

    @Test fun autofillOnlyFillsBlankFields() {
        val first = Entry(
            id = "e1", createdAt = 1_000, name = "예가체프 (리브레, 노르딕)", country = "에티오피아", region = "Yirgacheffe",
            price = "17000", bagWeight = "200", expectedNotes = "자스민, 베르가못", variety = "Heirloom", roasterDesc = "설명",
        )
        val second = Entry(id = "e2", createdAt = 2_000, name = "예가체프", process = "워시드", roast = "라이트", roastDate = "7. 11")
        val s = FormMapper.newState(FormMode.EXTRACT, null, now).copy(name = "예가체프", country = "케냐", variety = "")
        val out = FormMapper.autofill(s, listOf(second, first))
        assertEquals("케냐", out.country)
        assertEquals("Yirgacheffe", out.region)
        assertEquals("Heirloom", out.variety)
        assertEquals("리브레", out.roastery)
        assertEquals("노르딕", out.selection)
        assertEquals("워시드", out.process)
        assertEquals("라이트", out.roast)
        assertEquals("7. 11", out.roastDate)
        assertEquals("17,000", out.price)
        assertEquals("200", out.bagWeight)
        assertEquals("설명", out.roasterDesc)
        assertEquals(listOf("자스민", "베르가못"), out.expectedNotes)
        assertTrue(out.autofillBanner)

        val typed = s.copy(roastery = "내가 적음", price = "9,000", expectedNotes = listOf("복숭아"))
        val kept = FormMapper.autofill(typed, listOf(first))
        assertEquals("내가 적음", kept.roastery)
        assertEquals("9,000", kept.price)
        assertEquals(listOf("복숭아"), kept.expectedNotes)

        val none = FormMapper.autofill(s, emptyList())
        assertFalse(none.autofillBanner)
        assertEquals(s, none)
        val cafe = FormMapper.autofill(FormMapper.newState(FormMode.CAFE, null, now).copy(name = "예가체프"), listOf(first))
        assertEquals("", cafe.price)
    }

    @Test fun processSplitAndJoin() {
        assertEquals("허니", FormMapper.processValue("허니", ""))
        assertEquals("허니(옐로)", FormMapper.processValue("허니", " 옐로 "))
        assertEquals("기타", FormMapper.processValue("기타", "x"))
        assertEquals(Triple("허니", "더블 퍼멘티드", ""), FormMapper.splitProcess("허니(더블 퍼멘티드)", ""))
        assertEquals(Triple("기타", "", "웻헐드"), FormMapper.splitProcess("기타", "웻헐드"))
        assertEquals(Triple("기타", "", "디카페인"), FormMapper.splitProcess("디카페인", ""))
        assertEquals(Triple("", "", ""), FormMapper.splitProcess("", ""))
    }

    @Test fun editRoundTripKeepsFields() {
        val steps = listOf(RecipeStep("0:00", "30", "10", "1차"), RecipeStep("0:10", "", "30", "뜸"))
        val en = Entry(
            id = "e", createdAt = now, category = Category.BEAN, name = "케냐 AA", process = "기타", processOther = "웻헐드", price = "12000",
            packageType = PackageType.SAMPLE, steps = steps, recipeRef = RecipeRef("R", steps), attributes = mapOf("flavor" to 8.0),
            bagPhotos = listOf("p1.jpg"), tags = listOf("t"), roastDate = "7. 11", expectedNotes = "a, b", actualNotes = "c",
        )
        val s = FormMapper.fromEntry(en, FormMode.EXTRACT)
        assertEquals("e", s.editingId)
        assertEquals("기타", s.process)
        assertEquals("웻헐드", s.processOther)
        assertEquals("12,000", s.price)
        assertEquals(PackageType.SAMPLE, s.packageType)
        assertEquals("2026. 7. 11", s.roastDate)
        assertEquals(listOf("a", "b"), s.expectedNotes)
        assertEquals("p1.jpg", s.bagPhotos[0].existingName)
        assertNull(s.bagPhotos[1].existingName)
        val back = FormMapper.toEntry(s, en.id, en, listOf("p1.jpg"), now + 5)
        assertEquals(en.process, back.process)
        assertEquals(en.processOther, back.processOther)
        assertEquals(en.price, back.price)
        assertEquals(en.packageType, back.packageType)
        assertEquals(en.steps, back.steps)
        assertEquals(en.recipeRef, back.recipeRef)
        assertEquals(en.tags, back.tags)
        assertEquals(en.attributes, back.attributes)
        assertEquals(en.createdAt, back.createdAt)
        assertEquals("c", back.actualNotes)
    }

    @Test fun cuppingEditRestoresBeans() {
        val en = Entry(
            id = "c", createdAt = now, category = Category.CUPPING, name = "FELT", cuppingType = "", cuppingPlace = "FELT", notes = "전체",
            cuppingBeans = listOf(
                com.coffeejournal.domain.model.CuppingBean(id = "b1", name = "A", process = "워시드(더블)", price = "15000", evaluationScores = mapOf("aroma" to 7.5)),
                com.coffeejournal.domain.model.CuppingBean(id = "b2", name = "B", process = "카보닉"),
            ),
        )
        val s = FormMapper.fromEntry(en, FormMode.CUPPING)
        assertEquals(CuppingType.PUBLIC, s.cuppingType)
        assertEquals("전체", s.cuppingNotes)
        assertEquals("워시드", s.cuppingBeans[0].process)
        assertEquals("더블", s.cuppingBeans[0].processSub)
        assertEquals("15,000", s.cuppingBeans[0].price)
        assertTrue(s.cuppingBeans[0].evaluationOpen)
        assertEquals("기타", s.cuppingBeans[1].process)
        assertEquals("카보닉", s.cuppingBeans[1].processOther)
        val back = FormMapper.toEntry(s, en.id, en, emptyList(), now)
        assertEquals(listOf("b1", "b2"), back.cuppingBeans.map { it.id })
        assertEquals("워시드(더블)", back.cuppingBeans[0].process)
        assertEquals("카보닉", back.cuppingBeans[1].process)
    }

    @Test fun recipesApplyTheRightFields() {
        val base = FormMapper.newState(FormMode.EXTRACT, null, now).copy(filter = "내 필터", grind = "18")
        val champ = FormMapper.applyChampion(base.copy(openLauncher = RecipeLauncher.CHAMPIONS), Champions.all.first { it.year == 2023 })
        assertEquals("15.5", champ.dose)
        assertEquals("250", champ.water)
        assertEquals("91", champ.temp)
        assertEquals("오리가미", champ.dripper)
        assertNull(champ.appliedRecipeRef)
        assertNull(champ.openLauncher)
        assertEquals(6, champ.steps.size)

        // Glitch's published guide (shop.glitchcoffee.com/en/pages/brew-guide): 14.5g, 260g poured, 86-90℃, Kalita Wave
        val glitch = FormMapper.applyCafeRecipe(base, CafeRecipes.all.first { it.id == "glitch" })
        assertEquals(listOf("14.5", "260", "86"), listOf(glitch.dose, glitch.water, glitch.temp))
        assertEquals("권장 범위: 86~90°C", glitch.tempHint)
        assertEquals("글리치 커피 (Glitch Coffee & Roasters)", glitch.appliedRecipeRef?.name)
        assertEquals(7, glitch.steps.size)
        assertEquals("1:35", glitch.time) // no total time in the guide: the log ends with the last pour
        assertEquals("칼리타 웨이브 필터", glitch.filter)
        assertEquals("", glitch.grind)

        // a recipe without a filter keeps the one already in the form (web: only a given c.filter is applied)
        val origami = FormMapper.applyCafeRecipe(base, CafeRecipes.all.first { it.id == "kurasu-origami" })
        assertEquals("내 필터", origami.filter)
        assertEquals(listOf("15", "270", "88", "1:20"), listOf(origami.dose, origami.water, origami.temp, origami.time))
    }

    @Test fun nameTypingFillsFarmFromParensOnlyWhenEmpty() {
        val s = FormMapper.newState(FormMode.EXTRACT, null, now)
        val typed = FormMapper.onNameTyped(s, "벤사 (리브레, 노르딕, 농장, 생산자)")
        assertEquals("농장(생산자)", typed.farmProducer)
        assertEquals("괄호에서 인식: 로스터리: 리브레 · 출처: 노르딕 · 농장: 농장 · 생산자: 생산자", FormMapper.nameParenHint(typed.name))
        val kept = FormMapper.onNameTyped(s.copy(farmProducer = "직접"), "벤사 (리브레, 노르딕, 농장)")
        assertEquals("직접", kept.farmProducer)
        assertNull(FormMapper.nameParenHint("괄호 없음"))
    }

    @Test fun newRoastery_isOneTheSaveWouldRegister() {
        val known = listOf("커피 리브레", "Momos Coffee")
        assertTrue(FormMapper.isNewRoastery("프릳츠", known))
        // matched like the save's auto-registration: case and spaces do not make a new roastery
        assertFalse(FormMapper.isNewRoastery(" 커피리브레 ", known))
        assertFalse(FormMapper.isNewRoastery("momos coffee", known))
        assertFalse(FormMapper.isNewRoastery("  ", known))
        assertTrue(FormMapper.isNewRoastery("모모스", emptyList()))
    }
}
