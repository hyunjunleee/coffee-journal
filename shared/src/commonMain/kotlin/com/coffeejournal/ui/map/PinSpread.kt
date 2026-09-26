package com.coffeejournal.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * Places the pins' name chips so that none covers another: each chip tries the spots right, left, above and below its
 * point, then further up and down beside it, and takes the first one that is free and inside the canvas. Pins at the
 * same place (roasteries found only by their 시·군·구) therefore fan out into a column, each with a leader line back to
 * its point. Earlier pins get the better spots, so callers pass the most important first.
 */
object PinSpread {
    /**
     * Top-left corners (canvas px) for chips of [sizes] belonging to points [anchors], in a [width] × [height] canvas.
     * [gap] separates a chip from its point and from its neighbours.
     */
    fun place(anchors: List<Offset>, sizes: List<Size>, width: Float, height: Float, gap: Float): List<Offset> {
        val placed = ArrayList<Rect>(anchors.size)
        val out = ArrayList<Offset>(anchors.size)
        anchors.forEachIndexed { i, a ->
            val s = sizes[i]
            val candidates = candidates(a, s, gap).map { clamp(it, s, width, height) }
            fun free(tl: Offset) = placed.none { it.overlaps(Rect(tl, s).inflate(gap / 2f)) }
            // best: free and covering no pin's point (a chip pushed against the canvas edge could hide its own dot)
            fun clearOfPoints(tl: Offset) = Rect(tl, s).inflate(gap / 2f).let { r -> anchors.none { r.contains(it) } }
            val tl = candidates.firstOrNull { free(it) && clearOfPoints(it) }
                ?: candidates.firstOrNull { free(it) }
                ?: candidates.minBy { tl -> placed.sumOf { overlapArea(it, Rect(tl, s)).toDouble() } }
            placed += Rect(tl, s)
            out += tl
        }
        return out
    }

    private fun candidates(a: Offset, s: Size, gap: Float): List<Offset> {
        val right = Offset(a.x + gap, a.y - s.height / 2f)
        val left = Offset(a.x - gap - s.width, a.y - s.height / 2f)
        val above = Offset(a.x - s.width / 2f, a.y - gap - s.height)
        val below = Offset(a.x - s.width / 2f, a.y + gap)
        val out = mutableListOf(right, left, above, below)
        val step = s.height + gap
        for (k in 1..12) {
            val dy = step * k
            out += right + Offset(0f, dy); out += right - Offset(0f, dy)
            out += left + Offset(0f, dy); out += left - Offset(0f, dy)
        }
        return out
    }

    private fun clamp(tl: Offset, s: Size, width: Float, height: Float): Offset =
        Offset(tl.x.coerceIn(0f, maxOf(0f, width - s.width)), tl.y.coerceIn(0f, maxOf(0f, height - s.height)))

    private fun overlapArea(a: Rect, b: Rect): Float {
        val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return if (w > 0f && h > 0f) w * h else 0f
    }

    /** Where a leader line from [anchor] meets the chip [rect]: its nearest point (the anchor itself when inside). */
    fun nearestOnRect(anchor: Offset, rect: Rect): Offset =
        Offset(anchor.x.coerceIn(rect.left, rect.right), anchor.y.coerceIn(rect.top, rect.bottom))
}
