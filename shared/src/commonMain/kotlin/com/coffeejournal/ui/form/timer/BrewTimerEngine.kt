package com.coffeejournal.ui.form.timer

import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Numbers
import com.coffeejournal.domain.rules.Prices
import com.coffeejournal.domain.rules.RecipeSteps
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
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

/**
 * One segment of the brew as it happened: a pour (from 붓기 시작 to 붓기 끝) or the wait after it. A finished pour always
 * has [grams]: the app's estimate ([BrewTimerEngine.estimateGrams], [estimated]) until the user types or confirms them.
 */
@Serializable
data class TimerRow(
    val startMs: Long,
    val pour: Boolean,
    val grams: String = "",
    val notes: List<String> = emptyList(),
    val estimated: Boolean = false,
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
    /** Index of the pour whose grams the panel shows (the pour just ended, or one tapped in the log); nothing waits for it. */
    val gramsFor: Int? = null,
    /** Timer time at 추출 끝 (the end of the last row), null while brewing. */
    val endMs: Long? = null,
    /** How many recipe step changes were already announced (so a restored timer does not buzz for old ones). */
    val announced: Int = 0,
    /** The pour last removed with ✕, kept so 되돌리기 can bring it back while the log is still as the removal left it. */
    val removed: RemovedPour? = null,
)

/**
 * A pour taken out of the log ([BrewTimerEngine.removePour]): the log as it was before ([rows], [pouring],
 * [gramsFor]) and as the removal left it ([after]; 되돌리기 is offered only while the log still is that). [at] is where
 * the pour's row was in [after], [pourNumber] its number ("2차 푸어").
 */
@Serializable
data class RemovedPour(
    val pourNumber: Int,
    val at: Int,
    val rows: List<TimerRow>,
    val pouring: Boolean,
    val gramsFor: Int?,
    val after: List<TimerRow>,
)

/** The pure rules of the brew timer (feature-plan-v2 §2.1); the view model only adds the clock and saving. */
object BrewTimerEngine {
    const val BLOOM = "뜸"
    const val SWIRL = "스월"
    const val DRAWDOWN = "드로우다운"
    val quickNotes: List<String> = listOf(BLOOM, SWIRL, DRAWDOWN)

    /** A pour the recipe gives no grams for is taken to run at this rate (a kettle's steady spiral pour). */
    const val POUR_GRAMS_PER_SECOND = 6

    /** An estimate is rounded to this step (g), and no finished pour is estimated below it. */
    const val ESTIMATE_STEP_GRAMS = 10

    fun elapsedMs(s: BrewTimerState, nowMono: Long): Long = when (s.status) {
        TimerStatus.RUNNING -> s.accumulatedMs + (nowMono - s.runStartMono).coerceAtLeast(0)
        else -> s.accumulatedMs
    }

    fun start(s: BrewTimerState, mono: Long, wall: Long): BrewTimerState =
        if (s.status == TimerStatus.RUNNING || s.endMs != null) s else s.copy(status = TimerStatus.RUNNING, runStartMono = mono, runStartWall = wall)

    fun pause(s: BrewTimerState, mono: Long): BrewTimerState =
        if (s.status != TimerStatus.RUNNING) s else s.copy(status = TimerStatus.PAUSED, accumulatedMs = elapsedMs(s, mono))

    fun reset(): BrewTimerState = BrewTimerState()

    /**
     * 붓기 시작: a new pour row now; an idle timer starts at 0:00 with it. It never waits for the last pour's grams: that
     * pour keeps its estimate and the grams panel closes (a tap on the pour in the log opens it again).
     */
    fun startPour(s: BrewTimerState, mono: Long, wall: Long): BrewTimerState {
        if (s.pouring || s.endMs != null) return s
        val running = if (s.status == TimerStatus.IDLE) start(s, mono, wall) else s
        return running.copy(rows = running.rows + TimerRow(elapsedMs(running, mono), pour = true), pouring = true, gramsFor = null)
    }

    /**
     * 붓기 끝: closes the pour with its estimated grams ([estimateGrams] with the applied recipe [ref]), opens the grams
     * panel for it, and the wait after it begins.
     */
    fun endPour(s: BrewTimerState, mono: Long, ref: RecipeRef? = null): BrewTimerState {
        if (!s.pouring) return s
        val at = elapsedMs(s, mono)
        val i = s.rows.lastIndex
        val pour = s.rows[i].copy(grams = estimateGrams(ref, s.rows, i, at), estimated = true)
        return s.copy(rows = s.rows.dropLast(1) + pour + TimerRow(at, pour = false), pouring = false, gramsFor = i)
    }

    /** "붓기 계속" applies: the panel is on the pour just ended, the brew goes on and nothing was noted after the pour. */
    fun canKeepPouring(s: BrewTimerState): Boolean {
        val last = s.rows.lastOrNull() ?: return false
        return s.endMs == null && s.gramsFor == s.rows.lastIndex - 1 && !last.pour && last.notes.isEmpty()
    }

    /** "붓기 계속" in the grams panel: 붓기 끝 was pressed too early, so the pour goes on (and is estimated again when it ends). */
    fun cancelEndPour(s: BrewTimerState): BrewTimerState {
        if (!canKeepPouring(s)) return s
        val pour = s.rows[s.rows.lastIndex - 1].copy(grams = "", estimated = false)
        return s.copy(rows = s.rows.dropLast(2) + pour, pouring = true, gramsFor = null)
    }

    /** A tap on a finished pour in the log: its grams in the panel. */
    fun editGrams(s: BrewTimerState, rowIndex: Int): BrewTimerState {
        val row = s.rows.getOrNull(rowIndex) ?: return s
        if (!row.pour || (s.pouring && rowIndex == s.rows.lastIndex)) return s
        return s.copy(gramsFor = rowIndex)
    }

    /**
     * Typing in the grams panel, applied as it is typed (so starting the next pour or 추출 끝 keeps it): a positive number
     * is the pour's grams; an emptied field gives the pour its estimate back; anything else changes nothing.
     */
    fun setGrams(s: BrewTimerState, grams: String, ref: RecipeRef? = null): BrewTimerState {
        val i = s.gramsFor ?: return s
        val row = s.rows.getOrNull(i)?.takeIf { it.pour } ?: return s
        val next = if (grams.isBlank()) {
            val end = s.rows.getOrNull(i + 1)?.startMs ?: return s
            row.copy(grams = estimateGrams(ref, s.rows, i, end), estimated = true)
        } else {
            val value = Numbers.parse(grams)?.takeIf { it > 0 }?.let { Prices.trimNumber(it) } ?: return s
            row.copy(grams = value, estimated = false)
        }
        return s.copy(rows = s.rows.mapIndexed { k, r -> if (k == i) next else r })
    }

    /**
     * ✕ on a pour in the log: 붓기 시작·끝 was tapped by mistake, so the pour never happened. Its time becomes part of
     * the wait around it (an unnamed wait joins the wait before it, and one left at the top of the log is dropped, as
     * if the timer had been started with 시작), a note it carried stays on that time ("스월" did happen), and a pour
     * still going is simply cancelled. The clock is not touched. The pours after it move up a number, so their
     * estimates are taken again (the recipe's 2nd pour becomes its 1st); grams the user typed or confirmed stay.
     */
    fun removePour(s: BrewTimerState, rowIndex: Int, ref: RecipeRef? = null): BrewTimerState {
        val pour = s.rows.getOrNull(rowIndex)?.takeIf { it.pour } ?: return s
        val running = s.pouring && rowIndex == s.rows.lastIndex
        val converted = s.rows.toMutableList().also { it[rowIndex] = TimerRow(pour.startMs, pour = false, notes = pour.notes) }
        val kept = converted.indices.filter { k ->
            val r = converted[k]
            // only the pour's own row and the wait right after it can merge; elsewhere the log stays as it was
            val joinsWaitBefore = (k == rowIndex || k == rowIndex + 1) && !r.pour && r.notes.isEmpty() && (k == 0 || !converted[k - 1].pour)
            !joinsWaitBefore
        }
        val merged = kept.map { converted[it] }
        val rows = merged.mapIndexed { i, r ->
            val end = merged.getOrNull(i + 1)?.startMs
            if (r.pour && r.estimated && end != null) r.copy(grams = estimateGrams(ref, merged, i, end)) else r
        }
        val gramsFor = s.gramsFor?.takeIf { it != rowIndex }?.let { kept.indexOf(it) }?.takeIf { it >= 0 }
        val removed = RemovedPour(
            pourNumber = s.rows.take(rowIndex + 1).count { it.pour }, at = kept.count { it < rowIndex },
            rows = s.rows, pouring = s.pouring, gramsFor = s.gramsFor, after = rows,
        )
        return s.copy(rows = rows, pouring = s.pouring && !running, gramsFor = gramsFor, removed = removed)
    }

    /** 되돌리기 applies: a pour was removed, nothing in the log changed since, and the brew is still going. */
    fun canUndoRemove(s: BrewTimerState): Boolean = s.removed != null && s.removed.after == s.rows && s.endMs == null

    /** 되돌리기 after ✕: the pour is back as it was (a pour that was still going goes on). */
    fun undoRemove(s: BrewTimerState): BrewTimerState {
        val r = s.removed ?: return s
        if (!canUndoRemove(s)) return s
        return s.copy(rows = r.rows, pouring = r.pouring, gramsFor = r.gramsFor, removed = null)
    }

    /** 확인 in the grams panel: the pour's grams as they are now (typed, or the estimate accepted), and the panel closes. */
    fun confirmGrams(s: BrewTimerState): BrewTimerState {
        val i = s.gramsFor ?: return s
        return s.copy(rows = s.rows.mapIndexed { k, r -> if (k == i) r.copy(estimated = false) else r }, gramsFor = null)
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

    /**
     * 추출 끝: stops the timer at once. A pour still going is closed first with its estimate, and the grams panel stays
     * on it, so its real grams can still be typed before the rows go to the step log.
     */
    fun finish(s: BrewTimerState, mono: Long, ref: RecipeRef? = null): BrewTimerState {
        if (s.rows.isEmpty() || s.endMs != null) return s
        val paused = pause(if (s.pouring) endPour(s, mono, ref) else s, mono)
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
    fun toSteps(rows: List<TimerRow>, endMs: Long): List<RecipeStep> = stepRows(rows, endMs).map { it.second }

    /**
     * [toSteps] with the index of the row each step comes from (a pour's step opens that pour's grams). An estimate
     * goes in as its plain number: the step log and the saved record have no way to mark it, and leaving it is
     * accepting it.
     */
    fun stepRows(rows: List<TimerRow>, endMs: Long): List<Pair<Int, RecipeStep>> {
        if (rows.isEmpty()) return emptyList()
        val secs = rows.map { (it.startMs / 1000).toInt() }
        val end = maxOf((endMs / 1000).toInt(), secs.last())
        data class Seg(val index: Int, val row: TimerRow, val start: Int, val next: Int)
        val segs = rows.indices.map { i -> Seg(i, rows[i], secs[i], if (i < rows.lastIndex) maxOf(secs[i + 1], secs[i]) else end) }
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
            seg.index to RecipeStep(
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

    /** The recipe's grams for a pour: the water of the recipe's pour with the same number (1st, 2nd …). */
    fun suggestedGrams(ref: RecipeRef?, rows: List<TimerRow>, pourRowIndex: Int): String? {
        ref ?: return null
        val ordinal = rows.take(pourRowIndex + 1).count { it.pour } - 1
        if (ordinal < 0) return null
        return ref.steps.filter { (Numbers.parse(it.water) ?: 0.0) > 0 }.getOrNull(ordinal)?.water
    }

    /**
     * The grams a pour that lasted [durationMs] is taken to have: [POUR_GRAMS_PER_SECOND], to the nearest
     * [ESTIMATE_STEP_GRAMS] (halves up), and never less than that step.
     */
    fun gramsFromDuration(durationMs: Long): Int {
        val grams = durationMs.coerceAtLeast(0) / 1000.0 * POUR_GRAMS_PER_SECOND
        return maxOf((grams / ESTIMATE_STEP_GRAMS).roundToInt() * ESTIMATE_STEP_GRAMS, ESTIMATE_STEP_GRAMS)
    }

    /**
     * A finished pour's grams until the user types them: the recipe's grams for it ([suggestedGrams]) when there are
     * any, else [gramsFromDuration] of the pour, which ended at [endMs].
     */
    fun estimateGrams(ref: RecipeRef?, rows: List<TimerRow>, pourRowIndex: Int, endMs: Long): String {
        suggestedGrams(ref, rows, pourRowIndex)?.let(Numbers::parse)?.takeIf { it > 0 }?.let { return Prices.trimNumber(it) }
        return gramsFromDuration(endMs - rows[pourRowIndex].startMs).toString()
    }
}
