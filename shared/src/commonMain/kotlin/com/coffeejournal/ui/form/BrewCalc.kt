package com.coffeejournal.ui.form

import com.coffeejournal.domain.rules.BrewMath
import com.coffeejournal.domain.rules.Numbers
import kotlinx.serialization.Serializable

/** The record form's ratio / extraction-yield calculator as typed (feature-plan-v2 §2.2). Kept in [FormState]. */
@Serializable
data class CalcForm(
    val dose: String = "",
    val ratio: String = "",
    val water: String = "",
    val tds: String = "",
    val beverage: String = "",
)

/** Pure rules of the calculator: filling it from the recipe, the ratio edits, the result and the memo line. */
internal object BrewCalc {
    /** Opening the calculator: empty dose / water boxes take the recipe's, and the ratio follows. */
    fun opened(calc: CalcForm, formDose: String, formWater: String): CalcForm {
        var c = calc
        if (c.dose.isBlank() && Numbers.parse(formDose) != null) c = c.copy(dose = formDose.trim())
        if (c.water.isBlank() && Numbers.parse(formWater) != null) c = c.copy(water = formWater.trim())
        if (c.ratio.isBlank()) BrewMath.ratio(Numbers.parse(c.dose), Numbers.parse(c.water))?.let { c = c.copy(ratio = BrewMath.fmt1(it)) }
        return c
    }

    /** One of dose / ratio / water was typed: the third follows ([BrewMath.solve]). */
    fun edit(calc: CalcForm, edited: BrewMath.Edited, text: String): CalcForm {
        val typed = when (edited) {
            BrewMath.Edited.DOSE -> calc.copy(dose = text)
            BrewMath.Edited.RATIO -> calc.copy(ratio = text)
            BrewMath.Edited.WATER -> calc.copy(water = text)
        }
        val solved = BrewMath.solve(BrewMath.RatioFields(typed.dose, typed.ratio, typed.water), edited)
        return typed.copy(dose = solved.dose, ratio = solved.ratio, water = solved.water)
    }

    fun extractionYield(calc: CalcForm): Double? =
        BrewMath.extractionYield(Numbers.parse(calc.tds), Numbers.parse(calc.beverage), Numbers.parse(calc.dose))

    /**
     * The line "메모에 추가" appends, e.g. "[계산기] 원두 15g · 물 240g (1:16) · TDS 1.35% · 추출액 200g → 추출수율 18%
     * (SCA 고전 추출 차트 18~22% 안)". Null when nothing was worked out yet.
     */
    fun memoLine(calc: CalcForm): String? {
        val parts = mutableListOf<String>()
        val dose = Numbers.parse(calc.dose)?.takeIf { it > 0 }
        val water = Numbers.parse(calc.water)?.takeIf { it > 0 }
        val ratio = BrewMath.ratio(dose, water)
        if (dose != null) parts += "원두 ${BrewMath.fmt2(dose)}g"
        if (water != null) parts += "물 ${BrewMath.fmt2(water)}g" + (ratio?.let { " (1:${BrewMath.fmt1(it)})" } ?: "")
        val tds = Numbers.parse(calc.tds)?.takeIf { it > 0 }
        val bev = Numbers.parse(calc.beverage)?.takeIf { it > 0 }
        if (tds != null) parts += "TDS ${BrewMath.fmt2(tds)}%"
        if (bev != null) parts += "추출액 ${BrewMath.fmt2(bev)}g"
        val ey = extractionYield(calc)
        if (parts.isEmpty() && ey == null) return null
        val result = ey?.let { e ->
            val zone = when (BrewMath.zone(e, BrewMath.ClassicChart.EY_MIN, BrewMath.ClassicChart.EY_MAX)) {
                BrewMath.Zone.BELOW -> "18% 미만"
                BrewMath.Zone.INSIDE -> "18~22% 안"
                BrewMath.Zone.ABOVE -> "22% 초과"
            }
            " → 추출수율 ${BrewMath.fmt2(e)}% (SCA 고전 추출 차트 $zone)"
        } ?: ""
        return "[계산기] " + parts.joinToString(" · ") + result
    }

    /** [notes] with [line] added on a new line. */
    fun appendTo(notes: String, line: String): String = if (notes.isBlank()) line else notes.trimEnd() + "\n" + line
}
