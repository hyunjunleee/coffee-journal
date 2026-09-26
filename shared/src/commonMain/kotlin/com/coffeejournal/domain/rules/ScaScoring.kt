package com.coffeejournal.domain.rules

import com.coffeejournal.domain.reference.ScaForm
import kotlin.math.abs
import kotlin.math.roundToLong

object ScaScoring {
    /**
     * Web getScaTotal: sum of the 10 attributes (intensities excluded), null when nothing was scored.
     * Non-finite values (a backup can carry "NaN" or "1e999") count as not scored.
     */
    fun total(attributes: Map<String, Double>): Double? {
        val keys = ScaForm.attrKeys
        val scores = attributes.filter { (k, v) -> k in keys && v.isFinite() }
        if (scores.none { it.value > 0 }) return null
        val sum = scores.values.sum().takeIf { it.isFinite() } ?: return null
        return (sum * 100).roundToLong() / 100.0
    }

    /** App refinement: only count as "scored" when one of the 7 non-default attributes was set. */
    fun isScored(attributes: Map<String, Double>): Boolean =
        attributes.any { (k, v) -> k in ScaForm.attrKeys && k !in ScaForm.defaultTenKeys && v.isFinite() && v > 0 }

    fun effectiveTotal(attributes: Map<String, Double>): Double? = if (isScored(attributes)) total(attributes) else null

    /** Defaults for a fresh form: uniformity, clean cup and sweetness at 10 (no defects). */
    fun defaultAttributes(): Map<String, Double> = ScaForm.defaultTenKeys.associateWith { 10.0 }

    /** Web `toFixed(2)`: "8.25", "10.00", "-2.50". */
    fun format2(v: Double): String {
        if (!v.isFinite()) return "–"
        val cents = (abs(v) * 100).roundToLong()
        val sign = if (v < 0 && cents != 0L) "-" else ""
        val frac = (cents % 100).toInt()
        return "$sign${cents / 100}.${if (frac < 10) "0$frac" else frac}"
    }

    /** Web `toFixed(1).replace(/\.0$/, '')`: "3", "3.5". */
    fun format1(v: Double): String {
        if (!v.isFinite()) return "–"
        val tenths = (abs(v) * 10).roundToLong()
        val sign = if (v < 0 && tenths != 0L) "-" else ""
        return sign + if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
    }

    /**
     * Readout text for a slider row (web updateAttrDots, script3.js 3124-3139): `val = attrValues[key] || 0`, then
     * `val > 0 || min === 0` shows intensities as "3.5" and every other row with toFixed(2) ("8.25", and "10.00" /
     * "0.00" for the step-2 rows Uniformity, Clean Cup and Sweetness); otherwise "–". A missing or non-finite value
     * counts as 0, so a 0-10 row with no value reads "0.00" like the web.
     */
    fun readout(attr: ScaForm.Attr, value: Double?): String {
        val v = value?.takeIf { it.isFinite() } ?: 0.0
        if (!(v > 0 || attr.min == 0.0)) return "–"
        return if (attr.key in ScaForm.intensityKeys) format1(v) else format2(v)
    }
}
