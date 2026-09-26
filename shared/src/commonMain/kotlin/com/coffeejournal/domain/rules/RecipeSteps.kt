package com.coffeejournal.domain.rules

import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import kotlin.math.abs
import kotlin.math.roundToLong

data class StepsSummary(val totalWater: Double?, val totalTimeSec: Int?, val pourCount: Int)

object RecipeSteps {
    private val timeRe = Regex("^(\\d+):(\\d{1,2})$")

    /** Web parseTimeToSec: strict "M:SS". Minutes too large for a number of seconds count as no time. */
    fun parseTimeToSec(text: String?): Int? {
        val m = timeRe.find((text ?: "").trim()) ?: return null
        val minutes = m.groupValues[1].toLongOrNull()?.takeIf { it <= Int.MAX_VALUE } ?: return null
        val seconds = m.groupValues[2].toIntOrNull() ?: return null
        return toIntOrNull(minutes * 60 + seconds)
    }

    /** Web formatSec: "M:SS". */
    fun formatSec(sec: Int?): String? {
        sec ?: return null
        val m = sec / 60
        val s = sec % 60
        return "$m:${Dates.pad2(s)}"
    }

    /** Web computeStepsSummary. */
    fun summary(steps: List<RecipeStep>): StepsSummary {
        val live = steps.filter { !it.isEmpty }
        if (live.isEmpty()) return StepsSummary(null, null, 0)
        val pours = live.filter { it.water.isNotBlank() }
        // web `parseFloat(s.water)` / `parseFloat(last.wait) || 0`; "NaN", "Infinity" and "1e999" count as missing
        val totalWater = Numbers.finite(pours.sumOf { Numbers.parse(it.water) ?: 0.0 }) ?: 0.0
        val last = live.last()
        val lastSec = parseTimeToSec(last.time)
        val totalTime = lastSec?.let { sec ->
            val t = sec + (Numbers.parse(last.wait) ?: 0.0)
            if (t >= Int.MIN_VALUE && t <= Int.MAX_VALUE) toIntOrNull(t.roundToLong()) else null
        }
        return StepsSummary(if (pours.isEmpty()) null else totalWater, totalTime, pours.size)
    }

    private fun toIntOrNull(v: Long): Int? = if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else null

    fun summaryLine(s: StepsSummary): String {
        val water = s.totalWater?.let { Prices.trimNumber(it) + "g" } ?: "-"
        val time = formatSec(s.totalTimeSec) ?: "-"
        return "총 ${s.pourCount}차 추출 · 합계 물량 $water · 총 시간 $time"
    }

    /** Warnings when the log disagrees with the recipe targets (2 g / 10 s tolerance). */
    fun warnings(s: StepsSummary, targetWater: String?, targetTime: String?): List<String> {
        val out = mutableListOf<String>()
        val tw = Numbers.parse(targetWater)
        if (tw != null && s.totalWater != null && abs(s.totalWater - tw) > 2) {
            out += "⚠ 레시피의 물량(${Prices.trimNumber(tw)}g)과 맞지 않습니다. (합계 ${Prices.trimNumber(s.totalWater)}g)"
        }
        val tt = parseTimeToSec(targetTime)
        if (tt != null && s.totalTimeSec != null && abs(s.totalTimeSec - tt) > 10) {
            out += "⚠ 레시피의 총 추출시간(${formatSec(tt)})과 맞지 않습니다. (합계 ${formatSec(s.totalTimeSec)})"
        }
        return out
    }

    /**
     * Web compareStepsToRecipe (script3.js 6841-6865): position-wise exact comparison of water and wait, one line per
     * step with both differences joined, e.g. "뜸: 물량 30g→35g (+5g), 대기 30s→25s (-5s)".
     */
    fun diff(steps: List<RecipeStep>, ref: RecipeRef?): List<String> {
        ref ?: return emptyList()
        val n = minOf(steps.size, ref.steps.size)
        val out = mutableListOf<String>()
        for (i in 0 until n) {
            val cur = steps[i]
            val r = ref.steps[i]
            val label = r.note.ifBlank { cur.note.ifBlank { "${i + 1}단계" } }
            val parts = mutableListOf<String>()
            val rw = Numbers.parse(r.water)
            val cw = Numbers.parse(cur.water)
            if (rw != null && cw != null && rw != cw) parts += "물량 ${Prices.trimNumber(rw)}g→${Prices.trimNumber(cw)}g (${signed(cw - rw)}g)"
            val rt = Numbers.parse(r.wait)
            val ct = Numbers.parse(cur.wait)
            if (rt != null && ct != null && rt != ct) parts += "대기 ${Prices.trimNumber(rt)}s→${Prices.trimNumber(ct)}s (${signed(ct - rt)}s)"
            if (parts.isNotEmpty()) out += "$label: ${parts.joinToString(", ")}"
        }
        return out
    }

    private fun signed(v: Double): String = (if (v > 0) "+" else "") + Prices.trimNumber(v)
}
