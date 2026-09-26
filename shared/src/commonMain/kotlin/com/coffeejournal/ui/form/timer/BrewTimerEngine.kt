package com.coffeejournal.ui.form.timer

import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Numbers
import com.coffeejournal.domain.rules.Prices
import com.coffeejournal.domain.rules.RecipeSteps
import kotlinx.serialization.Serializable
import kotlin.time.TimeSource

/**
 * Time for the brew timer. [monotonicMs] measures the running timer (never jumps with a clock change); [wallMs] only
 * bridges a process death, when the monotonic origin of the old process is gone. Tests drive a fake.
 */
interface BrewClock {
    fun monotonicMs(): Long
    fun wallMs(): Long
}

object SystemBrewClock : BrewClock {
    private val origin = TimeSource.Monotonic.markNow()
    override fun monotonicMs(): Long = origin.elapsedNow().inWholeMilliseconds
    override fun wallMs(): Long = Dates.nowMillis()
}

@Serializable
enum class TimerStatus { IDLE, RUNNING, PAUSED }

/** One segment of the brew as it happened: a pour (from 붓기 시작 to 붓기 끝) or the wait after it. */
@Serializable
data class TimerRow(
    val startMs: Long,
    val pour: Boolean,
    val grams: String = "",
    val notes: List<String> = emptyList(),
)

/**
 * Everything the timer screen keeps, serializable so it survives process death (saved in the destination's
 * SavedStateHandle). Times are timer time (pauses excluded), in ms since 0:00.
 */
@Serializable
data class BrewTimerState(
    val status: TimerStatus = TimerStatus.IDLE,
    /** Timer time run before the current running stretch. */
    val accumulatedMs: Long = 0L,
    /** [BrewClock.monotonicMs] when the current running stretch began (only while running). */
    val runStartMono: Long = 0L,
    /** [BrewClock.wallMs] at the same moment, to rebuild the running time after a process death. */
    val runStartWall: Long = 0L,
    val rows: List<TimerRow> = emptyList(),
    /** The last row is a pour still going (붓기 끝 not pressed yet). */
    val pouring: Boolean = false,
    /** Index of the pour whose grams the 붓기 끝 dialog is asking for. */
    val gramsFor: Int? = null,
    /** 추출 끝 was pressed while pouring: finish once the grams are in. */
    val finishing: Boolean = false,
    /** Timer time at 추출 끝 (the end of the last row), null while brewing. */
    val endMs: Long? = null,
    /** How many recipe step changes were already announced (so a restored timer does not buzz for old ones). */
    val announced: Int = 0,
)

/** The pure rules of the brew timer (feature-plan-v2 §2.1); the view model only adds the clock and saving. */
object BrewTimerEngine {
    const val BLOOM = "뜸"
    const val SWIRL = "스월"
    const val DRAWDOWN = "드로우다운"
    val quickNotes: List<String> = listOf(BLOOM, SWIRL, DRAWDOWN)

    fun elapsedMs(s: BrewTimerState, nowMono: Long): Long = when (s.status) {
        TimerStatus.RUNNING -> s.accumulatedMs + (nowMono - s.runStartMono).coerceAtLeast(0)
        else -> s.accumulatedMs
    }

    fun start(s: BrewTimerState, mono: Long, wall: Long): BrewTimerState =
        if (s.status == TimerStatus.RUNNING || s.endMs != null) s else s.copy(status = TimerStatus.RUNNING, runStartMono = mono, runStartWall = wall)

    fun pause(s: BrewTimerState, mono: Long): BrewTimerState =
        if (s.status != TimerStatus.RUNNING) s else s.copy(status = TimerStatus.PAUSED, accumulatedMs = elapsedMs(s, mono))

    fun reset(): BrewTimerState = BrewTimerState()

    /** 붓기 시작: a new pour row now; an idle timer starts at 0:00 with it. */
    fun startPour(s: BrewTimerState, mono: Long, wall: Long): BrewTimerState {
        if (s.pouring || s.gramsFor != null || s.endMs != null) return s
        val running = if (s.status == TimerStatus.IDLE) start(s, mono, wall) else s
        return running.copy(rows = running.rows + TimerRow(elapsedMs(running, mono), pour = true), pouring = true)
    }

    /** 붓기 끝: closes the pour, asks for its grams, and the wait after it begins. */
    fun endPour(s: BrewTimerState, mono: Long): BrewTimerState {
        if (!s.pouring) return s
        val at = elapsedMs(s, mono)
        return s.copy(rows = s.rows + TimerRow(at, pour = false), pouring = false, gramsFor = s.rows.lastIndex)
    }

    /** "붓기 계속" in the grams dialog: 붓기 끝 was pressed too early, so the pour goes on. */
    fun cancelEndPour(s: BrewTimerState): BrewTimerState {
        val i = s.gramsFor ?: return s
        val last = s.rows.lastOrNull() ?: return s
        if (i != s.rows.lastIndex - 1 || last.pour || last.notes.isNotEmpty()) return s
        return s.copy(rows = s.rows.dropLast(1), pouring = true, gramsFor = null, finishing = false)
    }

    /** The grams of the pour the dialog asked about; a finish that waited for them completes. */
    fun setGrams(s: BrewTimerState, grams: String, mono: Long): BrewTimerState {
        val i = s.gramsFor ?: return s
        val value = Numbers.parse(grams)?.takeIf { it > 0 }?.let { Prices.trimNumber(it) } ?: return s
        val next = s.copy(rows = s.rows.mapIndexed { k, r -> if (k == i) r.copy(grams = value) else r }, gramsFor = null)
        return if (s.finishing) finish(next.copy(finishing = false), mono) else next
    }

    /**
     * 뜸 / 스월 / 드로우다운: while pouring the note goes on the pour; otherwise it names the current wait, or starts a
     * new wait row when the current one already has a name. An idle timer starts with it.
     */
    fun note(s: BrewTimerState, label: String, mono: Long, wall: Long): BrewTimerState {
        if (s.endMs != null) return s
        val running = if (s.status == TimerStatus.IDLE) start(s, mono, wall) else s
        val at = elapsedMs(running, mono)
        val last = running.rows.lastOrNull()
        val rows = when {
            last == null -> listOf(TimerRow(at, pour = false, notes = listOf(label)))
            running.pouring -> running.rows.dropLast(1) + last.copy(notes = (last.notes + label).distinct())
            !last.pour && last.notes.isEmpty() -> running.rows.dropLast(1) + last.copy(notes = listOf(label))
            !last.pour && label in last.notes -> running.rows
            else -> running.rows + TimerRow(at, pour = false, notes = listOf(label))
        }
        return running.copy(rows = rows)
    }

    /** 추출 끝: stops the timer; a pour still going is closed first and its grams asked for. */
    fun finish(s: BrewTimerState, mono: Long): BrewTimerState {
        if (s.rows.isEmpty() || s.endMs != null) return s
        if (s.pouring) return endPour(s, mono).copy(finishing = true)
        if (s.gramsFor != null) return s.copy(finishing = true)
        val paused = pause(s, mono)
        return paused.copy(endMs = paused.accumulatedMs)
    }

    /** Back to brewing after 추출 끝 (the result was not taken). */
    fun resume(s: BrewTimerState): BrewTimerState = if (s.endMs == null) s else s.copy(endMs = null, status = TimerStatus.PAUSED)

    /**
     * After a process death: a running timer continues from the wall-clock time that passed since its stretch began,
     * and the monotonic origin is taken anew from this process.
     */
    fun restored(s: BrewTimerState, mono: Long, wall: Long): BrewTimerState {
        if (s.status != TimerStatus.RUNNING) return s
        val ran = (wall - s.runStartWall).coerceAtLeast(0)
        return s.copy(accumulatedMs = s.accumulatedMs + ran, runStartMono = mono, runStartWall = wall)
    }

    // ---------- step log ----------

    /**
     * The rows as extraction-log steps (the record's [RecipeStep]: start time "M:SS", water of a pour, wait = seconds
     * until the next step, the last step's wait running to 추출 끝), so the step summary and the recipe diff work on
     * them as on typed rows. Pours are numbered ("2차 푸어, 스월"); an unnamed wait reads 대기, or 드로우다운 when it is
     * the last row. Times are whole seconds; an unnamed wait that lasted no whole second is dropped.
     */
    fun toSteps(rows: List<TimerRow>, endMs: Long): List<RecipeStep> {
        if (rows.isEmpty()) return emptyList()
        val secs = rows.map { (it.startMs / 1000).toInt() }
        val end = maxOf((endMs / 1000).toInt(), secs.last())
        data class Seg(val row: TimerRow, val start: Int, val next: Int)
        val segs = rows.indices.map { i -> Seg(rows[i], secs[i], if (i < rows.lastIndex) maxOf(secs[i + 1], secs[i]) else end) }
            .filter { it.row.pour || it.row.notes.isNotEmpty() || it.next > it.start }
        var pourNo = 0
        return segs.mapIndexed { i, seg ->
            val next = segs.getOrNull(i + 1)?.start ?: end
            val wait = (next - seg.start).coerceAtLeast(0)
            val note = when {
                seg.row.pour -> { pourNo++; (listOf("${pourNo}차 푸어") + seg.row.notes).joinToString(", ") }
                seg.row.notes.isNotEmpty() -> seg.row.notes.joinToString(", ")
                i == segs.lastIndex -> DRAWDOWN
                else -> "대기"
            }
            RecipeStep(
                time = RecipeSteps.formatSec(seg.start) ?: "0:00",
                water = if (seg.row.pour) seg.row.grams else "",
                wait = wait.toString(),
                note = note,
            )
        }
    }

    // ---------- recipe guidance ----------

    /** A recipe step with its start time in seconds. */
    data class TimedStep(val index: Int, val step: RecipeStep, val startSec: Int)

    /** What the timer shows for an applied recipe at a moment. */
    data class Guidance(
        val recipeName: String,
        /** The step under way (null before the first one starts). */
        val current: TimedStep?,
        /** The step after it (null once the last one started). */
        val next: TimedStep?,
        /** Whole seconds until [next] starts (or until the recipe ends when there is no next step). */
        val secondsToNext: Int?,
        /** The next step's own water (g), if it is a pour. */
        val nextWater: String?,
        /** Water the recipe has poured by the end of [next] (g). */
        val cumulativeTarget: Double?,
        /** The recipe's end (last step time + its wait), if known. */
        val endSec: Int?,
        val finished: Boolean,
    )

    fun timedSteps(ref: RecipeRef): List<TimedStep> =
        ref.steps.mapIndexedNotNull { i, s -> RecipeSteps.parseTimeToSec(s.time)?.let { TimedStep(i, s, it) } }.sortedBy { it.startSec }

    fun recipeEndSec(ref: RecipeRef): Int? = RecipeSteps.summary(ref.steps).totalTimeSec

    fun guidance(ref: RecipeRef, elapsedMs: Long): Guidance? {
        val steps = timedSteps(ref)
        if (steps.isEmpty()) return null
        val t = elapsedMs / 1000.0
        val current = steps.lastOrNull { it.startSec <= t }
        val next = steps.firstOrNull { it.startSec > t }
        val end = recipeEndSec(ref)
        val targetSec = next?.startSec ?: end?.takeIf { it > t }
        val secondsToNext = targetSec?.let { kotlin.math.ceil(it - t).toInt() }
        val cumulative = next?.let { n -> steps.filter { it.startSec <= n.startSec }.sumOf { Numbers.parse(it.step.water) ?: 0.0 } }
        return Guidance(
            recipeName = ref.name,
            current = current,
            next = next,
            secondsToNext = secondsToNext,
            nextWater = next?.step?.water?.takeIf { it.isNotBlank() },
            cumulativeTarget = cumulative?.takeIf { it > 0 },
            endSec = end,
            finished = next == null && (end == null || t >= end),
        )
    }

    /** Step changes (each step start after 0:00, and the recipe's end) reached by [elapsedMs]; the timer buzzes when it grows. */
    fun boundariesPassed(ref: RecipeRef?, elapsedMs: Long): Int {
        ref ?: return 0
        val marks = (timedSteps(ref).map { it.startSec }.filter { it > 0 } + listOfNotNull(recipeEndSec(ref))).distinct()
        return marks.count { it * 1000L <= elapsedMs }
    }

    /** The grams to suggest in the 붓기 끝 dialog: the water of the recipe's pour with the same number (1st, 2nd …). */
    fun suggestedGrams(ref: RecipeRef?, rows: List<TimerRow>, pourRowIndex: Int): String? {
        ref ?: return null
        val ordinal = rows.take(pourRowIndex + 1).count { it.pour } - 1
        if (ordinal < 0) return null
        return ref.steps.filter { (Numbers.parse(it.water) ?: 0.0) > 0 }.getOrNull(ordinal)?.water
    }
}
