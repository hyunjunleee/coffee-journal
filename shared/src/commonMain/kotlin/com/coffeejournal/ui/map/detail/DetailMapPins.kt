package com.coffeejournal.ui.map.detail

import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.KoreaRegions
import com.coffeejournal.ui.bean.b.RoasteryPin
import com.coffeejournal.ui.map.CafeSpot
import com.coffeejournal.ui.map.WorldProjection
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** How precisely a pin knows its place, which sets how close the detail map zooms in on it. */
enum class PinPrecision(val zoom: Double) {
    /** Its own position (set on a map). */
    EXACT(16.0),
    /** The centre of the 시·군·구 its location text names. */
    DISTRICT(12.5),
    /** The centre of a 시·도. */
    PROVINCE(9.0),
    /** The centre of a country (해외). */
    COUNTRY(5.0),
}

/** A pin on the detail map, from the same data as the SGIS map's pins ([key] is the same key). */
data class DetailPin(
    val key: String,
    val label: String,
    val point: GeoPoint,
    val precision: PinPrecision,
    val count: Int? = null,
    val description: String = label,
) {
    val exact: Boolean get() = precision == PinPrecision.EXACT
    /** Where the map goes to show this pin. */
    val camera: DetailCamera get() = DetailCamera(point.lat, point.lng, precision.zoom)
}

/** The detail map's pins: built from the roastery and café map data, and handed to MapLibre as GeoJSON. */
object DetailMapPins {
    const val PIN_LAYER = "detail-pins"
    const val LABEL_LAYER = "detail-pin-labels"

    /**
     * A roastery pin as the SGIS / world map places it: its own position when it has one, else the centre of the
     * area its location text names.
     */
    fun roastery(pin: RoasteryPin, domestic: Boolean): DetailPin? {
        val name = pin.item.name
        val description = "${name}, ${pin.count}잔"
        val own = pin.item.point
        if (pin.exact && own != null) return DetailPin(name, name, own, PinPrecision.EXACT, pin.count, description)
        val point = if (domestic) KoreaProjection.toGeo(pin.at.x, pin.at.y) else WorldProjection.toGeo(pin.at.x, pin.at.y)
        val geo = GeoPoint.of(point.lat, point.lng) ?: return null
        val precision = when {
            !domestic -> PinPrecision.COUNTRY
            KoreaRegions.matchText(pin.item.location)?.districtName != null -> PinPrecision.DISTRICT
            else -> PinPrecision.PROVINCE
        }
        return DetailPin(name, name, geo, precision, pin.count, "$description, ${pin.place} 중심")
    }

    fun roasteries(pins: List<RoasteryPin>, domestic: Boolean): List<DetailPin> = pins.mapNotNull { roastery(it, domestic) }

    /** Cafés with a position (the others are listed under the map, as on the SGIS map); no count for no visits. */
    fun cafes(spots: List<CafeSpot>): List<DetailPin> = spots.mapNotNull { s ->
        s.point?.let { DetailPin(s.key, s.name, it, PinPrecision.EXACT, s.visits.size, "${s.name}, ${s.visitText}") }
    }

    /** Fill of a pin's dot: ink for an exact position, white (a ring) for an area centre. */
    fun fillOf(pin: DetailPin): String = if (pin.exact) DetailMapPalette.INK else DetailMapPalette.ROAD

    /** The label under a dot: the name, and the count when there is one ("모모스 3"; no "0"). */
    fun labelOf(pin: DetailPin): String = pin.count?.takeIf { it > 0 }?.let { "${pin.label} $it" } ?: pin.label

    /**
     * The pins as a GeoJSON FeatureCollection (coordinates are [lng, lat]); each feature carries its key, label and
     * how to draw it (dot fill, radius dp); the selected pin comes last so it is drawn on top.
     */
    fun featureCollection(pins: List<DetailPin>, selectedKey: String?): String {
        val ordered = pins.sortedBy { if (it.key == selectedKey) 1 else 0 }
        return buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                ordered.forEach { pin ->
                    add(buildJsonObject {
                        put("type", "Feature")
                        putJsonObject("geometry") {
                            put("type", "Point")
                            put("coordinates", JsonArray(listOf(JsonPrimitive(pin.point.lng), JsonPrimitive(pin.point.lat))))
                        }
                        putJsonObject("properties") {
                            put("key", pin.key)
                            put("label", labelOf(pin))
                            put("exact", pin.exact)
                            put("selected", pin.key == selectedKey)
                            put("fill", fillOf(pin))
                            put("radius", if (pin.key == selectedKey) 8 else 6)
                        }
                    })
                }
            }
        }.toString()
    }
}
