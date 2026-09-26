package com.coffeejournal.android

import android.content.Context
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import com.coffeejournal.ui.form.timer.BrewTimerViewModel
import com.coffeejournal.ui.form.timer.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * The brew timer (feature-plan-v2 §2.1) driven through the real app with a fake clock: pours, notes, the grams dialog,
 * the step log it writes into the form, the recipe countdown and its vibration, keeping the screen on, the replace
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

    private fun enterGrams(grams: String) {
        waitForText("이번에 부은 물을 적어 주세요 (g).")
        typeInto("물량g", grams)
        tap(button("확인"))
        waitGone(hasText("이번에 부은 물을 적어 주세요 (g)."))
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
