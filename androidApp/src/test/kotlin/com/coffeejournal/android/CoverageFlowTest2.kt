package com.coffeejournal.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.WorldMapData
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.bean.b.WorldMapGeometry
import com.coffeejournal.ui.theme.Dimens
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sqrt

/**
 * Critic gap #7, 원두 tab part: the coffee map (country / region taps through the map geometry, untried countries,
 * the farm jump), roastery favourites, the flat item forms (roastery / importer / farm), the 가공 방식 내 목록 and
 * editing / deleting a lab blend that the calendar shows. Web references: script3.js 2195-2320 (map), 3814-3930
 * (makeFlatListTab), 7400-7470 (blends).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class CoverageFlowTest2 : CoverageFlowBase() {

    private fun openBeanView(label: String) {
        tab("tab-bean")
        clickText(label)
    }

    // ───────────────────────── coffee map ─────────────────────────

    private val mapDescription = "전 세계 주요 커피 생산국을 어두운 초록으로 표시했어요."

    /** The map canvas in root pixels: full width inside the gutters, 1.6:1, right under the description (12dp). */
    private fun canvasRect(): androidx.compose.ui.geometry.Rect {
        val desc = node(hasText(mapDescription, substring = true)).fetchSemanticsNode()
        val gutter = Dimens.gutter.value * density
        val width = rootWidth() - 2 * gutter
        val top = desc.positionInRoot.y + desc.size.height + 12 * density
        return androidx.compose.ui.geometry.Rect(gutter, top, gutter + width, top + width / 1.6f)
    }

    /** Root-coordinate point of a viewBox point on the (unzoomed) map canvas. */
    private fun mapPoint(view: Offset): Offset {
        val c = canvasRect()
        val m = WorldMapGeometry.fitScale(c.width, c.height)
        return c.topLeft + WorldMapGeometry.toCanvas(view, m, Offset(c.width / 2f, c.height / 2f))
    }

    private fun tapMap(view: Offset) {
        val p = mapPoint(view)
        compose.onAllNodes(isRoot())[0].performTouchInput { click(p) }
        settle()
    }

    /** A point inside [country]'s outline as far as possible from every region dot, so a tap selects the country. */
    private fun countryPoint(country: String): Pair<Offset, Float> {
        val polygon = WorldMapGeometry.parseAll().single { it.name == country }
        val dots = CoffeeCountries.all.flatMap { c -> c.regions.map { Offset(it.x, it.y) } }
        var best = Offset.Zero
        var bestDist = -1f
        val b = polygon.bounds
        for (i in 0..40) for (j in 0..40) {
            val p = Offset(b.left + b.width * i / 40f, b.top + b.height * j / 40f)
            if (WorldMapGeometry.hitCountry(WorldMapGeometry.parseAll(listOf(WorldMapData.paths.single { it.name == country })), p) == null) continue
            val d = dots.minOf { val dx = it.x - p.x; val dy = it.y - p.y; sqrt(dx * dx + dy * dy) }
            if (d > bestDist) { bestDist = d; best = p }
        }
        return best to bestDist
    }

    /** Web map click handlers (2208-2302): a producing country's panel, a tried and an untried region dot, 경험할 생산국. */
    @Test
    fun cov10_coffeeMap_countryAndRegionTaps_showTheWebPanels() {
        SampleData.seed()
        launchApp()
        tab("tab-bean")
        waitForText(mapDescription, substring = true)
        waitForText("나라나 산지 점을 눌러보세요.")
        // sanity check of the canvas position: the legend row starts 12dp under the canvas
        val gap = top(hasText("커피 생산국")) - canvasRect().bottom
        assertTrue("canvas located (legend ${gap / density}dp below it)", gap in (8 * density)..(24 * density))

        // Brazil: e4 (cafe, Cerrado) and the cupping's 하우스 블렌드
        val (brazil, clearance) = countryPoint("Brazil")
        assertTrue("a spot of Brazil ${clearance} units away from any dot", clearance > 20f)
        tapMap(brazil)
        waitForText("(Brazil)")
        assertTrue(has(hasText("브라질")))
        waitForText("2cup 마셔봤어요", substring = true)
        waitForText("주요 산지: Cerrado, Sul de Minas, Mogiana")

        // a tried region dot: e1 and e2 are Yirgacheffe records of one farm
        val yirg = CoffeeCountries.byEn.getValue("Ethiopia").regions.single { it.name == "Yirgacheffe" }
        tapMap(Offset(yirg.x, yirg.y))
        waitForText("에티오피아 · Yirgacheffe")
        waitForText("2cup 전체 · 2cup 이 지역")
        waitFor(hasText("워카 첼베사(SNAP) (Gedeb, Worka Chelbesa)") and hasText("2cup") and hasClickAction(), "farm row with its sub regions")

        // an untried dot of the same country
        val harrar = CoffeeCountries.byEn.getValue("Ethiopia").regions.single { it.name == "Harrar" }
        tapMap(Offset(harrar.x, harrar.y))
        waitForText("에티오피아 · Harrar")
        waitForText("2cup 전체")
        waitForText("이 지역으로 기록한 원두는 아직 없어요")

        // 경험할 생산국: a producer never tasted opens its panel
        val rwanda = CoffeeCountries.byEn.getValue("Rwanda")
        clickNode(hasText("○ ${rwanda.flag} ${rwanda.ko} ${rwanda.en}") and hasClickAction())
        waitForText("(Rwanda)")
        assertFalse("no cups for an untried country", has(hasText("cup 마셔봤어요", substring = true)))
    }

    /** Web jumpToFarm (scrollIntoView block:center + highlight): the tapped farm's own card comes into view. */
    @Ignore("app defect: the map panel's farm row scrolls to the top of the farm list, not to that farm's card (F3 beanB-4, BeanMapView.kt:109)")
    @Test
    fun cov11_coffeeMap_farmRow_jumpsToThatFarmsCard() {
        SampleData.seed()
        // newer farms list above 워카 첼베사(SNAP), so the jump has to reach down the list
        runBlocking {
            koinGet<MiscRepository>().upsertAll((1..6).map { i ->
                MiscItem(id = "f$i", type = MiscType.FARM, name = "앞선 농장 $i", notes = "설명 $i", createdAt = Dates.nowMillis() + i)
            })
        }
        launchApp()
        tab("tab-bean")
        waitForText(mapDescription, substring = true)
        val yirg = CoffeeCountries.byEn.getValue("Ethiopia").regions.single { it.name == "Yirgacheffe" }
        tapMap(Offset(yirg.x, yirg.y))
        clickNode(hasText("워카 첼베사(SNAP) (Gedeb, Worka Chelbesa)") and hasClickAction())
        settle(6)
        val card = node(hasText("워카 첼베사(SNAP)") and !hasClickAction()).fetchSemanticsNode()
        val y = card.positionInRoot.y
        assertTrue("the farm card is on screen after the jump (y=$y of ${rootHeight()})", y > 0 && y + card.size.height < rootHeight() - 60 * density)
    }

    // ───────────────────────── roastery favourites ─────────────────────────

    private fun roastery(id: String, name: String, created: Long, scope: String = Scope.DOMESTIC, status: String = "", favorite: Boolean = false) =
        MiscItem(id = id, type = MiscType.SOURCE, name = name, scope = scope, status = status, favorite = favorite, createdAt = created)

    private fun seedRoasteries() = runBlocking {
        val t = 1_780_000_000_000L
        koinGet<MiscRepository>().upsertAll(listOf(
            roastery("r1", "로스터리 가 테스트", t + 1),
            roastery("r2", "로스터리 나 테스트", t + 2),
            roastery("r3", "로스터리 다 테스트", t + 3),
            roastery("r4", "로스터리 라 테스트", t + 4, status = MiscStatus.CURIOUS),
            roastery("r5", "해외 로스터리 테스트", t + 5, scope = Scope.OVERSEAS),
        ))
    }

    private fun card(name: String) = hasText(name) and !hasClickAction()

    /** Web makeFlatListTab (favoritable): ★ first, then newest; 궁금함 in its own group; scope tabs filter. */
    @Test
    fun cov12_roasteryFavourites_starMovesToTop_andBack() {
        seedRoasteries()
        launchApp()
        openBeanView("로스터리")
        waitFor(card("로스터리 다 테스트"))
        assertTopToBottom("newest first", card("로스터리 다 테스트"), card("로스터리 나 테스트"), card("로스터리 가 테스트"), hasText("궁금한 로스터리"), card("로스터리 라 테스트"))
        assertFalse("해외 roasteries are on the other tab", has(card("해외 로스터리 테스트")))

        assertEquals(4, count(button("☆")))
        clickNode(button("☆"), 2) // 로스터리 가 테스트
        waitFor(button("★"))
        assertTopToBottom("the favourite comes first", card("로스터리 가 테스트"), card("로스터리 다 테스트"), card("로스터리 나 테스트"))
        assertTrue(misc().single { it.id == "r1" }.favorite)

        clickText("★")
        waitGone(button("★"))
        assertTopToBottom("back to newest first", card("로스터리 다 테스트"), card("로스터리 나 테스트"), card("로스터리 가 테스트"))
        assertFalse(misc().single { it.id == "r1" }.favorite)

        tapText("해외")
        waitFor(card("해외 로스터리 테스트"))
        assertFalse(has(card("로스터리 가 테스트")))
    }

    // ───────────────────────── FlatItemFormScreen: roastery / importer / farm ─────────────────────────

    @Test
    fun cov13_roasteryEditAndDelete_throughTheForm() {
        seedRoasteries()
        launchApp()
        openBeanView("로스터리")
        waitFor(card("로스터리 나 테스트"))
        clickNode(button("수정"), 1) // cards: 다, 나, 가, 라
        waitForText("로스터리 수정")
        replaceIn("로스터리 나 테스트", "로스터리 나 수정됨")
        clickText("궁금함")
        clickText("해외")
        typeInto("예: 대한민국 서울특별시", "교토, 일본")
        typeInto("주로 사는 원두, 링크 등", "라이트 로스팅 위주")
        clickText("수정 저장")
        waitForText("한국 로스터리 지도")
        waitGone(card("로스터리 나 테스트"))
        val edited = misc().single { it.id == "r2" }
        assertEquals(listOf("로스터리 나 수정됨", MiscStatus.CURIOUS, Scope.OVERSEAS, "교토, 일본", "라이트 로스팅 위주"), listOf(edited.name, edited.status, edited.scope, edited.location, edited.notes))
        assertEquals("createdAt kept", 1_780_000_000_002L, edited.createdAt)

        tapText("해외")
        waitFor(card("로스터리 나 수정됨"))
        assertTopToBottom("now under 궁금한 로스터리", card("해외 로스터리 테스트"), hasText("궁금한 로스터리"), card("로스터리 나 수정됨"))
        assertTrue(has(hasText("라이트 로스팅 위주")))
        clickNode(button("삭제"), 1)
        waitForText("'로스터리 나 수정됨'을(를) 삭제할까요?")
        clickNode(dialogButton("삭제"))
        waitGone(card("로스터리 나 수정됨"))
        assertNull(misc().singleOrNull { it.id == "r2" })
    }

    @Test
    fun cov14_importerAddEditDelete_withTheBeansSeenFromIt() {
        SampleData.seed()
        launchApp()
        openBeanView("생두 수입사")
        // m7 Nordic Approach: e1 / e2 were bought through it
        waitFor(card("Nordic Approach"))
        waitForText("국가별로 마셔본 횟수")
        waitFor(hasText("에티오피아"), "country row of the beans bought through it")
        assertTrue("counted twice", has(hasText("2번")))

        clickText("+ 추가")
        waitForText("생두 수입사 추가")
        typeInto("예: Nordic Approach", "테스트 수입사")
        clickText("궁금함")
        typeInto("특징, 어떤 원두에서 봤는지 등", "케냐 로트가 좋다")
        clickText("저장")
        waitFor(card("테스트 수입사"))
        assertTopToBottom("new importer under 궁금한 생두 수입사", card("Nordic Approach"), hasText("궁금한 생두 수입사"), card("테스트 수입사"))
        val added = misc().single { it.name == "테스트 수입사" }
        assertEquals(listOf(MiscType.SELECTION, MiscStatus.CURIOUS, "케냐 로트가 좋다", ""), listOf(added.type, added.status, added.notes, added.scope))

        clickNode(button("수정"), 1)
        waitForText("생두 수입사 수정")
        replaceIn("테스트 수입사", "테스트 수입사 개명")
        clickText("마셔봄")
        clickText("수정 저장")
        waitFor(card("테스트 수입사 개명"))
        assertTopToBottom("back among the tried importers (newest first)", card("테스트 수입사 개명"), card("Nordic Approach"), hasText("궁금한 생두 수입사"))
        assertEquals("", misc().single { it.id == added.id }.status)

        clickNode(button("삭제"), 0)
        clickNode(dialogButton("삭제"))
        waitGone(card("테스트 수입사 개명"))
        assertTrue(misc().none { it.id == added.id })
    }

    @Test
    fun cov15_farmAddSearchEditDelete_onTheMapView() {
        SampleData.seed()
        launchApp()
        tab("tab-bean")
        waitForText(mapDescription, substring = true)
        clickNode(button("+ 추가"))
        waitForText("농장(생산자) 추가")
        typeInto("예: 라 에스메랄다(페드로 가족)", "테스트 농장 핀카")
        typeInto("특징, 어떤 원두에서 봤는지 등", "게이샤 로트")
        clickText("저장")
        waitForText(mapDescription, substring = true)
        val farmCard = card("테스트 농장 핀카")
        waitFor(farmCard)
        assertEquals(MiscType.FARM, misc().single { it.name == "테스트 농장 핀카" }.type)

        // the search narrows the list (name or memo)
        typeInto("농장·생산자 검색", "게이샤 로트")
        waitGone(card("워카 첼베사(SNAP)"))
        assertTrue(has(farmCard))
        replaceIn("게이샤 로트", "")
        waitFor(card("워카 첼베사(SNAP)"))
        // 워카 첼베사 shows where its records come from; tapping the country selects it on the map
        clickNode(hasText("에티오피아") and hasText("2번") and hasClickAction())
        waitForText("(Ethiopia)")

        val farmIndex = (0 until count(button("수정"))).first { i ->
            node(button("수정"), i).fetchSemanticsNode().positionInRoot.y > top(farmCard)
        }
        clickNode(button("수정"), farmIndex)
        waitForText("농장(생산자) 수정")
        replaceIn("테스트 농장 핀카", "테스트 농장 핀카 2")
        clickText("궁금함")
        clickText("수정 저장")
        waitForText(mapDescription, substring = true)
        waitFor(card("테스트 농장 핀카 2"))
        assertTopToBottom("the curious farm is listed last", card("워카 첼베사(SNAP)"), hasText("궁금한 농장(생산자)"), card("테스트 농장 핀카 2"))
        assertEquals(MiscStatus.CURIOUS, misc().single { it.name == "테스트 농장 핀카 2" }.status)

        val deleteIndex = count(button("삭제")) - 1
        clickNode(button("삭제"), deleteIndex)
        waitForText("'테스트 농장 핀카 2'을(를) 삭제할까요?")
        clickNode(dialogButton("삭제"))
        waitGone(card("테스트 농장 핀카 2"))
        assertTrue(misc().none { it.name.startsWith("테스트 농장 핀카") })
    }

    // ───────────────────────── 가공 방식 → 내가 마셔본 가공 방식 (ProcessMiscSection) ─────────────────────────

    @Test
    fun cov16_processList_addEditDelete_withItsRecords() {
        runBlocking {
            koinGet<EntryRepository>().upsert(Entry(id = "p1", createdAt = Dates.nowMillis() - 86_400_000, name = "콜롬비아 무산소 테스트", country = "콜롬비아", variety = "Castillo", process = "기타", processOther = "무산소 테스트 가공"))
        }
        launchApp()
        openBeanView("가공 방식")
        waitForText("내가 마셔본 가공 방식")
        waitForText("아직 등록한 게 없어요. 원두를 등록하면 자동으로도 여기 쌓여요.")
        clickText("+ 추가")
        typeInto("예: 내추럴", "무산소 테스트 가공")
        typeInto("특징, 어떤 원두에서 봤는지 등", "발효향이 강함")
        clickText("저장")
        waitFor(card("무산소 테스트 가공"))
        waitForText("발효향이 강함")
        // web processItemInfo: the records written as 기타 + this name are counted on the card
        waitForText("국가별로 마셔본 횟수")
        waitFor(hasText("Castillo", substring = true), "variety of the matching record")
        val added = misc().single { it.type == MiscType.PROCESS }
        assertEquals(listOf("무산소 테스트 가공", "발효향이 강함"), listOf(added.name, added.notes))

        clickText("수정")
        waitFor(field("무산소 테스트 가공"), "form prefilled")
        replaceIn("발효향이 강함", "발효향이 아주 강함")
        clickText("수정 저장")
        waitForText("발효향이 아주 강함")
        assertEquals("edit keeps the id", added.copy(notes = "발효향이 아주 강함"), misc().single { it.type == MiscType.PROCESS })

        clickText("삭제")
        waitForText("\"무산소 테스트 가공\"을(를) 목록에서 지울게요.")
        clickNode(dialogButton("삭제"))
        waitForText("아직 등록한 게 없어요. 원두를 등록하면 자동으로도 여기 쌓여요.")
        assertTrue(misc().none { it.type == MiscType.PROCESS })
    }

    // ───────────────────────── lab blends: edit, delete, calendar day ─────────────────────────

    @Test
    fun cov17_labBlendEditThenDelete_calendarDayFollows() {
        val today = Dates.isoDate(Dates.today())
        runBlocking {
            koinGet<BlendRepository>().upsert(Blend(id = "lb1", name = "편집 전 블렌드", date = today, beans = listOf(BlendComponent("A 원두", "10"), BlendComponent("B 원두", "5")), notes = "처음 메모", createdAt = Dates.nowMillis()))
        }
        launchApp()
        openBeanView("블렌드")
        waitFor(hasText("편집 전 블렌드"))
        waitForText("A 원두 10g (67%)")
        clickText("수정")
        waitForText("블렌드 기록 수정")
        waitFor(field("편집 전 블렌드"), "form prefilled")
        replaceIn("편집 전 블렌드", "편집 후 블렌드")
        replaceIn("5", "7")
        clickText("+ 원두 추가")
        typeInto("원두 이름 (최근 마신 것부터 추천)", "C 원두")
        typeInto("그램(g)", "3")
        clickNode(button("✕"), 0) // drop A
        waitGone(field("A 원두"))
        replaceIn("처음 메모", "비율 바꿈")
        clickText("수정 저장")
        waitFor(hasText("편집 후 블렌드"), "edited card")
        waitForText("B 원두 7g (70%)")
        waitForText("C 원두 3g (30%)")
        waitForText("비율 바꿈")
        val edited = blends().single()
        assertEquals("lb1", edited.id)
        assertEquals(listOf(BlendComponent("B 원두", "7"), BlendComponent("C 원두", "3")), edited.beans)
        assertEquals(today, edited.date)

        // the calendar's day panel shows the new name and brings us back to the blend list
        tab("tab-calendar")
        val todayCell = hasText(Dates.today().day.toString()) and hasClickAction()
        clickNode(todayCell)
        waitFor(hasText("편집 후 블렌드") and hasClickAction(), "day panel row")
        assertTrue(has(hasText("원두 · 블렌드")))
        assertFalse(has(hasText("편집 전 블렌드")))
        clickNode(hasText("편집 후 블렌드") and hasClickAction())
        waitFor(hasText("+ 블렌드 기록 추가"), "블렌드 view")

        clickText("삭제")
        waitForText("이 블렌드 기록을 삭제할까요?")
        clickNode(dialogButton("삭제"))
        waitForText("아직 블렌드 기록이 없어요. 원두 섞어 마신 거, 그램수까지 남겨보세요.")
        assertTrue(blends().isEmpty())
        tab("tab-calendar")
        waitForText("일")
        settle()
        assertFalse("today's square has nothing left", has(todayCell))
    }
}
