package com.coffeejournal.android

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.domain.model.ClassType
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.RoadmapItem
import com.coffeejournal.domain.reference.RoadmapDefaults
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.calendar.forms.BookFormViewModel
import com.coffeejournal.ui.calendar.forms.ClassFormViewModel
import com.coffeejournal.ui.extract.PantryEditorViewModel
import com.coffeejournal.ui.misc.MiscFormViewModel
import com.coffeejournal.ui.misc.PhotoSlot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/** Audit fixes on the home tab, pantry, calendar / study / roadmap, the equipment tab, keyboard and date fields. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class HomeCalendarMiscFlowTest : FlowTestBase() {

    private val density get() = context.resources.displayMetrics.density

    // ───────────── calendar-7 / beanA-10 / design-11: one global "N entries" header ─────────────

    @Test
    fun header_everyTabShowsTheRecordCount() {
        SampleData.seed()
        launchApp()
        waitForText("5 entries")
        tab("tab-calendar")
        waitUntil("calendar tab with the count") { has(hasText("로드맵")) || has(hasText("커피 공부")) }
        waitForText("5 entries")
        tab("tab-bean")
        waitForText("커피 지도 + 농장(생산자)")
        waitForText("5 entries")
        assertFalse("no per-tab 'beans' count", has(hasText(" beans", substring = true)))
        tab("tab-misc")
        waitForText("전체 장비")
        waitForText("5 entries")
        assertFalse("no per-tab 'items' count", has(hasText(" items", substring = true)))
    }

    // ───────────── home-4: bean groups start collapsed ─────────────

    @Test
    fun home_beanGroupsStartCollapsed() {
        SampleData.seed()
        launchApp()
        waitForText("5 entries")
        val header = hasText("콜롬비아 라 플라타 게이샤 워시드") and hasClickAction()
        scrollListTo(hasText("+ 새 기록 추가"), header)
        settle()
        assertFalse("no group is open before the user opens one", has(button("✏️ 직접 정리하기")))
        clickNode(header)
        waitFor(button("✏️ 직접 정리하기"), "the tapped group opens")
    }

    // ───────────── home-2: the purchase price keeps the digits in the order typed ─────────────

    @Test
    fun pantryPrice_typedKeyByKey_keepsItsDigitsAndFormatsOnBlur() {
        launchApp()
        clickText("원두 보관함")
        clickText("+ 원두 추가")
        typeInto("예: 에티오피아 벤사 내추럴", "가격 테스트 원두")
        typeInto("예: 200", "200")
        // one key at a time, like a keyboard: formatting while typing used to leave the cursor before the 4th digit
        typeInto("예: 18,000", "2")
        var typed = "2"
        for (c in "1850") {
            val f = field(typed)
            waitFor(f, "price field with '$typed'")
            node(f).performTextInput(c.toString())
            settle(1)
            typed += c
        }
        waitFor(field("21850"), "digits in the order typed")
        clickNode(field("가격 테스트 원두")) // focus leaves the price field (web blur)
        waitFor(field("21,850"), "thousands separator added on blur")
        waitForText("100g 환산가 · 10,925원")
        clickText("저장")
        waitUntil("saved") { runBlocking { koinGet<PantryRepository>().getAll() }.isNotEmpty() }
        assertEquals("21850", runBlocking { koinGet<PantryRepository>().getAll().single().price })
    }

    // ───────────── calendar-3: an added phase can become current and can be deleted ─────────────

    @Test
    fun roadmap_addedPhaseBecomesCurrent_andCanBeDeleted() {
        runBlocking { koinGet<SettingsRepository>().setDdayStart(Dates.plusDays(Dates.today(), -40)) }
        launchApp()
        tab("tab-calendar")
        val addPhase = button("+ 단계 추가")
        scrollListTo(hasText("토"), addPhase)
        clickNode(addPhase)
        typeInto("예: 추출 기초 다지기", "추출 기초 다지기")
        clickNode(button("저장"))
        val indicator = hasText("지금은 \"추출 기초 다지기\" 단계예요")
        waitFor(indicator, "the added phase is the current one (the starter spans every day)")
        assertTrue(has(hasText("📍 추출 기초 다지기", substring = true)))

        val delete = button("단계 삭제")
        scrollListTo(hasText("토"), delete)
        clickNode(delete)
        clickNode(dialogButton("삭제"))
        waitFor(hasText("지금은 \"${RoadmapDefaults.STARTER_TITLE}\" 단계예요"), "back to the starter phase")
        assertEquals(listOf(RoadmapDefaults.STARTER_ID), runBlocking { koinGet<RoadmapRepository>().getAll().map { it.id } })
        assertFalse("the starter phase has no delete action", has(button("단계 삭제")))
    }

    // ───────────── calendar-4: an inline item edit is saved when editing ends by any route ─────────────

    @Test
    fun roadmap_inlineEdit_isSavedWhenThePhaseIsCollapsed() {
        runBlocking {
            val repo = koinGet<RoadmapRepository>()
            repo.ensureSeeded()
            val starter = repo.getAll().single()
            repo.upsert(starter.copy(items = listOf(RoadmapItem("i1", "커핑 5회"), RoadmapItem("i2", "핸드드립 30잔"))))
        }
        launchApp()
        tab("tab-calendar")
        val phaseHeader = hasText(RoadmapDefaults.STARTER_TITLE) and hasClickAction()
        scrollListTo(hasText("토"), phaseHeader)
        clickNode(phaseHeader) // no D-day: nothing is open until tapped
        val item = hasText("커핑 5회") and hasClickAction()
        scrollListTo(hasText("토"), item)
        clickNode(item)
        waitFor(field("커핑 5회"), "inline editor")
        replaceIn("커핑 5회", "커핑 10회")
        clickNode(phaseHeader) // collapsing the phase ends the edit, like the web's blur
        waitUntil("edit saved") { runBlocking { koinGet<RoadmapRepository>().getAll().single().items.first().text } == "커핑 10회" }

        // focus moving to another field saves too
        clickNode(phaseHeader)
        val second = hasText("핸드드립 30잔") and hasClickAction()
        scrollListTo(hasText("토"), second)
        clickNode(second)
        replaceIn("핸드드립 30잔", "핸드드립 50잔")
        clickNode(field("새 항목 추가 후 Enter"))
        waitUntil("second edit saved") { runBlocking { koinGet<RoadmapRepository>().getAll().single().items[1].text } == "핸드드립 50잔" }
    }

    // ───────────── miscBackup-9 / miscBackup-10: equipment tab ─────────────

    @Test
    fun equipment_addButtonHiddenOnAll_andPhotoRemoveHasA48dpTarget() {
        runBlocking {
            koinGet<MiscRepository>().upsert(MiscItem(id = "k1", type = MiscType.KETTLE, name = "펠로우 스타그", status = MiscStatus.OWNED, photos = listOf("missing.jpg"), createdAt = Dates.nowMillis()))
        }
        launchApp()
        tab("tab-misc")
        waitForText("펠로우 스타그")
        assertFalse("web hides '+ 추가' on 전체", has(hasContentDescription("추가") and hasClickAction()))
        clickText("케틀")
        waitFor(hasContentDescription("추가") and hasClickAction(), "the button is back once a type is picked")

        clickText("수정")
        waitForText("케틀 수정")
        val remove = hasContentDescription("대표 사진 삭제") and hasClickAction()
        waitFor(remove)
        val bounds = node(remove).fetchSemanticsNode().boundsInRoot
        assertTrue("remove target ≥ 48dp (was 24dp)", bounds.width / density >= 47.5f && bounds.height / density >= 47.5f)
        clickNode(remove)
        waitFor(button("대표 사진"), "the slot is empty again")
    }

    // ───────────── design-12 / design-6: one date field and one picker dialog ─────────────

    @Test
    fun equipmentDateField_sharedLook_squarePickerWithoutTextMode_andClear() {
        launchApp()
        tab("tab-misc")
        clickText("드리퍼")
        clickNode(hasContentDescription("추가") and hasClickAction())
        waitForText("드리퍼 추가")
        clickText("연도-월-일") // same placeholder as every other date field
        waitFor(dialogButton("확인"), "date picker")
        assertTrue(has(dialogButton("취소")))
        assertFalse("no switch to Material's text-entry mode", has(hasContentDescription("input mode", substring = true, ignoreCase = true)))
        clickNode(dialogButton("확인")) // today is preselected
        val today = Dates.isoDate(Dates.today())
        waitForText(today)
        clickNode(hasContentDescription("날짜 지우기") and hasClickAction())
        waitForText("연도-월-일")
        assertFalse(has(hasText(today)))
    }

    // ───────────── platform-4: the keyboard does not cover the field being typed into ─────────────

    private fun composeRootView(): View {
        var v: View = compose.activity.findViewById<ViewGroup>(android.R.id.content)
        while (v is ViewGroup && v.javaClass.simpleName != "AndroidComposeView") v = v.getChildAt(0)
        return v
    }

    private fun showKeyboard(heightDp: Int) {
        val px = (heightDp * density).roundToInt()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(composeRootView(), insets) }
        settle()
    }

    @Test
    fun bookForm_keyboardOpen_focusedMemoAndSaveStayAboveIt() {
        launchApp()
        tab("tab-calendar")
        clickText("스터디")
        clickText("+ 책 추가")
        waitForText("책 추가")
        val keyboardDp = 330
        showKeyboard(keyboardDp)
        val rootHeightDp = composeRootView().height / density
        val keyboardTop = rootHeightDp - keyboardDp

        val memo = field("인상 깊은 구절, 배운 점 등")
        waitFor(memo)
        node(memo).performSemanticsAction(SemanticsActions.RequestFocus)
        settle()
        waitUntil("memo field scrolled above the keyboard") {
            node(memo).fetchSemanticsNode().boundsInRoot.bottom / density <= keyboardTop + 1f
        }
        node(button("저장")).performScrollTo()
        settle()
        val save = node(button("저장")).fetchSemanticsNode().boundsInRoot
        assertTrue("저장 reachable above the keyboard (bottom ${save.bottom / density}dp, keyboard top ${keyboardTop}dp)", save.bottom / density <= keyboardTop + 1f)
    }

    // ───────────── home-8 / flows-1 / calendar-2: one save per form, one id per new item ─────────────

    @Test
    fun formSaves_areNotReentrant_andKeepOneId() {
        val pantryVm = PantryEditorViewModel(null, koinGet(), SavedStateHandle())
        waitUntil("pantry form loaded") { pantryVm.form.value.loaded }
        pantryVm.update { copy(name = "연타 봉투") }
        pantryVm.save(); pantryVm.save()
        waitUntil("bag saved") { pantryVm.form.value.saved }
        pantryVm.save()
        settle()
        assertEquals(listOf(pantryVm.form.value.draftId), runBlocking { koinGet<PantryRepository>().getAll().map { it.id } })

        var pops = 0
        val bookVm = BookFormViewModel(null, koinGet(), SavedStateHandle())
        bookVm.update { copy(title = "연타 책") }
        bookVm.save { pops++ }; bookVm.save { pops++ }
        waitUntil("book saved") { pops > 0 }
        bookVm.save { pops++ }
        settle()
        assertEquals("one pop, so the screen below the form stays", 1, pops)
        assertEquals(listOf(bookVm.state.value.id), runBlocking { koinGet<StudyRepository>().getBooks().map { it.id } })

        val miscVm = MiscFormViewModel(MiscType.SCALE, null, koinGet(), koinGet(), SavedStateHandle())
        miscVm.setName("연타 저울")
        miscVm.save(); miscVm.save()
        waitUntil("scale saved") { miscVm.state.value.done }
        miscVm.save()
        settle()
        assertTrue("저장 stays disabled after success", !miscVm.state.value.canSave)
        assertEquals(1, runBlocking { koinGet<MiscRepository>().getAll().size })
    }

    // ───────────── hand-off: typed input survives process death (the equipment form opens the camera) ─────────────

    @Test
    fun formInput_survivesProcessDeath() {
        // pantry editor
        val pantryHandle = SavedStateHandle()
        val pantryBefore = PantryEditorViewModel(null, koinGet(), pantryHandle)
        pantryBefore.update { copy(name = "복원 봉투", weight = "250", roastDate = "2026-09-01") }
        pantryBefore.setPrice("18000")
        waitUntil("pantry form kept") { pantryHandle.get<String>("pantryForm")?.contains("18000") == true }
        val pantryAfter = PantryEditorViewModel(null, koinGet(), SavedStateHandle(mapOf("pantryForm" to pantryHandle.get<String>("pantryForm"))))
        with(pantryAfter.form.value) {
            assertEquals("복원 봉투", name); assertEquals("250", weight); assertEquals("18000", price); assertEquals("2026-09-01", roastDate)
            assertEquals(pantryBefore.form.value.draftId, draftId)
            assertFalse(saving)
        }

        // class form
        val classHandle = SavedStateHandle()
        val classBefore = ClassFormViewModel(null, koinGet(), classHandle)
        classBefore.update { copy(title = "복원 클래스", classType = ClassType.RECURRING, startDate = "2026-09-01", notes = "배운 것") }
        waitUntil("class form kept") { classHandle.get<String>("classForm")?.contains("배운 것") == true }
        val classAfter = ClassFormViewModel(null, koinGet(), SavedStateHandle(mapOf("classForm" to classHandle.get<String>("classForm"))))
        assertEquals("복원 클래스", classAfter.state.value.title)
        assertTrue(classAfter.state.value.isRecurring)
        assertEquals("2026-09-01", classAfter.state.value.startDate)
        assertEquals(classBefore.state.value.id, classAfter.state.value.id)

        // equipment form, edited item with a stored photo; the camera result arrives after the restore
        runBlocking {
            koinGet<MiscRepository>().upsert(MiscItem(id = "d1", type = MiscType.DRIPPER, name = "원래 이름", photos = listOf("kept.jpg"), scope = "보존", createdAt = 5))
        }
        val miscHandle = SavedStateHandle()
        val miscBefore = MiscFormViewModel(MiscType.DRIPPER, "d1", koinGet(), koinGet(), miscHandle)
        waitUntil("equipment loaded") { !miscBefore.state.value.loading }
        miscBefore.setName("카메라 전에 쓴 이름")
        miscBefore.setSince("2026-03-01")
        waitUntil("equipment form kept") { miscHandle.get<String>("miscForm")?.contains("카메라 전에 쓴 이름") == true }
        val miscAfter = MiscFormViewModel(MiscType.DRIPPER, "d1", koinGet(), koinGet(), SavedStateHandle(mapOf("miscForm" to miscHandle.get<String>("miscForm"))))
        waitUntil("restored equipment loaded") { !miscAfter.state.value.loading }
        assertEquals("카메라 전에 쓴 이름", miscAfter.state.value.name)
        assertEquals("2026-03-01", miscAfter.state.value.since)
        assertEquals(PhotoSlot.Existing("kept.jpg"), miscAfter.state.value.slots[0])
        miscAfter.setPhoto(1, byteArrayOf(1, 2, 3))
        miscAfter.save()
        waitUntil("saved") { miscAfter.state.value.done }
        val saved = runBlocking { koinGet<MiscRepository>().getById("d1")!! }
        assertEquals("카메라 전에 쓴 이름", saved.name)
        assertEquals("보존", saved.scope)
        assertEquals(5L, saved.createdAt)
        assertEquals(2, saved.photos.size)
        assertEquals("kept.jpg", saved.photos[0])
    }

    // ───────────── design §2.3 #5 with a detail screen left open on the 원두 tab ─────────────

    @Test
    fun calendarBlend_opensTheBlendList_evenAfterAnotherBeanView() {
        SampleData.seed()
        launchApp()
        tab("tab-bean")
        clickText("가공 방식")
        tab("tab-calendar")
        waitForText("일")
        // 2026-09-20 holds the sample lab blend; move the month there if today is elsewhere
        val today = Dates.today()
        repeat(((today.year - 2026) * 12 + today.month.ordinal - 8).coerceAtLeast(0)) { clickNode(hasContentDescription("이전 달") and hasClickAction()) }
        clickNode(hasText("20") and hasClickAction())
        waitFor(hasText("에티오피아+콜롬비아"), "day panel lists the lab blend")
        clickNode(hasText("에티오피아+콜롬비아") and hasClickAction())
        waitFor(hasText("+ 블렌드 기록 추가"), "the 원두 tab opens on 블렌드, not on the last view (가공 방식)")
        tab("tab-calendar")
        tab("tab-bean")
        waitFor(hasText("+ 블렌드 기록 추가"), "블렌드 stays selected afterwards")
    }
}
