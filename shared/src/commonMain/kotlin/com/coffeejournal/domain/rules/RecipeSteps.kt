package com.coffeejournal.domain.rules

import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import kotlin.math.abs
import kotlin.math.roundToInt

data class StepsSummary(val totalWater: Double?, val totalTimeSec: Int?, val pourCount: Int)

object RecipeSteps {
    private val timeRe = Regex("^(\\d+):(\\d{1,2})$")

    /** Web parseTimeToSec: strict "M:SS". */
    fun parseTimeToSec(text: String?): Int? {
        val m = timeRe.find((text ?: "").trim()) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
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
        val totalWater = pours.sumOf { it.water.trim().toDoubleOrNull() ?: 0.0 }
        val last = live.last()
        val lastSec = parseTimeToSec(last.time)
        val totalTime = lastSec?.let { it + (last.wait.trim().toDoubleOrNull() ?: 0.0).roundToInt() }
        return StepsSummary(if (pours.isEmpty()) null else totalWater, totalTime, pours.size)
    }

    fun summaryLine(s: StepsSummary): String {
        val water = s.totalWater?.let { Prices.trimNumber(it) + "g" } ?: "-"
        val time = formatSec(s.totalTimeSec) ?: "-"
        return "총 ${s.pourCount}차 추출 · 합계 물량 $water · 총 시간 $time"
    }

    /** Warnings when the log disagrees with the recipe targets (2 g / 10 s tolerance). */
    fun warnings(s: StepsSummary, targetWater: String?, targetTime: String?): List<String> {
        val out = mutableListOf<String>()
        val tw = (targetWater ?: "").trim().toDoubleOrNull()
        if (tw != null && s.totalWater != null && abs(s.totalWater - tw) > 2) {
            out += "⚠ 레시피의 물량(${Prices.trimNumber(tw)}g)과 맞지 않습니다. (합계 ${Prices.trimNumber(s.totalWater)}g)"
        }
        val tt = parseTimeToSec(targetTime)
        if (tt != null && s.totalTimeSec != null && abs(s.totalTimeSec - tt) > 10) {
            out += "⚠ 레시피의 총 추출시간(${formatSec(tt)})과 맞지 않습니다. (합계 ${formatSec(s.totalTimeSec)})"
        }
        return out
    }

    /** Web compareStepsToRecipe: position-wise exact comparison of water and wait. */
    fun diff(steps: List<RecipeStep>, ref: RecipeRef?): List<String> {
        ref ?: return emptyList()
        val n = minOf(steps.size, ref.steps.size)
        val out = mutableListOf<String>()
        for (i in 0 until n) {
            val cur = steps[i]
            val r = ref.steps[i]
            val label = r.note.ifBlank { cur.note.ifBlank { "${i + 1}단계" } }
            val rw = r.water.trim().toDoubleOrNull()
            val cw = cur.water.trim().toDoubleOrNull()
            if (rw != null && cw != null && rw != cw) out += "$label: 물량 ${Prices.trimNumber(rw)}g→${Prices.trimNumber(cw)}g (${signed(cw - rw)} g)"
            val rt = r.wait.trim().toDoubleOrNull()
            val ct = cur.wait.trim().toDoubleOrNull()
            if (rt != null && ct != null && rt != ct) out += "$label: 대기 ${Prices.trimNumber(rt)}s→${Prices.trimNumber(ct)}s (${signed(ct - rt)} s)"
        }
        return out
    }

    private fun signed(v: Double): String = (if (v > 0) "+" else "") + Prices.trimNumber(v)
}
