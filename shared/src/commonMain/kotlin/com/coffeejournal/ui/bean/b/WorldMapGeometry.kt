package com.coffeejournal.ui.bean.b

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.WorldMapData

/** One country outline in viewBox units; a country may consist of several rings (islands). */
class MapPolygon(val name: String, val rings: List<List<Offset>>) {
    val bounds: Rect = run {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        rings.forEach { ring -> ring.forEach { p -> if (p.x < l) l = p.x; if (p.x > r) r = p.x; if (p.y < t) t = p.y; if (p.y > b) b = p.y } }
        if (l == Float.MAX_VALUE) Rect.Zero else Rect(l, t, r, b)
    }
    val area: Float get() = bounds.width * bounds.height

    fun contains(p: Offset): Boolean = bounds.contains(p) && rings.any { WorldMapGeometry.ringContains(it, p.x, p.y) }
}

data class RegionHit(val country: CoffeeCountries.Country, val region: CoffeeCountries.Region)

/** What a tap on the coffee map selects. */
sealed interface MapTap {
    data class Region(val hit: RegionHit) : MapTap
    data class Country(val name: String) : MapTap
}

/** Pure geometry for the coffee map: SVG path parsing, hit testing and coordinate helpers. */
object WorldMapGeometry {
    const val REGION_DOT_RADIUS = 2.2f
    const val REGION_TAP_RADIUS = 6f

    /**
     * The deepest zoom (1 = the fitted map): deep enough to tell apart most regions of Rwanda or Burundi. Regions closer
     * than about 25 km (Acatenango next to Antigua) still share a dot there; the country's list names them all.
     */
    const val MAX_SCALE = 40f

    /**
     * A drawn region dot's radius in canvas px at the total scale [m] ([dp] px to a dp): 2.5 dp on the fitted map,
     * growing with the zoom to 4 dp at most, so zooming in spreads the dots apart rather than growing them into each other.
     */
    fun dotRadiusPx(m: Float, dp: Float): Float = (REGION_DOT_RADIUS * m).coerceIn(2.5f * dp, 4f * dp)

    /** The dots drawn and tapped at the zoom [scale] (total scale [m]): a dot's radius of space between any two. */
    fun shownDots(scale: Float, m: Float, dp: Float): List<WorldRegions.Dot> = WorldRegions.shown(scale, 3f * dotRadiusPx(m, dp) / m)

    /** Parses path data that only uses absolute M/L/Z commands into closed rings (degenerate rings dropped). */
    fun parsePath(d: String): List<List<Offset>> {
        val rings = ArrayList<List<Offset>>()
        var ring = ArrayList<Offset>()
        var pendingX: Float? = null
        var i = 0
        fun closeRing() { if (ring.size >= 3) rings.add(ring); ring = ArrayList(); pendingX = null }
        while (i < d.length) {
            val c = d[i]
            when {
                c == 'M' || c == 'Z' || c == 'z' -> { closeRing(); i++ }
                c.isLetter() || c == ',' || c.isWhitespace() -> i++
                else -> {
                    val start = i
                    if (c == '-' || c == '+') i++
                    while (i < d.length && (d[i].isDigit() || d[i] == '.')) i++
                    if (i == start) { i++; continue }
                    val v = d.substring(start, i).toFloatOrNull() ?: continue
                    val px = pendingX
                    if (px == null) pendingX = v else { ring.add(Offset(px, v)); pendingX = null }
                }
            }
        }
        closeRing()
        return rings
    }

    fun parseAll(paths: List<WorldMapData.CountryPath> = WorldMapData.paths): List<MapPolygon> =
        paths.map { MapPolygon(it.name, parsePath(it.d)) }

    /** Even-odd ray casting against one ring. */
    fun ringContains(ring: List<Offset>, x: Float, y: Float): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i].x; val yi = ring[i].y; val xj = ring[j].x; val yj = ring[j].y
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** The smallest country containing the point, so an enclave wins over the country around it. */
    fun hitCountry(polygons: List<MapPolygon>, p: Offset): MapPolygon? =
        polygons.filter { it.contains(p) }.minByOrNull { it.area }

    /**
     * Like the web, where a dot catches only taps on its own circle and the country path gets every other tap: on land
     * a dot wins only within [dotRadius] (the dot as drawn, in viewBox units) and only when it belongs to the country
     * under the finger or is drawn over it, so a neighbour's dot near the border does not take the tap; otherwise the
     * country does. In the sea the nearest dot within [seaSlop] is taken, so coastal and island dots stay reachable.
     */
    fun resolveTap(polygons: List<MapPolygon>, p: Offset, dotRadius: Float, seaSlop: Float, dots: List<WorldRegions.Dot> = WorldRegions.webDots): MapTap? {
        val country = hitCountry(polygons, p) ?: return hitRegion(p, seaSlop, dots)?.let { MapTap.Region(it) }
        val dot = hitRegion(p, dotRadius, dots) { c, r -> c.en == country.name || country.contains(Offset(r.x, r.y)) }
        return if (dot != null) MapTap.Region(dot) else MapTap.Country(country.name)
    }

    /**
     * Pan after one pinch step: the viewBox point under [centroid] (canvas px) stays under the fingers while the scale
     * goes from [oldScale] to [newScale]; then the fingers' own movement [panChange] is added and the pan clamped.
     */
    fun zoomPan(pan: Offset, oldScale: Float, newScale: Float, centroid: Offset, panChange: Offset, fit: Float, canvasW: Float, canvasH: Float): Offset {
        val c = centroid - Offset(canvasW / 2f, canvasH / 2f)
        val r = if (oldScale > 0f) newScale / oldScale else 1f
        return clampPan((pan - c) * r + c + panChange, fit * newScale, canvasW, canvasH)
    }

    /** The nearest of the [dots] drawn within [radius] viewBox units (among those [accept] lets through), if any. */
    fun hitRegion(
        p: Offset,
        radius: Float = REGION_TAP_RADIUS,
        dots: List<WorldRegions.Dot> = WorldRegions.webDots,
        accept: (CoffeeCountries.Country, CoffeeCountries.Region) -> Boolean = { _, _ -> true },
    ): RegionHit? {
        var best: RegionHit? = null
        var bestDist = radius * radius
        dots.forEach { d ->
            val dx = d.region.x - p.x; val dy = d.region.y - p.y
            val d2 = dx * dx + dy * dy
            if (d2 <= bestDist && accept(d.country, d.region)) { bestDist = d2; best = RegionHit(d.country, d.region) }
        }
        return best
    }

    /** Scale that fits the whole viewBox into a canvas ("contain"). */
    fun fitScale(canvasW: Float, canvasH: Float): Float =
        if (canvasW <= 0f || canvasH <= 0f) 1f else minOf(canvasW / WorldMapData.VIEW_W, canvasH / WorldMapData.VIEW_H)

    val viewCenter: Offset = Offset(WorldMapData.VIEW_X + WorldMapData.VIEW_W / 2f, WorldMapData.VIEW_Y + WorldMapData.VIEW_H / 2f)

    /** Canvas point of a viewBox point given the total scale [m] and the canvas point the viewBox centre maps to. */
    fun toCanvas(view: Offset, m: Float, origin: Offset): Offset =
        Offset((view.x - viewCenter.x) * m + origin.x, (view.y - viewCenter.y) * m + origin.y)

    fun toView(canvas: Offset, m: Float, origin: Offset): Offset =
        Offset((canvas.x - origin.x) / m + viewCenter.x, (canvas.y - origin.y) / m + viewCenter.y)

    /** Keeps a zoomed map from being panned off the canvas. */
    fun clampPan(pan: Offset, m: Float, canvasW: Float, canvasH: Float): Offset {
        val maxX = maxOf(0f, WorldMapData.VIEW_W * m / 2f - canvasW / 2f)
        val maxY = maxOf(0f, WorldMapData.VIEW_H * m / 2f - canvasH / 2f)
        return Offset(if (maxX == 0f) 0f else pan.x.coerceIn(-maxX, maxX), if (maxY == 0f) 0f else pan.y.coerceIn(-maxY, maxY))
    }
}
