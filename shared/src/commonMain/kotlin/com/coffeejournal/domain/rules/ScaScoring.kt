package com.coffeejournal.domain.rules

import com.coffeejournal.domain.reference.ScaForm
import kotlin.math.roundToInt

object ScaScoring {
    /** Web getScaTotal: sum of the 10 attributes (intensities excluded), null when nothing was scored. */
    fun total(attributes: Map<String, Double>): Double? {
        val keys = ScaForm.attrKeys
        val any = attributes.any { (k, v) -> k in keys && v > 0 }
        if (!any) return null
        val sum = attributes.filter { (k, _) -> k in keys }.values.sum()
        return (sum * 100).roundToInt() / 100.0
    }

    /** App refinement: only count as "scored" when one of the 7 non-default attributes was set. */
    fun isScored(attributes: Map<String, Double>): Boolean =
        attributes.any { (k, v) -> k in ScaForm.attrKeys && k !in ScaForm.defaultTenKeys && v > 0 }

    fun effectiveTotal(attributes: Map<String, Double>): Double? = if (isScored(attributes)) total(attributes) else null

    /** Defaults for a fresh form: uniformity, clean cup and sweetness at 10 (no defects). */
    fun defaultAttributes(): Map<String, Double> = ScaForm.defaultTenKeys.associateWith { 10.0 }

    fun format2(v: Double): String {
        val cents = (v * 100).roundToInt()
        val whole = cents / 100
        val frac = cents % 100
        return "$whole.${if (frac < 10) "0$frac" else frac}"
    }

    fun format1(v: Double): String {
        val tenths = (v * 10).roundToInt()
        return if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
    }

    /** Readout text for a slider row (web updateAttrDots). */
    fun readout(attr: ScaForm.Attr, value: Double?): String {
        if (value == null || (value <= 0 && attr.min > 0)) return "–"
        return when {
            attr.key in ScaForm.intensityKeys -> format1(value)
            attr.step == 2.0 -> value.roundToInt().toString()
            else -> format2(value)
        }
    }
}
