package com.coffeejournal.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.rules.MapXY
import com.coffeejournal.ui.bean.b.MapPalette
import com.coffeejournal.ui.bean.b.WorldMapGeometry
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink

/** The world map's zoom for the overseas roastery map and picker; survives configuration changes. */
class WorldPinMapState {
    val viewport = MapViewport(WorldProjection.frame, WorldProjection.bounds, maxZoom = 16f)

    companion object {
        val Saver = listSaver<WorldPinMapState, Float>(
            save = { it.viewport.saveState() },
            restore = { saved -> WorldPinMapState().also { it.viewport.restoreState(saved) } },
        )
    }
}

@Composable
fun rememberWorldPinMapState(): WorldPinMapState = rememberSaveable(saver = WorldPinMapState.Saver) { WorldPinMapState() }

/**
 * The app's Natural Earth world map as a plain base map (every country the same, [shaded] ones tinted) with pins, for
 * overseas roasteries. Taps give the viewBox point to [onTap].
 */
@Composable
fun WorldPinMap(
    state: WorldPinMapState,
    description: String,
    pins: List<MapPin>,
    selectedKey: String?,
    onPinTap: (String) -> Unit,
    onTap: (MapXY) -> Unit,
    modifier: Modifier = Modifier,
    marker: MapXY? = null,
    shaded: Set<String> = emptySet(),
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
    GeoMapCanvas(
        viewport = state.viewport,
        description = description,
        pins = pins,
        selectedKey = selectedKey,
        onPinTap = onPinTap,
        onTap = onTap,
        modifier = modifier,
        aspectRatio = 1.6f,
        marker = marker,
        drawMap = { vp ->
            val hair = 0.6.dp.toPx() / vp.scale
            inMapUnits(vp) {
                polygons.forEachIndexed { i, poly ->
                    drawPath(paths[i], if (poly.name in shaded) MapPalette.tasted.copy(alpha = 0.35f) else Ink.surfaceRaised)
                    drawPath(paths[i], Ink.line, style = Stroke(hair))
                }
            }
        },
        overlay = {
            if (state.viewport.isZoomed) {
                GhostButton("전체 보기", onClick = { state.viewport.reset() }, small = true, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Ink.surface))
            }
        },
    )
}
