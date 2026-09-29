package com.coffeejournal.ui.map

import androidx.compose.ui.geometry.Rect
import com.coffeejournal.domain.reference.WorldMapData
import com.coffeejournal.domain.rules.MapXY

/**
 * What the coffee map draws apart from the web's world map, whose viewBox starts at 128°W and so leaves Hawaii out:
 * Hawaii, 1.8 times larger, in the empty Pacific west of Mexico inside a dashed [frame], as maps of the United States
 * draw it. It keeps its latitude band roughly (south of the Tropic of Cancer), not its longitude. Outlines: Natural
 * Earth 1:50m admin 0 (public domain), the seven main islands simplified (Douglas–Peucker, 0.03°) and moved with
 * [toView]. The pin map of roasteries (real positions) does not use it.
 */
object WorldMapInsets {
    /** The inset's name, as a map outline and as a coffee origin ([com.coffeejournal.domain.reference.CoffeeCountries]). */
    const val HAWAII = "Hawaii"

    private const val SCALE = 1.8
    private const val LNG0 = -157.5
    private const val LAT0 = 20.6
    private const val X0 = 163.0
    private const val Y0 = 217.0

    /** The dashed frame around the inset, in viewBox units; nothing else of the map is inside it. */
    val frame: Rect = Rect(146f, 205f, 180f, 229f)

    val paths: List<WorldMapData.CountryPath> = listOf(
        WorldMapData.CountryPath(
            HAWAII, "inset-hawaii",
            "M172.2,224.6L171.7,224.8L170.8,224.3L170.7,222.8L170.0,221.1L171.1,219.8L170.7,219.1L171.0,218.6L174.0,219.9L174.6,220.5L174.7,221.1L175.1,221.2L175.2,221.6L175.9,222.2L174.7,223.1L173.5,223.4L172.4,224.2ZM164.4,214.0L166.8,214.3L166.1,214.8L164.0,214.5L164.2,214.0ZM167.9,215.4L168.9,215.3L170.2,216.2L169.7,216.8L168.2,217.0L167.9,216.0L167.2,215.9L166.9,215.3L167.0,215.0L167.4,214.9ZM161.6,212.9L161.9,212.9L162.4,213.6L161.6,213.8L161.1,213.4L160.8,213.5L160.7,213.3L160.5,213.3L160.7,213.6L160.1,213.6L159.3,212.3L160.0,212.2L160.8,211.7ZM154.0,210.6L153.6,210.9L152.9,210.7L152.0,210.1L152.3,209.6L153.0,209.2L154.1,209.2L154.4,209.8ZM150.1,211.0L149.9,211.2L149.9,210.8L150.5,210.2L150.8,210.3L150.6,210.7ZM166.1,216.2L165.5,216.2L165.2,215.5L166.0,215.5L166.3,215.9Z",
        ),
    )

    fun inHawaii(lat: Double, lng: Double): Boolean = lat in 18.5..22.5 && lng in -160.5..-154.5

    /** A place on the coffee map: in the inset for Hawaii, else where [WorldProjection] puts it. */
    fun toView(lat: Double, lng: Double): MapXY =
        if (!inHawaii(lat, lng)) WorldProjection.toView(lat, lng)
        else MapXY(X0 + (lng - LNG0) * WorldProjection.UNITS_PER_DEGREE * SCALE, Y0 - (lat - LAT0) * WorldProjection.UNITS_PER_DEGREE * SCALE)
}
