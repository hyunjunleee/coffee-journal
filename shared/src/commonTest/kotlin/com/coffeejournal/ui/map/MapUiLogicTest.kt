package com.coffeejournal.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.coffeejournal.domain.model.CafePlace
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.reference.WorldMapData
import com.coffeejournal.ui.bean.b.WorldMapGeometry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The world map's lon/lat → viewBox mapping, derived from the web map (plate carrée, 8/3 units per degree, equator
 * midway between the tropics) and checked against Natural Earth 1:110m extremes and known cities.
 */
class WorldProjectionTest {
    private val polygons = WorldMapGeometry.parseAll()
    private fun bounds(name: String) = polygons.single { it.name == name }.bounds

    private fun assertNear(expected: Float, actual: Double, what: String) =
        assertTrue(abs(expected - actual) < 0.35, "$what: expected $expected, got $actual")

    @Test fun mapping_matchesTheOutlinesExtremes() {
        assertEquals((WorldMapData.TROPIC_NORTH_Y + WorldMapData.TROPIC_SOUTH_Y) / 2.0, WorldProjection.EQUATOR_Y, 1e-4)
        // the web's tropic lines sit at ±23.5° with the same scale
        assertEquals(WorldMapData.TROPIC_NORTH_Y.toDouble(), WorldProjection.toView(23.5, 0.0).y, 0.05)
        // Natural Earth 1:110m extents: South Korea 126.117–129.468°E, 34.390–38.612°N
        val kr = bounds("South Korea")
        assertNear(kr.left, WorldProjection.toView(0.0, 126.117).x, "Korea west")
        assertNear(kr.right, WorldProjection.toView(0.0, 129.468).x, "Korea east")
        assertNear(kr.top, WorldProjection.toView(38.612, 0.0).y, "Korea north")
        assertNear(kr.bottom, WorldProjection.toView(34.390, 0.0).y, "Korea south")
        // Australia 113.339–153.569°E, 10.668–43.635°S (Tasmania); Iceland 24.326–13.609°W, 63.496–66.526°N
        val au = bounds("Australia")
        assertNear(au.left, WorldProjection.toView(0.0, 113.339).x, "Australia west")
        assertNear(au.right, WorldProjection.toView(0.0, 153.569).x, "Australia east")
        assertNear(au.top, WorldProjection.toView(-10.668, 0.0).y, "Australia north")
        assertNear(au.bottom, WorldProjection.toView(-43.635, 0.0).y, "Australia south")
        val iceland = bounds("Iceland")
        assertNear(iceland.left, WorldProjection.toView(0.0, -24.326).x, "Iceland west")
        assertNear(iceland.top, WorldProjection.toView(66.526, 0.0).y, "Iceland north")
        // round trip
        val g = WorldProjection.toView(37.5663, 126.9779).let { WorldProjection.toGeo(it.x, it.y) }
        assertEquals(37.5663, g.lat, 1e-9); assertEquals(126.9779, g.lng, 1e-9)
    }

    @Test fun cities_fallInTheirCountries() {
        fun countryAt(lat: Double, lng: Double) = WorldPlaces.countryAt(WorldProjection.toView(lat, lng))
        assertEquals("South Korea", countryAt(36.3504, 127.3845), "Daejeon")
        assertEquals("Japan", countryAt(35.6762, 139.6503), "Tokyo")
        assertEquals("Norway", countryAt(60.4720, 8.4689), "inland Norway")
        assertEquals("United States of America", countryAt(39.7392, -104.9903), "Denver")
        assertEquals("Australia", countryAt(-25.0, 134.0), "central Australia")
        assertEquals("Ethiopia", countryAt(9.0, 39.0), "central Ethiopia")
        assertNull(countryAt(0.0, -150.0), "Pacific")
    }

    @Test fun locationTexts_findACountryCentreInsideTheCountry() {
        val cases = mapOf(
            "교토, 일본" to "Japan", "Portland, OR" to "United States of America", "Oslo" to "Norway", "코펜하겐" to "Denmark",
            "Melbourne" to "Australia", "런던" to "United Kingdom", "Stockholm, Sweden" to "Sweden", "콜롬비아 우일라" to "Colombia",
            "Chile" to "Chile", "Netherlands" to "Netherlands",
        )
        cases.forEach { (text, country) ->
            val place = assertNotNull(WorldPlaces.match(text), text)
            assertEquals(country, place.mapName, text)
            assertEquals(country, WorldPlaces.countryAt(place.center), "$text: centre lies inside")
        }
        assertEquals("일본", WorldPlaces.match("Tokyo")?.name)
        assertEquals("싱가포르", WorldPlaces.match("Singapore")?.name)
        assertNull(WorldPlaces.match("어딘가"))
        assertEquals("일본", WorldPlaces.displayName("Japan"))
        assertEquals("에티오피아", WorldPlaces.displayName("Ethiopia"))
        assertEquals("스웨덴", WorldPlaces.displayName("Sweden"))
        assertEquals("Kazakhstan", WorldPlaces.displayName("Kazakhstan"), "no Korean name known: the map's English")
    }
}

class MapLayoutTest {
    @Test fun pinSpread_fansOutPinsAtOnePlace_withoutOverlap() {
        val anchors = List(6) { Offset(200f, 200f) }
        val sizes = List(6) { Size(90f, 24f) }
        val tls = PinSpread.place(anchors, sizes, 400f, 400f, gap = 6f)
        val rects = tls.mapIndexed { i, tl -> Rect(tl, sizes[i]) }
        for (i in rects.indices) for (j in rects.indices) if (i < j) assertTrue(!rects[i].overlaps(rects[j]), "chips $i and $j overlap")
        assertTrue(rects.all { it.left >= 0f && it.top >= 0f && it.right <= 400f && it.bottom <= 400f })
        // the first pin keeps the preferred spot, right of its point
        assertEquals(Offset(206f, 188f), tls[0])
        // a chip near the edge stays inside the canvas, on the side where it leaves its point visible
        val edge = PinSpread.place(listOf(Offset(395f, 5f)), listOf(Size(90f, 24f)), 400f, 400f, 6f).single()
        assertEquals(Offset(299f, 0f), edge)
        // a chip does not cover another pin's point when it can go elsewhere
        val two = PinSpread.place(listOf(Offset(100f, 100f), Offset(150f, 100f)), listOf(Size(90f, 24f), Size(90f, 24f)), 400f, 400f, 6f)
        assertTrue(!Rect(two[0], Size(90f, 24f)).inflate(3f).contains(Offset(150f, 100f)))
        assertEquals(Offset(4f, 88f), two[0], "left of its point, clear of the neighbour's")
        assertEquals(Offset(10f, 5f), PinSpread.nearestOnRect(Offset(0f, 5f), Rect(10f, 0f, 20f, 10f)))
    }

    @Test fun viewport_mapsBothWays_zoomsAroundTheFingers_andKeepsTheMapInReach() {
        val frame = Rect(0f, 0f, 1000f, 500f)
        val canvas = Size(500f, 500f)
        val m = MapViewportMath.fitScale(frame, canvas)
        assertEquals(0.5f, m)
        val origin = Offset(250f, 250f)
        val c = MapViewportMath.toCanvas(100f, 400f, frame, m, origin)
        assertEquals(Offset(50f, 325f), c)
        assertEquals(Offset(100f, 400f), MapViewportMath.toMap(c, frame, m, origin))
        // zooming ×2 around a point keeps that point under the fingers
        val vp = MapViewport(frame)
        vp.canvasSize = canvas
        val under = vp.toMap(Offset(100f, 300f))
        vp.transform(Offset(100f, 300f), Offset.Zero, 2f)
        val after = vp.toMap(Offset(100f, 300f))
        assertEquals(under.x, after.x, 0.01); assertEquals(under.y, after.y, 0.01)
        // far panning stops with the canvas centre on the frame's edge
        vp.transform(Offset(250f, 250f), Offset(10_000f, 0f), 1f)
        assertEquals(frame.left.toDouble(), vp.toMap(Offset(250f, 250f)).x, 0.01)
        // bounds larger than the frame allow zooming out until they fit
        assertEquals(0.5f, MapViewportMath.minZoom(frame, Rect(-500f, 0f, 1500f, 500f), canvas))
        vp.reset()
        assertEquals(1f, vp.zoom)
    }
}

class KoreaPinClustersTest {
    @Test fun twoOrMorePinsOfAProvince_becomeOneChip_singlesStay() {
        val pins = listOf(
            MapPin("a", "A", com.coffeejournal.domain.rules.KoreaProjection.toMap(37.54, 127.05)),
            MapPin("b", "B", com.coffeejournal.domain.rules.KoreaProjection.toMap(37.57, 126.98)),
            MapPin("c", "C", com.coffeejournal.domain.rules.KoreaProjection.toMap(35.22, 129.08)),
        )
        val provinces = mapOf("a" to "11", "b" to "11", "c" to "26")
        val shown = KoreaPinClusters.cluster(pins, provinces)
        assertEquals(listOf("서울 2곳", "C"), shown.map { it.label })
        assertEquals("11", KoreaPinClusters.provinceOf(shown[0].key))
        assertNull(KoreaPinClusters.provinceOf("c"))
        assertEquals("서울특별시에 2곳. 누르면 확대돼요.", shown[0].description)
    }
}

class CafeMapLogicTest {
    private fun cafe(id: String, name: String, at: Long) = Entry(id = id, createdAt = at, category = Category.CAFE, name = "라떼 $id", cafeName = name)

    @Test fun spots_groupVisitsByCafeName_andJoinThePlaces() {
        val entries = listOf(
            cafe("a", "FELT 청계천", 1), cafe("b", "felt 청계천 ", 3), cafe("c", "프릳츠", 2),
            Entry(id = "d", createdAt = 4, category = Category.BEAN, name = "원두", cafeName = "FELT 청계천"),
            cafe("e", " ", 5),
        )
        val places = listOf(CafePlace("FELT 청계천", 37.5663, 126.9910, 9), CafePlace("없어진 카페", 1.0, 1.0, 1))
        val spots = CafeMapLogic.spots(entries, places)
        assertEquals(listOf("felt 청계천" to 2, "프릳츠" to 1), spots.map { it.name to it.visits.size })
        assertEquals(listOf("b", "a"), spots[0].visits.map { it.id }, "newest visit first; its spelling names the café")
        assertEquals(GeoPoint(37.5663, 126.9910), spots[0].point)
        assertNull(spots[1].place)
        assertEquals("서울특별시 중구", CafeMapLogic.placeLabel(spots[0].point!!))
        assertEquals("37.5663, 126.9910", CafeMapLogic.coords(spots[0].point!!))
        assertEquals("방문 카페 지도. 카페 1곳 표시, 위치 미지정 1곳. 시·도를 누르면 시·군·구 지도로 확대돼요.", CafeMapLogic.mapDescription(spots))
    }

    @Test fun pickResult_roundTrips_andNamesTheArea() {
        val p = GeoPoint(37.5446, 127.0557)
        assertEquals(p, MapPickResult.decode(MapPickResult.encode(p)))
        assertNull(MapPickResult.decode(MapPickResult.encode(null)))
        assertNull(MapPickResult.decode("abc"))
        assertNull(MapPickResult.decode("91,10"))
        assertEquals("서울특별시 성동구", MapPickResult.placeName(p, overseas = false))
        assertEquals("일본", MapPickResult.placeName(GeoPoint(35.0116, 135.7681), overseas = true))
        assertNull(MapPickResult.placeName(GeoPoint(36.0, 125.5), overseas = false), "the Yellow Sea")
    }
}
