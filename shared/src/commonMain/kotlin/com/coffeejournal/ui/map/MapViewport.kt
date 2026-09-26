package com.coffeejournal.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.coffeejournal.domain.rules.MapXY

/**
 * Pure viewport math shared by the Korea and world maps. A [frame] (map units) is fitted into the canvas at zoom 1;
 * [pan] is the canvas offset of the frame's centre from the canvas centre. [bounds] (≥ frame) is everything there is
 * to see: the zoom may go below 1 until the whole of it fits, and the pan keeps the canvas centre inside it.
 */
object MapViewportMath {
    fun fitScale(frame: Rect, canvas: Size): Float =
        if (canvas.width <= 0f || canvas.height <= 0f || frame.width <= 0f || frame.height <= 0f) 1f
        else minOf(canvas.width / frame.width, canvas.height / frame.height)

    /** Canvas px of a map point at total scale [m] (px per unit) with the frame centre at [origin]. */
    fun toCanvas(x: Float, y: Float, frame: Rect, m: Float, origin: Offset): Offset =
        Offset((x - frame.center.x) * m + origin.x, (y - frame.center.y) * m + origin.y)

    fun toMap(canvas: Offset, frame: Rect, m: Float, origin: Offset): Offset =
        Offset((canvas.x - origin.x) / m + frame.center.x, (canvas.y - origin.y) / m + frame.center.y)

    /** The smallest zoom: all of [bounds] fits (1 when the frame already holds everything). */
    fun minZoom(frame: Rect, bounds: Rect, canvas: Size): Float =
        (fitScale(bounds, canvas) / fitScale(frame, canvas)).coerceIn(0.05f, 1f)

    /** Keeps the map point under the canvas centre inside [bounds]. */
    fun clampPan(pan: Offset, frame: Rect, bounds: Rect, m: Float): Offset {
        val minX = (frame.center.x - bounds.right) * m
        val maxX = (frame.center.x - bounds.left) * m
        val minY = (frame.center.y - bounds.bottom) * m
        val maxY = (frame.center.y - bounds.top) * m
        return Offset(pan.x.coerceIn(minOf(minX, maxX), maxOf(minX, maxX)), pan.y.coerceIn(minOf(minY, maxY), maxOf(minY, maxY)))
    }

    /**
     * Pan after one pinch step: the map point under [centroid] stays under the fingers while the zoom goes from
     * [oldZoom] to [newZoom]; then the fingers' own movement is added.
     */
    fun zoomPan(pan: Offset, oldZoom: Float, newZoom: Float, centroid: Offset, panChange: Offset, canvas: Size): Offset {
        val c = centroid - Offset(canvas.width / 2f, canvas.height / 2f)
        val r = if (oldZoom > 0f) newZoom / oldZoom else 1f
        return (pan - c) * r + c + panChange
    }
}

/** Zoom and pan of one map view; the frame changes when the Korea map drills into a 시·도. */
class MapViewport(frame: Rect, bounds: Rect = frame, zoom: Float = 1f, pan: Offset = Offset.Zero, private val maxZoom: Float = 8f) {
    var frame by mutableStateOf(frame)
        private set
    var bounds by mutableStateOf(bounds)
        private set
    var zoom by mutableStateOf(zoom)
        private set
    var pan by mutableStateOf(pan)
        private set
    var canvasSize by mutableStateOf(Size.Zero)

    /** Canvas px per map unit. */
    val scale: Float get() = MapViewportMath.fitScale(frame, canvasSize) * zoom
    val origin: Offset get() = Offset(canvasSize.width / 2f, canvasSize.height / 2f) + pan
    val isZoomed: Boolean get() = zoom > 1.001f

    fun toCanvas(p: MapXY): Offset = MapViewportMath.toCanvas(p.x.toFloat(), p.y.toFloat(), frame, scale, origin)
    fun toCanvas(x: Float, y: Float): Offset = MapViewportMath.toCanvas(x, y, frame, scale, origin)
    fun toMap(canvas: Offset): MapXY = MapViewportMath.toMap(canvas, frame, scale, origin).let { MapXY(it.x.toDouble(), it.y.toDouble()) }

    fun show(frame: Rect, bounds: Rect = frame) {
        this.frame = frame
        this.bounds = bounds
        reset()
    }

    fun reset() {
        zoom = 1f
        pan = Offset.Zero
    }

    /** One pinch / pan step; the point under the fingers stays under them. */
    fun transform(centroid: Offset, panChange: Offset, zoomChange: Float) {
        val min = MapViewportMath.minZoom(frame, bounds, canvasSize)
        val newZoom = (zoom * zoomChange).coerceIn(min, maxZoom)
        val moved = MapViewportMath.zoomPan(pan, zoom, newZoom, centroid, panChange, canvasSize)
        zoom = newZoom
        pan = MapViewportMath.clampPan(moved, frame, bounds, scale)
    }

    /** Zooms around the canvas centre so [p] ends up there (the picker centres the chosen point). */
    fun centerOn(p: MapXY, atZoom: Float = zoom) {
        zoom = atZoom.coerceIn(MapViewportMath.minZoom(frame, bounds, canvasSize), maxZoom)
        val m = scale
        pan = MapViewportMath.clampPan(Offset((frame.center.x - p.x.toFloat()) * m, (frame.center.y - p.y.toFloat()) * m), frame, bounds, m)
    }

    /** Saved as [zoom, panX, panY]; the frame is restored by its owner. */
    fun saveState(): List<Float> = listOf(zoom, pan.x, pan.y)

    fun restoreState(saved: List<Float>?) {
        if (saved == null || saved.size < 3) return
        zoom = saved[0]
        pan = Offset(saved[1], saved[2])
    }
}
