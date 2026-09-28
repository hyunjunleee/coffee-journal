package com.coffeejournal.android

import androidx.compose.ui.test.hasText
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.db.MiscDao
import com.coffeejournal.data.db.MiscItemEntity
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.form.FormArgs
import com.coffeejournal.ui.form.RecordFormViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record-form audit fixes that need the real app: retry after a failed save, bad numbers, suggestions, repeat beans, saved state. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class FormFixesFlowTest : FlowTestBase() {

    private val namePlaceholder = "예: 콜롬비아 라 플라타 게이샤 워시드"
    private val searchPlaceholder = "예: 벤사, 게이샤, 리브레"
    private val bagPhotoSlot = button("대표 사진")
    private val lockBanner = "✓ 처음 등록한 날 입력했던 원두 정보를 자동으로 불러왔어요. 필요하면 그냥 고쳐서 입력하시면 돼요."

    private fun entries() = runBlocking { koinGet<EntryRepository>().getAll() }
    private fun onDetailOf(name: String): Boolean = has(hasText(name)) && has(button("수정")) && has(button("삭제"))

    private fun openNewForm() {
        clickText("+ 새 기록 추가")
        waitForText("새 기록")
    }

    /** The misc DAO throws once on its next bulk upsert (the auto-registration step that runs after the entry write). */
    private class FailOnceMiscDao(private val real: MiscDao) : MiscDao by real {
        @Volatile var armed = true
        override suspend fun upsertAll(items: List<MiscItemEntity>) {
            if (armed) { armed = false; throw IllegalStateException("디스크 공간 부족 (테스트)") }
            real.upsertAll(items)
        }
    }

    // ───────────── form-4: a retry after a failed save must not add a second record ─────────────

    @Test
    fun form4_retryAfterFailedSave_upsertsTheSameRecord() {
        val failing = FailOnceMiscDao(koinGet<AppDatabase>().miscDao())
        loadKoinModules(module { single<MiscDao> { failing } })
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "재시도 테스트 원두")
        typeInto("예: 커피정경", "재시도 로스터리") // a new roastery makes the pipeline auto-register (and fail)
        clickText("저장")
        waitForText("저장하지 못했어요", substring = true)
        assertFalse("the injected failure fired", failing.armed)
        val afterFailure = entries()
        assertTrue("at most the one row of this form", afterFailure.size <= 1)
        assertTrue("취소 is enabled again after the failure", has(button("취소")))

        clickText("저장")
        waitUntil("detail after the retry") { onDetailOf("재시도 테스트 원두") }
        val saved = entries()
        assertEquals("the retry reuses the form's record id instead of adding a copy", 1, saved.size)
        afterFailure.singleOrNull()?.let { assertEquals(it.id, saved.single().id) }
    }

    // ───────────── NaN / Infinity in a step field must not crash the form ─────────────

    /**
     * The wait and water fields are the web's type=number inputs: "NaN" and "Infinity" are not taken (gap #3 moved
     * that filter into the field), so the step log keeps its numbers and the form saves them. Stored "NaN"s from a
     * backup are still ignored by the summary (FormNumbers).
     */
    @Test
    fun nanStepWait_isNotTaken_andTheFormSavesTheNumbersThatStayed() {
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "NaN 테스트 원두")
        replaceIn("35", "NaN") // the last example step's wait (the drawdown)
        assertTrue("the wait keeps its number", has(field("35")))
        replaceIn("80", "Infinity") // a pour's water
        assertTrue(has(field("80")))
        waitUntil("총 추출시간 2:10") { has(field("2:10")) }
        settle()
        clickText("저장")
        waitUntil("detail") { onDetailOf("NaN 테스트 원두") }
        val saved = entries().single()
        assertEquals("35", saved.steps.last().wait)
        assertEquals("2:10", saved.time)
        assertTrue(saved.steps.none { it.water == "Infinity" })
    }

    // ───────────── form-7: name suggestions are the bags being drunk, not every past record ─────────────

    @Test
    fun form7_nameSuggestions_onlyOpenedBags() {
        SampleData.seed()
        launchApp()
        openNewForm()
        clickNode(field(namePlaceholder))
        waitFor(button("에티오피아 예가체프 워카 첼베사"), "opened bag suggested")
        assertFalse("a finished bean of a past record is not suggested", has(button("콜롬비아 라 플라타 게이샤 워시드")))
        assertFalse("an unopened bag is not suggested", has(button("과테말라 안티구아 부르봉")))
    }

    // ───────────── form-8: repeat brews of a known bean: lock banner, no bag-photo slots ─────────────

    @Test
    fun form8_repeatBean_hidesBagPhotosAndShowsBanner() {
        SampleData.seed()
        launchApp()
        openNewForm()
        assertTrue("a new bean can attach bag photos", has(bagPhotoSlot))
        typeInto(namePlaceholder, "콜롬비아 라 플라타 게이샤 워시드")
        typeInto("20", "15") // blur the name
        waitForText(lockBanner)
        waitGone(bagPhotoSlot)
        clickText("닫기")
        waitGone(hasText(lockBanner))
        assertFalse("closing the banner does not bring the photo slots back", has(bagPhotoSlot))
        back()
        clickNode(dialogButton("지우고 나가기")) // the typed form asks before it is left

        // editing the later of two records of the same bean (e2): locked
        typeInto(searchPlaceholder, "워카")
        waitForText("전체 기록에서", substring = true)
        clickNode(button("기록 보기"), 0)
        clickText("수정")
        waitForText("기록 수정")
        waitForText(lockBanner)
        assertFalse(has(bagPhotoSlot))
        back()
        waitFor(button("수정"))
        back()

        // editing the first registration (e1): not locked
        waitUntil("home with the search") { has(button("기록 보기")) || has(field(searchPlaceholder)) }
        if (!has(button("기록 보기"))) {
            typeInto(searchPlaceholder, "워카")
            waitForText("전체 기록에서", substring = true)
        }
        clickNode(button("기록 보기"), 1)
        clickText("수정")
        waitForText("기록 수정")
        waitFor(bagPhotoSlot)
        assertFalse(has(hasText(lockBanner)))
    }

    // ───────────── platform-7: the form survives process death ─────────────

    private fun formVm(handle: SavedStateHandle) =
        RecordFormViewModel(FormArgs(), koinGet(), koinGet(), koinGet(), koinGet(), koinGet(), koinGet(), handle)

    @Test
    fun platform7_formStateSurvivesProcessDeath() {
        val handle = SavedStateHandle()
        val before = formVm(handle)
        waitUntil("new form loaded") { before.loaded.value }
        before.onNameTyped("복원 테스트 원두")
        before.update { it.copy(dose = "17", notes = "카메라 켜기 전에 쓴 메모", attributes = ScaScoring.defaultAttributes() + ("flavor" to 8.5)) }
        waitUntil("form kept in the saved state") { handle.get<String>("recordForm")?.contains("카메라 켜기 전에 쓴 메모") == true }

        // process death: only the saved-state bundle survives, and a fresh view model is built from it
        val after = formVm(SavedStateHandle(mapOf("recordForm" to handle.get<String>("recordForm"))))
        waitUntil("restored form loaded") { after.loaded.value }
        val s = after.state.value
        assertEquals("복원 테스트 원두", s.name)
        assertEquals("17", s.dose)
        assertEquals("카메라 켜기 전에 쓴 메모", s.notes)
        assertEquals(8.5, s.attributes["flavor"])
        assertEquals("the record id survives too", before.state.value.draftId, s.draftId)
        assertFalse(s.saving)

        // the camera result arrives after the restore and lands in the restored form
        after.setPhoto(0, byteArrayOf(1, 2, 3))
        assertTrue(after.state.value.bagPhotos[0].hasImage)
        assertEquals("복원 테스트 원두", after.state.value.name)
        after.save()
        waitUntil("saved") { entries().isNotEmpty() }
        val saved = entries().single()
        assertEquals(before.state.value.draftId, saved.id)
        assertEquals(1, saved.bagPhotos.size)
        assertNotNull(saved.bagPhotos.single())
    }

    // ───────────── form-4: one save per tap sequence ─────────────

    @Test
    fun form4_saveIsNotReentrant() {
        val vm = formVm(SavedStateHandle())
        waitUntil("loaded") { vm.loaded.value }
        vm.onNameTyped("더블 저장 원두")
        vm.save()
        assertTrue("저장 중... right away", vm.state.value.saving)
        vm.save()
        waitUntil("saved") { entries().isNotEmpty() }
        settle()
        vm.save() // after a successful save the form stays in its saving state until it closes
        settle()
        assertEquals(1, entries().size)
        assertEquals(vm.state.value.draftId, entries().single().id)
    }
}
