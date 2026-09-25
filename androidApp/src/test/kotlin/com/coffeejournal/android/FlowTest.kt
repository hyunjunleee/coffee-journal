package com.coffeejournal.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.DdayRules
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** End-to-end user flows on the real app (Robolectric + Compose UI test, in-memory Room). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FlowTest : FlowTestBase() {

    private val newBean = "케냐 니에리 기통가 AA"
    private val namePlaceholder = "예: 콜롬비아 라 플라타 게이샤 워시드"
    private val dosePlaceholder = "20"

    private fun todayCell() = hasText(Dates.today().day.toString()) and hasClickAction()

    /** Home → "+ 새 기록 추가" → name + dose → 저장; ends on the new record's detail screen. */
    private fun createBrewFromHome(name: String = newBean, dose: String = "17") {
        clickText("+ 새 기록 추가")
        waitForText("새 기록")
        typeInto(namePlaceholder, name)
        typeInto(dosePlaceholder, dose)
        clickText("저장")
        waitUntil("detail of '$name'") { has(hasText(name)) && has(button("수정")) && !has(hasText("새 기록")) }
    }

    private fun onDetailOf(name: String): Boolean = has(hasText(name)) && has(button("수정")) && has(button("삭제"))

    // ───────────────────────── 1. new record ─────────────────────────

    @Test
    fun flow01_newBrewRecord_detailHomeGroupAndCalendarDay() {
        SampleData.seed()
        launchApp()
        waitForText("5 entries")
        // what today's calendar square looked like before the new record
        tab("tab-calendar")
        waitForText("일")
        val todayHadContent = has(todayCell())
        tab("tab-extract")
        waitForText("5 entries")

        createBrewFromHome()
        assertTrue("detail shows the dose in the 비율 row", has(hasText("17g : ?g")))

        back()
        waitForText("6 entries")
        val header = hasText(newBean) and hasClickAction()
        scrollListTo(hasText("+ 새 기록 추가"), header)
        assertTrue("home lists a bean group for the new record", has(header))

        tab("tab-calendar")
        waitFor(todayCell(), "today's square is marked (clickable)")
        clickNode(todayCell())
        if (todayHadContent) {
            waitFor(hasText(newBean, substring = true), "day panel lists the new record")
        } else {
            waitUntil("today's square opens the new record") { onDetailOf(newBean) }
        }
    }

    // ───────────────────────── 2. edit returns to the same detail ─────────────────────────

    @Test
    fun flow02_editFromDetail_returnsToSameDetailWithNewValue() {
        SampleData.seed()
        launchApp()
        waitForText("5 entries")
        createBrewFromHome(dose = "17")

        clickText("수정")
        waitForText("기록 수정")
        replaceIn("17", "18")
        clickText("수정 저장")
        waitUntil("back on the detail with the new dose") { !has(hasText("기록 수정")) && has(hasText("18g : ?g")) && onDetailOf(newBean) }
        assertFalse("old value gone", has(hasText("17g : ?g")))
        val stored = runBlocking { koinGet<EntryRepository>().getAll().filter { it.name == newBean } }
        assertEquals("edit must not create a second record", 1, stored.size)
        assertEquals("18", stored.single().dose)

        // exactly one detail screen on the back stack: one back press lands on home
        back()
        waitUntil("home after one back press") { has(hasText("6 entries")) && has(hasText("+ 새 기록 추가")) }
        assertFalse(has(button("수정") and hasAnyAncestor(isDialog())))
    }

    // ───────────────────────── 3. delete ─────────────────────────

    @Test
    fun flow03_deleteFromDetail_withConfirmation_removesFromHomeAndCalendar() {
        SampleData.seed()
        launchApp()
        waitForText("5 entries")
        tab("tab-calendar")
        waitForText("일")
        val todayHadContent = has(todayCell())
        tab("tab-extract")
        createBrewFromHome()

        clickText("삭제")
        waitForText("이 기록을 삭제할까요? 봉투 사진도 함께 지워져요.")
        // cancel first: nothing happens
        clickNode(dialogButton("취소"))
        waitGone(hasText("이 기록을 삭제할까요? 봉투 사진도 함께 지워져요."))
        assertTrue(onDetailOf(newBean))

        clickText("삭제")
        clickNode(dialogButton("삭제"))
        waitForText("5 entries")
        assertFalse("group gone from home", has(hasText(newBean) and hasClickAction()))
        assertTrue(runBlocking { koinGet<EntryRepository>().getAll().none { it.name == newBean } })

        tab("tab-calendar")
        waitForText("일")
        settle()
        assertEquals("today's square back to its previous state", todayHadContent, has(todayCell()))
    }

    // ───────────────────────── 4. cupping from the calendar ─────────────────────────

    @Test
    fun flow04_cuppingRecordFromCalendar_listedAndCountedInBeanTab() {
        SampleData.seed()
        launchApp()
        waitForText("5 entries")
        tab("tab-calendar")
        clickText("커핑")
        clickText("+ 커핑 기록 추가")
        waitForText("새 기록")

        val place = "모모스 로스터리 커핑룸"
        val beanA = "케냐 키암부 AB 테스트"
        val beanB = "파나마 게이샤 내추럴 테스트"
        typeInto("예: FELT 청계천", place)
        typeInto("원두 이름 (예: 에티오피아 예가체프)", beanA)
        clickNode(button("라이트"), 0)
        typeInto("노트 입력 후 Enter (예: 라즈베리)", "석류")
        clickText("+ 원두 추가")
        typeInto("원두 이름 (예: 에티오피아 예가체프)", beanB)
        clickNode(button("미디엄"), 1)
        typeInto("노트 입력 후 Enter (예: 라즈베리)", "밀크초콜릿")
        clickText("저장")
        waitUntil("cupping detail") { has(hasText(place)) && has(button("수정")) && !has(hasText("새 기록")) }
        assertTrue("detail lists bean A", has(hasText(beanA, substring = true)))
        assertTrue("detail lists bean B", has(hasText(beanB, substring = true)))

        val saved = runBlocking { koinGet<EntryRepository>().getAll().single { it.name == place } }
        assertEquals(Category.CUPPING, saved.category)
        assertEquals(listOf(beanA, beanB), saved.cuppingBeans.map { it.name })
        assertEquals(listOf("라이트", "미디엄"), saved.cuppingBeans.map { it.roast })
        assertEquals(listOf("석류", "밀크초콜릿"), saved.cuppingBeans.map { it.actualNotes })

        back()
        val row = hasText(place) and hasClickAction()
        scrollListTo(hasText("+ 커핑 기록 추가"), row)
        assertTrue("calendar cupping list shows the session", has(row))

        tab("tab-bean")
        clickText("커피 노트")
        clickText("내가 느낀 노트")
        val chip = hasText("석류 1") and hasClickAction()
        scrollListTo(hasText("플레이버 휠"), chip)
        assertTrue("note cloud counts the cupping bean note", has(chip))

        clickText("배전도")
        waitForText("기록에 입력된 배전도를 라이트계·중간·다크계로 나눠서 보여줘요.")
        val lightRow = hasText(beanA) and hasClickAction()
        scrollListTo(hasText("기록에 입력된 배전도를 라이트계·중간·다크계로 나눠서 보여줘요."), lightRow)
        assertTrue("light roast list has bean A", has(lightRow))
        clickNode(hasText("중간", substring = true) and hasClickAction())
        val mediumRow = hasText(beanB) and hasClickAction()
        scrollListTo(hasText("기록에 입력된 배전도를 라이트계·중간·다크계로 나눠서 보여줘요."), mediumRow)
        assertTrue("medium roast list has bean B", has(mediumRow))
    }

    // ───────────────────────── 5. pantry ─────────────────────────

    @Test
    fun flow05_pantryAddAndOpen_showsDrinkingCardOnHome() {
        launchApp()
        waitForText("0 entries")
        clickText("원두 보관함")
        waitForText("원두 보관함 · 0봉")
        clickText("+ 원두 추가")
        val bag = "에티오피아 구지 함벨라 테스트"
        typeInto("예: 에티오피아 벤사 내추럴", bag)
        typeInto("예: 커피 리브레", "테스트 로스터스")
        typeInto("예: 200", "250")
        clickText("저장")
        waitForText("원두 보관함 · 1봉")
        assertTrue(has(hasText(bag)))

        clickText("개봉함")
        clickNode(dialogButton("개봉함"))
        waitForText("마시는 중 (개봉)")
        waitForText("원두 보관함 · 0봉")
        val item = runBlocking { koinGet<PantryRepository>().getAll().single() }
        assertEquals(PantryItem.STATUS_OPENED, item.status)
        assertNotNull(item.openedAt)

        back()
        waitForText("0 entries")
        waitFor(hasText(bag), "drinking card for the opened bag")
        assertTrue("card shows the full bag as remaining", has(hasText("잔여량 250g/250g")))
    }

    // ───────────────────────── 6. equipment ─────────────────────────

    @Test
    fun flow06_addDripper_listedAndSuggestedInRecordForm() {
        SampleData.seed()
        launchApp()
        tab("tab-misc")
        clickText("드리퍼")
        clickNode(hasContentDescription("추가") and hasClickAction())
        waitForText("드리퍼 추가")
        val dripper = "하리오 V60 02 세라믹"
        typeInto("예: 오리가미 드리퍼", dripper)
        clickText("저장")
        waitFor(hasText(dripper), "dripper listed on the 기타 tab")
        assertTrue(runBlocking { koinGet<MiscRepository>().getAll().any { it.type == MiscType.DRIPPER && it.name == dripper } })

        tab("tab-extract")
        clickText("+ 새 기록 추가")
        waitForText("새 기록")
        clickNode(field("칼리타 웨이브"))
        waitFor(button(dripper), "dripper suggestion")
        clickText(dripper)
        waitFor(field(dripper), "dripper field filled from the suggestion")
    }

    // ───────────────────────── 7. books ─────────────────────────

    @Test
    fun flow07_bookAddEditDelete_onStudyView() {
        launchApp()
        tab("tab-calendar")
        clickText("스터디")
        waitForText("아직 기록한 책이 없습니다.")
        clickText("+ 책 추가")
        waitForText("책 추가")
        typeInto("예: 커핑 바이블", "커피 과학")
        clickText("저장")
        waitFor(hasText("커피 과학"), "book listed")
        assertEquals(1, runBlocking { koinGet<StudyRepository>().getBooks().size })

        clickText("수정")
        waitForText("책 수정")
        replaceIn("커피 과학", "커피 과학 개정판")
        clickText("저장")
        waitFor(hasText("커피 과학 개정판"), "edited title listed")
        assertFalse(has(hasText("커피 과학")))
        assertEquals(listOf("커피 과학 개정판"), runBlocking { koinGet<StudyRepository>().getBooks().map { it.title } })

        clickText("삭제")
        clickNode(dialogButton("삭제"))
        waitForText("아직 기록한 책이 없습니다.")
        assertTrue(runBlocking { koinGet<StudyRepository>().getBooks().isEmpty() })
    }

    // ───────────────────────── 8. D-day ─────────────────────────

    /** A date picker day button whose spoken label contains the day number [day] (e.g. "Tuesday, September 1, 2026"). */
    private fun pickerDay(day: Int) = SemanticsMatcher("picker day $day") { n ->
        val texts = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: return@SemanticsMatcher false
        Regex("(^|\\D)$day(\\D|$)").containsMatchIn(texts)
    } and hasClickAction() and hasAnyAncestor(isDialog())

    @Test
    fun flow08_setDday_pillShowsCoffeeDn() {
        launchApp()
        waitForText("커피 처음 마신 날을 기록해두면 여기 며칠째인지 보여드려요.")
        clickText("연도-월-일")
        waitFor(pickerDay(1), "date picker")
        clickNode(pickerDay(1))
        clickNode(dialogButton("확인"))
        val today = Dates.today()
        val start = LocalDate(today.year, today.month, 1)
        waitForText(Dates.isoDate(start))
        clickText("저장")
        val label = DdayRules.label(start, today)!!
        waitForText(label)
        assertEquals(start, runBlocking { koinGet<SettingsRepository>().get(SettingsRepository.KEY_DDAY_START) }?.let { Dates.parseIsoDate(it) })
    }

    // ───────────────────────── 10. save pipeline side effects ─────────────────────────

    @Test
    fun flow10_savingBrew_autoRegistersMetaItemsAndSyncsPantry() {
        launchApp()
        waitForText("0 entries")
        val bean = "에티오피아 시다마 벤사 테스트"
        clickText("+ 새 기록 추가")
        waitForText("새 기록")
        typeInto(namePlaceholder, bean)
        typeInto("예: 커피정경", "테스트 로스터리")
        typeInto("예: Nordic Approach", "Falcon Specialty")
        typeInto("예: 라 에스메랄다(페드로 가족)", "벤사 농장(아셰나피)")
        typeInto("예: Heirloom(74110), Mundo Novo", "74158, Kurume")
        clickNode(button("워시드"), 0)
        typeInto("세부 종류 (선택, 예: 드래곤 아이, 더블 퍼멘티드)", "더블 퍼멘티드")
        clickNode(button("라이트"), 0)
        typeInto(dosePlaceholder, "15")
        clickText("저장")
        waitUntil("detail") { onDetailOf(bean) }

        val misc = runBlocking { koinGet<MiscRepository>().getAll() }
        fun names(type: String) = misc.filter { it.type == type }.map { it.name }.toSet()
        assertEquals(setOf("테스트 로스터리"), names(MiscType.SOURCE))
        assertEquals("process main segment only", setOf("워시드"), names(MiscType.PROCESS))
        assertEquals(setOf("벤사 농장(아셰나피)"), names(MiscType.FARM))
        assertEquals(setOf("Falcon Specialty"), names(MiscType.SELECTION))
        assertEquals(setOf("74158", "Kurume"), names(MiscType.VARIETY))

        val pantry = runBlocking { koinGet<PantryRepository>().getAll() }
        val bag = pantry.single()
        assertEquals(bean, bag.name)
        assertEquals(PantryItem.STATUS_OPENED, bag.status)
        assertEquals("테스트 로스터리", bag.roastery)
        assertEquals("100", bag.weight)
        assertEquals("라이트", bag.roastLevel)

        // the roastery shows up in the 원두 tab's 로스터리 view
        back()
        tab("tab-bean")
        clickText("로스터리")
        waitFor(hasText("테스트 로스터리", substring = true), "auto-registered roastery listed in the bean tab")
    }

    @Test
    fun flow10b_newRecordOpensUnopenedBag_editOnlyFillsBlanks() {
        launchApp()
        waitForText("0 entries")
        val bean = "브라질 세하도 옐로우 버번 테스트"
        runBlocking {
            koinGet<PantryRepository>().upsert(PantryItem(id = "bag1", name = bean, createdAt = Dates.nowMillis(), weight = "200"))
        }
        createBrewFromHome(name = bean, dose = "16")
        var bag = runBlocking { koinGet<PantryRepository>().getById("bag1")!! }
        assertEquals("a new record opens the unopened bag", PantryItem.STATUS_OPENED, bag.status)
        assertEquals("existing weight kept", "200", bag.weight)
        assertEquals("pantry is not duplicated", 1, runBlocking { koinGet<PantryRepository>().getAll().size })

        // put the bag back to unopened, then edit the record: an edit must not open it again
        runBlocking { koinGet<PantryRepository>().upsert(bag.copy(status = PantryItem.STATUS_UNOPENED, openedAt = null, roastery = "")) }
        clickText("수정")
        waitForText("기록 수정")
        typeInto("예: 커피정경", "편집 로스터리")
        clickText("수정 저장")
        waitUntil("detail after edit") { !has(hasText("기록 수정")) && onDetailOf(bean) }
        bag = runBlocking { koinGet<PantryRepository>().getById("bag1")!! }
        assertEquals("edit keeps the bag unopened", PantryItem.STATUS_UNOPENED, bag.status)
        assertNull(bag.openedAt)
        assertEquals("edit fills blank pantry fields", "편집 로스터리", bag.roastery)
    }

    @Test
    fun flow10c_siblingRecordsOnlyReceiveMissingValues() {
        launchApp()
        waitForText("0 entries")
        val bean = "과테말라 우에우에테낭고 테스트"
        createBrewFromHome(name = bean, dose = "15")
        back()
        waitForText("1 entries")
        clickText("+ 새 기록 추가")
        waitForText("새 기록")
        typeInto(namePlaceholder, bean)
        typeInto("예: 커피정경", "형제 로스터리")
        typeInto("예: Heirloom(74110), Mundo Novo", "Bourbon")
        typeInto(dosePlaceholder, "16")
        clickText("저장")
        waitUntil("second detail") { has(hasText("16g : ?g")) && onDetailOf(bean) }
        val all = runBlocking { koinGet<EntryRepository>().getAll().filter { it.name == bean } }
        assertEquals(2, all.size)
        val first = all.single { it.dose == "15" }
        assertEquals("sibling received the missing roastery", "형제 로스터리", first.roastery)
        assertEquals("sibling received the missing variety", "Bourbon", first.variety)
    }
}
