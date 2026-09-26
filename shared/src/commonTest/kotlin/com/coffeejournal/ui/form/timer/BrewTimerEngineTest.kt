package com.coffeejournal.ui.form.timer

import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.rules.RecipeSteps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrewTimerEngineTest {
    private val E = BrewTimerEngine

    /** A little script: the clock moves only when told. */
    private class Run {
        private val E = BrewTimerEngine
        var mono = 1_000L
        var wall = 1_790_000_000_000L
        var s = BrewTimerState()
        fun at(sec: Double) { val target = 1_000L + (sec * 1000).toLong() + paused; wall += target - mono; mono = target }
        var paused = 0L
        fun elapsed() = E.elapsedMs(s, mono)
    }

    @Test fun pourAndNotesBecomeStepLogRows() {
        val r = Run()
        r.s = E.startPour(r.s, r.mono, r.wall) // 0:00 붓기 시작 (the idle timer starts)
        assertEquals(TimerStatus.RUNNING, r.s.status)
        r.at(9.6); r.s = E.endPour(r.s, r.mono)
        assertEquals(0, r.s.gramsFor)
        r.s = E.setGrams(r.s, "50", r.mono)
        r.at(12.0); r.s = E.note(r.s, E.BLOOM, r.mono, r.wall) // names the wait after the pour
        r.at(40.0); r.s = E.startPour(r.s, r.mono, r.wall)
        r.at(50.4); r.s = E.note(r.s, E.SWIRL, r.mono, r.wall) // during the pour: goes on the pour
        r.at(70.0); r.s = E.endPour(r.s, r.mono); r.s = E.setGrams(r.s, "190", r.mono)
        r.at(80.0); r.s = E.note(r.s, E.DRAWDOWN, r.mono, r.wall) // the wait after the pour has no name yet → named
        r.at(150.9); r.s = E.finish(r.s, r.mono)
        assertEquals(150_900L, r.s.endMs)

        val steps = E.toSteps(r.s.rows, r.s.endMs!!)
        assertEquals(
            listOf(
                RecipeStep("0:00", "50", "9", "1차 푸어"),
                RecipeStep("0:09", "", "31", "뜸"),
                RecipeStep("0:40", "190", "30", "2차 푸어, 스월"),
                RecipeStep("1:10", "", "80", "드로우다운"),
            ),
            steps,
        )
        // the step summary reads them like typed rows: 240 g in 2 pours, 2:30 in all
        val sum = RecipeSteps.summary(steps)
        assertEquals(240.0, sum.totalWater)
        assertEquals(150, sum.totalTimeSec)
        assertEquals(2, sum.pourCount)
    }

    @Test fun unnamedWaitsAreDefaultedAndEmptyOnesDropped() {
        val r = Run()
        r.s = E.startPour(r.s, r.mono, r.wall)
        r.at(10.0); r.s = E.endPour(r.s, r.mono); r.s = E.setGrams(r.s, "40", r.mono)
        r.at(10.5); r.s = E.startPour(r.s, r.mono, r.wall) // the wait lasted no whole second
        r.at(30.0); r.s = E.endPour(r.s, r.mono); r.s = E.setGrams(r.s, "100", r.mono)
        r.at(45.0); r.s = E.startPour(r.s, r.mono, r.wall)
        r.at(60.0); r.s = E.endPour(r.s, r.mono); r.s = E.setGrams(r.s, "100", r.mono)
        r.at(95.0); r.s = E.finish(r.s, r.mono)
        assertEquals(
            listOf(
                RecipeStep("0:00", "40", "10", "1차 푸어"),
                RecipeStep("0:10", "100", "20", "2차 푸어"),
                RecipeStep("0:30", "", "15", "대기"),
                RecipeStep("0:45", "100", "15", "3차 푸어"),
                RecipeStep("1:00", "", "35", "드로우다운"),
            ),
            E.toSteps(r.s.rows, r.s.endMs!!),
        )
    }

    @Test fun pausesAreNotTimerTime() {
        val r = Run()
        r.s = E.start(r.s, r.mono, r.wall)
        r.at(10.0); r.s = E.pause(r.s, r.mono)
        assertEquals(10_000L, r.elapsed())
        r.at(70.0) // a minute on the wall clock
        assertEquals(10_000L, r.elapsed(), message = "paused")
        r.s = E.start(r.s, r.mono, r.wall)
        r.at(75.0)
        assertEquals(15_000L, r.elapsed())
        r.s = E.startPour(r.s, r.mono, r.wall)
        assertEquals(15_000L, r.s.rows.single().startMs)
    }

    @Test fun finishingWhilePouringAsksForTheGramsFirst() {
        val r = Run()
        r.s = E.startPour(r.s, r.mono, r.wall)
        r.at(20.0); r.s = E.finish(r.s, r.mono)
        assertNull(r.s.endMs)
        assertTrue(r.s.finishing)
        assertEquals(0, r.s.gramsFor)
        r.at(25.0); r.s = E.setGrams(r.s, "250", r.mono)
        assertEquals(25_000L, r.s.endMs, "finishes when the grams are in")
        assertEquals(listOf(RecipeStep("0:00", "250", "20", "1차 푸어"), RecipeStep("0:20", "", "5", "드로우다운")), E.toSteps(r.s.rows, r.s.endMs!!))
        // after the finish the controls do nothing until 이어서 추출
        assertEquals(r.s, E.startPour(r.s, r.mono, r.wall))
        val resumed = E.resume(r.s)
        assertNull(resumed.endMs)
        assertEquals(TimerStatus.PAUSED, resumed.status)
    }

    @Test fun gramsMustBeAPositiveNumber() {
        val r = Run()
        r.s = E.startPour(r.s, r.mono, r.wall)
        r.at(5.0); r.s = E.endPour(r.s, r.mono)
        assertEquals(r.s, E.setGrams(r.s, "", r.mono))
        assertEquals(r.s, E.setGrams(r.s, "0", r.mono))
        assertEquals(r.s, E.setGrams(r.s, "NaN", r.mono))
        assertEquals("45.5", E.setGrams(r.s, "45.50", r.mono).rows[0].grams)
    }

    @Test fun keepPouringUndoesAnEarlyPourEnd() {
        val r = Run()
        r.s = E.startPour(r.s, r.mono, r.wall)
        r.at(5.0); r.s = E.endPour(r.s, r.mono)
        val back = E.cancelEndPour(r.s)
        assertTrue(back.pouring)
        assertNull(back.gramsFor)
        assertEquals(1, back.rows.size)
    }

    @Test fun restoredTimerCountsTheTimeTheProcessWasGone() {
        val r = Run()
        r.s = E.start(r.s, r.mono, r.wall)
        r.at(30.0)
        // the process dies; a new one has a new monotonic origin, the wall clock moved on 45 s in all
        val restored = E.restored(r.s, mono = 5L, wall = r.wall + 15_000L)
        assertEquals(45_000L, E.elapsedMs(restored, 5L))
        assertEquals(46_000L, E.elapsedMs(restored, 1_005L))
        // a paused timer comes back unchanged
        val paused = E.pause(r.s, r.mono)
        assertEquals(paused, E.restored(paused, 5L, r.wall + 60_000L))
    }

    private val yourHome = RecipeRef(
        "유어홈", listOf(RecipeStep("0:00", "50", "10", "1차 푸어"), RecipeStep("0:10", "", "40", "스월, 대기"), RecipeStep("0:50", "190", "30", "2차 푸어"), RecipeStep("1:20", "", "70", "드로우다운")),
    )

    @Test fun recipeGuidanceCountsDownToTheNextStep() {
        val g0 = E.guidance(yourHome, 0)!!
        assertEquals("1차 푸어", g0.current?.step?.note)
        assertEquals("스월, 대기", g0.next?.step?.note)
        assertEquals(10, g0.secondsToNext)
        val g = E.guidance(yourHome, 32_000)!!
        assertEquals("스월, 대기", g.current?.step?.note)
        assertEquals("2차 푸어", g.next?.step?.note)
        assertEquals(18, g.secondsToNext)
        assertEquals("190", g.nextWater)
        assertEquals(240.0, g.cumulativeTarget)
        val last = E.guidance(yourHome, 100_000)!!
        assertNull(last.next)
        assertEquals(50, last.secondsToNext, "until the recipe's 2:30")
        assertFalse(last.finished)
        assertTrue(E.guidance(yourHome, 151_000)!!.finished)
        assertNull(E.guidance(RecipeRef("빈", emptyList()), 0))
    }

    @Test fun stepChangesToBuzzFor() {
        assertEquals(0, E.boundariesPassed(yourHome, 9_999))
        assertEquals(1, E.boundariesPassed(yourHome, 10_000))
        assertEquals(3, E.boundariesPassed(yourHome, 80_000))
        assertEquals(4, E.boundariesPassed(yourHome, 150_000), "and the recipe's end")
        assertEquals(0, E.boundariesPassed(null, 150_000))
    }

    @Test fun suggestsTheRecipePourWithTheSameNumber() {
        val rows = listOf(TimerRow(0, pour = true, grams = "50"), TimerRow(10_000, pour = false), TimerRow(48_000, pour = true))
        assertEquals("50", E.suggestedGrams(yourHome, rows, 0))
        assertEquals("190", E.suggestedGrams(yourHome, rows, 2))
        assertNull(E.suggestedGrams(yourHome, rows + TimerRow(90_000, pour = true), 3), "the recipe has only two pours")
        assertNull(E.suggestedGrams(null, rows, 0))
    }

    @Test fun resultRoundTripsThroughTheBackStack() {
        val steps = listOf(RecipeStep("0:00", "50", "9", "뜸"), RecipeStep("0:09", "", "31", "대기"))
        assertEquals(steps, BrewTimerResult.decode(BrewTimerResult.encode(steps)))
        assertNull(BrewTimerResult.decode("not json"))
        assertEquals(yourHome, BrewTimerResult.decodeRecipe(BrewTimerResult.encodeRecipe(yourHome)))
        assertNull(BrewTimerResult.encodeRecipe(null))
    }
}
