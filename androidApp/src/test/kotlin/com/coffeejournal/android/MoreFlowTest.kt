package com.coffeejournal.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.data.repo.BeanMetaRepository
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.MyRecipeRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.BlendComponent
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Secondary flows: navigation targets, detail/edit fidelity, pantry, equipment, study, roadmap, recipes. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MoreFlowTest : FlowTestBase() {

    private val newBean = "케냐 니에리 기통가 AA"
    private val namePlaceholder = "예: 콜롬비아 라 플라타 게이샤 워시드"
    private val searchPlaceholder = "예: 벤사, 게이샤, 리브레"

    private fun todayCell() = hasText(Dates.today().day.toString()) and hasClickAction()
    private fun onDetailOf(name: String): Boolean = has(hasText(name)) && has(button("수정")) && has(button("삭제"))

    private fun createBrewFromHome(name: String = newBean, dose: String = "17") {
        openNewRecord()
        waitForText("새 기록")
        typeInto(namePlaceholder, name)
        typeInto("20", dose)
        clickText("저장")
        waitUntil("detail of '$name'") { onDetailOf(name) && !has(hasText("새 기록")) }
    }

    private fun entries() = runBlocking { koinGet<EntryRepository>().getAll() }

    /** Opens a record through the home search: query → "기록 보기" (index-th) → detail. */
    private fun openViaSearch(query: String, index: Int = 0) {
        typeInto(searchPlaceholder, query)
        waitForText("전체 기록에서", substring = true)
        clickNode(button("기록 보기"), index)
        waitUntil("detail opened from search") { has(button("수정")) && has(button("삭제")) }
    }

    // ───────────── design §2.3.5: calendar day panel blend → blend view ─────────────

    @Test
    fun more01_calendarDayPanelBlend_opensBlendView() {
        runBlocking {
            koinGet<BlendRepository>().upsert(
                Blend(id = "blToday", name = "오늘 만든 테스트 블렌드", date = Dates.isoDate(Dates.today()), beans = listOf(BlendComponent("A 원두", "10"), BlendComponent("B 원두", "5")), createdAt = Dates.nowMillis())
            )
        }
        launchApp()
        tab("tab-calendar")
        clickNode(todayCell())
        waitFor(hasText("오늘 만든 테스트 블렌드"), "day panel lists the blend")
        clickNode(hasText("오늘 만든 테스트 블렌드") and hasClickAction())
        waitFor(hasText("+ 블렌드 기록 추가"), "the 원두 tab opens on its 블렌드 view (design §2.3.5)")
    }

    // ───────────── design §2.3.3: no SCA total for unscored records ─────────────

    @Test
    fun more02_unscoredRecord_detailShowsNoScaTotal() {
        launchApp()
        createBrewFromHome()
        val saved = entries().single()
        assertEquals("form defaults are stored", 10.0, saved.attributes["uniformity"])
        assertFalse("home/list rows agree the record is unscored", runBlocking { com.coffeejournal.domain.rules.ScaScoring.isScored(saved.attributes) })
        assertFalse(
            "a record without any of the 7 scored attributes must not show a SCA total (design §2.3.3), " +
                "but the detail shows 'SCA CUPPING FORM … TOTAL SCORE 30.00'",
            has(hasText("TOTAL SCORE")) && has(hasText("30.00")),
        )
        assertFalse("nor the SCA block with only the default 10s", has(hasText("SCA CUPPING FORM")))
    }

    // ───────────── design §2.3.4: photos survive edits and are deleted with the record ─────────────

    @Test
    fun more03_editKeepsBagPhotos_deleteRemovesPhotoFiles() {
        val photos: PhotoStore = koinGet()
        val (a, b) = runBlocking { photos.save(byteArrayOf(1, 2, 3)) to photos.save(byteArrayOf(4, 5, 6)) }
        val createdAt = Dates.nowMillis() - 3_600_000
        runBlocking {
            koinGet<EntryRepository>().upsert(Entry(id = "ph1", createdAt = createdAt, name = "사진 원두 테스트", dose = "13.5", bagPhotos = listOf(a, b)))
        }
        launchApp()
        openViaSearch("사진 원두")
        assertEquals(2, count(hasContentDescription("원두 봉투 사진")))

        clickText("수정")
        waitForText("기록 수정")
        replaceIn("13.5", "14")
        clickText("수정 저장")
        waitUntil("detail after edit") { !has(hasText("기록 수정")) && has(hasText("14g : ?g")) }
        val edited = entries().single()
        assertEquals("bag photos kept through an edit", listOf(a, b), edited.bagPhotos)
        assertTrue(runBlocking { photos.exists(a) && photos.exists(b) })

        clickText("삭제")
        clickNode(dialogButton("삭제"))
        waitUntil("record deleted") { entries().isEmpty() }
        settle()
        assertFalse("bag photo files deleted with the record", runBlocking { photos.exists(a) || photos.exists(b) })
    }

    @Test
    fun more04_saveWithoutName_showsErrorAndSavesNothing() {
        SampleData.seed()
        launchApp()
        openNewRecord()
        waitForText("새 기록")
        clickText("저장")
        waitForText("원두 이름을 입력해 주세요.")
        assertTrue("still on the form", has(hasText("새 기록")))
        assertEquals(5, entries().size)
    }

    @Test
    fun more05_homeSearch_findsBeansAcrossCategoriesAndOpensDetail() {
        SampleData.seed()
        launchApp()
        typeInto(searchPlaceholder, "게이샤")
        waitForText("전체 기록에서 2건 찾았어요.")
        assertTrue(has(hasText("콜롬비아 라 플라타 게이샤 워시드")))
        assertTrue("cupping beans are searchable", has(hasText("파나마 보케테 게이샤")))
        clickNode(button("기록 보기"), 0)
        waitUntil("detail") { has(button("수정")) }
    }

    @Test
    fun more06_bestRecipeFromDetail_andManualSummaryOnHome() {
        SampleData.seed()
        launchApp()
        createBrewFromHome()
        clickText("이 원두의 베스트 레시피로 지정")
        waitFor(button("⭐ 베스트 레시피 해제"))
        back()
        waitForText("6 entries")
        // groups start collapsed like the web (home-4): open the new bean's group
        val header = hasText(newBean) and hasClickAction()
        scrollListTo(hasText("+ 새 기록 추가"), header)
        clickNode(header)
        val bestCard = hasText("⭐ 이 원두의 베스트 레시피")
        scrollListTo(hasText("+ 새 기록 추가"), bestCard)
        assertTrue("home group shows the best recipe card", has(bestCard))

        clickText("✏️ 직접 정리하기")
        typeInto("이 원두를 마시면서 어땠는지 자유롭게 적어보세요", "산미가 밝고 단맛이 길다")
        clickText("저장")
        waitForText("☕ 이 원두 총정리")
        waitForText("산미가 밝고 단맛이 길다")
        val key = BeanNames.coreBeanName(newBean)
        assertEquals("산미가 밝고 단맛이 길다", runBlocking { koinGet<BeanMetaRepository>().getSummaries().single { it.beanKey == key }.text })
        assertEquals(entries().single { it.name == newBean }.id, runBlocking { koinGet<BeanMetaRepository>().getBest().single { it.beanKey == key }.entryId })
    }

    /**
     * "⭐ 내 레시피로 저장" on the detail opens an AlertDialog with a text field; any M3 AlertDialog holding a text field
     * never goes idle under Robolectric (reproduced with a bare AlertDialog + OutlinedTextField), so the recipe is
     * created through the 내 레시피 screen instead and then applied from the record form's launcher.
     */
    @Test
    fun more07_createMyRecipe_thenApplyItInANewRecord() {
        SampleData.seed()
        launchApp()
        openNewRecord()
        waitForText("새 기록")
        clickText("⭐ 내 레시피")
        clickText("+ 새 레시피 만들기")
        waitForText("내 레시피")
        // form-10: the screen opens with the new-recipe form already open (web toggles it open inline)
        typeInto("예: 밝은 산미용 3단 푸어", "산미용 3단 푸어")
        // name, dripper, filter, grind, dose, water, temp, time
        node(hasSetTextAction(), 1).performClick()
        waitFor(button("오리가미 드리퍼 S"), "owned dripper suggested (web dripper-datalist)")
        node(hasSetTextAction(), 1).performTextInput("V60")
        node(hasSetTextAction(), 4).performTextInput("16")
        node(hasSetTextAction(), 5).performTextInput("250")
        node(hasSetTextAction(), 6).performTextInput("93")
        clickText("저장")
        waitForText("16g : 250g · 93°C · V60")
        assertEquals("산미용 3단 푸어", runBlocking { koinGet<MyRecipeRepository>().getAll().single().name })

        back()
        waitForText("새 기록")
        if (!has(button("이 레시피 적용 →"))) clickText("⭐ 내 레시피")
        waitFor(hasText("산미용 3단 푸어"), "my recipe card in the launcher")
        clickText("이 레시피 적용 →")
        waitFor(field("16"), "dose filled from my recipe")
        assertTrue("water filled", has(field("250")))
        assertTrue("temperature filled", has(field("93")))
        typeInto(namePlaceholder, "레시피 적용 원두")
        clickText("저장")
        waitUntil("detail") { onDetailOf("레시피 적용 원두") }
        val applied = entries().single { it.name == "레시피 적용 원두" }
        assertEquals(listOf("16", "250", "93", "V60"), listOf(applied.dose, applied.water, applied.temp, applied.dripper))
        assertEquals("산미용 3단 푸어", applied.recipeRef?.name)
    }

    @Test
    fun more08_pantryEditOpenCancelOpenAndDelete() {
        runBlocking { koinGet<PantryRepository>().upsert(PantryItem(id = "bagA", name = "르완다 니야마샤케 테스트", weight = "200", createdAt = Dates.nowMillis())) }
        launchApp()
        clickText("원두 보관함")
        waitForText("원두 보관함 · 1봉")
        clickText("수정")
        waitForText("수정 저장")
        replaceIn("200", "250")
        clickText("수정 저장")
        waitForText("원두 보관함 · 1봉")
        waitUntil("weight saved") { runBlocking { koinGet<PantryRepository>().getById("bagA")?.weight } == "250" }

        clickText("개봉함")
        clickNode(dialogButton("개봉함"))
        waitForText("원두 보관함 · 0봉")
        clickText("개봉 취소")
        waitForText("원두 보관함 · 1봉")
        val bag = runBlocking { koinGet<PantryRepository>().getById("bagA")!! }
        assertEquals(PantryItem.STATUS_UNOPENED, bag.status)

        clickText("삭제")
        waitForText("'르완다 니야마샤케 테스트' 항목을 보관함에서 삭제할까요?")
        clickNode(dialogButton("삭제"))
        waitForText("아직 개봉하지 않은 원두가 없습니다.")
        assertTrue(runBlocking { koinGet<PantryRepository>().getAll().isEmpty() })
    }

    @Test
    fun more09_equipmentEditStatusAndDelete() {
        runBlocking { koinGet<MiscRepository>().upsert(MiscItem(id = "d1", type = MiscType.DRIPPER, name = "칼리타 웨이브 185", status = MiscStatus.OWNED, createdAt = Dates.nowMillis())) }
        launchApp()
        tab("tab-misc")
        waitForText("칼리타 웨이브 185")
        clickText("수정")
        waitForText("드리퍼 수정")
        replaceIn("칼리타 웨이브 185", "칼리타 웨이브 155")
        clickText("궁금한 장비")
        clickText("수정 저장")
        waitForText("칼리타 웨이브 155")
        clickText("드리퍼")
        waitForText("궁금한 드리퍼")
        assertTrue("owned section now empty", has(hasText("아직 등록한 드리퍼이(가) 없어요.")))
        assertEquals(MiscStatus.CURIOUS, runBlocking { koinGet<MiscRepository>().getById("d1")!!.status })

        clickText("삭제")
        clickNode(dialogButton("삭제"))
        waitGone(hasText("칼리타 웨이브 155"))
        assertTrue(runBlocking { koinGet<MiscRepository>().getAll().isEmpty() })
    }

    @Test
    fun more10_videoAndClassAddEditDelete() {
        launchApp()
        tab("tab-calendar")
        clickText("스터디")
        clickText("동영상")
        waitForText("아직 기록한 동영상이 없습니다.")
        clickText("+ 동영상 추가")
        typeInto("예: 추출 변수와 맛의 관계", "푸어오버 기초")
        clickText("저장")
        waitForText("푸어오버 기초")
        clickText("수정")
        waitForText("동영상 수정")
        replaceIn("푸어오버 기초", "푸어오버 심화")
        clickText("저장")
        waitForText("푸어오버 심화")
        clickText("삭제")
        clickNode(dialogButton("삭제"))
        waitForText("아직 기록한 동영상이 없습니다.")

        clickText("클래스")
        waitForText("아직 기록한 클래스가 없습니다.")
        clickText("+ 클래스 추가")
        typeInto("예: 홈카페 원데이 클래스", "라떼아트 원데이")
        clickText("저장")
        waitForText("라떼아트 원데이")
        clickText("수정")
        waitForText("클래스 수정")
        replaceIn("라떼아트 원데이", "라떼아트 2회차")
        clickText("저장")
        waitForText("라떼아트 2회차")
        clickText("삭제")
        clickNode(dialogButton("삭제"))
        waitForText("아직 기록한 클래스가 없습니다.")
        val study: StudyRepository = koinGet()
        assertTrue(runBlocking { study.getVideos().isEmpty() && study.getClasses().isEmpty() })
    }

    @Test
    fun more11_roadmapAddCheckAndDeleteItem() {
        SampleData.seed()
        launchApp()
        tab("tab-calendar")
        val input = field("새 항목 추가 후 Enter")
        scrollListTo(hasText("토"), input)
        node(input).performScrollTo()
        node(input).performTextInput("커핑 연습 5회")
        settle(1)
        node(field("커핑 연습 5회")).performImeAction()
        settle()
        waitForText("0/1")
        waitFor(hasText("커핑 연습 5회") and hasClickAction() and !hasSetTextAction(), "roadmap item row")

        // the checkbox is the sibling right before the item text
        val itemNode = node(hasText("커핑 연습 5회") and hasClickAction() and !hasSetTextAction())
        val parent = itemNode.onParent()
        val kids = parent.fetchSemanticsNode().children
        val idx = kids.indexOfFirst { k -> k.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "커핑 연습 5회" } == true }
        parent.onChildAt(idx - 1).performClick()
        waitForText("1/1")
        assertTrue(runBlocking { koinGet<RoadmapRepository>().getAll().single().items.single().done })

        clickNode(hasContentDescription("항목 삭제") and hasClickAction()) // platform-10: the ✕ is a labelled button
        clickNode(dialogButton("삭제"))
        waitForText("0/0")
    }

    @Test
    fun more12_editWithoutChanges_keepsEveryBrewField() {
        SampleData.seed()
        val before = entries().single { it.id == "e1" }
        launchApp()
        openViaSearch("워카", index = 1)
        clickText("수정")
        waitForText("기록 수정")
        clickText("수정 저장")
        waitUntil("back on detail") { !has(hasText("기록 수정")) && has(button("수정")) }
        val after = entries().single { it.id == "e1" }
        assertEquals("brew record unchanged by a no-op edit", before, after)
    }

    @Test
    fun more13_editCuppingWithoutChanges_keepsEveryBeanField() {
        SampleData.seed()
        val before = entries().single { it.id == "e5" }
        launchApp()
        openViaSearch("키리냐가")
        waitFor(hasText("커피플랜트", substring = true))
        clickText("수정")
        waitForText("기록 수정")
        clickText("수정 저장")
        waitUntil("back on detail") { !has(hasText("기록 수정")) && has(button("수정")) }
        val after = entries().single { it.id == "e5" }
        // the web renames a cupping session to its place on every save, so only the name may change
        assertEquals("cupping record unchanged by a no-op edit (except the name)", before.copy(name = after.name), after)
    }

    @Test
    fun more14_cafeRecordFromCalendar_listedInCafeListNotInPantry() {
        SampleData.seed()
        launchApp()
        tab("tab-calendar")
        clickText("카페")
        clickText("+ 카페 기록 추가")
        waitForText("새 기록")
        typeInto("예: OO카페 (서울 성수동)", "테스트 카페 성수")
        typeInto(namePlaceholder, "에티오피아 게뎁 카페 테스트")
        clickText("저장")
        waitUntil("cafe detail") { onDetailOf("에티오피아 게뎁 카페 테스트") }
        val saved = entries().single { it.name == "에티오피아 게뎁 카페 테스트" }
        assertEquals(Category.CAFE, saved.category)
        assertEquals("테스트 카페 성수", saved.cafeName)
        assertTrue("cafe cups do not create pantry bags", runBlocking { koinGet<PantryRepository>().getAll().none { it.name == saved.name } })
        back()
        val row = hasText("에티오피아 게뎁 카페 테스트") and hasClickAction()
        scrollListTo(hasText("+ 카페 기록 추가"), row)
        assertTrue(has(row))
    }

    @Test
    fun more15_customBlendRecord_namesItselfAndDrawsDownComponentBags() {
        launchApp()
        createBrewFromHome(name = "재료 에티오피아 구지", dose = "15")
        back()
        openNewRecord()
        waitForText("새 기록")
        clickText("직접 블렌드")
        typeInto("원두 선택", "재료 에티오피아 구지")
        typeInto("원두 선택", "재료 브라질 세하도")
        typeInto("그램(g)", "10")
        typeInto("그램(g)", "5")
        clickText("저장")
        val blendName = "재료 에티오피아 구지 + 재료 브라질 세하도"
        waitUntil("blend detail") { onDetailOf(blendName) }
        val blend = entries().single { it.name == blendName }
        assertEquals("dose is the sum of the components", "15", blend.dose)
        back()
        waitForText("2 entries")
        // bag of the first bean: 100 g default − 15 g brewed − 10 g used in the blend
        waitFor(hasText("잔여량 75g/100g"), "drinking card draws down the blend component")
    }

    @Test
    fun more16_ddayLongPress_changesStartDate() {
        SampleData.seed()
        launchApp()
        val today = Dates.today()
        val oldLabel = DdayRules.label(LocalDate(2026, 1, 1), today)!!
        waitForText(oldLabel)
        node(hasText(oldLabel)).performTouchInput { longClick() }
        settle()
        val day2 = SemanticsMatcher("picker day 2") { n ->
            val texts = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: return@SemanticsMatcher false
            Regex("(^|\\D)2(\\D|$)").containsMatchIn(texts)
        } and hasClickAction() and hasAnyAncestor(isDialog())
        clickNode(day2)
        clickNode(dialogButton("확인"))
        waitForText(DdayRules.label(LocalDate(2026, 1, 2), today)!!)
        assertEquals("2026-01-02", runBlocking { koinGet<SettingsRepository>().get(SettingsRepository.KEY_DDAY_START) })
    }

    @Test
    fun more17_beanTabKeepsSelectedViewAcrossTabSwitches() {
        SampleData.seed()
        launchApp()
        tab("tab-bean")
        clickText("블렌드")
        waitForText("+ 블렌드 기록 추가")
        tab("tab-calendar")
        tab("tab-bean")
        waitFor(hasText("+ 블렌드 기록 추가"), "블렌드 view still selected after switching tabs")
    }

    @Test
    fun more18_newRecord_updatesDrinkingCardRemaining() {
        launchApp()
        createBrewFromHome(dose = "17")
        back()
        waitForText("1 entries")
        waitFor(hasText("잔여량 83g/100g"), "drinking card for the new bean with 100 − 17 g left")
        assertNotNull(runBlocking { koinGet<PantryRepository>().getAll().singleOrNull { it.name == newBean && it.isOpened } })
    }

    @Test
    fun more19_blendFormFromBeanTab_appearsOnCalendarDay() {
        launchApp()
        tab("tab-bean")
        clickText("블렌드")
        clickText("+ 블렌드 기록 추가")
        typeInto("예: 에티오피아+콜롬비아 디카페인", "탭에서 만든 블렌드")
        typeInto("원두 이름 (최근 마신 것부터 추천)", "원두 가")
        typeInto("그램(g)", "12")
        clickText("저장")
        waitFor(hasText("탭에서 만든 블렌드"), "blend listed in the blend view")
        val saved = runBlocking { koinGet<BlendRepository>().getAll().single() }
        assertEquals(Dates.isoDate(Dates.today()), saved.date)
        tab("tab-calendar")
        clickNode(todayCell())
        waitFor(hasText("탭에서 만든 블렌드"), "blend listed in today's day panel")
    }

    @Test
    fun more20_deletingRecordFromCalendarOpenedDetail_returnsToCalendar() {
        SampleData.seed()
        launchApp()
        tab("tab-calendar")
        clickText("커핑")
        clickText("전체 보기")
        val row = hasText("커피플랜트 퍼블릭 커핑") and hasClickAction()
        scrollListTo(hasText("+ 커핑 기록 추가"), row)
        clickNode(row)
        waitUntil("detail") { has(button("수정")) }
        clickText("삭제")
        clickNode(dialogButton("삭제"))
        waitFor(hasText("+ 커핑 기록 추가"), "back on the calendar cupping view")
        assertFalse(has(row))
        assertTrue(entries().none { it.id == "e5" })
    }

    @Test
    fun more21_homeCuppingOfOneBean_joinsThatBeansHomeGroup() {
        launchApp()
        createBrewFromHome(name = "홈커핑 대상 원두", dose = "15")
        back()
        tab("tab-calendar")
        clickText("커핑")
        clickText("홈커핑")
        clickText("+ 커핑 기록 추가")
        waitForText("새 기록")
        assertFalse("홈커핑 has no place field", has(field("예: FELT 청계천")))
        typeInto("원두 이름 (예: 에티오피아 예가체프)", "홈커핑 대상 원두")
        typeInto("어땠는지, 인상 깊었던 점, 배운 점 등", "집에서 비교 커핑")
        clickText("저장")
        waitUntil("cupping detail") { has(button("수정")) && !has(hasText("새 기록")) }
        val cupping = entries().single { it.isCupping }
        assertEquals("홈커핑", cupping.cuppingType)
        back()
        tab("tab-extract")
        waitForText("2 entries")
        // the brew group now also lists the home cupping row (groups start collapsed, so open it first)
        val header = hasText("홈커핑 대상 원두") and hasClickAction()
        scrollListTo(hasText("+ 새 기록 추가"), header)
        clickNode(header)
        val cuppingRow = hasText("커핑 · 홈커핑")
        scrollListTo(hasText("+ 새 기록 추가"), cuppingRow)
        assertTrue("home group shows the single-bean home cupping", has(cuppingRow))

        // and the cupping review collection lists it (calendar 전체 → 커피 공부 → 커핑 리뷰 모음)
        tab("tab-calendar")
        clickText("전체")
        val reviewsTab = button("커핑 리뷰 모음")
        scrollListTo(hasText("토"), reviewsTab)
        clickNode(reviewsTab)
        val review = hasText("집에서 비교 커핑")
        scrollListTo(hasText("토"), review)
        assertTrue(has(review))
    }

    @Test
    fun more22_twoBrewsSameDay_calendarShowsCupBadgeAndDayPanel() {
        launchApp()
        tab("tab-calendar")
        waitForText("토")
        val todayHadContent = has(todayCell())
        tab("tab-extract")
        createBrewFromHome(name = "하루 두 잔 원두 A", dose = "15")
        back()
        createBrewFromHome(name = "하루 두 잔 원두 B", dose = "16")
        back()
        tab("tab-calendar")
        waitFor(hasText("2잔"), "cup badge on today's square")
        if (!todayHadContent) {
            clickNode(todayCell())
            waitFor(hasText("하루 두 잔 원두 A"), "day panel row A")
            assertTrue(has(hasText("하루 두 잔 원두 B")))
            clickNode(hasText("하루 두 잔 원두 B") and hasClickAction())
            waitUntil("detail B") { onDetailOf("하루 두 잔 원두 B") }
        }
        back()
        clickText("원두")
        val brewRow = hasText("하루 두 잔 원두 A", substring = true) and hasClickAction()
        scrollListTo(hasText("토"), brewRow)
        assertTrue("이 달 추출 기록 lists the brews", has(brewRow))
    }

    @Test
    fun more23_dripbagRecord_onlyUnderSmallPackFilter() {
        launchApp()
        openNewRecord()
        waitForText("새 기록")
        clickText("드립백")
        typeInto(namePlaceholder, "드립백 테스트 원두")
        typeInto("20", "12")
        clickText("저장")
        waitUntil("detail") { onDetailOf("드립백 테스트 원두") }
        back()
        waitForText("1 entries")
        val group = hasText("드립백 테스트 원두") and hasClickAction()
        assertFalse("전체 filter lists only regular beans", has(group))
        waitForText("아직 추출 기록이 없습니다. 오늘 내린 커피부터 남겨보세요.")
        clickText("드립백 / 소량")
        scrollListTo(hasText("+ 새 기록 추가"), group)
        assertTrue("드립백 / 소량 filter lists the dripbag record", has(group))
        val bag = runBlocking { koinGet<PantryRepository>().getAll().single() }
        assertEquals("dripbag", bag.packageType)
        assertTrue("opened small pack card", has(hasText("소량 · 개봉")))
    }

    @Test
    fun more24_secondRecordOfSameBean_autofillsBagInfoOnNameBlur() {
        launchApp()
        openNewRecord()
        waitForText("새 기록")
        typeInto(namePlaceholder, "자동채움 테스트 원두")
        typeInto("예: 커피정경", "자동채움 로스터리")
        typeInto("에티오피아", "르완다")
        typeInto("20", "15")
        clickText("저장")
        waitUntil("first detail") { onDetailOf("자동채움 테스트 원두") }
        back()

        openNewRecord()
        waitForText("새 기록")
        typeInto(namePlaceholder, "자동채움 테스트 원두")
        typeInto("20", "16") // moving focus away from the name field triggers the autofill
        waitForText("✓ 처음 등록한 날 입력했던 원두 정보를 자동으로 불러왔어요. 필요하면 그냥 고쳐서 입력하시면 돼요.")
        assertTrue("roastery filled from the first record", has(field("자동채움 로스터리")))
        assertTrue("country filled from the first record", has(field("르완다")))
    }

    @Test
    fun more25_noteChip_opensNoteDetail_thenRecord_andBackKeepsNotesView() {
        SampleData.seed()
        launchApp()
        tab("tab-bean")
        clickText("커피 노트")
        clickText("내가 느낀 노트")
        val chip = SemanticsMatcher("note chip '자스민 N'") { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.any { Regex("^자스민 \\d+$").matches(it.text) } == true
        } and hasClickAction()
        scrollListTo(hasText("플레이버 휠"), chip)
        clickNode(chip)
        waitForText("“자스민”와 함께 기록된 노트 조합")
        clickNode(button("원래 기록 보기"))
        waitUntil("record detail") { has(button("수정")) && has(button("삭제")) }
        back()
        waitForText("“자스민”와 함께 기록된 노트 조합")
        back()
        waitFor(hasText("노트와 내가 쓴 표현 검색"), "back on the 커피 노트 view")
    }

    @Test
    fun more26_addRoasteryInBeanTab_listedAndSuggestedInRecordForm() {
        launchApp()
        tab("tab-bean")
        clickText("로스터리")
        clickText("+ 로스터리 추가")
        typeInto("예: 영천카페 듀잇", "테스트 로스터스 성수")
        clickText("저장")
        waitFor(hasText("테스트 로스터스 성수", substring = true), "roastery listed")
        val item = runBlocking { koinGet<MiscRepository>().getAll().single() }
        assertEquals(MiscType.SOURCE, item.type)

        tab("tab-extract")
        openNewRecord()
        waitForText("새 기록")
        clickNode(field("예: 커피정경"))
        waitFor(button("테스트 로스터스 성수"), "roastery suggestion in the record form")
    }

    @Test
    fun more27_siblingValuesAreNeverOverwritten() {
        launchApp()
        openNewRecord()
        waitForText("새 기록")
        typeInto(namePlaceholder, "형제 덮어쓰기 테스트")
        typeInto("예: Heirloom, Mundo Novo", "Bourbon")
        typeInto("노트 추가 후 Enter (예: 오렌지)", "오렌지")
        typeInto("20", "15")
        clickText("저장")
        waitUntil("first detail") { onDetailOf("형제 덮어쓰기 테스트") }
        back()

        openNewRecord()
        waitForText("새 기록")
        typeInto(namePlaceholder, "형제 덮어쓰기 테스트")
        typeInto("20", "16") // blur → autofill copies Bourbon / 오렌지 into the empty fields
        waitFor(field("Bourbon"), "variety autofilled")
        replaceIn("Bourbon", "Caturra")
        clickNode(hasText("×") and hasClickAction()) // remove the autofilled 오렌지 chip
        typeInto("노트 추가 후 Enter (예: 오렌지)", "자두")
        clickText("저장")
        waitUntil("second detail") { has(hasText("16g : ?g")) && onDetailOf("형제 덮어쓰기 테스트") }
        val first = entries().single { it.name == "형제 덮어쓰기 테스트" && it.dose == "15" }
        assertEquals("design §2.3.6: an existing variety is not overwritten by a sibling", "Bourbon", first.variety)
        assertEquals("design §2.3.6: existing expected notes are not overwritten by a sibling", "오렌지", first.expectedNotes)
    }

    @Test
    fun more28_scoredRecord_totalOnFormDetailAndHomeGroup() {
        launchApp()
        openNewRecord()
        waitForText("새 기록")
        typeInto(namePlaceholder, "점수 테스트 원두")
        typeInto("20", "15")
        val sliders = SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetProgress)
        waitFor(sliders)
        val flavor = node(sliders, 2) // Fragrance, Aroma Intensity, Flavor, …
        flavor.performScrollTo()
        flavor.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(8.5f) }
        settle()
        waitForText("38.50") // form TOTAL SCORE: 8.5 + the three default 10s
        clickText("저장")
        waitUntil("detail") { onDetailOf("점수 테스트 원두") }
        assertEquals(8.5, entries().single().attributes["flavor"])
        assertTrue("detail subtitle score", has(hasText("38.50 / 100")))
        back()
        waitFor(hasText("최고 38.50"), "home group shows the best score")
    }

    @Test
    fun more29_ddayMilestones_onHomePill() {
        val today = Dates.today()
        runBlocking { koinGet<SettingsRepository>().setDdayStart(Dates.plusDays(today, -29)) }
        launchApp()
        waitForText("Coffee D-30")
        waitForText("30일 기념")
        runBlocking { koinGet<SettingsRepository>().setDdayStart(Dates.plusDays(today, -99)) }
        waitForText("Coffee D-100")
        waitForText("100일 기념 ✦")
    }

    /**
     * Two taps on 저장 ~150 ms apart: the first save finishes and starts the pop transition, the second tap lands on
     * the still-visible (exiting) form. Returns how many taps were actually delivered.
     */
    private fun doubleTapSave(saveLabel: String = "저장"): Int {
        val save = button(saveLabel)
        waitFor(save)
        runCatching { node(save).performScrollTo() }
        settle()
        compose.mainClock.autoAdvance = false
        try {
            node(save).performClick()
            Thread.sleep(150) // real time for the Room write on the IO dispatcher
            compose.mainClock.advanceTimeBy(150)
            Thread.sleep(50)
            compose.mainClock.advanceTimeBy(16)
            val second = compose.onAllNodes(save).fetchSemanticsNodes().isNotEmpty()
            if (second) node(save).performClick()
            return if (second) 2 else 1
        } finally {
            compose.mainClock.autoAdvance = true
            settle()
        }
    }

    @Test
    fun more30_doubleTapSave_recordFormCreatesOneRecord() {
        launchApp()
        openNewRecord()
        waitForText("새 기록")
        typeInto(namePlaceholder, "더블탭 기록")
        doubleTapSave() // the button turns into "저장 중..." after the first tap, so the second tap finds nothing to hit
        waitUntil("saved") { entries().isNotEmpty() }
        settle()
        assertEquals("control: the record form guards its save, one record", 1, entries().size)
    }

    @Test
    fun more31_doubleTapSave_pantryEditorCreatesOneBag() {
        launchApp()
        clickText("원두 보관함")
        clickText("+ 원두 추가")
        typeInto("예: 에티오피아 벤사 내추럴", "더블탭 봉투")
        val taps = doubleTapSave()
        waitUntil("saved") { runBlocking { koinGet<PantryRepository>().getAll() }.isNotEmpty() }
        settle()
        assertEquals("taps delivered", 2, taps)
        assertEquals("one bag after a double tap on 저장", 1, runBlocking { koinGet<PantryRepository>().getAll().size })
    }

    @Test
    fun more32_doubleTapSave_bookFormCreatesOneBook() {
        launchApp()
        tab("tab-calendar")
        clickText("스터디")
        clickText("+ 책 추가")
        typeInto("예: 커핑 바이블", "더블탭 책")
        val taps = doubleTapSave()
        waitUntil("saved") { runBlocking { koinGet<StudyRepository>().getBooks() }.isNotEmpty() }
        settle()
        assertEquals("taps delivered", 2, taps)
        assertEquals("one book after a double tap on 저장", 1, runBlocking { koinGet<StudyRepository>().getBooks().size })
    }

    @Test
    fun more33_doubleTapSave_equipmentFormCreatesOneItem() {
        launchApp()
        tab("tab-misc")
        clickText("드리퍼") // "+ 추가" is hidden on 전체, like the web
        clickNode(hasContentDescription("추가") and hasClickAction())
        typeInto("예: 오리가미 드리퍼", "더블탭 드리퍼")
        val taps = doubleTapSave()
        waitUntil("saved") { runBlocking { koinGet<MiscRepository>().getAll() }.isNotEmpty() }
        settle()
        assertEquals("taps delivered", 2, taps)
        assertEquals("one dripper after a double tap on 저장", 1, runBlocking { koinGet<MiscRepository>().getAll().size })
    }

    @Test
    fun more34_doubleTapSave_blendFormCreatesOneBlend() {
        launchApp()
        tab("tab-bean")
        clickText("블렌드")
        clickText("+ 블렌드 기록 추가")
        typeInto("예: 에티오피아+콜롬비아 디카페인", "더블탭 블렌드")
        typeInto("원두 이름 (최근 마신 것부터 추천)", "원두 가")
        typeInto("그램(g)", "12")
        val taps = doubleTapSave()
        waitUntil("saved") { runBlocking { koinGet<BlendRepository>().getAll() }.isNotEmpty() }
        settle()
        assertEquals("taps delivered", 2, taps)
        assertEquals("one blend after a double tap on 저장", 1, runBlocking { koinGet<BlendRepository>().getAll().size })
    }

    @Test
    fun more35_nameParensRoastery_reachesThePantryBag() {
        launchApp()
        openNewRecord()
        waitForText("새 기록")
        typeInto(namePlaceholder, "에티오피아 구지 괄호 테스트 (모모스)")
        waitForText("괄호에서 인식: 로스터리: 모모스")
        typeInto("20", "15")
        clickText("저장")
        waitUntil("detail") { has(button("수정")) && !has(hasText("새 기록")) }
        val bag = runBlocking { koinGet<PantryRepository>().getAll().single() }
        // web syncBrewEntryToOpenedPantry: roastery = entry.roastery || entry.source (the name's parenthesised roastery)
        assertEquals("pantry bag roastery falls back to the roastery in the name's parentheses", "모모스", bag.roastery)
    }

    @Test
    fun more36_cuppingBeanNameParens_registersImporter() {
        launchApp()
        tab("tab-calendar")
        clickText("커핑")
        clickText("+ 커핑 기록 추가")
        waitForText("새 기록")
        typeInto("원두 이름 (예: 에티오피아 예가체프)", "케냐 키암부 AB (커피 리브레, 모모스 셀렉션)")
        clickText("저장")
        waitUntil("detail") { has(button("수정")) && !has(hasText("새 기록")) }
        val importers = runBlocking { koinGet<MiscRepository>().getAll().filter { it.type == MiscType.SELECTION }.map { it.name } }
        // web save-entry (cupping): pairs.push(['selection', getEntrySelection(b)]) for every cupping bean
        assertEquals("cupping beans register their importer like the web", listOf("모모스"), importers)
    }

    @Suppress("unused")
    private fun photosDir() = File(context.cacheDir, "photos")
}
