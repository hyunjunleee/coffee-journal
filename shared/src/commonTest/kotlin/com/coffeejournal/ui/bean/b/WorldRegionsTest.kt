package com.coffeejournal.ui.bean.b

import androidx.compose.ui.geometry.Offset
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.OriginRegions
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The world map's region dots: the web's at the fitted map, the bean form's representative regions once zoomed in. */
class WorldRegionsTest {
    private val polygons = WorldMapGeometry.parseAll()

    // as in MapFixesTest: a 411dp phone, 379dp canvas, dp = px
    private val fit = WorldMapGeometry.fitScale(379f, 379f / 1.6f)
    private fun dotRadius(scale: Float) = WorldMapGeometry.dotRadiusPx(fit * scale, 1f) / (fit * scale)
    private fun shown(scale: Float) = WorldMapGeometry.shownDots(scale, fit * scale, 1f)

    private fun distanceToBorder(poly: MapPolygon, p: Offset): Float = poly.rings.minOf { ring ->
        ring.indices.minOf { i ->
            val a = ring[i]; val b = ring[(i + 1) % ring.size]
            val dx = b.x - a.x; val dy = b.y - a.y
            val t = if (dx == 0f && dy == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
            hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
        }
    }

    @Test fun theFittedMap_showsTheWebsDotsOnly() {
        assertEquals(WorldRegions.webDots, shown(1f))
        assertEquals(CoffeeCountries.all.sumOf { it.regions.size }, WorldRegions.webDots.size)
        assertTrue(WorldRegions.dots.size > WorldRegions.webDots.size + 100, "${WorldRegions.dots.size} dots")
    }

    @Test fun everyRepresentativeRegion_isOnTheMap_orIsOneOfTheWebsDots() {
        OriginRegions.all.forEach { o ->
            val c = CoffeeCountries.byEn.getValue(o.countryEn)
            val names = WorldRegions.of(c).map { it.name.lowercase() }.toSet()
            o.regions.forEach { p ->
                assertTrue((listOf(p.en) + p.aliases).any { it.lowercase() in names }, "${o.countryEn} · ${p.en} has no dot")
            }
        }
        // the panel lists the web's regions first, then the rest
        val ethiopia = CoffeeCountries.byEn.getValue("Ethiopia")
        assertEquals(ethiopia.regions, WorldRegions.of(ethiopia).take(ethiopia.regions.size))
    }

    @Test fun anAddedDot_liesInItsCountry() {
        // the simplified coastlines leave coastal and island regions a little off the land
        val off = WorldRegions.dots.filterNot { it.web }.mapNotNull { d ->
            val poly = polygons.firstOrNull { it.name == d.country.en } ?: return@mapNotNull "${d.country.en} has no outline"
            val p = Offset(d.region.x, d.region.y)
            if (poly.contains(p)) null else distanceToBorder(poly, p).takeIf { it > 3f }?.let { "${d.country.en} · ${d.region.name} ${it}u off" }
        }
        assertTrue(off.isEmpty(), off.joinToString("\n"))
    }

    @Test fun zoomedIn_theAddedDotsAppear_neverOnTopOfAnother() {
        val counts = listOf(WorldRegions.DETAIL_SCALE, 5f, 12f, WorldMapGeometry.MAX_SCALE).map { scale ->
            val dots = shown(scale)
            val gap = 3f * dotRadius(scale)
            assertTrue(dots.containsAll(WorldRegions.webDots), "the web's dots stay")
            dots.filterNot { it.web }.forEach { d ->
                val near = dots.filter { it != d }.minOf { hypot(it.region.x - d.region.x, it.region.y - d.region.y) }
                assertTrue(near >= gap, "$scale: ${d.region.name} is $near from another dot")
            }
            dots.size
        }
        // deeper, more dots; at the deepest zoom nearly every region has its dot (Rwanda's too)
        assertEquals(counts.sorted(), counts)
        assertTrue(counts.first() > WorldRegions.webDots.size, "$counts")
        assertTrue(counts.last() >= WorldRegions.dots.size * 0.9, "$counts of ${WorldRegions.dots.size}")
        val rwanda = shown(WorldMapGeometry.MAX_SCALE).count { it.country.en == "Rwanda" }
        assertTrue(rwanda >= 8, "Rwanda: $rwanda dots")
        // the dot keeps its size on the screen: 2.5 dp on the fitted map, 4 dp at most
        assertEquals(2.5f, WorldMapGeometry.dotRadiusPx(fit, 1f))
        assertEquals(4f, WorldMapGeometry.dotRadiusPx(fit * WorldMapGeometry.MAX_SCALE, 1f))
    }

    @Test fun anAddedDot_isTappedOnlyWhereItIsDrawn() {
        val scale = 3f
        val dots = shown(scale)
        val added = dots.first { !it.web && it.country.en == "Ethiopia" }
        val p = Offset(added.region.x, added.region.y)
        val zoomed = WorldMapGeometry.resolveTap(polygons, p, dotRadius(scale), 14f / (fit * scale), dots)
        assertEquals(added.region.name, (zoomed as MapTap.Region).hit.region.name)
        // at the fitted map the dot is not drawn: the tap opens Ethiopia (or a web dot close by), never the hidden dot
        val fitted = WorldMapGeometry.resolveTap(polygons, p, dotRadius(1f), 14f / fit)
        assertTrue(fitted !is MapTap.Region || fitted.hit.region.name != added.region.name, "$fitted")
        // the hidden dot's region is still one tap away in the country's list
        assertTrue(added.region in WorldRegions.of(added.country))
    }
}
