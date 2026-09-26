package com.coffeejournal.ui.extract.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.rules.BrewMath
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.fontScaled
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/*
 * The 통계 charts, drawn with Compose Canvas in the archive style: 0.5 dp hairlines, ink marks, mono numbers, no
 * chart library. Every chart is one node for TalkBack with a sentence that says what it shows.
 */

private val AxisStyle: TextStyle get() = AppType.monoSmall.copy(color = Ink.textMuted)

private fun DrawScope.hLine(y: Float, x0: Float, x1: Float, color: Color = Ink.line) =
    drawLine(color, Offset(x0, y), Offset(x1, y), strokeWidth = Dimens.hairline.toPx())

private fun DrawScope.label(m: TextMeasurer, text: String, x: Float, y: Float, alignRight: Boolean = false, center: Boolean = false) {
    val layout = m.measure(text, AxisStyle)
    val dx = when { alignRight -> -layout.size.width.toFloat(); center -> -layout.size.width / 2f; else -> 0f }
    drawText(layout, topLeft = Offset(x + dx, y))
}

/** A readable upper bound for a count axis: 1, 2, 5, 10, 20 … */
internal fun niceMax(v: Int): Int {
    if (v <= 1) return 1
    var step = 1
    while (true) {
        for (m in intArrayOf(1, 2, 5)) if (step * m >= v) return step * m
        step *= 10
    }
}

/** Cups per day / month: ink for home brews with café cups stacked above in the café colour. */
@Composable
internal fun CupBarsChart(bars: List<CupBar>, daily: Boolean, description: String, height: Dp = 150.dp) {
    val m = rememberTextMeasurer()
    val max = niceMax(bars.maxOfOrNull { it.total } ?: 0)
    Canvas(
        Modifier.fillMaxWidth().height(height.fontScaled(1.4f)).testTag("chart-cups")
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val left = m.measure(max.toString(), AxisStyle).size.width + 6.dp.toPx()
        val labelH = m.measure("0", AxisStyle).size.height.toFloat()
        val bottom = size.height - labelH - 4.dp.toPx()
        val top = labelH / 2
        val plotW = size.width - left
        // grid: 0, half, max
        listOf(0, max / 2, max).distinct().forEach { v ->
            val y = bottom - (bottom - top) * v / max
            hLine(y, left, size.width, if (v == 0) Ink.text else Ink.line)
            label(m, v.toString(), left - 4.dp.toPx(), y - labelH / 2, alignRight = true)
        }
        if (bars.isEmpty()) return@Canvas
        val slot = plotW / bars.size
        // thin bars for a month of days, never a slab for a period of one or two months
        val barW = max(1f, minOf(slot * 0.62f, 28.dp.toPx()))
        val every = when {
            daily -> 5
            bars.size <= 12 -> 1
            else -> ceil(bars.size / 12.0).toInt()
        }
        bars.forEachIndexed { i, b ->
            val x = left + slot * i + (slot - barW) / 2
            val hBrew = (bottom - top) * b.brew / max
            val hCafe = (bottom - top) * b.cafe / max
            if (b.brew > 0) drawRect(Ink.accent, Offset(x, bottom - hBrew), Size(barW, hBrew))
            if (b.cafe > 0) drawRect(Ink.cafe, Offset(x, bottom - hBrew - hCafe), Size(barW, hCafe))
            val show = if (daily) (i == 0 || (i + 1) % every == 0) else i % every == 0
            if (show) label(m, b.label, left + slot * i + slot / 2, bottom + 3.dp.toPx(), center = true)
        }
    }
}

@Composable
internal fun Legend(vararg items: Pair<String, Color>, hollow: Set<String> = emptySet(), round: Set<String> = emptySet()) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (label, color) ->
            if (label in hollow) {
                Canvas(Modifier.size(8.dp)) { drawRect(color, style = Stroke(1.dp.toPx())) }
            } else if (label in round) {
                Canvas(Modifier.size(8.dp)) { drawCircle(color) }
            } else {
                Box(Modifier.size(8.dp).background(color))
            }
            Spacer(Modifier.width(5.dp))
            Text(label, style = AppType.faint)
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** Top values as hairline bars with their counts. */
@Composable
internal fun RankedBars(items: List<Ranked>, unit: String = "회") {
    val max = items.maxOfOrNull { it.count } ?: 1
    Column(Modifier.fillMaxWidth()) {
        items.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, style = AppType.small.copy(color = Ink.text), maxLines = 2, modifier = Modifier.weight(0.45f))
                Box(Modifier.weight(0.45f).height(10.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                        drawLine(Ink.line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), Dimens.hairline.toPx())
                        drawRect(Ink.accent, Offset(0f, 0f), Size(size.width * r.count / max, size.height))
                    }
                }
                Text("${r.count}$unit", style = AppType.monoValue, modifier = Modifier.width(40.dp.fontScaled()).padding(start = 6.dp))
            }
        }
    }
}

private fun paddedRange(values: List<Double>, minSpan: Double): Pair<Double, Double> {
    val lo = values.minOrNull() ?: 0.0
    val hi = values.maxOrNull() ?: 1.0
    val span = max(hi - lo, minSpan)
    val mid = (lo + hi) / 2
    return floor(mid - span / 2 - span * 0.1) to ceil(mid + span / 2 + span * 0.1)
}

private fun DrawScope.dot(c: Offset, cva: Boolean) {
    val r = 3.dp.toPx()
    if (cva) drawRect(Ink.cupping, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(1.2.dp.toPx()))
    else drawCircle(Ink.accent, r, c)
}

/** Scores over time: SCA 2004 totals as ink dots on a line, CVA scores as hollow squares (never joined to them). */
@Composable
internal fun ScoreTrendChart(points: List<ScorePoint>, description: String, height: Dp = 160.dp) {
    val m = rememberTextMeasurer()
    Canvas(
        Modifier.fillMaxWidth().height(height.fontScaled(1.4f)).testTag("chart-scores")
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (points.isEmpty()) return@Canvas
        val (lo, hi) = paddedRange(points.map { it.score }, 4.0)
        val labelH = m.measure("0", AxisStyle).size.height.toFloat()
        val left = m.measure(ScaScoring.format1(hi), AxisStyle).size.width + 6.dp.toPx()
        val bottom = size.height - labelH - 4.dp.toPx()
        val top = labelH / 2
        val t0 = points.minOf { it.createdAt }
        val t1 = points.maxOf { it.createdAt }
        fun x(t: Long) = if (t1 == t0) left + (size.width - left) / 2 else left + 8.dp.toPx() + (size.width - left - 16.dp.toPx()) * (t - t0) / (t1 - t0).toFloat()
        fun y(v: Double) = (bottom - (bottom - top) * ((v - lo) / (hi - lo))).toFloat()
        listOf(lo, (lo + hi) / 2, hi).forEach { v ->
            hLine(y(v), left, size.width, if (v == lo) Ink.text else Ink.line)
            label(m, ScaScoring.format1(v), left - 4.dp.toPx(), y(v) - labelH / 2, alignRight = true)
        }
        val sca = points.filter { !it.cva }
        if (sca.size > 1) {
            val path = Path()
            sca.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.createdAt), y(p.score)) else path.lineTo(x(p.createdAt), y(p.score)) }
            drawPath(path, Ink.accent, style = Stroke(1.dp.toPx()))
        }
        points.forEach { p -> dot(Offset(x(p.createdAt), y(p.score)), p.cva) }
        label(m, Dates.md(t0), left, bottom + 3.dp.toPx())
        if (t1 != t0) label(m, Dates.md(t1), size.width, bottom + 3.dp.toPx(), alignRight = true)
    }
}

/** A variable against the score (ratio, water temperature), one mark per brew. */
@Composable
internal fun ScatterChart(points: List<ScatterPoint>, xLabel: (Double) -> String, description: String, tag: String, height: Dp = 160.dp) {
    val m = rememberTextMeasurer()
    Canvas(
        Modifier.fillMaxWidth().height(height.fontScaled(1.4f)).testTag(tag)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (points.isEmpty()) return@Canvas
        val (ylo, yhi) = paddedRange(points.map { it.y }, 4.0)
        val (xlo, xhi) = paddedRange(points.map { it.x }, 2.0)
        val labelH = m.measure("0", AxisStyle).size.height.toFloat()
        val left = m.measure(ScaScoring.format1(yhi), AxisStyle).size.width + 6.dp.toPx()
        val bottom = size.height - labelH - 4.dp.toPx()
        val top = labelH / 2
        fun x(v: Double) = (left + (size.width - left) * ((v - xlo) / (xhi - xlo))).toFloat()
        fun y(v: Double) = (bottom - (bottom - top) * ((v - ylo) / (yhi - ylo))).toFloat()
        listOf(ylo, (ylo + yhi) / 2, yhi).forEach { v ->
            hLine(y(v), left, size.width, if (v == ylo) Ink.text else Ink.line)
            label(m, ScaScoring.format1(v), left - 4.dp.toPx(), y(v) - labelH / 2, alignRight = true)
        }
        drawLine(Ink.line, Offset(left, top), Offset(left, bottom), Dimens.hairline.toPx())
        points.forEach { p -> dot(Offset(x(p.x), y(p.y)), p.cva) }
        label(m, xLabel(xlo), left, bottom + 3.dp.toPx())
        label(m, xLabel(xhi), size.width, bottom + 3.dp.toPx(), alignRight = true)
    }
}
