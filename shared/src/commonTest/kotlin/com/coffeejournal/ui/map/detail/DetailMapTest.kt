package com.coffeejournal.ui.map.detail

import androidx.compose.ui.geometry.Size
import com.coffeejournal.domain.model.CafePlace
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.KoreaRegions
import com.coffeejournal.ui.about.Credits
import com.coffeejournal.ui.about.LicenseTexts
import com.coffeejournal.ui.bean.b.FlatItemLogic
import com.coffeejournal.ui.map.CafeMapLogic
import com.coffeejournal.ui.map.KoreaMapState
import com.coffeejournal.ui.map.MapViewportMath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The detail map's style (MapLibre style spec v8 over OpenFreeMap's OpenMapTiles source). */
class DetailMapStyleTest {
    private val style: JsonObject = Json.parseToJsonElement(DetailMapStyle.json).jsonObject
    private val layers: List<JsonObject> = style["layers"]!!.jsonArray.map { it.jsonObject }
    private fun layer(id: String): JsonObject = layers.single { it.str("id") == id }
    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content
    private fun ids(): List<String> = layers.map { it.str("id")!! }

    @Test fun isStyleSpecV8_withOnlyTheOpenFreeMapSource_andItsGlyphs() {
        assertEquals(8, style["version"]!!.jsonPrimitive.int)
        val sources = style["sources"]!!.jsonObject
        assertEquals(setOf(DetailMapStyle.SOURCE), sources.keys)
        val source = sources[DetailMapStyle.SOURCE]!!.jsonObject
        assertEquals("vector", source.str("type"))
        assertEquals("https://tiles.openfreemap.org/planet", source.str("url"))
        assertEquals("https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf", style.str("glyphs"))
        // no icons, so no sprite sheet to download
        assertNull(style["sprite"])
        // nothing from tile.openstreetmap.org (its policy forbids apps like this), and every URL is OpenFreeMap over https
        assertFalse("openstreetmap.org/{" in DetailMapStyle.json || "tile.openstreetmap.org" in DetailMapStyle.json)
        Regex("https?://[^\"<> ]+").findAll(DetailMapStyle.json).map { it.value }.filter { "{" in it || it.endsWith("planet") }.forEach {
            assertTrue(it.startsWith("https://${OpenFreeMap.HOST}/"), it)
        }
    }

    @Test fun attribution_namesOpenMapTilesAndOpenStreetMap() {
        assertEquals("© OpenMapTiles © OpenStreetMap contributors", OpenFreeMap.ATTRIBUTION)
        val html = style["sources"]!!.jsonObject[DetailMapStyle.SOURCE]!!.jsonObject.str("attribution")!!
        assertTrue("OpenMapTiles" in html && "OpenStreetMap contributors" in html && "https://www.openstreetmap.org/copyright" in html, html)
    }

    @Test fun everyLayerReadsTheOpenMapTilesSchema_andIdsAreUnique() {
        assertEquals(ids().size, ids().toSet().size, "duplicate layer ids")
        assertEquals("background", layers.first().str("type"))
        layers.drop(1).forEach { l ->
            assertEquals(DetailMapStyle.SOURCE, l.str("source"), l.str("id"))
            assertTrue(l.str("source-layer") in OpenFreeMap.sourceLayers, "${l.str("id")}: ${l.str("source-layer")}")
            assertTrue(l.str("type") in setOf("fill", "line", "symbol"), "${l.str("id")}: ${l.str("type")}")
        }
    }

    @Test fun archivePalette_ivoryLand_blueGreyWater_greyBuildingsFromZ14_faintParks() {
        assertEquals("#F5F4EF", layer("background")["paint"]!!.jsonObject.str("background-color"))
        assertEquals(DetailMapPalette.WATER, layer("water")["paint"]!!.jsonObject.str("fill-color"))
        val building = layer("building")
        assertTrue(building["minzoom"]!!.jsonPrimitive.int >= 14)
        assertEquals(DetailMapPalette.BUILDING, building["paint"]!!.jsonObject.str("fill-color"))
        assertTrue(layer("park")["paint"]!!.jsonObject["fill-opacity"]!!.jsonPrimitive.double < 1.0)
        // water and parks under the roads, buildings under the labels
        assertTrue(ids().indexOf("water") < ids().indexOf("road-minor-casing"))
        assertTrue(ids().indexOf("building") < ids().indexOf("place-city"))
    }

    @Test fun roads_whiteOverInkCasings_strongerForBiggerRoads() {
        val classes = listOf("minor", "secondary", "primary", "motorway")
        var lastOpacity = 0.0
        classes.forEach { c ->
            val casing = layer("road-$c-casing")
            val fill = layer("road-$c")
            assertEquals(DetailMapPalette.INK, casing["paint"]!!.jsonObject.str("line-color"), c)
            assertEquals("#FFFFFF", fill["paint"]!!.jsonObject.str("line-color"), c)
            assertTrue(ids().indexOf("road-$c-casing") < ids().indexOf("road-minor"), "$c: casings are drawn before any fill")
            val opacity = casing["paint"]!!.jsonObject["line-opacity"]!!.jsonPrimitive.double
            assertTrue(opacity > lastOpacity, "$c casing opacity $opacity")
            lastOpacity = opacity
        }
    }

    @Test fun labels_areKoreanFirst_inInk_withTheOpenFreeMapFont() {
        val symbols = layers.filter { it.str("type") == "symbol" }
        assertTrue(symbols.size >= 8)
        val koreanName = JsonArray(listOf(Json.parseToJsonElement("\"coalesce\""), Json.parseToJsonElement("[\"get\",\"name:ko\"]"), Json.parseToJsonElement("[\"get\",\"name\"]")))
        symbols.forEach { l ->
            val layout = l["layout"]!!.jsonObject
            assertEquals(koreanName, layout["text-field"], l.str("id"))
            assertEquals(listOf(OpenFreeMap.FONT), layout["text-font"]!!.jsonArray.map { it.jsonPrimitive.content }, l.str("id"))
            assertNull(layout["icon-image"], l.str("id"))
        }
        assertEquals("#191916", layer("place-city")["paint"]!!.jsonObject.str("text-color"))
        assertEquals(setOf("water_name", "waterway", "transportation_name", "poi", "place"), symbols.mapNotNull { it.str("source-layer") }.toSet())
    }
}

/** Camera fitting, route arguments and the SGIS map's visible area. */
class DetailMapCameraTest {
    private fun near(expected: Double, actual: Double, tol: Double, what: String) =
        assertTrue(abs(expected - actual) <= tol, "$what: expected $expected ± $tol, got $actual")

    @Test fun mercator_roundTrips() {
        listOf(-80.0, -33.9, 0.0, 37.5665, 60.0).forEach { lat ->
            near(lat, DetailMapCamera.latOf(DetailMapCamera.mercatorY(lat)), 1e-9, "lat $lat")
        }
        near(0.5, DetailMapCamera.mercatorY(0.0), 1e-12, "equator")
        near(0.5, DetailMapCamera.mercatorX(0.0), 1e-12, "Greenwich")
    }

    @Test fun fit_centresTheBox_atTheLargestZoomThatShowsAllOfIt() {
        // Seoul's 25 구: 37.4285–37.7015°N, 126.7642–127.1838°E, in a 400 × 600 dp map with 24 dp padding
        val seoul = GeoBounds(37.4285, 126.7642, 37.7015, 127.1838)
        val cam = DetailMapCamera.fit(seoul, 400.0, 600.0)
        near((126.7642 + 127.1838) / 2, cam.lng, 1e-9, "centre lng")
        near(37.5651, cam.lat, 0.001, "centre lat (Mercator middle)")
        val world = DetailMapCamera.TILE_DP * 2.0.pow(cam.zoom)
        val w = (DetailMapCamera.mercatorX(seoul.east) - DetailMapCamera.mercatorX(seoul.west)) * world
        val h = (DetailMapCamera.mercatorY(seoul.south) - DetailMapCamera.mercatorY(seoul.north)) * world
        assertTrue(w <= 352.0 + 1e-6 && h <= 552.0 + 1e-6, "fits: $w × $h")
        assertTrue(abs(w - 352.0) < 1e-6 || abs(h - 552.0) < 1e-6, "touches one side: $w × $h")
        // 0.42° of longitude in 352 dp: 512·2^z · 0.42/360 = 352 → z ≈ 9.2
        near(9.2, cam.zoom, 0.05, "zoom")
    }

    @Test fun fit_ofAPoint_stopsAtTheFitLimit_andAcrossTheAntimeridianStaysFinite() {
        val p = DetailMapCamera.fit(GeoBounds(37.5, 127.0, 37.5, 127.0), 400.0, 600.0)
        assertEquals(DetailMapCamera.FIT_MAX_ZOOM, p.zoom)
        val fiji = DetailMapCamera.fit(GeoBounds(-19.0, 177.0, -16.0, -179.0), 400.0, 600.0)
        assertTrue(fiji.zoom.isFinite() && fiji.zoom > 5, "zoom ${fiji.zoom}")
        assertTrue(fiji.lng > 177.0 || fiji.lng < -179.0, "centre lng ${fiji.lng}")
    }

    @Test fun start_prefersTheCamera_thenTheBounds_thenKoreaOrTheWorld() {
        val cam = DetailCamera(35.1, 129.0, 15.0)
        val box = GeoBounds(37.4, 126.8, 37.7, 127.2)
        assertEquals(cam, DetailMapCamera.start(cam, box, overseas = false, widthDp = 400.0, heightDp = 500.0))
        assertEquals(DetailMapCamera.fit(box, 400.0, 500.0), DetailMapCamera.start(null, box, false, 400.0, 500.0))
        val korea = DetailMapCamera.start(null, null, overseas = false, widthDp = 400.0, heightDp = 500.0)
        assertTrue(KoreaProjection.inKoreaBox(GeoPoint(korea.lat, korea.lng)), "$korea")
        assertTrue(korea.zoom in 5.0..8.0, "$korea")
        assertEquals(DetailMapCamera.world, DetailMapCamera.start(null, null, overseas = true, widthDp = 400.0, heightDp = 500.0))
    }

    @Test fun areaBounds_comeFromTheSgisOutlines() {
        val seoul = DetailMapCamera.provinceBounds("11")!!
        assertTrue(seoul.south in 37.40..37.45 && seoul.north in 37.68..37.72, "$seoul")
        assertTrue(seoul.west in 126.70..126.80 && seoul.east in 127.15..127.22, "$seoul")
        val cityHall = KoreaRegions.locate(37.5663, 126.9779)!!
        val jung = DetailMapCamera.districtBounds(cityHall.provinceCode, cityHall.districtCode!!)!!
        assertTrue(37.5663 in jung.south..jung.north && 126.9779 in jung.west..jung.east, "$jung")
        assertNull(DetailMapCamera.provinceBounds("99"))
    }

    @Test fun visibleBoundsOfTheSgisMap_areWhatItShows() {
        val state = KoreaMapState("11")
        assertNull(state.visibleBounds(), "not laid out yet")
        state.viewport.canvasSize = Size(1000f, 1000f)
        val fitted = state.visibleBounds()!!
        assertTrue(37.5663 in fitted.south..fitted.north && 126.9779 in fitted.west..fitted.east, "$fitted")
        // zoomed in around the centre: a smaller box around the same middle
        state.viewport.transform(androidx.compose.ui.geometry.Offset(500f, 500f), androidx.compose.ui.geometry.Offset.Zero, 4f)
        val zoomed = state.visibleBounds()!!
        near((fitted.north - fitted.south) / 4, zoomed.north - zoomed.south, 1e-6, "height")
        near((fitted.south + fitted.north) / 2, (zoomed.south + zoomed.north) / 2, 1e-6, "middle")
    }

    @Test fun routeArguments_roundTrip_andRejectDamage() {
        val cam = DetailCamera(37.5446, 127.0557, 16.0)
        assertEquals(cam, DetailCamera.decode(cam.encode()))
        assertNull(DetailCamera.decode("abc"))
        assertNull(DetailCamera.decode("91,0,5"))
        assertNull(DetailCamera.decode("37.5,127.0,NaN"))
        assertEquals(DetailMapCamera.MAX_ZOOM, DetailCamera.decode("37.5,127.0,40")!!.zoom)
        val box = GeoBounds(37.4, 126.8, 37.7, 127.2)
        assertEquals(box, GeoBounds.decode(box.encode()))
        assertNull(GeoBounds.decode("37.7,126.8,37.4,127.2"), "south above north")
        assertNull(GeoBounds.decode("1,2,3"))
    }

    @Test fun zoomDescription_forTalkBack() {
        assertEquals("기본 배율", MapViewportMath.zoomDescription(1f))
        assertEquals("기본 배율", MapViewportMath.zoomDescription(1.02f))
        assertEquals("확대 2.5배", MapViewportMath.zoomDescription(2.5f))
        assertEquals("축소 0.6배", MapViewportMath.zoomDescription(0.62f))
    }
}

/** The detail map's pins come from the same data (and keys) as the SGIS map's. */
class DetailMapPinsTest {
    private fun roastery(id: String, name: String, location: String, lat: Double? = null, lng: Double? = null, scope: String = Scope.DOMESTIC) =
        MiscItem(id = id, type = MiscType.SOURCE, name = name, scope = scope, location = location, createdAt = id.hashCode().toLong(), lat = lat, lng = lng)

    @Test fun roasteries_exactWhereSet_elseTheAreaCentre_withTheirPrecision() {
        val items = listOf(
            roastery("r1", "모모스", "부산 금정구", 35.2270, 129.0880),
            roastery("r2", "커피 리브레", "서울 성동구"),
            roastery("r3", "어느 로스터리", "부산"),
        )
        val model = FlatItemLogic.roasteryMap(items, emptyList(), Scope.DOMESTIC)
        val pins = DetailMapPins.roasteries(model.pins, domestic = true).associateBy { it.key }
        assertEquals(setOf("모모스", "커피 리브레", "어느 로스터리"), pins.keys)
        assertEquals(PinPrecision.EXACT, pins.getValue("모모스").precision)
        assertEquals(GeoPoint(35.2270, 129.0880), pins.getValue("모모스").point)
        val librae = pins.getValue("커피 리브레")
        assertEquals(PinPrecision.DISTRICT, librae.precision)
        assertEquals("서울특별시 성동구", KoreaRegions.locate(librae.point.lat, librae.point.lng, 1.0)?.label)
        assertEquals(PinPrecision.PROVINCE, pins.getValue("어느 로스터리").precision)
        assertEquals("부산광역시", KoreaRegions.locate(pins.getValue("어느 로스터리").point.lat, pins.getValue("어느 로스터리").point.lng, 3.0)?.provinceName)
        // the camera zooms in closer on a precise pin
        assertTrue(pins.getValue("모모스").camera.zoom > librae.camera.zoom)
        assertTrue(librae.camera.zoom > pins.getValue("어느 로스터리").camera.zoom)
    }

    @Test fun overseasRoastery_withoutAPosition_isTheCountryCentre() {
        val model = FlatItemLogic.roasteryMap(listOf(roastery("o1", "Coava", "Portland, USA", scope = Scope.OVERSEAS)), emptyList(), Scope.OVERSEAS)
        val pin = DetailMapPins.roasteries(model.pins, domestic = false).single()
        assertEquals(PinPrecision.COUNTRY, pin.precision)
        assertTrue(pin.point.lng < -60 && pin.point.lat in 25.0..50.0, "${pin.point}")
    }

    @Test fun cafes_onlyThoseWithAPosition_withTheirVisits() {
        fun visit(id: String, at: Long, cafe: String) = Entry(id = id, createdAt = at, category = Category.CAFE, name = "라떼", cafeName = cafe)
        val spots = CafeMapLogic.spots(
            listOf(visit("v1", 2, "FELT 청계천"), visit("v2", 1, "felt 청계천 "), visit("v3", 3, "어딘가")),
            listOf(CafePlace("FELT 청계천", 37.5663, 126.9910, 1)),
        )
        val pins = DetailMapPins.cafes(spots)
        assertEquals(1, pins.size)
        assertEquals(CafePlace.key("FELT 청계천"), pins[0].key)
        assertEquals(2, pins[0].count)
        assertEquals("FELT 청계천 2", DetailMapPins.labelOf(pins[0]))
    }

    @Test fun featureCollection_isGeoJson_lngLat_withTheSelectedPinLast() {
        val a = DetailPin("a", "모모스", GeoPoint(35.2270, 129.0880), PinPrecision.EXACT, 3)
        val b = DetailPin("b", "리브레", GeoPoint(37.5446, 127.0557), PinPrecision.DISTRICT)
        val fc = Json.parseToJsonElement(DetailMapPins.featureCollection(listOf(a, b), selectedKey = "a")).jsonObject
        assertEquals("FeatureCollection", fc["type"]!!.jsonPrimitive.content)
        val features = fc["features"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("b", "a"), features.map { it["properties"]!!.jsonObject["key"]!!.jsonPrimitive.content })
        val geom = features[1]["geometry"]!!.jsonObject
        assertEquals("Point", geom["type"]!!.jsonPrimitive.content)
        assertEquals(listOf(129.0880, 35.2270), geom["coordinates"]!!.jsonArray.map { it.jsonPrimitive.double })
        val pa = features[1]["properties"]!!.jsonObject
        assertEquals("모모스 3", pa["label"]!!.jsonPrimitive.content)
        assertTrue(pa["selected"]!!.jsonPrimitive.boolean)
        assertEquals(DetailMapPalette.INK, pa["fill"]!!.jsonPrimitive.content)
        val pb = features[0]["properties"]!!.jsonObject
        assertFalse(pb["exact"]!!.jsonPrimitive.boolean)
        assertEquals("#FFFFFF", pb["fill"]!!.jsonPrimitive.content, "an area centre is a ring")
        assertTrue(pa["radius"]!!.jsonPrimitive.int > pb["radius"]!!.jsonPrimitive.int)
        assertEquals("[]", Json.parseToJsonElement(DetailMapPins.featureCollection(emptyList(), null)).jsonObject["features"].toString())
    }

    @Test fun routes_forAPin_anArea_andThePicker() {
        val pin = DetailPin("모모스", "모모스", GeoPoint(35.2270, 129.0880), PinPrecision.EXACT)
        val r = DetailMapRoutes.pin(DetailMapLayer.ROASTERY, Scope.DOMESTIC, pin)
        assertEquals(DetailMapMode.VIEW, r.mode)
        assertEquals("모모스", r.focus)
        assertEquals(DetailCamera(35.2270, 129.0880, PinPrecision.EXACT.zoom), DetailCamera.decode(r.camera))
        val box = GeoBounds(37.4, 126.8, 37.7, 127.2)
        val area = DetailMapRoutes.area(DetailMapLayer.CAFE, Scope.DOMESTIC, box)
        assertEquals(box, GeoBounds.decode(area.bounds))
        assertNull(area.camera)
        val pick = DetailMapRoutes.pick(Scope.DOMESTIC, "커피 리브레", GeoPoint(37.5446, 127.0557), box)
        assertEquals(DetailMapMode.PICK, pick.mode)
        assertEquals(16.0, DetailCamera.decode(pick.camera)!!.zoom)
        assertEquals("커피 리브레", pick.name)
        assertNull(DetailMapRoutes.pick(Scope.DOMESTIC, "x", null, null).camera)
    }
}

/** The screen's view of the renderer: loading, slow, offline, failure and retry. */
class DetailMapControllerTest {
    private val start = DetailCamera(37.5, 127.0, 12.0)

    @Test fun loads_orSlowsDown_orFails() {
        val c = DetailMapController(start)
        assertEquals(DetailMapStatus.Loading, c.status)
        assertTrue(c.status.showsMap)
        c.reportSlow()
        assertEquals(DetailMapStatus.Slow, c.status)
        c.reportLoaded()
        assertEquals(DetailMapStatus.Ready, c.status)
        c.reportSlow()
        assertEquals(DetailMapStatus.Ready, c.status, "a loaded map is not slow")
        c.reportFailure("style")
        assertEquals(DetailMapStatus.Failed("style"), c.status)
        assertFalse(c.status.showsMap)
        c.reportLoaded()
        assertEquals(DetailMapStatus.Failed("style"), c.status, "a failure stays until retried")
    }

    @Test fun retry_startsAgain_orStaysOfflineWithoutNetwork() {
        val c = DetailMapController(start)
        c.reportOffline()
        assertFalse(c.status.showsMap)
        c.retry(online = false)
        assertEquals(DetailMapStatus.Offline, c.status)
        assertEquals(1, c.attempt)
        c.moveTo(DetailCamera(35.0, 129.0, 15.0))
        c.retry(online = true)
        assertEquals(DetailMapStatus.Loading, c.status)
        assertEquals(2, c.attempt)
        assertNull(c.request, "a new map starts from the last camera, not an old move")
    }

    @Test fun camera_movesAreReported_andSettleWhenTheyStop() {
        val c = DetailMapController(start)
        assertEquals(start, c.settled)
        val moving = DetailCamera(37.51, 127.01, 13.0)
        c.reportCamera(moving, moving = true)
        assertEquals(moving, c.camera)
        assertEquals(start, c.settled)
        c.reportCamera(moving, moving = false)
        assertEquals(moving, c.settled)
        c.moveTo(start)
        c.moveTo(start)
        assertEquals(2, c.request!!.id, "the same target twice is two moves")
    }

    @Test fun unavailableRenderer_hidesTheButtons() {
        assertFalse(UnavailableDetailMapRenderer.isSupported)
    }
}

class DetailMapCreditTest {
    @Test fun creditNamesTheSources_andCarriesMapLibresNotices() {
        val credit = Credits.all.single { it.title.startsWith("상세 지도") }
        listOf("OpenStreetMap", "ODbL 1.0", "OpenMapTiles", "OpenFreeMap", "MapLibre", "tiles.openfreemap.org").forEach {
            assertTrue(it in credit.body, "body mentions $it")
        }
        val urls = credit.links.map { it.second }
        listOf("https://www.openstreetmap.org/copyright", "https://openmaptiles.org/", "https://openfreemap.org/", "https://maplibre.org/").forEach {
            assertTrue(it in urls, "links $it")
        }
        val text = assertNotNull(credit.licenseText)
        // MapLibre Native's BSD 2-Clause license, the bundled C++ code's notices, and MapLibre Compose's BSD 3-Clause
        listOf("BSD 2-Clause License", "Copyright (c) 2021 MapLibre contributors", "kdbush.hpp", "RapidJSON", "Boost Software License",
            "Copyright (c) 2024, MapLibre Compose contributors").forEach { assertTrue(it in text, "notice has $it") }
        assertEquals("BSD 2-Clause License", LicenseTexts.title("BSD-2-Clause"))
        assertEquals("https://opensource.org/license/bsd-2-clause", LicenseTexts.url("BSD-2-Clause"))
    }
}
