package com.coffeejournal.ui.bean.b

import androidx.compose.ui.geometry.Offset
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.WorldMapData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldMapGeometryTest {
    private val polygons = WorldMapGeometry.parseAll()

    @Test fun parsesEveryCountryPath() {
        assertEquals(175, WorldMapData.paths.size)
        assertEquals(WorldMapData.paths.size, polygons.size)
        assertTrue(polygons.all { it.rings.isNotEmpty() && it.rings.all { r -> r.size >= 3 } }, "every country has at least one ring")
        assertTrue(polygons.first { it.name == "Fiji" }.rings.size >= 2, "compound paths split into rings")
        assertTrue(polygons.all { it.bounds.left >= WorldMapData.VIEW_X - 200 && it.bounds.right <= WorldMapData.VIEW_X + WorldMapData.VIEW_W + 200 })
    }

    @Test fun regionDotsFallInsideTheirCountry() {
        listOf("Brazil" to "Cerrado", "Ethiopia" to "Yirgacheffe", "Colombia" to "Huila", "Kenya" to "Nyeri", "Guatemala" to "Antigua").forEach { (en, name) ->
            val region = CoffeeCountries.byEn.getValue(en).regions.first { it.name == name }
            val hit = WorldMapGeometry.hitCountry(polygons, Offset(region.x, region.y))
            assertEquals(en, hit?.name, "$name should be inside $en")
        }
    }

    @Test fun oceanHitsNothingAndRegionTapUsesRadius() {
        assertNull(WorldMapGeometry.hitCountry(polygons, Offset(410f, 300f)))
        val hit = WorldMapGeometry.hitRegion(Offset(581f, 253f))
        assertNotNull(hit)
        assertEquals("Yirgacheffe", hit.region.name)
        assertEquals("Ethiopia", hit.country.en)
        assertNull(WorldMapGeometry.hitRegion(Offset(0f, 0f)))
        assertNull(WorldMapGeometry.hitRegion(Offset(581f, 253f + 6.5f)))
    }

    @Test fun canvasMappingRoundTripsAndPanIsClampedAtFit() {
        val m = WorldMapGeometry.fitScale(720f, 450f)
        assertEquals(720f / WorldMapData.VIEW_W, m)
        val origin = Offset(360f, 225f)
        val p = Offset(354.7f, 316.7f)
        val back = WorldMapGeometry.toView(WorldMapGeometry.toCanvas(p, m, origin), m, origin)
        assertTrue(kotlin.math.abs(back.x - p.x) < 0.01f && kotlin.math.abs(back.y - p.y) < 0.01f)
        assertEquals(Offset.Zero, WorldMapGeometry.clampPan(Offset(50f, -80f), m, 720f, 450f))
        val zoomed = WorldMapGeometry.clampPan(Offset(10_000f, 0f), m * 3f, 720f, 450f)
        assertTrue(kotlin.math.abs(zoomed.x - (WorldMapData.VIEW_W * m * 3f / 2f - 360f)) < 0.01f)
    }
}
