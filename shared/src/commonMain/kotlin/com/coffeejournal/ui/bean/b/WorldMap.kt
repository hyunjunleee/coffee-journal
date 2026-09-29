package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.WorldMapData
import com.coffeejournal.ui.map.WorldMapInsets
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink
import kotlin.math.abs

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

    /** One pinch / pan step: [centroid] is in canvas px; the map point under the fingers stays under them. */
    fun transform(centroid: Offset, panChange: Offset, zoomChange: Float) {
        val size = canvasSize
        val newScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        pan = WorldMapGeometry.zoomPan(pan, scale, newScale, centroid, panChange, WorldMapGeometry.fitScale(size.width, size.height), size.width, size.height)
        scale = newScale
    }

    companion object {
        val Saver = listSaver<WorldMapState, Any?>(
            save = { listOf(it.scale, it.pan.x, it.pan.y, it.selectedCountry, it.selectedRegion) },
            restore = { WorldMapState(it[0] as Float, Offset(it[1] as Float, it[2] as Float), it[3] as String?, it[4] as String?) },
        )
    }
}

@Composable
fun rememberWorldMapState(): WorldMapState = rememberSaveable(saver = WorldMapState.Saver) { WorldMapState() }

/**
 * Coffee map colours, shared by the canvas and the legend. The app keeps its dark producer green; tasted countries
 * get a warm coffee tone instead of the near-black ink, which could hardly be told from that green (about 1.4:1).
 */
object MapPalette {
    val producer: Color = Ink.mapProducer
    /** The web's coffee accent #B58968 deepened a little: at least 3:1 against the producer green, the page and other land. */
    val tasted: Color = Color(0xFFA87B58)
    val tastedStroke: Color = Color(0xFFF0DCB8)
    val otherLand: Color = Ink.surfaceRaised
    /** 주요 산지 dots (the web copy calls them 연두색 점). */
    val dot: Color = Ink.mapDot
    /** Dots of regions already tasted: the web's dark accent fill with the light ring. */
    val triedDot: Color = Ink.accent
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = WorldMapGeometry.MAX_SCALE

/** Radius of a drawn region dot in canvas px at the total scale [m]; taps count as on the dot within this radius. */
private fun Density.dotRadiusPx(m: Float): Float = WorldMapGeometry.dotRadiusPx(m, density)

private fun Density.shownDots(scale: Float, m: Float): List<WorldRegions.Dot> = WorldMapGeometry.shownDots(scale, m, density)

/**
 * The world map: 175 polygons from [WorldMapData] fitted into the canvas, producers in dark green, tasted countries
 * in [MapPalette.tasted], the region dots ([WorldRegions]), the two tropics, tap to select and pinch to zoom around the fingers.
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
    description: String = MapStats.mapDescription(visited),
) {
    val polygons = remember { WorldMapGeometry.parseCoffeeMap() }
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
    // enough room for every name a zoomed-in view writes, so they are not measured again each frame
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    Box(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .background(Ink.surface)
                .border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
                .clipToBounds()
                // TalkBack cannot tap a polygon; it reads what the map shows and points to the lists below it
                .semantics { contentDescription = description }
                .onSizeChanged { state.canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(polygons) {
                    detectTapGestures { pos ->
                        val size = state.canvasSize
                        val m = WorldMapGeometry.fitScale(size.width, size.height) * state.scale
                        val origin = Offset(size.width / 2f, size.height / 2f) + state.pan
                        val v = WorldMapGeometry.toView(pos, m, origin)
                        when (val hit = WorldMapGeometry.resolveTap(polygons, v, dotRadiusPx(m) / m, 14.dp.toPx() / m, shownDots(state.scale, m))) {
                            is MapTap.Region -> onRegionTap(hit.hit)
                            is MapTap.Country -> onCountryTap(hit.name)
                            null -> Unit
                        }
                    }
                }
                .pointerInput(state) { detectMapTransform(isZoomed = { state.scale > MIN_SCALE }, onTransform = state::transform) },
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
                    val fill = when { isVisited -> MapPalette.tasted; poly.name in producers -> MapPalette.producer; else -> MapPalette.otherLand }
                    drawPath(paths[i], fill)
                    drawPath(paths[i], if (isVisited) MapPalette.tastedStroke else Ink.line, style = Stroke(hair))
                }
                if (selectedIndex >= 0) drawPath(paths[selectedIndex], Ink.mapDot, style = Stroke(1.5.dp.toPx() / m))
                // Hawaii's inset: a dashed frame around the islands, which are drawn with the countries
                val f = WorldMapInsets.frame
                drawRect(
                    Ink.textFaint.copy(alpha = 0.6f), topLeft = f.topLeft, size = f.size,
                    style = Stroke(hair, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx() / m, 2.dp.toPx() / m))),
                )
            }
            // Tropics, drawn in canvas units so the dashes keep their size while zooming.
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            val labelStyle = AppType.monoSmall.copy(fontSize = 9.sp, color = Ink.textFaint)
            val writing = ArrayList<Rect>() // the map's own words, which the dots' names keep clear of
            listOf(WorldMapData.TROPIC_NORTH_Y to "북회귀선 23.5°N", WorldMapData.TROPIC_SOUTH_Y to "남회귀선 23.5°S").forEach { (vy, label) ->
                val a = WorldMapGeometry.toCanvas(Offset(WorldMapData.VIEW_X, vy), m, origin)
                val b = WorldMapGeometry.toCanvas(Offset(WorldMapData.VIEW_X + WorldMapData.VIEW_W, vy), m, origin)
                drawLine(Ink.textFaint.copy(alpha = 0.5f), Offset(maxOf(0f, a.x), a.y), Offset(minOf(w, b.x), b.y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                val layout = textMeasurer.measure(label, labelStyle)
                val at = Offset(maxOf(0f, a.x) + 4.dp.toPx(), a.y - layout.size.height - 1.dp.toPx())
                drawText(layout, topLeft = at)
                writing += Rect(at, layout.size.toSize())
            }
            val insetName = textMeasurer.measure("하와이", labelStyle)
            val insetAt = WorldMapGeometry.toCanvas(WorldMapInsets.frame.bottomLeft, m, origin) + Offset(1.dp.toPx(), 1.dp.toPx())
            drawText(insetName, topLeft = insetAt)
            writing += Rect(insetAt, insetName.size.toSize())
            // Region dots on top of everything: tasted ones dark with a light ring, the others 연두색. The selected region
            // (picked from the country's list) is drawn even where the zoom leaves its dot out.
            val r = dotRadiusPx(m)
            val shown = shownDots(state.scale, m)
            val selected = WorldRegions.dots.firstOrNull { it.country.en == state.selectedCountry && it.region.name == state.selectedRegion }
            (if (selected != null && selected !in shown) shown + selected else shown).forEach { (c, reg) ->
                val p = WorldMapGeometry.toCanvas(Offset(reg.x, reg.y), m, origin)
                if (p.x < -r || p.x > w + r || p.y < -r || p.y > h + r) return@forEach
                val tried = "${c.en}|${reg.name.lowercase()}" in triedRegions
                drawCircle(if (tried) MapPalette.triedDot else MapPalette.dot, r, p)
                drawCircle(if (tried) MapPalette.tastedStroke else Ink.surface, r, p, style = Stroke(if (tried) 1.dp.toPx() else 0.6.dp.toPx()))
                if (selected?.country == c && selected.region == reg) drawCircle(Ink.text, r + 3.dp.toPx(), p, style = Stroke(1.dp.toPx()))
            }
            // Zoomed in, the dots' Korean names where they fit: the selected region's first, then the tasted ones, then
            // the web's dots, then the rest.
            if (state.scale >= WorldRegions.LABEL_SCALE) {
                val nameStyle = AppType.small.copy(fontSize = 10.sp, color = Ink.text)
                val layouts = HashMap<String, TextLayoutResult>()
                val onCanvas = (if (selected != null && selected !in shown) shown + selected else shown)
                    .map { d -> d to WorldMapGeometry.toCanvas(Offset(d.region.x, d.region.y), m, origin) }
                    .filter { (_, p) -> p.x in 0f..w && p.y in 0f..h }
                    .sortedBy { (d, _) ->
                        when {
                            d == selected -> 0
                            "${d.country.en}|${d.region.name.lowercase()}" in triedRegions -> 1
                            d.web -> 2
                            else -> 3
                        }
                    }
                val names = WorldMapGeometry.placeLabels(
                    onCanvas.map { (d, p) -> p to WorldRegions.label(d) },
                    measure = { t -> layouts.getOrPut(t) { textMeasurer.measure(t, nameStyle) }.size.toSize() },
                    dotRadius = r, gap = 3.dp.toPx(), canvas = Size(w, h), obstacles = writing,
                )
                val pad = 1.5.dp.toPx()
                names.forEach { l ->
                    drawRect(Ink.surface.copy(alpha = 0.8f), topLeft = l.topLeft - Offset(pad, 0f), size = Size(l.size.width + 2 * pad, l.size.height))
                    drawText(layouts.getValue(l.text), topLeft = l.topLeft)
                }
            }
        }
        if (state.scale > MIN_SCALE) {
            GhostButton("전체 보기", onClick = { state.resetZoom() }, small = true, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Ink.surface))
        }
    }
}

/**
 * Pinch zoom around the fingers' centroid and, once zoomed in, one-finger panning. At the fitted zoom a one-finger
 * drag is left alone, so the page keeps scrolling over the map; taps are left to the tap detector until the fingers
 * move past the touch slop.
 */
internal suspend fun PointerInputScope.detectMapTransform(isZoomed: () -> Boolean, onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        var pan = Offset.Zero
        var pastSlop = false
        val slop = viewConfiguration.touchSlop
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed } || event.changes.any { it.isConsumed }) break
            if (event.changes.count { it.pressed } < 2 && !isZoomed()) continue
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            if (!pastSlop) {
                zoom *= zoomChange
                pan += panChange
                pastSlop = abs(1f - zoom) * event.calculateCentroidSize(useCurrent = false) > slop || pan.getDistance() > slop
            }
            if (pastSlop) {
                if (zoomChange != 1f || panChange != Offset.Zero) onTransform(event.calculateCentroid(useCurrent = false), panChange, zoomChange)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        }
    }
}
