package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.WorldMapData
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink

/** Zoom / pan and the current selection of the coffee map; survives configuration changes. */
class WorldMapState(scale: Float = 1f, pan: Offset = Offset.Zero, selectedCountry: String? = null, selectedRegion: String? = null) {
    var scale by mutableStateOf(scale)
    var pan by mutableStateOf(pan)
    /** English map name of the tapped country (any country, not only producers). */
    var selectedCountry by mutableStateOf(selectedCountry)
    var selectedRegion by mutableStateOf(selectedRegion)
    var canvasSize by mutableStateOf(Size.Zero)

    fun selectCountry(en: String?) { selectedCountry = en; selectedRegion = null }
    fun selectRegion(en: String, region: String) { selectedCountry = en; selectedRegion = region }
    fun resetZoom() { scale = 1f; pan = Offset.Zero }

    companion object {
        val Saver = listSaver<WorldMapState, Any?>(
            save = { listOf(it.scale, it.pan.x, it.pan.y, it.selectedCountry, it.selectedRegion) },
            restore = { WorldMapState(it[0] as Float, Offset(it[1] as Float, it[2] as Float), it[3] as String?, it[4] as String?) },
        )
    }
}

@Composable
fun rememberWorldMapState(): WorldMapState = rememberSaveable(saver = WorldMapState.Saver) { WorldMapState() }

private val VISITED_STROKE = Color(0xFFF0DCB8)
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

/**
 * The world map: 175 polygons from [WorldMapData] fitted into the canvas, producers in dark green, tasted countries
 * in the accent colour, 60 region dots, the two tropics, tap to select and pinch to zoom.
 * [triedRegions] holds "En|region" keys in lower case.
 */
@Composable
fun WorldMapCanvas(
    state: WorldMapState,
    visited: Set<String>,
    triedRegions: Set<String>,
    onCountryTap: (String) -> Unit,
    onRegionTap: (RegionHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val polygons = remember { WorldMapGeometry.parseAll() }
    val paths = remember(polygons) {
        polygons.map { poly ->
            Path().apply {
                poly.rings.forEach { ring ->
                    moveTo(ring[0].x, ring[0].y)
                    for (i in 1 until ring.size) lineTo(ring[i].x, ring[i].y)
                    close()
                }
            }
        }
    }
    val producers = remember { CoffeeCountries.byEn.keys }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val transformable = rememberTransformableState { zoom, panChange, _ ->
        val size = state.canvasSize
        val newScale = (state.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val m = WorldMapGeometry.fitScale(size.width, size.height) * newScale
        state.scale = newScale
        state.pan = WorldMapGeometry.clampPan(state.pan + panChange, m, size.width, size.height)
    }
    Box(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .background(Ink.surface)
                .border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
                .clipToBounds()
                .onSizeChanged { state.canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(polygons) {
                    detectTapGestures { pos ->
                        val size = state.canvasSize
                        val m = WorldMapGeometry.fitScale(size.width, size.height) * state.scale
                        val origin = Offset(size.width / 2f, size.height / 2f) + state.pan
                        val v = WorldMapGeometry.toView(pos, m, origin)
                        val tapRadius = maxOf(WorldMapGeometry.REGION_TAP_RADIUS, with(density) { 14.dp.toPx() } / m)
                        val region = WorldMapGeometry.hitRegion(v, tapRadius)
                        if (region != null) onRegionTap(region)
                        else WorldMapGeometry.hitCountry(polygons, v)?.let { onCountryTap(it.name) }
                    }
                }
                .transformable(transformable, canPan = { state.scale > MIN_SCALE }, lockRotationOnZoomPan = true),
        ) {
            val w = size.width; val h = size.height
            val m = WorldMapGeometry.fitScale(w, h) * state.scale
            val origin = Offset(w / 2f, h / 2f) + state.pan
            val center = WorldMapGeometry.viewCenter
            val hair = 0.6.dp.toPx() / m
            val selectedIndex = polygons.indexOfFirst { it.name == state.selectedCountry }
            withTransform({ translate(origin.x - center.x * m, origin.y - center.y * m); scale(m, m, Offset.Zero) }) {
                polygons.forEachIndexed { i, poly ->
                    val isVisited = poly.name in visited
                    val fill = when { isVisited -> Ink.accent; poly.name in producers -> Ink.mapProducer; else -> Ink.surfaceRaised }
                    drawPath(paths[i], fill)
                    drawPath(paths[i], if (isVisited) VISITED_STROKE else Ink.line, style = Stroke(hair))
                }
                if (selectedIndex >= 0) drawPath(paths[selectedIndex], Ink.mapDot, style = Stroke(1.5.dp.toPx() / m))
            }
            // Tropics, drawn in canvas units so the dashes keep their size while zooming.
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            val labelStyle = AppType.monoSmall.copy(fontSize = 9.sp, color = Ink.textFaint)
            listOf(WorldMapData.TROPIC_NORTH_Y to "북회귀선 23.5°N", WorldMapData.TROPIC_SOUTH_Y to "남회귀선 23.5°S").forEach { (vy, label) ->
                val a = WorldMapGeometry.toCanvas(Offset(WorldMapData.VIEW_X, vy), m, origin)
                val b = WorldMapGeometry.toCanvas(Offset(WorldMapData.VIEW_X + WorldMapData.VIEW_W, vy), m, origin)
                drawLine(Ink.textFaint.copy(alpha = 0.5f), Offset(maxOf(0f, a.x), a.y), Offset(minOf(w, b.x), b.y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                val layout = textMeasurer.measure(label, labelStyle)
                drawText(layout, topLeft = Offset(maxOf(0f, a.x) + 4.dp.toPx(), a.y - layout.size.height - 1.dp.toPx()))
            }
            // Region dots on top of everything.
            val r = maxOf(WorldMapGeometry.REGION_DOT_RADIUS * m, 2.5.dp.toPx())
            CoffeeCountries.all.forEach { c ->
                c.regions.forEach { reg ->
                    val p = WorldMapGeometry.toCanvas(Offset(reg.x, reg.y), m, origin)
                    if (p.x < -r || p.x > w + r || p.y < -r || p.y > h + r) return@forEach
                    val tried = "${c.en}|${reg.name.lowercase()}" in triedRegions
                    drawCircle(Ink.mapDot, r, p)
                    drawCircle(if (tried) Ink.accent else Ink.surface, r, p, style = Stroke(if (tried) 1.4.dp.toPx() else 0.6.dp.toPx()))
                    if (state.selectedCountry == c.en && state.selectedRegion == reg.name) drawCircle(Ink.text, r + 3.dp.toPx(), p, style = Stroke(1.dp.toPx()))
                }
            }
        }
        if (state.scale > MIN_SCALE) {
            GhostButton("전체 보기", onClick = { state.resetZoom() }, small = true, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Ink.surface))
        }
    }
}
