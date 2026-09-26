package com.coffeejournal.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffeejournal.domain.reference.KoreaMapData
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.KoreaRegions
import com.coffeejournal.domain.rules.KoreaShapes
import com.coffeejournal.domain.rules.MapXY
import com.coffeejournal.ui.bean.b.MapPalette
import com.coffeejournal.ui.map.detail.DetailMapCamera
import com.coffeejournal.ui.map.detail.GeoBounds
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink

/** The national frame, and each 시·도's frame with a little room around it. */
object KoreaFrames {
    val national: Rect = Rect(KoreaMapData.VIEW_X, KoreaMapData.VIEW_Y, KoreaMapData.VIEW_X + KoreaMapData.VIEW_W, KoreaMapData.VIEW_Y + KoreaMapData.VIEW_H)

    private fun IntArray.toRect(margin: Float): Rect {
        val m = maxOf(this[2], this[3]) * margin
        return Rect(this[0] - m, this[1] - m, this[0] + this[2] + m, this[1] + this[3] + m)
    }

    fun provinceFrame(code: String): Rect? = KoreaMapData.provinces.firstOrNull { it.code == code }?.frame?.toRect(0.04f)
    fun provinceBounds(code: String): Rect? = KoreaMapData.provinces.firstOrNull { it.code == code }?.bounds?.toRect(0.04f)
}

/** National-map clusters: the pins of a 시·도 holding two or more become one chip at the 시·도's label point. */
object KoreaPinClusters {
    private const val PREFIX = "province-cluster:"

    fun provinceOf(key: String): String? = if (key.startsWith(PREFIX)) key.removePrefix(PREFIX) else null

    fun cluster(pins: List<MapPin>, provinceOf: Map<String, String?>): List<MapPin> {
        val groups = pins.groupBy { provinceOf[it.key] }
        val out = ArrayList<MapPin>()
        val done = HashSet<String?>()
        pins.forEach { pin ->
            val code = provinceOf[pin.key]
            val group = groups[code].orEmpty()
            if (code == null || group.size < 2) { out += pin; return@forEach }
            if (!done.add(code)) return@forEach
            val p = KoreaShapes.province(code) ?: return@forEach
            out += MapPin(PREFIX + code, "${p.info.short} ${group.size}곳", p.labelPoint, null, "${p.info.name}에 ${group.size}곳. 누르면 확대돼요.")
        }
        return out
    }
}

/** Which Korea map is showing (전국 or one 시·도) and its zoom; survives configuration changes. */
class KoreaMapState(provinceCode: String? = null) {
    var provinceCode by mutableStateOf<String?>(null)
        private set
    val viewport = MapViewport(KoreaFrames.national, maxZoom = 12f)

    init { if (provinceCode != null) showProvince(provinceCode) }

    val isNational: Boolean get() = provinceCode == null

    fun showProvince(code: String) {
        val frame = KoreaFrames.provinceFrame(code) ?: return
        provinceCode = code
        viewport.show(frame, KoreaFrames.provinceBounds(code) ?: frame)
    }

    fun showNational() {
        provinceCode = null
        viewport.show(KoreaFrames.national)
    }

    /** The part of the country the map shows now (degrees), for the detail map to open on; null before layout. */
    fun visibleBounds(): GeoBounds? {
        val size = viewport.canvasSize
        if (size.width <= 0f || size.height <= 0f) return null
        val a = viewport.toMap(Offset.Zero)
        val b = viewport.toMap(Offset(size.width, size.height))
        return GeoBounds.ofMapRect(a.x, a.y, b.x, b.y)
    }

    /** The 시·도 under a national-map tap; a tap just off a coast or an islet (within 12 km) still finds it. */
    fun provinceAt(p: MapXY): String? {
        val shapes = KoreaShapes.provinces
        shapes.filter { it.outline.contains(p.x, p.y) }.minByOrNull { it.outline.area }?.let { return it.code }
        val reach = 12.0 / KoreaProjection.KM_PER_UNIT
        return shapes.filter { it.outline.boxContains(p.x, p.y, reach) }
            .map { it to it.outline.distanceToEdge(p.x, p.y) }
            .filter { it.second <= reach }
            .minByOrNull { it.second }?.first?.code
    }

    companion object {
        val Saver = listSaver<KoreaMapState, Any?>(
            save = { listOf(it.provinceCode) + it.viewport.saveState() },
            restore = { saved ->
                KoreaMapState(saved[0] as String?).also { st -> st.viewport.restoreState(saved.drop(1).map { (it as Number).toFloat() }) }
            },
        )
    }
}

@Composable
fun rememberKoreaMapState(initialProvince: String? = null): KoreaMapState =
    rememberSaveable(saver = KoreaMapState.Saver) { KoreaMapState(initialProvince) }

private val provinceLabel: TextStyle get() = TextStyle(fontFamily = AppType.mono, fontSize = 9.5.sp, color = Ink.textMuted)
private val districtLabel: TextStyle get() = TextStyle(fontFamily = AppType.sans, fontSize = 9.sp, color = Ink.textFaint)

/**
 * The Korea map (통계청 SGIS 경계, simplified): the 16 시·도 nationally, and after a tap on one its 시·군·구 (the
 * neighbouring 시·도 stay faintly around it). System back and the "← 전국" button return to the national map.
 * Nationally a pin tap selects the pin and opens its 시·도 (a chip may cover a small 시·도 such as 서울), and two or
 * more pins of one 시·도 show as a single "서울 3곳" chip that opens it.
 * [onPointTap], when set, receives taps inside a 시·도 view (the location picker); otherwise such taps call
 * [onBackgroundTap]. [shaded] 시·도 (those with pins) are tinted on the national map.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KoreaMap(
    state: KoreaMapState,
    description: String,
    pins: List<MapPin>,
    selectedKey: String?,
    onPinTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    marker: MapXY? = null,
    shaded: Set<String> = emptySet(),
    onPointTap: ((MapXY) -> Unit)? = null,
    onBackgroundTap: () -> Unit = {},
    onOpenDetail: ((GeoBounds) -> Unit)? = null,
) {
    BackHandler(enabled = !state.isNational) { state.showNational() }
    val measurer = rememberTextMeasurer()
    val provincePaths = remember { KoreaShapes.provinces.map { it.outline.toPath() } }
    val code = state.provinceCode
    // nationally, several pins of one 시·도 become one "서울 3곳" chip; a tap on any pin opens its 시·도
    val pinProvince = remember(pins) { pins.associate { p -> p.key to KoreaRegions.locateMap(p.at.x, p.at.y, snapKm = 5.0)?.provinceCode } }
    val shown = remember(pins, code) { if (code == null) KoreaPinClusters.cluster(pins, pinProvince) else pins }
    val districts = remember(code) { if (code == null) emptyList() else KoreaShapes.districts(code) }
    val districtPaths = remember(code) { districts.map { it.outline.toPath() } }
    GeoMapCanvas(
        viewport = state.viewport,
        description = description,
        pins = shown,
        selectedKey = selectedKey,
        onPinTap = { key ->
            val cluster = KoreaPinClusters.provinceOf(key)
            when {
                cluster != null -> state.showProvince(cluster)
                state.isNational -> {
                    // select (not toggle off) and show the pin in its 시·도
                    if (key != selectedKey) onPinTap(key)
                    pinProvince[key]?.let { state.showProvince(it) }
                }
                else -> onPinTap(key)
            }
        },
        onTap = { p ->
            if (state.isNational) {
                val hit = state.provinceAt(p)
                if (hit != null) state.showProvince(hit) else onBackgroundTap()
            } else {
                onPointTap?.invoke(p) ?: onBackgroundTap()
            }
        },
        modifier = modifier,
        aspectRatio = 1f,
        marker = marker,
        drawMap = { vp ->
            val hair = 0.8.dp.toPx() / vp.scale
            inMapUnits(vp) {
                KoreaShapes.provinces.forEachIndexed { i, p ->
                    val fill = when {
                        code != null -> Ink.bg
                        p.code in shaded -> MapPalette.tasted.copy(alpha = 0.28f)
                        else -> Ink.surfaceRaised
                    }
                    drawPath(provincePaths[i], fill)
                    drawPath(provincePaths[i], if (code != null) Ink.line.copy(alpha = 0.7f) else Ink.line, style = Stroke(hair))
                }
                districtPaths.forEach { path ->
                    drawPath(path, Ink.surfaceRaised)
                    drawPath(path, Ink.line, style = Stroke(hair))
                }
            }
            if (code == null) {
                drawLabels(measurer, provinceLabel, KoreaShapes.provinces.map { it.info.short to it.labelPoint }, vp)
            } else {
                drawLabels(measurer, districtLabel, districts.map { it.info.name.substringAfterLast(' ') to it.labelPoint }, vp)
            }
        },
        overlay = { KoreaMapOverlay(state, onOpenDetail.takeIf { !state.isNational || selectedKey != null }) },
    )
}

@Composable
private fun BoxScope.KoreaMapOverlay(state: KoreaMapState, onOpenDetail: ((GeoBounds) -> Unit)?) {
    val code = state.provinceCode
    if (code != null) {
        val name = KoreaShapes.province(code)?.info?.name ?: ""
        GhostButton("← 전국 · $name", onClick = { state.showNational() }, small = true, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Ink.surface))
    }
    if (onOpenDetail != null) {
        // the street-level map (OpenStreetMap) of what this map shows
        GhostButton(
            "상세 지도", small = true,
            onClick = { (state.visibleBounds() ?: code?.let { DetailMapCamera.provinceBounds(it) })?.let(onOpenDetail) },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Ink.surface),
        )
    }
    if (state.viewport.isZoomed) {
        GhostButton("전체 보기", onClick = { state.viewport.reset() }, small = true, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Ink.surface))
    }
}

/** Runs [block] with the canvas transformed so that drawing happens in map units. */
internal inline fun DrawScope.inMapUnits(vp: MapViewport, block: DrawScope.() -> Unit) {
    val m = vp.scale
    val o = vp.origin
    val c = vp.frame.center
    withTransform({ translate(o.x - c.x * m, o.y - c.y * m); scale(m, m, Offset.Zero) }) { block() }
}

/** Area names centred on their label points, skipping any that would overlap one already drawn or leave the canvas. */
internal fun DrawScope.drawLabels(measurer: TextMeasurer, style: TextStyle, labels: List<Pair<String, MapXY>>, vp: MapViewport) {
    val drawn = ArrayList<Rect>()
    labels.forEach { (text, at) ->
        val layout = measurer.measure(text, style, maxLines = 1, softWrap = false)
        val c = vp.toCanvas(at)
        val r = Rect(Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f), androidx.compose.ui.geometry.Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
        if (r.left < 0f || r.top < 0f || r.right > size.width || r.bottom > size.height) return@forEach
        if (drawn.any { it.overlaps(r.inflate(2.dp.toPx())) }) return@forEach
        drawn += r
        drawText(layout, topLeft = r.topLeft)
    }
}
