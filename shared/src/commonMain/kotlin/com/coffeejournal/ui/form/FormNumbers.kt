package com.coffeejournal.ui.form

import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import kotlin.math.abs

/**
 * Guards for number text typed into the form. Kotlin's toDoubleOrNull accepts "NaN", "Infinity" and overflowing
 * literals such as "1e999", and the step summary rounds those with roundToInt, which throws. Such values count as missing.
 */
internal object FormNumbers {
    /** Longest wait (s) the step summary takes; larger values would overflow the Int total time. */
    private const val MAX_WAIT_SEC = 1_000_000.0
    /** "M:SS" whose minutes have more digits than this would overflow the Int parser of RecipeSteps. */
    private const val MAX_MINUTE_DIGITS = 6
    private val timeRe = Regex("^(\\d+):(\\d{1,2})$")

    /** The finite number in [text], or null when it is not a number or not finite. */
    fun finiteOrNull(text: String?): Double? = text?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** [text] unchanged unless it parses to a non-finite number, which becomes "". Free text is kept as typed. */
    fun finiteText(text: String): String {
        val v = text.trim().toDoubleOrNull() ?: return text
        return if (v.isFinite()) text else ""
    }

    /** "M:SS" text unchanged unless its minutes would overflow the parser, which makes it "". */
    fun safeTime(text: String): String {
        val minutes = timeRe.find(text.trim())?.groupValues?.get(1) ?: return text
        return if (minutes.length > MAX_MINUTE_DIGITS) "" else text
    }

    /** A step whose wait, water or time could not be summarised safely has that value blanked. */
    fun finiteStep(step: RecipeStep): RecipeStep {
        val waitValue = step.wait.trim().toDoubleOrNull()
        val wait = if (waitValue != null && !(waitValue.isFinite() && abs(waitValue) < MAX_WAIT_SEC)) "" else step.wait
        val water = finiteText(step.water)
        val time = safeTime(step.time)
        return if (wait == step.wait && water == step.water && time == step.time) step else step.copy(time = time, water = water, wait = wait)
    }

    fun finiteSteps(steps: List<RecipeStep>): List<RecipeStep> = steps.map(::finiteStep)

    fun finiteRef(ref: RecipeRef?): RecipeRef? = ref?.let { r -> r.copy(steps = finiteSteps(r.steps)) }

    /** SCA attributes without non-finite values (they can only arrive through a backup file). */
    fun finiteAttributes(attributes: Map<String, Double>): Map<String, Double> =
        if (attributes.values.all { it.isFinite() }) attributes else attributes.filterValues { it.isFinite() }
}
