package com.coffeejournal.ui.map.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.coffeejournal.ui.theme.Ink
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.dp
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Position
import kotlin.time.Duration.Companion.milliseconds

/**
 * The detail map as both platforms draw it: MapLibre Native (Android's native library, iOS's MapLibre framework)
 * through maplibre-compose, with [style] (DetailMapStyle.json) over OpenFreeMap's vector tiles and the pins on top.
 * MapLibre's own attribution button and logo are off: the screen shows the attribution line itself, always visible.
 * Rotation and tilt are off too (a flat, north-up street map). Tiles come and go with what is on screen; MapLibre keeps
 * its usual ambient cache and nothing is prefetched.
 *
 * The platform renderers only decide whether the map can start (see `MapLibreDetailMapRenderer` in androidMain and
 * iosMain) and then draw this.
 */
@Composable
internal fun MapLibreDetailMap(
    style: String,
    controller: DetailMapController,
    pins: List<DetailPin>,
    selectedKey: String?,
    onPinTap: (String) -> Unit,
    onMapTap: () -> Unit,
    modifier: Modifier,
) {
    // read without subscribing: the camera changes every frame of a pan, and the map must not recompose for it
    val first = remember { Snapshot.withoutReadObservation { controller.camera } }
    val cameraState = rememberCameraState(CameraPosition(target = Position(longitude = first.lng, latitude = first.lat), zoom = first.zoom))
    // the camera goes back to the controller: the picker reads the centre once it rests
    LaunchedEffect(cameraState, controller) {
        snapshotFlow { cameraState.position to cameraState.isCameraMoving }.collect { (p, moving) ->
            controller.reportCamera(DetailCamera(p.target.latitude, p.target.longitude, p.zoom), moving)
        }
    }
    val request = controller.request
    LaunchedEffect(request?.id) {
        val target = request?.camera ?: return@LaunchedEffect
        cameraState.animateTo(CameraPosition(target = Position(longitude = target.lng, latitude = target.lat), zoom = target.zoom), duration = 600.milliseconds)
    }
    val pinJson = remember(pins, selectedKey) { DetailMapPins.featureCollection(pins, selectedKey) }
    MaplibreMap(
        modifier = modifier,
        baseStyle = BaseStyle.Json(style),
        cameraState = cameraState,
        zoomRange = DetailMapCamera.MIN_ZOOM.toFloat()..DetailMapCamera.MAX_ZOOM.toFloat(),
        pitchRange = 0f..0f,
        options = MapOptions(gestureOptions = GestureOptions.RotationLocked, ornamentOptions = OrnamentOptions.AllDisabled),
        onMapClick = { _, offset ->
            // a finger-sized box around the tap, so a small dot or its label is easy to hit
            val r = 22.dp
            val hits = cameraState.projection?.queryRenderedFeatures(
                DpRect(offset.x - r, offset.y - r, offset.x + r, offset.y + r),
                setOf(DetailMapPins.PIN_LAYER, DetailMapPins.LABEL_LAYER),
            ).orEmpty()
            val key = hits.firstNotNullOfOrNull { f -> f.properties?.get("key")?.jsonPrimitive?.contentOrNull }
            if (key != null) {
                onPinTap(key)
                ClickResult.Consume
            } else {
                onMapTap()
                ClickResult.Pass
            }
        },
        onMapLoadFailed = { reason -> controller.reportFailure(reason) },
        onMapLoadFinished = { controller.reportLoaded() },
    ) {
        val source = rememberGeoJsonSource(GeoJsonData.JsonString(pinJson))
        CircleLayer(
            id = DetailMapPins.PIN_LAYER,
            source = source,
            color = feature["fill"].convertToColor(),
            radius = feature["radius"].asNumber().dp,
            strokeColor = const(Ink.text),
            strokeWidth = const(2.dp),
        )
        SymbolLayer(
            id = DetailMapPins.LABEL_LAYER,
            source = source,
            textField = format(span(feature["label"].asString())),
            textFont = const(listOf(OpenFreeMap.FONT)),
            textSize = const(12.sp),
            textColor = const(Ink.text),
            textHaloColor = const(Color.White),
            textHaloWidth = const(1.5.dp),
            textAnchor = const(SymbolAnchor.Top),
            textOffset = offset(0f.em, 0.9f.em),
        )
    }
}
