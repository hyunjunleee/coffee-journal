package com.coffeejournal.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffeejournal.domain.rules.MapXY
import com.coffeejournal.domain.rules.Outline
import com.coffeejournal.ui.bean.b.detectMapTransform
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Ink
import kotlin.math.abs
import kotlin.math.roundToInt

/** A pin: a name chip (with an optional count) tied to its map point by a dot and, when pushed aside, a leader line. */
data class MapPin(val key: String, val label: String, val at: MapXY, val count: Int? = null, val description: String = label)

private val chipName: TextStyle get() = TextStyle(fontFamily = AppType.sans, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, color = Ink.text)
private val chipCount: TextStyle get() = TextStyle(fontFamily = AppType.mono, fontSize = 9.sp, lineHeight = 14.sp, color = Ink.textMuted)
private val CHIP_PAD_H = 7.dp
private val CHIP_PAD_V = 4.dp
private val CHIP_COUNT_GAP = 4.dp
private val PIN_GAP = 6.dp
/** Taps land on a chip within the app's touch height ([Dimens.touch]), although the chip itself is drawn smaller. */
private val PIN_TOUCH = Dimens.touch

private class ChipSize(val size: Size, val name: Size, val count: Size?)

/** One pin as laid out: its index in the pin list, point and chip rectangle in canvas px. */
private class PlacedPin(val index: Int, val anchor: Offset, val chip: Rect)

/** Chips whose point is inside the canvas, the selected one first so it keeps its best spot. */
private fun layoutPins(pins: List<MapPin>, sizes: List<ChipSize>, selectedKey: String?, viewport: MapViewport, gap: Float): List<PlacedPin> {
    val w = viewport.canvasSize.width
    val h = viewport.canvasSize.height
    if (w <= 0f || h <= 0f) return emptyList()
    val order = pins.indices.filter { i ->
        val a = viewport.toCanvas(pins[i].at)
        a.x in 0f..w && a.y in 0f..h
    }.sortedBy { if (pins[it].key == selectedKey) 0 else 1 }
    val anchors = order.map { viewport.toCanvas(pins[it].at) }
    val tls = PinSpread.place(anchors, order.map { sizes[it].size }, w, h, gap)
    return order.mapIndexed { k, i -> PlacedPin(i, anchors[k], Rect(tls[k], sizes[i].size)) }
}

/**
 * A map canvas: [drawMap] paints the geography (in canvas px through [MapViewport]), then the pins' dots and leader
 * lines, an optional [marker] (the picker's chosen point), and the pins' chips as buttons. One finger taps
 * ([onTap] gets the map point); two fingers pinch-zoom around themselves, and once zoomed one finger pans (at the
 * fitted zoom a one-finger drag scrolls the page instead).
 */
@Composable
fun GeoMapCanvas(
    viewport: MapViewport,
    description: String,
    pins: List<MapPin>,
    selectedKey: String?,
    onPinTap: (String) -> Unit,
    onTap: (MapXY) -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
    marker: MapXY? = null,
    drawMap: DrawScope.(MapViewport) -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val sizes = remember(pins, density, chipName, chipCount) {
        with(density) {
            pins.map { p ->
                val name = measurer.measure(p.label, chipName, maxLines = 1, softWrap = false).size
                val count = p.count?.let { measurer.measure(it.toString(), chipCount, maxLines = 1, softWrap = false).size }
                val w = CHIP_PAD_H.toPx() * 2 + name.width + (count?.let { CHIP_COUNT_GAP.toPx() + it.width } ?: 0f)
                val h = CHIP_PAD_V.toPx() * 2 + maxOf(name.height, count?.height ?: 0)
                ChipSize(Size(kotlin.math.ceil(w), kotlin.math.ceil(h)), Size(name.width.toFloat(), name.height.toFloat()), count?.let { Size(it.width.toFloat(), it.height.toFloat()) })
            }
        }
    }
    val tap by rememberUpdatedState(onTap)
    val gap = with(density) { PIN_GAP.toPx() }
    // TalkBack hears the zoom ("확대 2.5배"); rounded, so a pinch recomposes only at every tenth
    val zoomState by remember(viewport) { derivedStateOf { MapViewportMath.zoomDescription(viewport.zoom) } }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(Ink.surface)
            .border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
            .clipToBounds()
            .onSizeChanged { viewport.canvasSize = Size(it.width.toFloat(), it.height.toFloat()) },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                // TalkBack cannot tap a shape; the label says what the map shows, the pins are buttons of their own
                .semantics { contentDescription = description; stateDescription = zoomState }
                .pointerInput(viewport) { detectTapGestures { pos -> tap(viewport.toMap(pos)) } }
                .pointerInput(viewport) { detectMapTransform(isZoomed = { abs(viewport.zoom - 1f) > 0.001f }, onTransform = viewport::transform) },
        ) {
            drawMap(viewport)
            val placed = layoutPins(pins, sizes, selectedKey, viewport, gap)
            placed.forEach { p ->
                val c = p.chip
                val end = PinSpread.nearestOnRect(p.anchor, c)
                if ((end - p.anchor).getDistance() > 1.dp.toPx()) drawLine(Ink.text.copy(alpha = 0.55f), p.anchor, end, strokeWidth = 1.dp.toPx())
            }
            placed.forEach { p -> drawPinDot(p.anchor, pins[p.index].key == selectedKey) }
            marker?.let { drawMarker(viewport.toCanvas(it)) }
        }
        PinChips(pins, sizes, selectedKey, viewport, gap, onPinTap)
        overlay()
    }
}

@Composable
private fun PinChips(pins: List<MapPin>, sizes: List<ChipSize>, selectedKey: String?, viewport: MapViewport, gap: Float, onPinTap: (String) -> Unit) {
    val density = LocalDensity.current
    Layout(
        content = {
            pins.forEachIndexed { i, pin ->
                val active = pin.key == selectedKey
                val press = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .selectable(selected = active, interactionSource = press, indication = null, role = Role.Button) { onPinTap(pin.key) }
                        .semantics { contentDescription = pin.description },
                    contentAlignment = Alignment.Center,
                ) {
                    val s = sizes[i]
                    Row(
                        Modifier
                            .background(if (active) Ink.text else Ink.surface)
                            .border(BorderStroke(1.dp, Ink.text), RectangleShape)
                            .indication(press, ripple())
                            .then(with(density) { Modifier.width(s.size.width.toDp()) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(CHIP_PAD_H))
                        Text(pin.label, style = chipName.copy(color = if (active) Ink.surface else Ink.text), maxLines = 1, softWrap = false, modifier = Modifier.padding(vertical = CHIP_PAD_V))
                        pin.count?.let {
                            Spacer(Modifier.width(CHIP_COUNT_GAP))
                            Text(it.toString(), style = chipCount.copy(color = if (active) Ink.surface else Ink.textMuted), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placed = layoutPins(pins, sizes, selectedKey, viewport, gap)
        val touch = PIN_TOUCH.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEach { p ->
                val c = p.chip
                val h = maxOf(touch, c.height.roundToInt())
                val placeable = measurables[p.index].measure(Constraints.fixed(c.width.roundToInt(), h))
                placeable.place(c.left.roundToInt(), (c.top + c.height / 2f - h / 2f).roundToInt())
            }
        }
    }
}

private fun DrawScope.drawPinDot(at: Offset, active: Boolean) {
    val r = (if (active) 5.dp else 3.5.dp).toPx()
    drawCircle(Ink.surface, r + 1.5.dp.toPx(), at)
    drawCircle(Ink.text, r, at)
}

/** The picker's chosen point: a ring with a dot, in the app's red so it stands apart from the ink pins. */
private fun DrawScope.drawMarker(at: Offset) {
    drawCircle(Ink.surface, 10.dp.toPx(), at)
    drawCircle(Ink.bad, 8.dp.toPx(), at, style = Stroke(2.dp.toPx()))
    drawCircle(Ink.bad, 3.dp.toPx(), at)
}

/** Compose paths (map units, even-odd so holes stay holes) of decoded outlines. */
fun Outline.toPath(): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    rings.forEach { r ->
        if (r.size < 6) return@forEach
        moveTo(r[0].toFloat(), r[1].toFloat())
        var i = 2
        while (i + 1 < r.size) { lineTo(r[i].toFloat(), r[i + 1].toFloat()); i += 2 }
        close()
    }
}
