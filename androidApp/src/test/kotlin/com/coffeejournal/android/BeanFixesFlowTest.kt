package com.coffeejournal.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.bean.BeanViewRequests
import com.coffeejournal.ui.bean.BeanViews
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.nav.appGraph
import com.coffeejournal.ui.theme.CoffeeJournalTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Audit fixes on the 원두 tab: honey detail keys, sub-tab row, layouts, map colours / taps / zoom, farm jump, blend form. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class BeanFixesFlowTest : FlowTestBase() {

    private val density get() = context.resources.displayMetrics.density

    private fun addEntries(vararg e: Entry) = runBlocking { e.forEach { koinGet<EntryRepository>().upsert(it) } }
    private fun addMisc(items: List<MiscItem>) = runBlocking { koinGet<MiscRepository>().upsertAll(items) }

    private fun openBeanTab() {
        launchApp()
        tab("tab-bean")
        waitForText("Coffee Map")
    }

    // ───────────── beanA-1: two same-name beans of one cupping session in one honey subtype ─────────────

    @Test
    fun beanA1_honeyDetail_sameNameBeansOfOneSession_doNotCrash() {
        val sample = CuppingBean(name = "에티오피아 구지", country = "에티오피아", process = "레드 허니")
        addEntries(
            Entry(
                id = "cup1", createdAt = Dates.nowMillis(), category = Category.CUPPING, name = "허니 비교 커핑", cuppingPlace = "홈",
                cuppingBeans = listOf(sample, sample.copy(roast = "라이트")),
            )
        )
        openBeanTab()
        clickText("가공 방식")
        clickText("허니")
        waitForText("허니 세부 종류")
        clickText("레드 허니") // both rows share entryId, name and createdAt: the old key crashed here
        waitForText("레드 허니로 마신 기록")
        waitUntil("both samples listed") { count(hasText("에티오피아 구지") and hasClickAction()) == 2 }
    }

    // ───────────── beanA-3 / design-7: the selected sub tab is on screen ─────────────

    private fun chipFullyOnScreen(label: String): Boolean {
        val n = node(hasText(label) and hasClickAction()).fetchSemanticsNode()
        val selected = n.config.getOrNull(SemanticsProperties.Selected) == true
        // the row clips its scrolled-out part: a chip on screen keeps its whole width
        return selected && n.boundsInRoot.width > 0f && n.boundsInRoot.width >= n.size.width - 1f
    }

    @Test
    fun beanA3_selectedSubTabIsScrolledIntoView_onOpenAndOnRequests() {
        SampleData.seed()
        openBeanTab()
        waitUntil("the default 커피 지도 chip (8th of 9) is on screen and selected") { chipFullyOnScreen("커피 지도 + 농장(생산자)") }

        val requests = koinGet<BeanViewRequests>()
        compose.runOnUiThread { requests.request(BeanViews.NOTES) }
        waitForText("플레이버 휠")
        waitUntil("커피 노트 scrolled back into view") { chipFullyOnScreen("커피 노트") }

        compose.runOnUiThread { requests.request(BeanViews.SPECIALTY) }
        waitFor(hasText("대회 컵 점수를 기록한", substring = true))
        waitUntil("✦ Competition Lots scrolled into view") { chipFullyOnScreen("✦ Competition Lots") }
    }

    // ───────────── beanA-4: long names do not squeeze the category badge ─────────────

    private fun assertBadgesOnOneLine() {
        val badges = compose.onAllNodes(hasText("직접 내림"), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("a badge is shown", badges.isNotEmpty())
        badges.forEach { b ->
            val w = b.size.width / density; val h = b.size.height / density
            assertTrue("badge keeps one line (${w}x$h dp)", h < 26f && w > 40f)
        }
    }

    @Test
    fun beanA4_longBeanNames_keepTheBadgeReadable() {
        val long = "에티오피아 예가체프 워카 첼베사 내추럴 아나에어로빅 스페셜 마이크로랏"
        addEntries(Entry(id = "long", createdAt = Dates.nowMillis(), category = Category.BEAN, name = long, process = "워시드", actualNotes = "자스민"))
        openBeanTab()
        clickText("커피 노트")
        clickText("내가 느낀 노트")
        val chip = hasText("자스민 1") and hasClickAction()
        scrollListTo(hasText("플레이버 휠"), chip)
        clickNode(chip)
        waitForText("원래 기록 보기")
        assertBadgesOnOneLine() // NoteContextCard
        back()
        clickText("가공 방식")
        typeInto("가공 방식 검색 · 예: 레드 허니, 더블 퍼멘티드", "워시드")
        waitFor(hasText(long) and hasClickAction(), "search result row")
        assertBadgesOnOneLine() // RecordLine
    }

    // ───────────── beanA-5: 수정 on a lower process card shows the form ─────────────

    @Test
    fun beanA5_editOnALowerProcessCard_bringsTheFormIntoViewAndFocusesIt() {
        val t = Dates.nowMillis()
        addMisc((1..8).map { i -> MiscItem(id = "pr$i", type = MiscType.PROCESS, name = "테스트 가공 $i", notes = "메모 $i: 어떤 원두에서 봤는지 길게 적어 둔 설명이에요.", createdAt = t + i) })
        openBeanTab()
        clickText("가공 방식")
        waitForText("테스트 가공 8")
        val edits = count(button("수정"))
        assertEquals(8, edits)
        clickText("수정", index = edits - 1) // the oldest card, at the bottom of the list
        val nameField = field("테스트 가공 1")
        waitFor(nameField)
        waitUntil("the edit form is on screen") { runCatching { node(nameField).assertIsDisplayed() }.isSuccess }
        node(nameField).assertIsFocused()
    }

    // ───────────── beanA-6: no empty-state flash before the records load ─────────────

    /** Composes [route] with the frame clock paused: the screen stays on its very first frame (repositories not yet emitted). */
    private fun firstFrameOf(route: Route) {
        SampleData.seed()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CoffeeJournalTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = route) { appGraph(nav) }
            }
        }
        compose.waitForIdle()
    }

    private fun resumeFrames() {
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun beanA6_noteDetail_firstFrameHasNoEmptyCopy() {
        firstFrameOf(Route.NoteDetail(kind = "actual", noteKey = "자스민"))
        assertTrue("the screen is composed", has(hasContentDescription("뒤로")))
        assertFalse(has(hasText("이 노트로 기록된 원두가 아직 없어요.")))
        assertFalse(has(hasText("노트 조합")))
        resumeFrames()
        waitForText("“자스민”와 함께 기록된 노트 조합")
    }

    @Test
    fun beanA6_processDetail_firstFrameHasNoEmptyCopy() {
        firstFrameOf(Route.ProcessDetail("워시드", "워시드"))
        assertTrue("the title is there", has(hasText("워시드")))
        assertFalse(has(hasText("아직 이 가공방식으로 기록한 원두가 없어요", substring = true)))
        resumeFrames()
        waitForText("국가별로 마셔본 횟수")
    }

    @Test
    fun beanA6_varietyDetail_firstFrameHasNoEmptyCopy() {
        firstFrameOf(Route.VarietyDetail("geisha")) // the key the seeded "Geisha" records are indexed under
        assertTrue("the screen is composed", has(hasContentDescription("뒤로")))
        assertFalse(has(hasText("0 cups")))
        assertFalse(has(hasText("아직 이 품종으로 마신 기록이 없어요.")))
        resumeFrames()
        waitForText("2 cups")
    }

    @Test
    fun beanA6_beanTab_firstFrameHasNoEmptyCopy() {
        firstFrameOf(Route.Bean)
        assertTrue("the sub tabs are there", has(hasText("커피 지도 + 농장(생산자)") and hasClickAction()))
        assertFalse(has(hasText("기록에 \"국가\"를 적어두면", substring = true)))
        resumeFrames()
        waitFor(hasText("지금까지 총", substring = true), "the map summary once loaded")
    }

    // ───────────── beanA-8: cards in one grid row share their height ─────────────

    @Test
    fun beanA8_gridCardsInOneRowShareTheirHeight() {
        SampleData.seed()
        addEntries(Entry(id = "honey", createdAt = Dates.nowMillis(), category = Category.BEAN, name = "코스타리카 허니", process = "허니"))
        openBeanTab()
        clickText("커피 노트")
        fun height(name: String) = node(hasText(name) and hasClickAction()).fetchSemanticsNode().size.height
        scrollListTo(hasText("플레이버 휠"), hasText("풋내·식물 Green/Vegetative") and hasClickAction())
        assertEquals(height("단맛 Sweet"), height("견과·코코아 Nutty/Cocoa"))
        assertEquals(height("발효·신맛 Sour/Fermented"), height("풋내·식물 Green/Vegetative"))
        clickText("가공 방식")
        waitForText("가공 방식 종류")
        assertEquals("허니 (✓ 마셔봄) next to 아나로빅", height("허니"), height("아나로빅"))
    }

    // ───────────── beanB-2: region names in the country panel ─────────────

    @Test
    fun beanB2_countryPanel_regionNamesOpenTheirPanel() {
        SampleData.seed()
        openBeanTab()
        clickText("🇪🇹 에티오피아") // the accordion also selects the country on the map
        val guji = button("Guji")
        waitFor(guji)
        node(guji).assertHeightIsAtLeast(40.dp)
        clickNode(guji)
        waitForText("에티오피아 · Guji")
        waitForText("이 지역으로 기록한 원두는 아직 없어요")
    }

    // ───────────── beanB-4: a farm in the map panel scrolls to its own card ─────────────

    @Test
    fun beanB4_farmRowInTheMapPanel_scrollsToThatFarmsCard() {
        SampleData.seed()
        val t = Dates.nowMillis()
        // newer farms come first, so 워카 첼베사(SNAP) is the 7th card
        addMisc((1..6).map { i -> MiscItem(id = "farm$i", type = MiscType.FARM, name = "테스트 농장 $i", notes = "메모 $i: 방문했던 농장, 가공 방식과 품종을 길게 적어 둔 설명이에요.", createdAt = t + i) })
        openBeanTab()
        clickText("🇪🇹 에티오피아")
        clickText("워카 첼베사(SNAP)")
        val card = hasText("워카 첼베사(SNAP)") and !hasClickAction()
        waitUntil("the farm's own card is on screen") { runCatching { node(card).assertIsDisplayed() }.isSuccess }
    }

    // ───────────── beanB-5: "어디서 마셨는지" with badges, no extra places line ─────────────

    @Test
    fun beanB5_roasteryAndImporterCards_showTheWebBreakdown() {
        SampleData.seed()
        openBeanTab()
        clickText("로스터리")
        waitForText("여기서 산 원두")
        assertEquals(1, count(hasText("어디서 마셨는지")))
        assertTrue("[직접 내림] 1종", has(hasText("1종")))
        assertFalse("no 'places' line", has(hasText("집 추출")))
        clickText("생두 수입사")
        waitForText("국가별로 마셔본 횟수")
        assertEquals(1, count(hasText("어디서 마셨는지")))
        assertFalse(has(hasText("집 추출")))
    }

    // ───────────── beanB-7 / platform-10: blend grams and the row ✕ ─────────────

    @Test
    fun beanB7_blendGrams_takeOnlyNumbers_andTheRowRemoveIsLabelled() {
        launchApp()
        tab("tab-bean")
        clickText("블렌드")
        clickText("+ 블렌드 기록 추가")
        typeInto("그램(g)", "10,5")
        waitFor(field("10.5"), "comma read as the decimal point")
        node(field("10.5")).performTextInput("g")
        settle(1)
        assertTrue(has(field("10.5")))
        assertFalse(has(field("10.5g")))
        compose.onAllNodes(hasContentDescription("원두 행 삭제") and hasClickAction()).onFirst()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    // ───────────── beanB-8: touch targets on the map and roastery views ─────────────

    @Test
    fun beanB8_smallMapAndRoasteryControls_haveTouchTargets() {
        SampleData.seed()
        openBeanTab()
        val untried = hasText("○", substring = true) and hasClickAction()
        waitFor(untried)
        node(untried).performScrollTo().assertHeightIsAtLeast(40.dp)
        clickText("🇰🇪 케냐")
        val visit = hasText("커피플랜트 성수", substring = true) and hasClickAction()
        waitFor(visit)
        node(visit).assertHeightIsAtLeast(40.dp)
        clickText("🇪🇹 에티오피아")
        node(button("워카 첼베사(SNAP)")).assertHeightIsAtLeast(40.dp)

        clickText("로스터리")
        val star = hasContentDescription("즐겨찾기 해제") and hasClickAction()
        waitFor(star)
        node(star).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        node(hasText("커피 리브레") and hasClickAction()).assertHeightIsAtLeast(44.dp) // the pin
    }

    // ───────────── beanB-9: several countries stay open ─────────────

    @Test
    fun beanB9_severalCountriesStayOpen() {
        SampleData.seed()
        openBeanTab()
        val ethiopiaRecord = hasText("에티오피아 예가체프 워카 첼베사") and hasClickAction()
        val colombiaRecord = hasText("콜롬비아 라 플라타 게이샤 워시드") and hasClickAction()
        clickText("🇪🇹 에티오피아")
        waitFor(ethiopiaRecord)
        clickText("🇨🇴 콜롬비아")
        waitFor(colombiaRecord)
        assertTrue("에티오피아 is still open", has(ethiopiaRecord))
        clickText("🇪🇹 에티오피아")
        waitGone(ethiopiaRecord)
        assertTrue("콜롬비아 stays open", has(colombiaRecord))
    }

    // ───────────── design-17: roastery bean names are not squeezed ─────────────

    @Test
    fun design17_roasteryCard_beanNameKeepsItsWidth() {
        SampleData.seed()
        openBeanTab()
        clickText("로스터리")
        waitForText("여기서 산 원두")
        val name = node(hasText("에티오피아 예가체프 워카 첼베사")).fetchSemanticsNode()
        assertTrue("name ${name.size.width / density}dp wide (was about 110dp)", name.size.width / density > 150f)
        val range = node(hasText("2026.9.21 ~ 2026.9.23")).fetchSemanticsNode()
        assertTrue("range on one line (${range.size.height / density}dp)", range.size.height / density < 20f)
    }

    // ───────────── platform-10: labelled 48dp glyph buttons ─────────────

    @Test
    fun platform10_searchClears_areLabelled48dpButtons() {
        SampleData.seed()
        launchApp()
        typeInto("예: 벤사, 게이샤, 리브레", "게이샤")
        val clearHome = hasContentDescription("검색어 지우기") and hasClickAction()
        waitFor(clearHome)
        node(clearHome).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        clickNode(clearHome)
        waitGone(field("게이샤"))

        tab("tab-bean")
        clickText("가공 방식")
        typeInto("가공 방식 검색 · 예: 레드 허니, 더블 퍼멘티드", "워시드")
        val clearBean = hasContentDescription("지우기") and hasClickAction()
        waitFor(clearBean)
        node(clearBean).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        clickNode(clearBean)
        waitGone(field("워시드"))
    }
}
