package com.coffeejournal.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.BeanMetaRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MyRecipeRepository
import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.nav.FormMode
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.nav.appGraph
import com.coffeejournal.ui.theme.CoffeeJournalTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record form / detail / my-recipes routes opened directly: best-recipe keys, section order, touch targets, column alignment. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FormRouteTest {
    @get:Rule val compose = createComposeRule()

    private inline fun <reified T : Any> koinGet(): T = GlobalContext.get().get()

    @Before
    fun setUp() {
        startTestKoin(ApplicationProvider.getApplicationContext())
        SampleData.seed()
    }

    @After
    fun tearDown() = stopKoin()

    private fun settle() { repeat(4) { Thread.sleep(120); compose.waitForIdle() } }

    private fun show(route: Route) {
        compose.setContent {
            CoffeeJournalTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = route) { appGraph(nav) }
            }
        }
        settle()
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!runCatching(condition).getOrDefault(false)) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for: $what")
            Thread.sleep(60)
            compose.waitForIdle()
        }
    }

    private fun has(m: SemanticsMatcher) = compose.onAllNodes(m).fetchSemanticsNodes().isNotEmpty()
    private fun button(text: String) = hasText(text) and hasClickAction()
    private fun best() = runBlocking { koinGet<BeanMetaRepository>().getBest().associate { it.beanKey to it.entryId } }

    // ───────────── data-5: a custom blend's best recipe uses the home group keys ─────────────

    private fun seedBlend() = runBlocking {
        koinGet<EntryRepository>().upsert(
            Entry(
                id = "blendE", createdAt = Dates.nowMillis(), name = "아침 블렌드", beanMode = BeanMode.CUSTOM_BLEND, dose = "15",
                blendComponents = listOf(BlendComponent("에티오피아 예가체프 워카 첼베사", "10"), BlendComponent("콜롬비아 라 플라타 게이샤 워시드", "5")),
            )
        )
    }

    @Test
    fun data5_customBlendBest_isStoredUnderEachComponentGroup() {
        seedBlend()
        show(Route.EntryDetail("blendE"))
        compose.onNode(button("이 원두의 베스트 레시피로 지정")).performScrollTo().performClick()
        waitUntil("best stored") { best()["콜롬비아 라 플라타 게이샤 워시드"] == "blendE" }
        assertEquals("every component group shows it", "blendE", best()["에티오피아 예가체프 워카 첼베사"])
        assertTrue("no orphan key for the blend's own name", best().keys.none { it.contains("블렌드") })
        waitUntil("badge") { has(hasText("⭐ 베스트")) }

        compose.onNode(button("⭐ 베스트 레시피 해제")).performScrollTo().performClick()
        waitUntil("cleared") { best().values.none { it == "blendE" } }
    }

    @Test
    fun data5_bestChosenInAComponentGroup_isShownOnTheBlendDetail() {
        seedBlend()
        runBlocking { koinGet<BeanMetaRepository>().setBest("콜롬비아 라 플라타 게이샤 워시드", "blendE") }
        show(Route.EntryDetail("blendE"))
        waitUntil("badge from the group picker's key") { has(hasText("⭐ 베스트")) && has(button("⭐ 베스트 레시피 해제")) }
        compose.onNode(button("⭐ 베스트 레시피 해제")).performScrollTo().performClick()
        waitUntil("cleared") { best()["콜롬비아 라 플라타 게이샤 워시드"] == null }
        assertEquals("another record's best is left alone", "e2", best()["에티오피아 예가체프 워카 첼베사"])
    }

    // ───────────── form-11: FINAL EVALUATION comes first ─────────────

    @Test
    fun form11_finalEvaluationAboveTheInfoGrid() {
        show(Route.EntryDetail("e5"))
        val final = compose.onNode(hasText("FINAL EVALUATION")).getBoundsInRoot()
        val info = compose.onNode(hasText("정보")).getBoundsInRoot()
        assertTrue("FINAL EVALUATION (${final.top}) is above 정보 (${info.top})", final.top < info.top)
        assertTrue("the rest of the notes stays under 전체적인 경험", has(hasText("전체적인 경험")))
    }

    // ───────────── form-18: small controls have 48 dp touch targets ─────────────

    @Test
    fun form18_formControlsHave48dpTouchTargets() {
        runBlocking { koinGet<MyRecipeRepository>().upsert(MyRecipe(id = "r1", name = "테스트 레시피", dose = "15", createdAt = Dates.nowMillis())) }
        show(Route.RecordForm(mode = FormMode.EXTRACT))
        compose.onAllNodes(hasText("✕") and hasClickAction()).onFirst().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onAllNodes(hasContentDescription("붓기 단계 여부") and hasClickAction()).onFirst().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)

        compose.onNode(button("🏆 챔피언 레시피")).performClick()
        settle()
        compose.onAllNodes(button("이 비율 적용 →")).onFirst().assertHeightIsAtLeast(48.dp)
        compose.onNode(button("🏆 챔피언 레시피")).performClick()
        compose.onNode(button("⭐ 내 레시피")).performClick()
        settle()
        val apply = compose.onNode(button("이 레시피 적용 →")).assertHeightIsAtLeast(48.dp).getBoundsInRoot()
        val delete = compose.onNode(button("삭제")).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).getBoundsInRoot()
        assertTrue("삭제 is kept apart from 이 레시피 적용 →", delete.left - apply.right >= 16.dp)
    }

    @Test
    fun form18_lockBannerCloseHas48dpTouchTarget() {
        show(Route.RecordForm(mode = FormMode.EXTRACT, entryId = "e2")) // a repeat of e1's bean
        waitUntil("lock banner") { has(button("닫기")) }
        compose.onNode(button("닫기")).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    // ───────────── form-16: step-log headers sit over their inputs ─────────────

    @Test
    fun form16_stepLogHeadersLineUpWithTheInputs() {
        show(Route.RecordForm(mode = FormMode.EXTRACT))
        // scroll the first editable row into view; the header sits right above it (off-screen nodes report empty bounds)
        val toggle = compose.onAllNodes(hasContentDescription("붓기 단계 여부") and hasClickAction()).onFirst().performScrollTo().getBoundsInRoot()
        val header = compose.onNode(hasText("이번 물량(g)")).getBoundsInRoot()
        // [0] is the read-only reference table above, [1] the editable log's header
        val waitHeader = compose.onAllNodes(hasText("대기(초)"))[1].getBoundsInRoot()
        val waitField = compose.onAllNodes(hasText("10") and androidx.compose.ui.test.hasSetTextAction()).onFirst().getBoundsInRoot()
        assertTrue("'이번 물량(g)' starts at the pour column (${header.left} vs ${toggle.left})", header.left >= toggle.left && header.left < toggle.right)
        assertTrue("'대기(초)' starts over the wait input (${waitHeader.left} vs ${waitField.left})", waitHeader.left >= waitField.left && waitHeader.left < waitField.right)
        assertTrue("no '메모' column header over the water input", compose.onAllNodes(hasText("메모")).fetchSemanticsNodes().size <= 1)
    }
}
