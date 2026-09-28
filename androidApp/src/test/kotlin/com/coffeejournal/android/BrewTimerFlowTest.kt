package com.coffeejournal.android

import android.content.Context
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.reference.CafeRecipes
import com.coffeejournal.domain.rules.RecipeSteps
import com.coffeejournal.ui.form.timer.BrewClock
import com.coffeejournal.ui.form.timer.BrewTimerArgs
import com.coffeejournal.ui.form.timer.BrewTimerUi
import com.coffeejournal.ui.form.timer.BrewTimerViewModel
import com.coffeejournal.ui.form.timer.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A brew clock the test moves by hand; the timer never reads real time. */
class FakeBrewClock(var mono: Long = 50_000L, var wall: Long = 1_790_000_000_000L) : BrewClock {
    override fun monotonicMs(): Long = mono
    override fun wallMs(): Long = wall
    fun advance(ms: Long) { mono += ms; wall += ms }
}

/**
 * The brew timer (feature-plan-v2 §2.1) driven through the real app with a fake clock: pours, notes, the grams panel
 * and its estimates (the next pour never waits for them), removing a mistaken pour (✕, 되돌리기), the step log it writes into the form, the recipe countdown and its vibration, keeping the screen on, the replace
 * confirmation and the state kept for a process death.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class BrewTimerFlowTest : CoverageFlowBase() {
    private val clock = FakeBrewClock()

    @Before
    fun installClock() {
        loadKoinModules(module { single<BrewClock> { clock } })
    }

    private fun advance(seconds: Int) {
        clock.advance(seconds * 1000L)
        // the timer's ticker re-reads the clock every 200 ms
        Thread.sleep(450)
        settle(1)
    }

    private fun display(): String = node(hasTestTag("timer-display")).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: ""

    private fun waitDisplay(text: String) = waitUntil("timer shows $text (now ${runCatching { display() }.getOrNull()})") { display() == text }

    private fun openTimerFromNewForm(name: String) {
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, name)
        tap(button("⏱ 타이머로 기록"))
        waitForText("추출 타이머")
    }

    /** The grams panel's field (its placeholder is the pour's estimate). */
    private val gramsField = hasSetTextAction() and hasAnyAncestor(hasTestTag("grams-panel"))

    /** Types a pour's grams over its estimate and confirms them. */
    private fun enterGrams(grams: String) {
        waitFor(gramsField)
        node(gramsField).performTextInput(grams)
        settle(1)
        tap(button("확인"))
        waitGone(hasTestTag("grams-panel"))
    }

    private fun vibrator(): Vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    private fun keepsScreenOn(): Boolean {
        fun any(v: View): Boolean = v.keepScreenOn || (v is ViewGroup && (0 until v.childCount).any { any(v.getChildAt(it)) })
        return any(compose.activity.window.decorView)
    }

    @Test
    fun pourAndNotes_becomeTheFormsStepLog_andAreSaved() {
        openTimerFromNewForm("타이머 원두")
        assertEquals("0:00", display())
        assertFalse(keepsScreenOn())
        tap(button("💧 붓기 시작"))
        assertTrue("the screen stays on while timing", keepsScreenOn())
        advance(10)
        waitDisplay("0:10")
        tap(button("붓기 끝"))
        enterGrams("50")
        advance(2)
        tap(button("뜸"))
        advance(28)
        tap(button("💧 붓기 시작"))
        advance(20)
        tap(button("붓기 끝"))
        enterGrams("190")
        waitForText("부은 물 240g", substring = true)
        advance(60)
        waitDisplay("2:00")
        tap(button("추출 끝"))
        waitForText("단계 로그로 옮길 내용")
        assertFalse("the timer stopped", keepsScreenOn())
        waitForText("총 2차 추출 · 합계 물량 240g · 총 시간 2:00")
        // the new form only had the example rows: no confirmation
        tap(button("단계 로그로 옮기기"))
        waitForText("새 기록")
        waitFor(field("0:40"))
        waitForText("총 2차 추출 · 합계 물량 240g · 총 시간 2:00", substring = true)
        saveForm("타이머 원두")
        val saved = entries().single()
        assertEquals(
            listOf(
                RecipeStep("0:00", "50", "10", "1차 푸어"), RecipeStep("0:10", "", "30", "뜸"),
                RecipeStep("0:40", "190", "20", "2차 푸어"), RecipeStep("1:00", "", "60", "드로우다운"),
            ),
            saved.steps,
        )
        assertEquals("2:00", saved.time)
        assertEquals(120, RecipeSteps.summary(saved.steps).totalTimeSec)
    }

    @Test
    fun nextPour_startsWithoutTheGrams_estimatesShownLighter_fixedFromTheLog_andSaved() {
        openTimerFromNewForm("어림 물량")
        tap(button("💧 붓기 시작"))
        advance(9)
        tap(button("붓기 끝"))
        // 9 s × 6 g/s = 54 g → 50 g, already the pour's: pre-filled in the field, "≈" in the log and the total
        waitFor(hasTestTag("grams-panel"))
        assertTrue(has(gramsField and hasText("50")))
        assertTrue(has(hasText("적지 않으면 붓는 시간으로 어림한 50g(초당 6g)으로 기록해요.")))
        waitForText("부은 물 ≈ 50g", substring = true)
        assertTrue(has(hasTestTag("timer-row-0") and hasText("1차 푸어 · ≈ 50g")))
        // the next pour does not wait for the grams
        advance(3)
        tap(button("💧 붓기 시작"))
        waitGone(hasTestTag("grams-panel"))
        advance(16)
        tap(button("붓기 끝"))
        waitForText("부은 물 ≈ 150g", substring = true)
        waitForText("2차 푸어 물량")

        // the first pour, tapped in the log: the real amount typed over its estimate, counted as it is typed
        tap(hasTestTag("timer-row-0"))
        waitForText("1차 푸어 물량")
        assertFalse("붓기 계속 is only for the pour just ended", has(button("붓기 계속")))
        node(gramsField).performTextInput("45")
        waitFor(hasTestTag("timer-row-0") and hasText("1차 푸어 · 45g"))
        tap(button("확인"))
        waitGone(hasTestTag("grams-panel"))
        waitForText("부은 물 ≈ 145g", substring = true)
        advance(30)
        tap(button("추출 끝"))
        waitForText("단계 로그로 옮길 내용")

        // the second pour's estimate is still marked; confirmed from the preview
        assertTrue(has(hasTestTag("timer-step-2") and hasText("≈ 100g")))
        tap(hasTestTag("timer-step-2"))
        waitForText("2차 푸어 물량")
        tap(button("확인"))
        waitFor(hasTestTag("timer-step-2") and hasText("100g"))
        waitForText("총 2차 추출 · 합계 물량 145g · 총 시간 0:58")
        tap(button("단계 로그로 옮기기"))
        waitForText("새 기록")
        saveForm("어림 물량")
        assertEquals(
            listOf(
                RecipeStep("0:00", "45", "9", "1차 푸어"), RecipeStep("0:09", "", "3", "대기"),
                RecipeStep("0:12", "100", "16", "2차 푸어"), RecipeStep("0:28", "", "30", "드로우다운"),
            ),
            entries().single().steps,
        )
    }

    @Test
    fun keepPouring_afterAnEarlyPourEnd_theWholePourIsEstimated_andAnEstimateLeftAloneIsSavedAsItsNumber() {
        openTimerFromNewForm("붓기 계속")
        tap(button("💧 붓기 시작"))
        advance(5)
        tap(button("붓기 끝"))
        waitFor(hasTestTag("grams-panel"))
        tap(button("붓기 계속"))
        waitGone(hasTestTag("grams-panel"))
        waitForText("[ 붓는 중 ]")
        advance(10)
        tap(button("붓기 끝"))
        // 15 s of pouring: 90 g
        waitFor(hasTestTag("timer-row-0") and hasText("1차 푸어 · ≈ 90g"))
        // 추출 끝 does not wait for it either
        advance(20)
        tap(button("추출 끝"))
        waitForText("총 1차 추출 · 합계 물량 90g · 총 시간 0:35")
        tap(button("단계 로그로 옮기기"))
        waitForText("새 기록")
        saveForm("붓기 계속")
        assertEquals(listOf(RecipeStep("0:00", "90", "15", "1차 푸어"), RecipeStep("0:15", "", "20", "드로우다운")), entries().single().steps)
    }

    @Test
    fun aMistakenPour_isRemovedWithItsX_undoBringsItBack_andTheStepLogAndTotalsLeaveItOut() {
        openTimerFromNewForm("잘못 누른 붓기")
        tap(button("💧 붓기 시작"))
        advance(10)
        tap(button("붓기 끝"))
        enterGrams("50")
        advance(2)
        tap(button("뜸"))
        // 붓기 시작·끝 tapped by mistake during the bloom
        advance(8)
        tap(button("💧 붓기 시작"))
        advance(2)
        tap(button("붓기 끝"))
        waitForText("부은 물 ≈ 60g", substring = true)
        assertTrue(has(hasTestTag("timer-row-2") and hasText("2차 푸어 · ≈ 10g")))
        // the ✕ is its own button with a label; the row still opens the grams
        tap(hasContentDescription("2차 푸어 삭제") and hasClickAction())
        waitFor(hasText("2차 푸어를 지웠어요") and hasAnyAncestor(hasTestTag("timer-removed")))
        waitGone(hasTestTag("grams-panel"))
        waitForText("부은 물 50g", substring = true)
        assertFalse("the pour's row and its wait are gone", has(hasTestTag("timer-row-2")))
        // 되돌리기 brings it back as it was
        tap(button("되돌리기"))
        waitFor(hasTestTag("timer-row-2") and hasText("2차 푸어 · ≈ 10g"))
        waitGone(hasTestTag("timer-removed"))
        tap(hasContentDescription("2차 푸어 삭제") and hasClickAction())
        waitFor(hasTestTag("timer-removed"))
        // the real second pour: numbered 2, and the removal can no longer be undone
        advance(20)
        tap(button("💧 붓기 시작"))
        waitGone(hasTestTag("timer-removed"))
        // a pour still going can be cancelled too
        advance(1)
        tap(hasContentDescription("2차 푸어 삭제") and hasClickAction())
        waitForText("[ 진행 중 ]", substring = true)
        tap(button("되돌리기"))
        waitForText("[ 붓는 중 ]", substring = true)
        advance(19)
        tap(button("붓기 끝"))
        enterGrams("190")
        waitForText("부은 물 240g", substring = true)
        advance(60)
        tap(button("추출 끝"))
        waitForText("총 2차 추출 · 합계 물량 240g · 총 시간 2:02")
        tap(button("단계 로그로 옮기기"))
        waitForText("새 기록")
        saveForm("잘못 누른 붓기")
        assertEquals(
            listOf(
                RecipeStep("0:00", "50", "10", "1차 푸어"), RecipeStep("0:10", "", "32", "뜸"),
                RecipeStep("0:42", "190", "20", "2차 푸어"), RecipeStep("1:02", "", "60", "드로우다운"),
            ),
            entries().single().steps,
        )
    }

    @Test
    fun viewModel_estimate_recipeFirst_thenFromTheTime_theNextPourAtOnce_pouredSoFarCountsIt() {
        val ref = RecipeRef("테스트", listOf(RecipeStep("0:00", "50", "30", "뜸"), RecipeStep("0:30", "200", "60", "2차")))
        lateinit var vm: BrewTimerViewModel
        // the ui flow is combined on the main thread: act, then let it settle before reading
        fun act(block: () -> Unit) {
            compose.runOnUiThread(block)
            Thread.sleep(300)
            compose.waitForIdle()
        }
        act { vm = BrewTimerViewModel(BrewTimerArgs(ref), clock) }
        act { vm.startPour(); clock.advance(20_000); vm.endPour() }
        // the recipe's first pour (50 g), not 20 s × 6 g/s
        assertEquals(
            BrewTimerUi.GramsPanel(0, 1, grams = "50", estimated = true, estimate = "50", suggestion = "50", canKeepPouring = true),
            vm.ui.value.gramsPanel,
        )
        assertEquals(50.0, vm.ui.value.pouredSoFar, 0.0)
        assertTrue(vm.ui.value.pouredEstimated)
        // the next pour at once, nothing confirmed
        act { clock.advance(2_000); vm.startPour() }
        assertTrue(vm.ui.value.pouring)
        assertNull(vm.ui.value.gramsPanel)
        act { clock.advance(10_000); vm.endPour() }
        assertEquals("200", vm.ui.value.gramsPanel?.estimate)
        // past the recipe's pours: 7 s → 42 g → 40 g
        act { clock.advance(3_000); vm.startPour(); clock.advance(7_000); vm.endPour() }
        assertEquals(BrewTimerUi.GramsPanel(4, 3, grams = "40", estimated = true, estimate = "40", suggestion = null, canKeepPouring = true), vm.ui.value.gramsPanel)
        assertEquals(290.0, vm.ui.value.pouredSoFar, 0.0)
        // typed over the estimate, then the other two confirmed as they are
        act { vm.setGrams("35"); vm.confirmGrams() }
        assertEquals(285.0, vm.ui.value.pouredSoFar, 0.0)
        assertTrue(vm.ui.value.pouredEstimated)
        act { vm.editGrams(0); vm.confirmGrams(); vm.editGrams(2); vm.confirmGrams() }
        assertFalse(vm.ui.value.pouredEstimated)
        assertEquals(listOf("50", "200", "35"), vm.ui.value.rows.filter { it.pour }.map { it.grams })
        act { vm.viewModelScope.cancel() }
    }

    @Test
    fun recipeCountdown_vibratesAtStepChanges_andAsksBeforeReplacingTheLog() {
        launchApp()
        openNewForm()
        typeInto(namePlaceholder, "레시피 타이머")
        clickText("☕ 카페 레시피")
        val recipe = CafeRecipes.all.first { it.steps.size >= 2 }
        val first = recipe.steps.map { RecipeSteps.parseTimeToSec(it.time) ?: 0 }.first { it > 0 }
        clickNode(button("이 레시피 적용 →"), CafeRecipes.all.indexOf(recipe))
        tap(button("⏱ 타이머로 기록"))
        waitForText("추출 타이머")
        waitForText("레시피 · ${recipe.name}")
        waitForText("까지 ${first}초", substring = true)
        val shadow = Shadows.shadowOf(vibrator())
        assertEquals(0L, shadow.milliseconds)
        tap(button("시작"))
        advance(first - 1)
        assertEquals("no step change yet", 0L, shadow.milliseconds)
        advance(1)
        waitUntil("vibrated at the step change") { shadow.milliseconds > 0 }
        tap(button("💧 붓기 시작"))
        advance(5)
        tap(button("붓기 끝"))
        // the recipe's first pour is offered
        waitFor(field(recipe.steps.first { it.water.isNotBlank() }.water))
        tap(button("확인"))
        advance(3)
        tap(button("추출 끝"))
        tap(button("단계 로그로 옮기기"))
        // the form's log was the recipe's rows: ask first
        waitForText("지금 단계 로그를 타이머 기록으로 바꿀까요?", substring = true)
        tap(dialogButton("취소"))
        assertTrue(has(hasText("추출 타이머")))
        tap(button("단계 로그로 옮기기"))
        tap(dialogButton("바꾸기"))
        waitForText("새 기록")
        waitForText("${recipe.name} 대비 이번에 어긋난 부분", substring = true)
    }

    @Test
    fun pauseStopsTheClock_resetAsks_andLeavingAsks() {
        openTimerFromNewForm("일시정지")
        tap(button("시작"))
        advance(5)
        tap(button("일시정지"))
        advance(30)
        waitDisplay("0:05")
        tap(button("계속"))
        advance(3)
        waitDisplay("0:08")
        tap(button("뜸"))
        tap(button("초기화"))
        waitForText("타이머를 처음으로 되돌릴까요?", substring = true)
        tap(dialogButton("초기화"))
        waitDisplay("0:00")
        tap(button("💧 붓기 시작"))
        back()
        waitForText("타이머 기록을 옮기지 않고 나갈까요?", substring = true)
        tap(dialogButton("나가기"))
        waitForText("새 기록")
        assertFalse(has(hasText("추출 타이머")))
    }

    @Test
    fun runningTimerSurvivesAProcessDeath() {
        val handle = SavedStateHandle()
        val ref = RecipeRef("테스트", listOf(RecipeStep("0:00", "50", "30", "뜸"), RecipeStep("0:30", "200", "60", "2차")))
        var first: BrewTimerViewModel? = null
        compose.runOnUiThread {
            val vm = BrewTimerViewModel(BrewTimerArgs(ref), clock, handle)
            first = vm
            vm.startPour()
            clock.advance(12_000)
            vm.endPour()
            vm.setGrams("50")
            clock.advance(8_000)
            vm.tick()
        }
        Thread.sleep(300)
        compose.waitForIdle()
        // a new process: the monotonic clock starts over, the wall clock went on for 20 s more
        val later = FakeBrewClock(mono = 7L, wall = clock.wall + 20_000L)
        compose.runOnUiThread {
            val restored = BrewTimerViewModel(BrewTimerArgs(ref), later, handle)
            val ui = restored.ui.value
            assertEquals(TimerStatus.RUNNING, ui.status)
            assertEquals(40L, ui.elapsedSec)
            assertEquals(2, ui.rows.size)
            assertEquals("50", ui.rows[0].grams)
            assertEquals("2차", ui.guidance?.current?.step?.note)
            restored.viewModelScope.cancel()
            first?.viewModelScope?.cancel()
        }
    }

    @Test
    fun timerControlsHaveLabels() {
        openTimerFromNewForm("라벨")
        assertTrue(has(hasContentDescription("경과 시간 0분 0초, 대기")))
        tap(button("시작"))
        advance(65)
        waitFor(hasContentDescription("경과 시간 1분 5초, 진행 중"))
    }
}
