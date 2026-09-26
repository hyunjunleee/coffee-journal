package com.coffeejournal.ui.form.timer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.rules.Numbers
import com.coffeejournal.ui.theme.DerivationDispatcher
import com.coffeejournal.ui.theme.SavedFormState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Route arguments of the timer: the recipe applied in the form (if any) and whether the form already has a step log. */
data class BrewTimerArgs(val recipe: RecipeRef? = null, val formHasLog: Boolean = false)

/** What the timer screen draws. Times are whole seconds so the screen recomposes once a second at most. */
data class BrewTimerUi(
    val status: TimerStatus = TimerStatus.IDLE,
    val elapsedSec: Long = 0,
    val rows: List<TimerRow> = emptyList(),
    val pouring: Boolean = false,
    /** The 붓기 끝 dialog: which pour, and the recipe's grams to offer. */
    val gramsDialog: GramsDialog? = null,
    val finished: Boolean = false,
    val steps: List<RecipeStep> = emptyList(),
    val guidance: BrewTimerEngine.Guidance? = null,
    val pouredSoFar: Double = 0.0,
) {
    data class GramsDialog(val rowIndex: Int, val pourNumber: Int, val suggestion: String?)
}

sealed interface BrewTimerEvent {
    /** A recipe step change was reached: vibrate. */
    data object StepChange : BrewTimerEvent
}

/**
 * The full-screen brew timer (feature-plan-v2 §2.1). Time comes from [BrewClock] (monotonic, fake in tests); the state
 * is kept in the destination's [SavedStateHandle], and a timer that was running when the process died continues from
 * the wall-clock time that passed. A background ticker republishes the elapsed time and announces recipe step changes.
 */
class BrewTimerViewModel(
    val args: BrewTimerArgs,
    private val clock: BrewClock,
    savedState: SavedStateHandle? = null,
) : ViewModel() {
    private val saved = SavedFormState(savedState, STATE_KEY, BrewTimerState.serializer())
    private val state = MutableStateFlow(
        saved.restore()?.let { BrewTimerEngine.restored(it, clock.monotonicMs(), clock.wallMs()) } ?: BrewTimerState()
    )
    private val now = MutableStateFlow(clock.monotonicMs())

    private val _events = MutableSharedFlow<BrewTimerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BrewTimerEvent> = _events

    val ui: StateFlow<BrewTimerUi> = combine(state, now) { s, n -> build(s, n) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(state.value, now.value))

    init {
        saved.keep(viewModelScope, state)
        // a restored timer does not buzz again for step changes it passed while the app was gone
        state.update { it.copy(announced = BrewTimerEngine.boundariesPassed(args.recipe, BrewTimerEngine.elapsedMs(it, clock.monotonicMs()))) }
        viewModelScope.launch(DerivationDispatcher) {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    /** Re-reads the clock: publishes the time and buzzes once per recipe step change reached while running. */
    fun tick() {
        val mono = clock.monotonicMs()
        now.value = mono
        val s = state.value
        if (s.status != TimerStatus.RUNNING) return
        val passed = BrewTimerEngine.boundariesPassed(args.recipe, BrewTimerEngine.elapsedMs(s, mono))
        if (passed > s.announced) {
            state.update { it.copy(announced = passed) }
            _events.tryEmit(BrewTimerEvent.StepChange)
        }
    }

    private fun act(transform: (BrewTimerState, Long) -> BrewTimerState) {
        val mono = clock.monotonicMs()
        state.update { transform(it, mono) }
        now.value = mono
    }

    fun start() = act { s, m -> BrewTimerEngine.start(s, m, clock.wallMs()) }
    fun pause() = act { s, m -> BrewTimerEngine.pause(s, m) }
    fun reset() = act { _, _ -> BrewTimerEngine.reset() }
    fun startPour() = act { s, m -> BrewTimerEngine.startPour(s, m, clock.wallMs()) }
    fun endPour() = act { s, m -> BrewTimerEngine.endPour(s, m) }
    fun setGrams(grams: String) = act { s, m -> BrewTimerEngine.setGrams(s, grams, m) }
    fun keepPouring() = act { s, _ -> BrewTimerEngine.cancelEndPour(s) }
    fun note(label: String) = act { s, m -> BrewTimerEngine.note(s, label, m, clock.wallMs()) }
    fun finish() = act { s, m -> BrewTimerEngine.finish(s, m) }
    fun resumeBrewing() = act { s, _ -> BrewTimerEngine.resume(s) }

    private fun build(s: BrewTimerState, mono: Long): BrewTimerUi {
        val elapsed = s.endMs ?: BrewTimerEngine.elapsedMs(s, mono)
        val dialog = s.gramsFor?.let { i ->
            BrewTimerUi.GramsDialog(i, s.rows.take(i + 1).count { it.pour }, BrewTimerEngine.suggestedGrams(args.recipe, s.rows, i))
        }
        return BrewTimerUi(
            status = s.status,
            elapsedSec = elapsed / 1000,
            rows = s.rows,
            pouring = s.pouring,
            gramsDialog = dialog,
            finished = s.endMs != null,
            steps = s.endMs?.let { BrewTimerEngine.toSteps(s.rows, it) } ?: emptyList(),
            // guidance moves in whole seconds like the display
            guidance = args.recipe?.let { BrewTimerEngine.guidance(it, elapsed / 1000 * 1000) },
            pouredSoFar = s.rows.sumOf { Numbers.parse(it.grams) ?: 0.0 },
        )
    }

    companion object {
        private const val STATE_KEY = "brewTimer"
        const val TICK_MS = 200L
    }
}

/** The timer's result, handed back to the record form through its back stack entry. */
object BrewTimerResult {
    const val KEY = "brewTimerSteps"
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = kotlinx.serialization.builtins.ListSerializer(RecipeStep.serializer())

    fun encode(steps: List<RecipeStep>): String = json.encodeToString(serializer, steps)
    fun decode(text: String): List<RecipeStep>? = runCatching { json.decodeFromString(serializer, text) }.getOrNull()

    fun encodeRecipe(ref: RecipeRef?): String? = ref?.let { json.encodeToString(RecipeRef.serializer(), it) }
    fun decodeRecipe(text: String?): RecipeRef? = text?.let { runCatching { json.decodeFromString(RecipeRef.serializer(), it) }.getOrNull() }
}
