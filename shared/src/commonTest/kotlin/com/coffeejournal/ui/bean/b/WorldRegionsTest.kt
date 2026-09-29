package com.coffeejournal.ui.bean.b

import androidx.compose.ui.geometry.Offset
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.reference.OriginRegions
import com.coffeejournal.domain.rules.CountryLookup
import androidx.compose.ui.geometry.Size
import com.coffeejournal.ui.map.WorldMapInsets
import com.coffeejournal.ui.map.WorldProjection
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The world map's region dots: the web's at the fitted map, the bean form's representative regions once zoomed in. */
class WorldRegionsTest {
    private val polygons = WorldMapGeometry.parseCoffeeMap()

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

    @Test fun everyRegion_liesInItsCountry() {
        // Every listed region's point, not only the dots it adds: inside its country's outline, or within 1.5° of it,
        // since the map's simplified coastlines leave coastal and island regions a little off the land. However far
        // apart a country's regions are (Rondônia and Minas Gerais, Papua and Sumatra), a swapped or mistyped
        // coordinate lands in another country or the sea.
        // islands the simplified map leaves out altogether: their dot is drawn in the sea where they are
        val islands = mapOf("Ecuador|Galápagos" to (-1.5..0.7 to -92.0..-89.0))
        islands.forEach { (key, box) ->
            val (country, name) = key.split('|')
            val r = OriginRegions.all.single { it.countryEn == country }.regions.single { it.en == name }
            assertTrue(r.lat!! in box.first && r.lng!! in box.second, "$key at (${r.lat}, ${r.lng})")
        }
        val off = OriginRegions.all.flatMap { o -> o.regions.map { o.countryEn to it } }.filter { (c, r) -> "$c|${r.en}" !in islands }.mapNotNull { (country, r) ->
            val poly = polygons.firstOrNull { it.name == country } ?: return@mapNotNull "$country has no outline"
            val v = WorldMapInsets.toView(r.lat!!, r.lng!!)
            val p = Offset(v.x.toFloat(), v.y.toFloat())
            if (poly.contains(p)) null else distanceToBorder(poly, p).takeIf { it > 4f }?.let { "$country · ${r.en} (${r.lat}, ${r.lng}) ${it}u off" }
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

    @Test fun hawaii_isAnInsetInThePacific_withKonaOnTheFittedMap() {
        val hawaii = CoffeeCountries.byEn.getValue(WorldMapInsets.HAWAII)
        assertEquals("하와이", hawaii.ko)
        val kona = WorldRegions.webDots.single { it.country == hawaii }
        assertEquals("Kona", kona.region.name)
        assertEquals("코나", WorldRegions.label(kona))
        // the web's viewBox starts at 128°W: Hawaii itself is off it, so it is drawn in the frame, west of Mexico
        assertTrue(WorldProjection.toView(19.53, -155.92).x < 138.0)
        val frame = WorldMapInsets.frame
        val p = WorldMapInsets.toView(19.53, -155.92)
        assertTrue(frame.contains(Offset(p.x.toFloat(), p.y.toFloat())), "$p")
        assertEquals(kona.region.x, p.x.toFloat(), 0.1f)
        assertEquals(kona.region.y, p.y.toFloat(), 0.1f)
        // no other country reaches into the frame, and the tropic of Cancer passes above it, as it does above Hawaii
        polygons.filter { it.name != WorldMapInsets.HAWAII }.forEach { poly ->
            assertTrue(poly.rings.none { ring -> ring.any { frame.contains(it) } }, "${poly.name} is in the inset")
        }
        assertTrue(frame.top > com.coffeejournal.domain.reference.WorldMapData.TROPIC_NORTH_Y)
        // a tap on the Big Island opens Hawaii; the rest of the map is as before (Hawaii's islands are not elsewhere)
        val bigIsland = WorldMapInsets.toView(19.6, -155.5)
        assertEquals(MapTap.Country("Hawaii"), WorldMapGeometry.resolveTap(polygons, Offset(bigIsland.x.toFloat(), bigIsland.y.toFloat()), 1f, 1f))
        assertEquals(polygons.size - 1, WorldMapGeometry.parseAll().size)
    }

    @Test fun hawaiiAndKona_areFoundByTheirUsualNames() {
        listOf("하와이", "하와이 코나", "Hawaii", "미국", "USA", "United States", "미국(하와이)").forEach {
            assertEquals("Hawaii", CountryLookup.lookup(it)?.en, it)
        }
        assertEquals("Puerto Rico", CountryLookup.lookup("푸에르토리코")?.en)
        assertEquals("Kona", com.coffeejournal.domain.rules.RegionHierarchy.normalize("코나"))
        assertEquals("Ka'u", com.coffeejournal.domain.rules.RegionHierarchy.normalize("카우"))
    }

    @Test fun zoomedIn_dotsAreNamedInKorean() {
        val ethiopia = WorldRegions.webDots.first { it.region.name == "Yirgacheffe" }
        assertEquals("예가체프", WorldRegions.label(ethiopia))
        WorldRegions.dots.forEach { d -> assertTrue(WorldRegions.label(d).any { it in '\uAC00'..'\uD7A3' }, "${d.region.name} has no Korean name") }
    }

    @Test fun names_goRightOfTheirDot_elseLeftAboveOrBelow_neverOnAnotherNameOrDot() {
        val measure = { t: String -> Size(t.length * 10f, 12f) }
        val canvas = Size(300f, 200f)
        fun place(vararg dots: Pair<Offset, String>) = WorldMapGeometry.placeLabels(dots.toList(), measure, dotRadius = 4f, gap = 3f, canvas = canvas)
        // alone: right of the dot, centred on it
        assertEquals(Offset(27f, 94f), place(Offset(20f, 100f) to "가나").single().topLeft)
        // at the canvas's right edge: left of it
        assertEquals(Offset(263f, 44f), place(Offset(290f, 50f) to "코나").single().topLeft)
        // clear of the map's own words: a tropic's name to the right and above, the canvas edge to the left → below
        val tropic = androidx.compose.ui.geometry.Rect(25f, 90f, 120f, 102f)
        val below = WorldMapGeometry.placeLabels(listOf(Offset(20f, 100f) to "가나"), measure, 4f, 3f, canvas, obstacles = listOf(tropic))
        assertEquals(Offset(10f, 107f), below.single().topLeft)
        // dots right and left of it: above
        val row = place(Offset(100f, 100f) to "다라마바", Offset(60f, 100f) to "가", Offset(140f, 100f) to "나")
        assertEquals(Offset(80f, 81f), row.first { it.text == "다라마바" }.topLeft)
        // dots on all four sides: no room, left out (the neighbours still get theirs)
        val boxed = place(
            Offset(100f, 100f) to "다라마바", Offset(60f, 100f) to "가", Offset(140f, 100f) to "나",
            Offset(100f, 80f) to "다", Offset(100f, 120f) to "라",
        )
        assertTrue(boxed.none { it.text == "다라마바" } && boxed.isNotEmpty(), "$boxed")
        // however many, no two placed names overlap, none covers a dot, all inside the canvas
        val many = (0 until 40).map { i -> Offset(10f + (i % 8) * 36f, 10f + (i / 8) * 40f) to "이름$i" }
        val boxes = WorldMapGeometry.placeLabels(many, measure, 4f, 3f, canvas).map { androidx.compose.ui.geometry.Rect(it.topLeft, it.size) }
        assertTrue(boxes.size > 10, "${boxes.size} names")
        boxes.forEachIndexed { k, a -> boxes.drop(k + 1).forEach { b -> assertTrue(!a.overlaps(b), "$a overlaps $b") } }
        boxes.forEach { b ->
            assertTrue(b.left >= 0f && b.top >= 0f && b.right <= 300f && b.bottom <= 200f, "$b")
            many.forEach { (c, _) -> assertTrue(!b.overlaps(androidx.compose.ui.geometry.Rect(c.x - 4f, c.y - 4f, c.x + 4f, c.y + 4f)), "$b covers $c") }
        }
    }

    @Test fun aRecordsRegion_countsForItsDot_underEveryNameOfTheRegion() {
        // the map marks a dot tasted by the record's region as RegionHierarchy.normalize names it, so every spelling of
        // a listed region must come out as that region's dot name: the web's ("수마트라" → "Sumatra (Mandheling)") or
        // the added dot's English name. A name two countries share goes to the first, so it is left out here.
        val owners = OriginRegions.all.flatMap { o -> o.regions.flatMap { p -> (listOf(p.ko, p.en) + p.aliases).map { it.lowercase() to o.countryEn } } }
            .groupBy({ it.first }, { it.second }).filterValues { it.toSet().size == 1 }.keys
        val wrong = OriginRegions.all.flatMap { o ->
            val c = CoffeeCountries.byEn.getValue(o.countryEn)
            val dotNames = WorldRegions.of(c).map { it.name }
            o.regions.flatMap { p ->
                val names = listOf(p.ko, p.en) + p.aliases
                val dot = dotNames.firstOrNull { d -> names.any { it.equals(d, ignoreCase = true) } }
                names.filter { it.lowercase() in owners && dot != null && !com.coffeejournal.domain.rules.RegionHierarchy.normalize(it).equals(dot, ignoreCase = true) }
                    .map { "${o.countryEn}: $it → ${com.coffeejournal.domain.rules.RegionHierarchy.normalize(it)}, dot $dot" }
            }
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
        assertEquals("Sumatra (Mandheling)", com.coffeejournal.domain.rules.RegionHierarchy.normalize("수마트라"))
    }
}
