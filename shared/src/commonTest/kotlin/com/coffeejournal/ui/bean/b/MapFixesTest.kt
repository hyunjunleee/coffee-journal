package com.coffeejournal.ui.bean.b

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.ui.theme.Ink
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** beanB-1 (tasted colour), beanB-2 (tap resolution), beanB-4 (farm jump), beanB-10 (pinch), beanB-6/-7 (blend). */
class MapFixesTest {
    private val polygons = WorldMapGeometry.parseCoffeeMap()

    // The default zoom on a 411dp phone: the canvas is 379dp wide (16dp gutters). Dp values below are in "canvas px"
    // at density 1; the view-unit radii do not depend on the density.
    private val canvasW = 379f
    private val canvasH = canvasW / 1.6f
    private val m = WorldMapGeometry.fitScale(canvasW, canvasH)
    private val dotRadius = maxOf(WorldMapGeometry.REGION_DOT_RADIUS * m, 2.5f) / m // the drawn dot
    private val seaSlop = 14f / m

    private fun tap(x: Float, y: Float) = WorldMapGeometry.resolveTap(polygons, Offset(x, y), dotRadius, seaSlop)
    private fun region(en: String, name: String) = CoffeeCountries.byEn.getValue(en).regions.first { it.name == name }

    /** Share of sample points inside [country] (0.5-unit grid) whose tap opens that country's panel. */
    private fun countryShare(country: String): Double {
        val poly = polygons.first { it.name == country }
        var inside = 0; var country0 = 0
        var y = poly.bounds.top
        while (y <= poly.bounds.bottom) {
            var x = poly.bounds.left
            while (x <= poly.bounds.right) {
                if (WorldMapGeometry.hitCountry(polygons, Offset(x, y))?.name == country) {
                    inside++
                    if (tap(x, y) == MapTap.Country(country)) country0++
                }
                x += 0.5f
            }
            y += 0.5f
        }
        return country0.toDouble() / inside
    }

    @Test fun defaultZoom_mostOfAProducerOpensTheCountryPanel() {
        // before: the 14dp dot slop (29 units) was checked first, so 0% of Colombia, Ethiopia and Kenya reached the country
        assertTrue(countryShare("Colombia") > 0.6, "Colombia ${countryShare("Colombia")}")
        assertTrue(countryShare("Ethiopia") > 0.6, "Ethiopia ${countryShare("Ethiopia")}")
        assertTrue(countryShare("Kenya") > 0.55, "Kenya ${countryShare("Kenya")}")
        assertTrue(countryShare("Brazil") > 0.9, "Brazil ${countryShare("Brazil")}")
    }

    @Test fun aTapOnADrawnDotStillOpensItsRegion() {
        val huila = region("Colombia", "Huila")
        val hit = tap(huila.x + 2f, huila.y + 2f) as MapTap.Region
        assertEquals("Huila", hit.hit.region.name)
        // Zambia's Nyika Plateau dot is drawn over Malawi: tapping it there still opens it
        val nyika = region("Zambia", "Nyika Plateau")
        assertEquals("Nyika Plateau", (tap(nyika.x, nyika.y) as MapTap.Region).hit.region.name)
    }

    @Test fun neighboursDotsDoNotTakeTapsAcrossTheBorder() {
        // northern Brazil, 17 units from Guyana's Rupununi dot: Brazil, not "가이아나 · Rupununi"
        assertEquals(MapTap.Country("Brazil"), tap(321.3f, 275f))
        // Somaliland next to Ethiopia's Harrar dot: the non-producer message, not "에티오피아 · Harrar"
        val harrar = region("Ethiopia", "Harrar")
        assertEquals(MapTap.Country("Somaliland"), tap(harrar.x + 6f, harrar.y))
    }

    @Test fun seaTapsReachNearbyDotsOnly() {
        val jamaica = region("Jamaica", "Blue Mountains") // drawn in the sea off the simplified island
        assertEquals("Blue Mountains", (tap(jamaica.x, jamaica.y + 1f) as MapTap.Region).hit.region.name)
        assertNull(tap(410f, 300f), "open ocean selects nothing")
    }

    @Test fun pinchKeepsThePointUnderTheFingers() {
        val fit = m
        val centroid = Offset(300f, 125f) // near the vertical middle, so the fitted height does not clamp the pan
        val before = WorldMapGeometry.toView(centroid, fit, Offset(canvasW / 2, canvasH / 2))
        val pan = WorldMapGeometry.zoomPan(Offset.Zero, 1f, 2f, centroid, Offset.Zero, fit, canvasW, canvasH)
        val after = WorldMapGeometry.toView(centroid, fit * 2f, Offset(canvasW / 2, canvasH / 2) + pan)
        assertEquals(before.x, after.x, 0.01f)
        assertEquals(before.y, after.y, 0.01f)
        // a second step from a panned state, plus the fingers' own movement
        val pan2 = WorldMapGeometry.zoomPan(pan, 2f, 3f, centroid, Offset(5f, -3f), fit, canvasW, canvasH)
        val moved = WorldMapGeometry.toView(centroid + Offset(5f, -3f), fit * 3f, Offset(canvasW / 2, canvasH / 2) + pan2)
        assertEquals(before.x, moved.x, 0.01f)
        assertEquals(before.y, moved.y, 0.01f)
        // zooming back out to the fitted scale recentres the map
        assertEquals(Offset.Zero, WorldMapGeometry.zoomPan(pan2, 3f, 1f, centroid, Offset.Zero, fit, canvasW, canvasH))
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance(); val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    @Test fun tastedCountriesStandOutFromProducersAndThePage() {
        assertTrue(contrast(MapPalette.tasted, MapPalette.producer) >= 3f, "vs producer green ${contrast(MapPalette.tasted, MapPalette.producer)}")
        assertTrue(contrast(MapPalette.tasted, Ink.bg) >= 3f, "vs ivory page ${contrast(MapPalette.tasted, Ink.bg)}")
        assertTrue(contrast(MapPalette.tasted, MapPalette.otherLand) >= 3f, "vs other land ${contrast(MapPalette.tasted, MapPalette.otherLand)}")
        assertTrue(contrast(MapPalette.triedDot, MapPalette.tasted) >= 3f, "tried dots on a tasted country")
        // the old fill (ink) against the producer green was about 1.4:1
        assertTrue(contrast(Ink.accent, MapPalette.producer) < 1.5f)
    }

    @Test fun farmJumpCentresTheCard() {
        // card 1500px below the viewport top, 300px tall, viewport 1800px: its middle lands in the middle
        assertEquals(200 + 1500 - 750, FarmJump.target(scrollValue = 200, cardTop = 1500f, cardHeight = 300, viewportHeight = 1800))
        // a card taller than the viewport is aligned at its top
        assertEquals(200 + 1500, FarmJump.target(scrollValue = 200, cardTop = 1500f, cardHeight = 2000, viewportHeight = 1800))
    }

    @Test fun commercialBlendLine_fallsBackToTheNamesRoastery() {
        val base = Entry(id = "e", createdAt = 1, beanMode = BeanMode.COMMERCIAL_BLEND)
        assertEquals("상업 블렌드 · 듀잇", BlendSources.commercialLine(base.copy(name = "하우스 블렌드 (프릳츠)", roastery = "듀잇")))
        assertEquals("상업 블렌드 · 프릳츠", BlendSources.commercialLine(base.copy(name = "하우스 블렌드 (프릳츠)")))
        assertEquals("상업 블렌드", BlendSources.commercialLine(base.copy(name = "하우스 블렌드")))
    }

    @Test fun gramsInput_acceptsOnlyNumbersBeingTyped() {
        assertEquals("10", BlendSources.gramsInput("10"))
        assertEquals("10.", BlendSources.gramsInput("10."))
        assertEquals("10.5", BlendSources.gramsInput("10,5"))
        assertEquals("", BlendSources.gramsInput(""))
        assertNull(BlendSources.gramsInput("10g"))
        assertNull(BlendSources.gramsInput("1.2.3"))
        assertNull(BlendSources.gramsInput("NaN"))
        assertNull(BlendSources.gramsInput("-3"))
    }
}
