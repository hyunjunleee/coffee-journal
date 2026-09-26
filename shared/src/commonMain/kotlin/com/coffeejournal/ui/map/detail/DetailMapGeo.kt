package com.coffeejournal.ui.map.detail

import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.KoreaShapes
import com.coffeejournal.domain.rules.Outline
import com.coffeejournal.ui.map.KoreaFrames
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.tan

/** Where the detail map looks: its centre (WGS84) and MapLibre zoom (the world is 512·2^zoom dp wide). */
data class DetailCamera(val lat: Double, val lng: Double, val zoom: Double) {
    val point: GeoPoint? get() = GeoPoint.of(lat, lng)

    /** Route argument "lat,lng,zoom". */
    fun encode(): String = "$lat,$lng,$zoom"

    companion object {
        fun decode(s: String?): DetailCamera? {
            val parts = s?.split(',')?.map { it.trim().toDoubleOrNull() } ?: return null
            if (parts.size != 3) return null
            val p = GeoPoint.of(parts[0], parts[1]) ?: return null
            val z = parts[2]?.takeIf { it.isFinite() } ?: return null
            return DetailCamera(p.lat, p.lng, z.coerceIn(DetailMapCamera.MIN_ZOOM, DetailMapCamera.MAX_ZOOM))
        }
    }
}

/** A south-west / north-east box in degrees (west > east when it crosses the antimeridian). */
data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    /** Route argument "south,west,north,east". */
    fun encode(): String = "$south,$west,$north,$east"

    companion object {
        fun decode(s: String?): GeoBounds? {
            val v = s?.split(',')?.map { it.trim().toDoubleOrNull() } ?: return null
            if (v.size != 4 || v.any { it == null || !it.isFinite() }) return null
            val (s0, w, n, e) = v.map { it!! }
            if (s0 !in -90.0..90.0 || n !in -90.0..90.0 || w !in -180.0..180.0 || e !in -180.0..180.0 || s0 > n) return null
            return GeoBounds(s0, w, n, e)
        }

        /** The box of the Korea map's outline (map units) in degrees. */
        fun ofOutline(o: Outline): GeoBounds = ofMapRect(o.minX.toDouble(), o.minY.toDouble(), o.maxX.toDouble(), o.maxY.toDouble())

        /** A rectangle in Korea-map units (y grows southwards) in degrees. */
        fun ofMapRect(left: Double, top: Double, right: Double, bottom: Double): GeoBounds {
            val nw = KoreaProjection.toGeo(left, top)
            val se = KoreaProjection.toGeo(right, bottom)
            return GeoBounds(south = se.lat, west = nw.lng, north = nw.lat, east = se.lng)
        }
    }
}

/**
 * Web-Mercator camera math for MapLibre (512 dp tiles): which centre and zoom show a box, and the boxes of the
 * Korea map's areas, so the detail map opens on what the SGIS map was showing.
 */
object DetailMapCamera {
    const val TILE_DP = 512.0
    const val MIN_ZOOM = 2.0
    const val MAX_ZOOM = 19.0
    /** A fitted box is not zoomed in further than this (a single point would otherwise fill the screen). */
    const val FIT_MAX_ZOOM = 17.0

    /** South Korea as the SGIS national map frames it; the default for 국내. */
    val korea: GeoBounds
        get() = KoreaFrames.national.let { GeoBounds.ofMapRect(it.left.toDouble(), it.top.toDouble(), it.right.toDouble(), it.bottom.toDouble()) }

    /** The default for 해외: the whole world, a little north of the equator. */
    val world = DetailCamera(20.0, 10.0, MIN_ZOOM)

    fun provinceBounds(code: String): GeoBounds? = KoreaShapes.province(code)?.outline?.let { GeoBounds.ofOutline(it) }

    fun districtBounds(provinceCode: String, districtCode: String): GeoBounds? =
        KoreaShapes.districts(provinceCode).firstOrNull { it.code == districtCode }?.outline?.let { GeoBounds.ofOutline(it) }

    /** Mercator x in 0..1 (0 = 180°W). */
    fun mercatorX(lng: Double): Double = (lng + 180.0) / 360.0

    /** Mercator y in 0..1 (0 = north edge at 85.05°N). */
    fun mercatorY(lat: Double): Double {
        val phi = lat.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
        return (1.0 - ln(tan(PI / 4 + phi / 2)) / PI) / 2.0
    }

    fun latOf(mercatorY: Double): Double = atan(sinh(PI * (1.0 - 2.0 * mercatorY))) * 180.0 / PI

    private fun sinh(x: Double): Double = (exp(x) - exp(-x)) / 2.0

    /**
     * The camera that shows all of [bounds] in a [widthDp] × [heightDp] map with [paddingDp] around it: centred on
     * the box (in Mercator, so north and south margins match) at the largest zoom that fits, at most [maxZoom].
     */
    fun fit(bounds: GeoBounds, widthDp: Double, heightDp: Double, paddingDp: Double = 24.0, maxZoom: Double = FIT_MAX_ZOOM): DetailCamera {
        val x0 = mercatorX(bounds.west)
        var x1 = mercatorX(bounds.east)
        if (x1 < x0) x1 += 1.0 // across the antimeridian
        val y0 = mercatorY(bounds.north)
        val y1 = mercatorY(bounds.south)
        val w = (widthDp - 2 * paddingDp).coerceAtLeast(1.0)
        val h = (heightDp - 2 * paddingDp).coerceAtLeast(1.0)
        val spanX = x1 - x0
        val spanY = y1 - y0
        val zx = if (spanX > 0) log2(w / (TILE_DP * spanX)) else maxZoom
        val zy = if (spanY > 0) log2(h / (TILE_DP * spanY)) else maxZoom
        val zoom = minOf(zx, zy, maxZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
        var cx = (x0 + x1) / 2.0
        if (cx > 1.0) cx -= 1.0
        return DetailCamera(latOf((y0 + y1) / 2.0), cx * 360.0 - 180.0, zoom)
    }

    /**
     * Where the detail map opens: an explicit [camera] (a pin, the picker's point), else the [bounds] the SGIS map
     * showed, else all of South Korea (국내) or the world (해외).
     */
    fun start(camera: DetailCamera?, bounds: GeoBounds?, overseas: Boolean, widthDp: Double, heightDp: Double): DetailCamera =
        camera
            ?: bounds?.let { fit(it, widthDp, heightDp) }
            ?: if (overseas) world else fit(korea, widthDp, heightDp, paddingDp = 8.0)
}
