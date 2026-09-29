package com.coffeejournal.android

import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.CuppingType
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.reference.CafeRecipes
import com.coffeejournal.domain.reference.Champions
import com.coffeejournal.domain.reference.FlavorWheel
import com.coffeejournal.domain.reference.GenericSteps
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.about.Credits
import com.coffeejournal.ui.form.sections.FlavorWheelTexts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Critic gap #7, record form part: recipe launchers, the steps log and its recipe diff, the flavor wheel and the
 * bag-note suggestions, 카페 블렌드 (+ 원두 추가) / 직접 블렌드, editing a cupping's bean list and the 허니 detail.
 * Expected values come from the web handlers in script3.js (line numbers in each test).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class CoverageFlowTest : CoverageFlowBase() {

    // ───────────── web 6143-6173: 🏆 챔피언 레시피 → "이 비율 적용 →" ─────────────

    /** Web champ-apply: dose, water, temp, dripper; the temp hint is cleared; steps and the recipe reference stay. */
    @Test
    fun cov01_championRecipe_fillsDoseWaterTempDripper_keepsStepsAndName() {
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "챔피언 비율 테스트 원두")
        clickText("🏆 챔피언 레시피")
        waitForText("역대 월드 브루어스컵 우승자들의 실제 레시피예요.", substring = true)
        val carlos = Champions.all.single { it.year == 2023 }
        waitForText("${carlos.name} · ${carlos.country}")
        waitForText("15.5g : 250g · 91°C · 오리가미")
        // cards are listed newest first (web CHAMPIONS.slice().reverse())
        assertTopToBottom("champion cards", hasText("WBrC 2025"), hasText("WBrC 2023"), hasText("WBrC 2016"))
        clickNode(button("이 비율 적용 →"), Champions.all.asReversed().indexOf(carlos))

        waitGone(hasText("역대 월드 브루어스컵 우승자들의 실제 레시피예요.", substring = true)) // panel.style.display = 'none'
        assertTrue("dose", has(field("15.5")))
        assertTrue("water", has(field("250")))
        assertTrue("temp", has(field("91")))
        assertTrue("dripper", has(field("오리가미")))
        assertTrue("the typed name is kept (prepareFormForRecipeApplication)", has(field("챔피언 비율 테스트 원두")))
        assertTrue("no recipe reference: the generic example stays", has(hasText("추출 예시 (참고용)")))
        assertTrue("the example steps' total time", has(field("2:10")))

        saveForm("챔피언 비율 테스트 원두")
        assertTrue("detail 비율 row", has(hasText("15.5g : 250g")))
        assertTrue(has(hasText("91°C")))
        val saved = entries().single()
        assertEquals(listOf("15.5", "250", "91", "오리가미"), listOf(saved.dose, saved.water, saved.temp, saved.dripper))
        assertNull("champions carry no steps, so no recipe reference is stored", saved.recipeRef)
        assertEquals("the log keeps the example steps", GenericSteps.example, saved.steps)
        assertEquals("2:10", saved.time)
    }

    // ───────────── web 6177-6300: ☕ 카페 레시피 → "이 레시피 적용 →" ─────────────

    /** Web cafe-apply: dose/water/temp(+hint)/dripper/grind, filter and time when given, the steps and the reference. */
    @Test
    fun cov02_cafeRecipe_fillsRecipeStepsAndReference_secondRecipeReplacesTheFirst() {
        // a previous brew gives the new form a grind of "20" (web openForm); a cafe recipe always overwrites it
        runBlocking { koinGet<EntryRepository>().upsert(Entry(id = "prev", createdAt = Dates.nowMillis() - 86_400_000, name = "이전 원두", grind = "20")) }
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "카페 레시피 테스트 원두")
        assertEquals("last grind prefilled (plus the dose placeholder '20')", 2, count(field("20")))
        clickText("☕ 카페 레시피")
        waitForText("유명 스페셜티 카페들이 공개한 브루 가이드예요.", substring = true)
        val glitch = CafeRecipes.all.single { it.id == "glitch" }
        waitForText("14.5g : 260g · 86°C · 오리가미 드리퍼 (칼리타 웨이브 필터)") // web champ-spec, no grind for glitch
        waitForText("출처: shop.glitchcoffee.com ↗")
        clickNode(button("이 레시피 적용 →"), CafeRecipes.all.indexOf(glitch))

        waitForText("권장 범위: 86~90°C")
        assertTrue(has(field("14.5")) && has(field("260")) && has(field("86")) && has(field("오리가미 드리퍼")) && has(field("칼리타 웨이브 필터")))
        assertEquals("f-grind = c.grind ('' for glitch)", 0, count(field("20")))
        assertTrue("the time the log gives", has(field("1:35")))
        waitForText("추출 예시: ${glitch.name} (참고, 수정 불가)")
        waitForText("총 4차 추출 · 합계 물량 260g · 총 시간 1:35")
        waitForText("✓ ${glitch.name} 그대로 부었어요")
        assertTrue("the glitch steps are in the log", has(field("2차 푸어 (누적 60g)")))

        // a second recipe clears the log first (web clearSteps) and replaces the reference
        clickText("☕ 카페 레시피")
        val origami = CafeRecipes.all.single { it.id == "kurasu-origami" }
        waitForText("15g : 270g · 88°C · 오리가미 드리퍼 · 가늘게 (EK43 약 6.5)")
        clickNode(button("이 레시피 적용 →"), CafeRecipes.all.indexOf(origami))
        waitForText("추출 예시: ${origami.name} (참고, 수정 불가)")
        waitForText("권장 범위: 88~90°C")
        assertFalse("old steps are gone", has(field("2차 푸어 (누적 60g)")))
        assertTrue(has(field("가늘게 (EK43 약 6.5)")) && has(field("1:20")))

        saveForm("카페 레시피 테스트 원두")
        waitForText("✓ ${origami.name} 그대로 부었어요")
        val saved = entries().single { it.name == "카페 레시피 테스트 원두" }
        // no filter in this recipe: the one the first recipe set stays (web applies c.filter only when given)
        assertEquals(listOf("15", "270", "88", "오리가미 드리퍼", "칼리타 웨이브 필터", "가늘게 (EK43 약 6.5)", "1:20"), listOf(saved.dose, saved.water, saved.temp, saved.dripper, saved.filter, saved.grind, saved.time))
        assertEquals(RecipeRef(origami.name, origami.steps), saved.recipeRef)
        assertEquals(origami.steps, saved.steps)
    }

    // ───────────── web 6696-6930: editing the steps log, the summary and the recipe diff ─────────────

    @Test
    fun cov03_stepsLog_editAddRemove_updatesSummaryWarningAndDiff() {
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "단계 로그 테스트 원두")
        clickText("☕ 카페 레시피")
        val yourHome = CafeRecipes.all.single { it.id == "yourhome" }
        clickNode(button("이 레시피 적용 →"), CafeRecipes.all.indexOf(yourHome))
        waitForText("총 2차 추출 · 합계 물량 240g · 총 시간 2:30")
        waitForText("✓ ${yourHome.name} 그대로 부었어요")

        // pour more in the 2nd pour: total water, the web's water warning and one diff line
        replaceIn("190", "200")
        waitForText("총 2차 추출 · 합계 물량 250g · 총 시간 2:30")
        waitForText("⚠ 레시피의 물량(240g)과 맞지 않습니다. (합계 250g)")
        waitForText("${yourHome.name} 대비 이번에 어긋난 부분")
        waitForText("2차 푸어: 물량 190g→200g (+10g)")
        assertFalse(has(hasText("그대로 부었어요", substring = true)))

        // a longer drawdown: 총 추출시간 follows the last row (web updateStepsSummary writes f-time)
        replaceIn("70", "80")
        waitFor(field("2:40"), "총 추출시간 2:40")
        waitForText("드로우다운: 대기 70s→80s (+10s)")

        // a pour toggled on for the swirl row
        clickNode(hasContentDescription("붓기 단계 여부") and hasClickAction(), 1)
        waitFor(field("물량g"), "water input of the swirl row")
        typeInto("물량g", "10")
        waitForText("총 3차 추출 · 합계 물량 260g · 총 시간 2:40")

        // a row added after the drawdown moves the total time; removing it restores it
        clickText("+ 단계 추가")
        // the first row's time is "0:00" too; the new row's empty field is the last match
        val newTime = node(field("0:00"), count(field("0:00")) - 1)
        runCatching { newTime.performScrollTo() }
        newTime.performTextInput("2:40")
        settle(1)
        val newWait = node(hasSetTextAction() and hasText("드로우다운"), count(hasSetTextAction() and hasText("드로우다운")) - 1)
        runCatching { newWait.performScrollTo() }
        newWait.performTextInput("20")
        settle(1)
        typeInto("메모 (뜸 / 1차 푸어 등)", "추가 대기")
        waitFor(field("3:00"), "총 추출시간 3:00 from the added row")
        waitForText("총 3차 추출 · 합계 물량 260g · 총 시간 3:00")
        assertEquals(5, count(button("✕")))
        clickNode(button("✕"), 4)
        waitFor(field("2:40"), "back to 2:40 after removing the row")
        // toggling the pour off again empties its water (web: input.value = '')
        clickNode(hasContentDescription("붓기 단계 여부") and hasClickAction(), 1)
        waitForText("총 2차 추출 · 합계 물량 250g · 총 시간 2:40")

        saveForm("단계 로그 테스트 원두")
        waitForText("2차 푸어: 물량 190g→200g (+10g)")
        waitForText("드로우다운: 대기 70s→80s (+10s)")
        val saved = entries().single()
        assertEquals(
            listOf(RecipeStep("0:00", "50", "10", "1차 푸어"), RecipeStep("0:10", "", "40", "스월, 대기"), RecipeStep("0:50", "200", "30", "2차 푸어"), RecipeStep("1:20", "", "80", "드로우다운")),
            saved.steps,
        )
        assertEquals("2:40", saved.time)
        assertEquals("240", saved.water)
        assertEquals("the reference keeps the recipe's own steps", RecipeRef(yourHome.name, yourHome.steps), saved.recipeRef)
    }

    // ───────────── web 79-104, 3196-3246: flavor wheel and 봉투 노트에서 추천 ─────────────

    @Test
    fun cov04_flavorWheel_pickAndUnpick_andBagNoteSuggestions() {
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "노트 선택 테스트 원두")
        val chipPlaceholder = "노트 추가 후 Enter (예: 오렌지)" // 예상 노트 (0) and 내가 느낀 노트 (1)
        typeInto(chipPlaceholder, "자스민, 복숭아", 0)
        clickNode(button("추가"), 0)
        waitFor(hasContentDescription("복숭아 삭제"), "expected note chips")
        waitForText("봉투 노트에서 추천:")
        assertTrue(has(button("+ 자스민")) && has(button("+ 복숭아")))

        clickText("🎨 SCA Coffee Taster's Flavor Wheel")
        waitForText("Start at the center and move outward. Select a descriptor to add it to your tasting notes.")
        assertEquals(ToggleableState.Off, toggleState(button("Jasmine")))
        clickText("Jasmine")
        waitFor(hasContentDescription("Jasmine 삭제"), "Jasmine added to 내가 느낀 노트")
        assertEquals(ToggleableState.On, toggleState(button("Jasmine")))
        clickText("Peach")
        waitFor(hasContentDescription("Peach 삭제"))
        clickText("Jasmine") // picking it again removes it (web: idx !== -1 → splice)
        waitGone(hasContentDescription("Jasmine 삭제"))
        assertEquals(ToggleableState.Off, toggleState(button("Jasmine")))

        // one tap on a suggestion adds it; the chip then shows ✓ and is no longer tappable
        clickText("+ 자스민")
        waitFor(hasText("✓ 자스민"), "suggestion marked as added")
        assertFalse(has(button("✓ 자스민")))
        assertEquals("expected + actual chip", 2, count(hasContentDescription("자스민 삭제")))
        assertTrue("the other suggestion is still offered", has(button("+ 복숭아")))

        // a typed note in another case lights up the wheel term; the term removes it case-insensitively
        typeInto(chipPlaceholder, "honey", 1)
        clickNode(button("추가"), 1)
        waitFor(hasContentDescription("honey 삭제"))
        assertEquals(ToggleableState.On, toggleState(button("Honey")))
        clickText("Honey")
        waitGone(hasContentDescription("honey 삭제"))

        saveForm("노트 선택 테스트 원두")
        val saved = entries().single()
        assertEquals("Peach, 자스민", saved.actualNotes)
        assertEquals("자스민, 복숭아", saved.expectedNotes)
        assertTrue("detail row", has(hasText("Peach, 자스민")))
    }

    /**
     * Not in the web app: every label of the SCA/WCR wheel can be picked (the inner "Fruity", the middle "Brown Sugar"),
     * and after each category's wheel terms the app's own notes (FlavorWheelExtras) sit in a block of their own as grey
     * chips that TalkBack reads as 비공식, yet pick and unpick like any wheel term.
     */
    @Test
    fun cov04b_flavorWheel_everyTierPickable_unofficialNotesGreyButSelectable() {
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "비공식 노트 테스트 원두")
        clickText("🎨 SCA Coffee Taster's Flavor Wheel")
        waitForText(Credits.FLAVOR_WHEEL_EXTRAS_NOTE)
        val apricot = button("Apricot")
        waitFor(apricot)
        assertTrue("labelled unofficial", has(apricot and hasContentDescription(FlavorWheelTexts.unofficial("Apricot")) and isToggleable()))
        assertFalse("wheel terms are not", has(button("Peach") and hasContentDescription("비공식", substring = true)))
        assertEquals(FlavorWheel.categories.size, count(hasText(FlavorWheelTexts.EXTRAS_LABEL)))
        // Fruity's card: the wheel's own groups first, then the unofficial block, then the next category
        val fruityExtras = hasText(FlavorWheelTexts.EXTRAS_LABEL) // the second block; the first is Floral's
        assertTopToBottom("Fruity card", button("Fruity"), button("Berry"), button("Lime"), apricot, button("Sour"))
        assertTrue("the block heading sits between Lime and Apricot", top(fruityExtras, 1) > top(button("Lime")) && top(fruityExtras, 1) < top(apricot))
        assertEquals(ToggleableState.Off, toggleState(apricot))

        clickNode(apricot)
        waitFor(hasContentDescription("Apricot 삭제"), "Apricot added to 내가 느낀 노트")
        assertEquals(ToggleableState.On, toggleState(apricot))
        clickText("Brown Sugar")
        waitFor(hasContentDescription("Brown Sugar 삭제"))
        clickText("Fruity")
        waitFor(hasContentDescription("Fruity 삭제"))
        clickNode(apricot) // picking it again removes it, like a wheel term
        waitGone(hasContentDescription("Apricot 삭제"))
        assertEquals(ToggleableState.Off, toggleState(apricot))
        clickText("Tartaric Acid")
        waitFor(hasContentDescription("Tartaric Acid 삭제"))

        saveForm("비공식 노트 테스트 원두")
        assertEquals("Brown Sugar, Fruity, Tartaric Acid", entries().single().actualNotes)
    }

    // ───────────── web 649 / 7428-7446: 원두 구성 = 카페 블렌드 (a second bean block makes one) ─────────────

    @Test
    fun cov05_commercialBlendRecord_listedUnderCafeBlendsAndOpensTheRecord() {
        launchApp()
        openNewForm()
        assertFalse("no separate choice: the bean blocks make a café blend", has(button("카페 블렌드")))
        tap(button("+ 원두 추가 (블렌드)"))
        waitFor(button("카페 블렌드"))
        assertTrue("the name keeps its single-bean label", has(hasText("원두 이름")))
        assertFalse("no custom-blend rows for a cafe blend", has(field("원두 선택")))
        typeInto(namePlaceholder, "모모스 에스쇼콜라 테스트")
        typeInto("예: 커피정경", "모모스 테스트")
        saveForm("모모스 에스쇼콜라 테스트")
        val saved = entries().single()
        assertEquals(BeanMode.COMMERCIAL_BLEND, saved.beanMode)
        assertTrue(saved.blendComponents.isEmpty())

        back()
        tab("tab-bean")
        clickText("블렌드")
        clickText("카페 블렌드")
        waitFor(hasText("모모스 에스쇼콜라 테스트"), "cafe blend card")
        waitForText("카페 블렌드 · ${Dates.ymdCompact(saved.createdAt)}")
        waitForText("상업 블렌드 · 모모스 테스트")
        clickText("내가 만든 블렌드")
        waitGone(hasText("모모스 에스쇼콜라 테스트"))
        clickText("카페 블렌드")
        clickText("추출 기록 열기")
        waitUntil("the record's detail") { onDetailOf("모모스 에스쇼콜라 테스트") }
    }

    // ───────────── web 6904-6930 / 6947-6958: 원두 구성 = 직접 블렌드 ─────────────

    @Test
    fun cov06_customBlendRecord_rowsValidationAutoNameDoseAndBlendCard() {
        launchApp()
        openNewForm()
        clickText("직접 블렌드")
        waitForText("블렌드 이름 (선택)")
        waitForText("비워두면 구성 원두 이름으로 자동 생성돼요.")
        assertEquals("two empty rows to start with (web setBeanMode)", 2, count(field("원두 선택")))
        typeInto("원두 선택", "재료 에티오피아 테스트")
        typeInto("그램(g)", "10")
        clickText("저장")
        waitForText("직접 블렌드는 섞은 원두를 2개 이상 적어 주세요.")
        assertTrue("nothing saved", entries().isEmpty())

        typeInto("원두 선택", "재료 브라질 테스트")
        typeInto("그램(g)", "5")
        waitGone(hasText("직접 블렌드는 섞은 원두를 2개 이상 적어 주세요."))
        clickText("+ 원두 추가")
        typeInto("원두 선택", "재료 빼는 원두")
        typeInto("그램(g)", "3")
        clickNode(button("✕"), 2) // the third blend row (the steps log's ✕ come after it)
        waitGone(field("재료 빼는 원두"))

        val name = "재료 에티오피아 테스트 + 재료 브라질 테스트"
        saveForm(name)
        val saved = entries().single()
        assertEquals(BeanMode.CUSTOM_BLEND, saved.beanMode)
        assertEquals(listOf(BlendComponent("재료 에티오피아 테스트", "10"), BlendComponent("재료 브라질 테스트", "5")), saved.blendComponents)
        assertEquals("dose = sum of the grams", "15", saved.dose)

        back()
        tab("tab-bean")
        clickText("블렌드")
        clickText("내가 만든 블렌드")
        waitFor(hasText(name), "custom blend card")
        waitForText("내가 만든 블렌드 · ${Dates.ymdCompact(saved.createdAt)}")
        waitForText("재료 에티오피아 테스트 10g (67%)")
        waitForText("재료 브라질 테스트 5g (33%)")
    }

    // ───────────── form-1 / data-1 through the screens ─────────────

    /** Edit a cupping: remove the first bean, add a new one, save, reopen; every remaining bean keeps every field. */
    @Test
    fun cov07_cuppingEdit_removeFirstBeanAddOne_saveReopen_allBeansIntact() {
        SampleData.seed()
        val before = entry("e5")!!
        launchApp()
        openViaSearch("키리냐가")
        waitFor(hasText("커피플랜트", substring = true))
        clickText("수정")
        waitForText("기록 수정")
        assertTrue(has(field("케냐 키리냐가 AA")) && has(field("파나마 보케테 게이샤")) && has(field("하우스 블렌드")))

        clickNode(button("✕"), 0)
        waitGone(field("케냐 키리냐가 AA"))
        assertTrue("the second bean moved up with its fields", has(field("파나마 보케테 게이샤")) && has(field("Boquete")))
        clickText("+ 원두 추가")
        typeInto("원두 이름 (예: 에티오피아 예가체프)", "르완다 니야마샤케 테스트")
        typeInto("국가", "르완다")
        typeInto("나의 순위", "1")
        clickNode(button("미디엄"), 2)
        typeInto("노트 입력 후 Enter (예: 라즈베리)", "무화과", 2)
        saveForm("커피플랜트 성수", label = "수정 저장")

        fun stripIds(e: Entry) = e.cuppingBeans.map { it.copy(id = "") }
        val after = entry("e5")!!
        assertEquals(3, after.cuppingBeans.size)
        assertEquals("the two kept beans are unchanged", stripIds(before).drop(1), stripIds(after).take(2))
        val added = after.cuppingBeans[2]
        assertEquals(listOf("르완다 니야마샤케 테스트", "르완다", "1", "미디엄", "무화과"), listOf(added.name, added.country, added.rank, added.roast, added.actualNotes))
        assertEquals(before.copy(name = after.name, cuppingBeans = after.cuppingBeans), after)
        assertTrue(has(hasText("르완다 니야마샤케 테스트", substring = true)))
        assertFalse(has(hasText("케냐 키리냐가 AA", substring = true)))

        // reopen: the form shows the three beans, and saving again changes nothing
        clickText("수정")
        waitForText("기록 수정")
        listOf("파나마 보케테 게이샤", "하우스 블렌드", "르완다 니야마샤케 테스트", "르완다", "브라질 60 · 콜롬비아 40").forEach { v ->
            assertTrue("reopened form shows '$v'", has(field(v)))
        }
        saveForm("커피플랜트 성수", label = "수정 저장")
        assertEquals(after, entry("e5"))
    }

    // ───────────── web 3983-4016 / 4147-4190: 가공 방식 → 허니 → 세부 종류 ─────────────

    private fun seedHoneyRecords() = runBlocking {
        val repo = koinGet<EntryRepository>()
        val now = Dates.nowMillis()
        repo.upsert(Entry(id = "h1", createdAt = now - 5 * 86_400_000L, name = "과테말라 옐로우 허니 테스트", country = "과테말라", process = "허니(옐로우 허니)"))
        repo.upsert(Entry(id = "h2", createdAt = now - 4 * 86_400_000L, category = Category.CAFE, name = "코스타리카 카페 레드 허니", cafeName = "허니 테스트 카페", country = "코스타리카", process = "Red Honey"))
        repo.upsert(Entry(id = "h3", createdAt = now - 3 * 86_400_000L, name = "브라질 블랙 테스트", process = "허니(블랙)"))
        repo.upsert(
            Entry(
                id = "h4", createdAt = now - 2 * 86_400_000L, category = Category.CUPPING, name = "허니 커핑 테스트", cuppingType = CuppingType.PUBLIC,
                cuppingPlace = "허니 커핑 테스트", cuppingBeans = listOf(CuppingBean(name = "엘살바도르 허니 테스트", process = "허니")),
            )
        )
        repo.upsert(Entry(id = "w1", createdAt = now - 86_400_000L, name = "워시드 대조군", process = "워시드"))
    }

    @Test
    fun cov08_honeyProcess_subtypesFromTheFormAndOldRecords_listAndOpenRecords() {
        seedHoneyRecords()
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "코스타리카 레드 허니 테스트")
        clickNode(button("허니"), 0)
        typeInto("세부 종류 (선택, 예: 드래곤 아이, 더블 퍼멘티드)", "레드 허니")
        saveForm("코스타리카 레드 허니 테스트")
        assertEquals("허니(레드 허니)", entries().single { it.name == "코스타리카 레드 허니 테스트" }.process)
        assertTrue("detail 가공 row", has(hasText("허니(레드 허니)")))

        back()
        tab("tab-bean")
        clickText("가공 방식")
        clickNode(clickableWith("Honey"))
        waitForText("허니 세부 종류")
        // web: subtypes by count, then 가나다; "허니(블랙)" keeps its parenthesis text, a bare 허니 is 세부 미기록
        val rows = listOf("레드 허니" to "2번 ›", "블랙" to "1번 ›", "세부 미기록" to "1번 ›", "옐로 허니" to "1번 ›")
        rows.forEach { (sub, n) -> waitFor(hasText(sub) and hasText(n) and hasClickAction(), "row $sub $n") }
        assertTopToBottom("honey subtype order", *rows.map { (sub, _) -> hasText(sub) and hasClickAction() }.toTypedArray())
        assertEquals("the washed record is not a honey subtype", 4, count(hasText("번 ›", substring = true) and hasClickAction()))

        clickNode(hasText("레드 허니") and hasClickAction())
        waitForText("레드 허니로 마신 기록")
        waitFor(hasText("코스타리카 레드 허니 테스트"))
        waitFor(hasText("코스타리카 카페 레드 허니"))
        waitForText("카페 · 허니 테스트 카페 · 코스타리카") // web meta: category · place · country
        assertFalse(has(hasText("과테말라 옐로우 허니 테스트")))
        clickNode(hasText("코스타리카 레드 허니 테스트") and hasClickAction())
        waitUntil("record detail") { onDetailOf("코스타리카 레드 허니 테스트") }
        back()
        waitForText("레드 허니로 마신 기록")
        clickNode(hasText("옐로 허니") and hasClickAction())
        waitForText("옐로 허니로 마신 기록")
        waitFor(hasText("과테말라 옐로우 허니 테스트"))
    }

    /**
     * F3 beanA-1: a cupping that tasted the same honey bean twice gives two records with the same LazyColumn key
     * (entryId|name|createdAt), which crashes the honey detail as soon as that subtype is opened.
     */
    @Test
    fun cov09_honeyDetail_sameBeanTwiceInOneCupping_doesNotCrash() {
        runBlocking {
            koinGet<EntryRepository>().upsert(
                Entry(
                    id = "dup", createdAt = Dates.nowMillis() - 86_400_000L, category = Category.CUPPING, name = "허니 비교 커핑", cuppingPlace = "허니 비교 커핑",
                    cuppingBeans = listOf(CuppingBean(name = "파나마 레드 허니 로트", process = "허니(레드 허니)", roast = "라이트"), CuppingBean(name = "파나마 레드 허니 로트", process = "허니(레드 허니)", roast = "미디엄")),
                )
            )
        }
        launchApp()
        tab("tab-bean")
        clickText("가공 방식")
        clickNode(clickableWith("Honey"))
        waitForText("허니 세부 종류")
        clickNode(hasText("레드 허니") and hasText("2번 ›") and hasClickAction())
        waitForText("레드 허니로 마신 기록")
        assertEquals("both tastings are listed", 2, count(hasText("파나마 레드 허니 로트") and hasClickAction()))
    }
}
