package com.coffeejournal.domain.rules

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Brew ratio and extraction-yield arithmetic for the record form's calculator and the comparison table.
 *
 * Extraction yield (EY) follows from mass conservation: the dissolved solids in the cup (TDS% × beverage weight) out of
 * the dry coffee dose. EY% = TDS% × beverage g ÷ dose g.
 */
object BrewMath {
    /** water ÷ dose (the x of "1:x"); null unless both are finite and positive. */
    fun ratio(dose: Double?, water: Double?): Double? {
        val d = Numbers.finite(dose)?.takeIf { it > 0 } ?: return null
        val w = Numbers.finite(water)?.takeIf { it > 0 } ?: return null
        return Numbers.finite(w / d)
    }

    /** "1:16", "1:16.7" (one decimal, trailing .0 dropped); null without a ratio. */
    fun ratioText(dose: String?, water: String?): String? = ratio(Numbers.parse(dose), Numbers.parse(water))?.let { "1:" + fmt1(it) }

    /** Extraction yield in percent: TDS% × beverage g ÷ dose g. Null unless all three are finite and positive. */
    fun extractionYield(tdsPercent: Double?, beverageGrams: Double?, dose: Double?): Double? {
        val tds = Numbers.finite(tdsPercent)?.takeIf { it > 0 } ?: return null
        val bev = Numbers.finite(beverageGrams)?.takeIf { it > 0 } ?: return null
        val d = Numbers.finite(dose)?.takeIf { it > 0 } ?: return null
        return Numbers.finite(tds * bev / d)
    }

    /** One decimal place, trailing ".0" dropped: 16.0 → "16", 16.666 → "16.7". */
    fun fmt1(v: Double): String {
        val tenths = (abs(v) * 10).roundToLong()
        val sign = if (v < 0 && tenths != 0L) "-" else ""
        return sign + if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
    }

    /** Two decimal places, trailing zeros dropped: 1.35 → "1.35", 1.30 → "1.3", 20.0 → "20". */
    fun fmt2(v: Double): String {
        val cents = (abs(v) * 100).roundToLong()
        val sign = if (v < 0 && cents != 0L) "-" else ""
        val whole = cents / 100
        val frac = (cents % 100).toInt()
        return sign + when {
            frac == 0 -> whole.toString()
            frac % 10 == 0 -> "$whole.${frac / 10}"
            else -> "$whole.${if (frac < 10) "0$frac" else frac.toString()}"
        }
    }

    /** Which of dose / ratio / water the user just typed in the calculator. */
    enum class Edited { DOSE, RATIO, WATER }

    /** Calculator fields as text (what the boxes show). */
    data class RatioFields(val dose: String = "", val ratio: String = "", val water: String = "")

    /**
     * Ratio 1:x ↔ dose / water: after [edited] changed, the third field follows from the other two. Typing the dose or
     * the ratio recomputes the water (or, without a dose, the dose from water ÷ ratio); typing the water recomputes the
     * ratio (or, without a dose, the dose). A field the other two cannot decide keeps its text.
     */
    fun solve(fields: RatioFields, edited: Edited): RatioFields {
        val d = Numbers.parse(fields.dose)?.takeIf { it > 0 }
        val r = Numbers.parse(fields.ratio)?.takeIf { it > 0 }
        val w = Numbers.parse(fields.water)?.takeIf { it > 0 }
        return when (edited) {
            Edited.DOSE, Edited.RATIO -> when {
                d != null && r != null -> fields.copy(water = fmt1(d * r))
                edited == Edited.RATIO && d == null && r != null && w != null -> fields.copy(dose = fmt1(w / r))
                edited == Edited.DOSE && d != null && r == null && w != null -> fields.copy(ratio = fmt1(w / d))
                else -> fields
            }
            Edited.WATER -> when {
                d != null && w != null -> fields.copy(ratio = fmt1(w / d))
                d == null && r != null && w != null -> fields.copy(dose = fmt1(w / r))
                else -> fields
            }
        }
    }

    /**
     * The "Ideal – Optimum Balance" box of the classic Coffee Brewing Control Chart as the SCA printed it:
     * EXTRACTION (solubles yield) 18%–22% and STRENGTH (solubles concentration, TDS) 1.15%–1.35%.
     *
     * Verified 2026-09-26 against the SCA's own publication: "Towards a New Brewing Chart", 25 Magazine Issue 13
     * (Specialty Coffee Association, 2020; Frost, Batali, Guinard, Ristenpart), Figure 1 "Classic Coffee Brewing Control
     * Chart" — the shaded IDEAL OPTIMUM BALANCE zone spans 18%–22% on the extraction axis and 1.15%–1.35% on the
     * strength axis; left of it the chart reads UNDER-DEVELOPED, right of it BITTER, above STRONG, below WEAK.
     * Article: https://sca.coffee/sca-news/25/issue-13/towards-a-new-brewing-chart (moved in the 2026 site redesign;
     * archived at https://web.archive.org/web/20210811223756/https://sca.coffee/sca-news/25/issue-13/towards-a-new-brewing-chart).
     * The same article notes the chart mixes sensory description with preference and that the SCA is researching a
     * new chart, so the app shows the zone as a reference, not as a rule for good coffee.
     */
    object ClassicChart {
        const val EY_MIN = 18.0
        const val EY_MAX = 22.0
        const val TDS_MIN = 1.15
        const val TDS_MAX = 1.35
        const val SOURCE_URL = "https://web.archive.org/web/20210811223756/https://sca.coffee/sca-news/25/issue-13/towards-a-new-brewing-chart"
    }

    enum class Zone { BELOW, INSIDE, ABOVE }

    fun zone(value: Double, min: Double, max: Double): Zone = when {
        value < min -> Zone.BELOW
        value > max -> Zone.ABOVE
        else -> Zone.INSIDE
    }

    /** Korean verdict for an extraction yield against the chart's 18–22% box. */
    fun eyVerdict(ey: Double): String = when (zone(ey, ClassicChart.EY_MIN, ClassicChart.EY_MAX)) {
        Zone.BELOW -> "18% 미만 · 차트의 UNDER-DEVELOPED 쪽"
        Zone.INSIDE -> "18~22% 안 · 차트의 이상적 균형 구역"
        Zone.ABOVE -> "22% 초과 · 차트의 BITTER 쪽"
    }

    /** Korean verdict for a TDS against the chart's 1.15–1.35% box. */
    fun tdsVerdict(tds: Double): String = when (zone(tds, ClassicChart.TDS_MIN, ClassicChart.TDS_MAX)) {
        Zone.BELOW -> "1.15% 미만 · 차트의 WEAK 쪽"
        Zone.INSIDE -> "1.15~1.35% 안"
        Zone.ABOVE -> "1.35% 초과 · 차트의 STRONG 쪽"
    }

    /** A step value snapped to whole seconds (the step log's resolution). */
    fun wholeSeconds(ms: Long): Int = floor(ms / 1000.0).toInt()
}
