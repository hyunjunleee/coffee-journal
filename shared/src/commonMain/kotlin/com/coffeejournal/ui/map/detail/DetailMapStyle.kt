package com.coffeejournal.ui.map.detail

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The detail map's data: OpenFreeMap's public instance (openfreemap.org, checked 2026-09) serves OpenStreetMap as
 * vector tiles in the unmodified OpenMapTiles schema, with no key, no account and no cookies; commercial use is allowed
 * and attribution is required. Only what the map shows is requested (MapLibre's ambient cache keeps recent tiles);
 * nothing is prefetched or bulk-downloaded.
 */
object OpenFreeMap {
    /** TileJSON of the planet tiles (it names the current weekly build's tile URLs). */
    const val TILEJSON_URL = "https://tiles.openfreemap.org/planet"
    /** Glyphs for Latin text; Hangul, Hanja and Kana are drawn by the device's own font (MapLibre local ideographs). */
    const val GLYPHS_URL = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"
    const val HOST = "tiles.openfreemap.org"
    /** A font stack the glyphs endpoint serves (the one OpenFreeMap's own styles use). */
    const val FONT = "Noto Sans Regular"
    /** Shown on the map at all times: OpenMapTiles' and OpenStreetMap's attribution. */
    const val ATTRIBUTION = "© OpenMapTiles © OpenStreetMap contributors"
    const val ATTRIBUTION_HTML =
        "<a href=\"https://openfreemap.org\">OpenFreeMap</a> <a href=\"https://www.openmaptiles.org/\">&copy; OpenMapTiles</a> " +
            "<a href=\"https://www.openstreetmap.org/copyright\">&copy; OpenStreetMap contributors</a>"
    const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
    const val SITE_URL = "https://openfreemap.org/"
    const val OPENMAPTILES_URL = "https://openmaptiles.org/"

    /** The OpenMapTiles vector layers the TileJSON lists (schema 3.x); the style only reads these. */
    val sourceLayers: Set<String> = setOf(
        "aerodrome_label", "aeroway", "boundary", "building", "housenumber", "landcover", "landuse", "mountain_peak",
        "park", "place", "poi", "transportation", "transportation_name", "water", "water_name", "waterway",
    )
}

/** The archive palette on the map: ivory land, blue-grey water, white roads with ink casings, ink labels. */
object DetailMapPalette {
    const val LAND = "#F5F4EF"
    const val LAND_RAISED = "#ECEBE5"
    const val PARK = "#DFE6D0"
    const val WOOD = "#D8E0C6"
    const val WATER = "#C3CED4"
    const val WATERWAY = "#B2C0C8"
    const val WATER_TEXT = "#4B606B"
    const val BUILDING = "#E0DED7"
    const val BUILDING_LINE = "#CBC8BF"
    const val ROAD = "#FFFFFF"
    const val INK = "#191916"
    const val INK_MUTED = "#5D5B54"
    const val INK_FAINT = "#858177"
    const val RAIL = "#9E9B92"
    const val CAFE = "#8B5C35"
}

/**
 * Builds the detail map's MapLibre style (style spec v8) over the OpenMapTiles schema: ivory land, muted blue-grey
 * water, faint green parks, light grey buildings from z14, white roads with ink casings weighted by road class, and
 * Korean labels (`name:ko`, else `name`) in ink with an ivory halo. No sprite: nothing on the map needs icons.
 */
object DetailMapStyle {
    const val SOURCE = "openmaptiles"

    /** The style as JSON text, built once. */
    val json: String by lazy { build().toString() }

    fun build(): JsonObject = obj(
        "version" to 8,
        "name" to "coffee_journal archive",
        "sources" to obj(SOURCE to obj("type" to "vector", "url" to OpenFreeMap.TILEJSON_URL, "attribution" to OpenFreeMap.ATTRIBUTION_HTML)),
        "glyphs" to OpenFreeMap.GLYPHS_URL,
        "layers" to JsonArray(layers()),
    )

    private val P = DetailMapPalette

    /** `name:ko`, else the local name. */
    val koreanName: JsonArray = arr("coalesce", arr("get", "name:ko"), arr("get", "name"))

    private fun layers(): List<JsonObject> = buildList {
        add(obj("id" to "background", "type" to "background", "paint" to obj("background-color" to P.LAND)))
        // ground cover, faint
        add(fill("landcover-wood", "landcover", classIn("wood"), P.WOOD, opacity = 0.4))
        add(fill("landcover-grass", "landcover", classIn("grass"), P.PARK, opacity = 0.6))
        add(fill("landuse-institution", "landuse", classIn("school", "university", "college", "hospital", "cemetery"), P.LAND_RAISED, opacity = 0.9, minzoom = 12))
        add(fill("park", "park", null, P.PARK, opacity = 0.5))
        // water
        add(layer("waterway", "line", "waterway", minzoom = 8, filter = notTunnel(),
            layout = mapOf("line-cap" to "round", "line-join" to "round"),
            // rivers wider than streams and canals (one zoom curve, the class picked at each stop)
            paint = mapOf("line-color" to P.WATERWAY, "line-width" to arr(
                "interpolate", arr("exponential", 1.3), arr("zoom"),
                8, riverOr(0.6, 0.2), 12, riverOr(1.6, 0.4), 16, riverOr(5, 2), 18, riverOr(10, 4),
            ))))
        add(fill("water", "water", notTunnel(), P.WATER))
        // buildings from z14, fading in
        add(layer("building", "fill", "building", minzoom = 14,
            paint = mapOf("fill-color" to P.BUILDING, "fill-outline-color" to P.BUILDING_LINE, "fill-opacity" to zoom(1.0, 14 to 0.45, 15 to 1))))
        // roads: casings (ink, stronger for bigger roads) under white fills, small roads first
        roads.forEach { r -> add(road("road-${r.id}-casing", r, casing = true)) }
        add(layer("road-path", "line", "transportation", minzoom = 14, filter = all(lines(), classIn("path", "track")),
            paint = mapOf("line-color" to P.INK_FAINT, "line-width" to zoom(1.3, 14 to 0.6, 18 to 1.6), "line-dasharray" to arr(2, 1.5), "line-opacity" to 0.7)))
        roads.forEach { r -> add(road("road-${r.id}", r, casing = false)) }
        add(layer("rail", "line", "transportation", minzoom = 10, filter = all(lines(), classIn("rail", "transit"), notTunnel()),
            paint = mapOf("line-color" to P.RAIL, "line-width" to zoom(1.3, 10 to 0.6, 16 to 1.6), "line-dasharray" to arr(3, 2))))
        add(layer("boundary", "line", "boundary", filter = all(arr("in", arr("get", "admin_level"), arr("literal", arr(2, 4))), arr("!=", arr("get", "maritime"), 1)),
            paint = mapOf("line-color" to P.INK_FAINT, "line-width" to zoom(1.3, 5 to 0.6, 12 to 1.4), "line-dasharray" to arr(3, 2), "line-opacity" to 0.7)))
        // labels
        add(label("water-name", "water_name", null, size = zoom(1.2, 8 to 11, 14 to 13), color = P.WATER_TEXT, placeLine = false,
            filter = arr("match", arr("geometry-type"), arr("Point", "MultiPoint"), true, false)))
        add(label("water-name-line", "water_name", null, size = const(12), color = P.WATER_TEXT, placeLine = true,
            filter = arr("match", arr("geometry-type"), arr("LineString", "MultiLineString"), true, false)))
        add(label("waterway-name", "waterway", 12, size = const(11), color = P.WATER_TEXT, placeLine = true, filter = arr("has", "name")))
        add(label("road-name", "transportation_name", 13, size = zoom(1.2, 13 to 10, 17 to 12), color = P.INK_MUTED, placeLine = true,
            filter = arr("!", classIn("ferry", "rail", "transit"))))
        add(label("poi-cafe", "poi", 15, size = const(11.5), color = P.CAFE, placeLine = false, filter = classIn("cafe")))
        add(label("poi", "poi", 17, size = const(10.5), color = P.INK_FAINT, placeLine = false,
            filter = all(arr("!", classIn("cafe")), arr("<=", arr("get", "rank"), 20))))
        add(label("place-neighbourhood", "place", 12, size = zoom(1.2, 12 to 11, 16 to 13), color = P.INK_MUTED, placeLine = false,
            filter = classIn("suburb", "quarter", "neighbourhood")))
        add(label("place-village", "place", 11, size = zoom(1.2, 11 to 11, 15 to 13), color = P.INK_MUTED, placeLine = false, filter = classIn("village", "hamlet")))
        add(label("place-town", "place", 8, size = zoom(1.2, 8 to 11, 12 to 14, 15 to 16), color = P.INK, placeLine = false, filter = classIn("town")))
        add(label("place-city", "place", 5, size = zoom(1.2, 5 to 12, 10 to 16, 14 to 20), color = P.INK, placeLine = false, filter = classIn("city")))
        add(label("place-state", "place", 5, size = const(12), color = P.INK_FAINT, placeLine = false, filter = classIn("state", "province"), maxzoom = 9))
        add(label("place-country", "place", null, size = const(13), color = P.INK_FAINT, placeLine = false, filter = classIn("country"), maxzoom = 7))
    }

    /** One road class group: [classes], its minimum zoom and the line widths (dp) by zoom, fill and casing. */
    private class Road(val id: String, val classes: List<String>, val minzoom: Int, val casingOpacity: Double, val fill: List<Pair<Number, Number>>, val casing: List<Pair<Number, Number>>)

    private val roads = listOf(
        Road("minor", listOf("minor", "service"), 12, 0.28, listOf(12 to 0.3, 14 to 1.6, 16 to 4, 18 to 10), listOf(12 to 0.6, 14 to 2.6, 16 to 5.4, 18 to 12)),
        Road("secondary", listOf("secondary", "tertiary"), 9, 0.45, listOf(9 to 0.3, 12 to 1, 14 to 2.8, 18 to 14), listOf(9 to 0.6, 12 to 1.8, 14 to 4.2, 18 to 16.5)),
        Road("primary", listOf("primary", "trunk"), 7, 0.6, listOf(7 to 0.4, 10 to 1.2, 14 to 4, 18 to 18), listOf(7 to 0.8, 10 to 2, 14 to 5.6, 18 to 21)),
        Road("motorway", listOf("motorway"), 5, 0.75, listOf(5 to 0.5, 10 to 1.8, 14 to 5, 18 to 22), listOf(5 to 1, 10 to 2.8, 14 to 7, 18 to 26)),
    )

    private fun road(id: String, r: Road, casing: Boolean): JsonObject = layer(
        id, "line", "transportation", minzoom = r.minzoom, filter = all(lines(), classIn(*r.classes.toTypedArray())),
        layout = mapOf("line-cap" to "round", "line-join" to "round"),
        paint = if (casing) mapOf("line-color" to P.INK, "line-opacity" to r.casingOpacity, "line-width" to zoom(1.4, *r.casing.toTypedArray()))
        else mapOf("line-color" to P.ROAD, "line-width" to zoom(1.4, *r.fill.toTypedArray())),
    )

    private fun label(id: String, sourceLayer: String, minzoom: Int?, size: JsonElement, color: String, placeLine: Boolean, filter: JsonElement?, maxzoom: Int? = null): JsonObject {
        val layout = buildMap<String, Any?> {
            put("text-field", koreanName)
            put("text-font", arr(OpenFreeMap.FONT))
            put("text-size", size)
            put("text-max-width", 8)
            if (placeLine) {
                put("symbol-placement", "line")
                put("text-rotation-alignment", "map")
            }
        }
        return layer(id, "symbol", sourceLayer, minzoom = minzoom, maxzoom = maxzoom, filter = filter, layout = layout,
            paint = mapOf("text-color" to color, "text-halo-color" to P.LAND, "text-halo-width" to 1.5, "text-halo-blur" to 0.5))
    }

    private fun fill(id: String, sourceLayer: String, filter: JsonElement?, color: String, opacity: Double = 1.0, minzoom: Int? = null): JsonObject =
        layer(id, "fill", sourceLayer, minzoom = minzoom, filter = filter, paint = mapOf("fill-color" to color, "fill-opacity" to opacity))

    private fun layer(
        id: String, type: String, sourceLayer: String, minzoom: Int? = null, maxzoom: Int? = null, filter: JsonElement? = null,
        layout: Map<String, Any?> = emptyMap(), paint: Map<String, Any?> = emptyMap(),
    ): JsonObject = obj(
        "id" to id, "type" to type, "source" to SOURCE, "source-layer" to sourceLayer,
        "minzoom" to minzoom, "maxzoom" to maxzoom, "filter" to filter,
        "layout" to layout.takeIf { it.isNotEmpty() }?.let { obj(*it.toList().toTypedArray()) },
        "paint" to paint.takeIf { it.isNotEmpty() }?.let { obj(*it.toList().toTypedArray()) },
    )

    // expressions
    private fun zoom(base: Double, vararg stops: Pair<Number, Number>): JsonArray =
        arr("interpolate", arr("exponential", base), arr("zoom"), *stops.flatMap { listOf(it.first, it.second) }.toTypedArray())
    private fun const(v: Number): JsonPrimitive = JsonPrimitive(v)
    private fun riverOr(river: Number, other: Number): JsonArray = arr("match", arr("get", "class"), arr("river"), river, other)
    private fun classIn(vararg classes: String): JsonArray = arr("match", arr("get", "class"), arr(*classes), true, false)
    private fun lines(): JsonArray = arr("match", arr("geometry-type"), arr("LineString", "MultiLineString"), true, false)
    private fun notTunnel(): JsonArray = arr("!=", arr("get", "brunnel"), "tunnel")
    private fun all(vararg e: JsonElement): JsonArray = arr("all", *e)
}

internal fun arr(vararg v: Any?): JsonArray = JsonArray(v.map { it.toJsonElement() })

/** A JSON object without the null members (optional style properties are simply left out). */
internal fun obj(vararg members: Pair<String, Any?>): JsonObject =
    JsonObject(members.filter { it.second != null }.associate { it.first to it.second.toJsonElement() })

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is List<*> -> JsonArray(map { it.toJsonElement() })
    else -> error("not a JSON value: $this")
}
