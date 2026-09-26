package com.coffeejournal.domain

import com.coffeejournal.domain.reference.KoreaMapData
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.KoreaRegions
import com.coffeejournal.domain.rules.KoreaShapes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The embedded SGIS / admdongkor map: projection, point-in-polygon lookup and the location-text matcher. */
class KoreaGeoTest {
    @Test fun generatedData_hasEveryProvinceAndDistrict_withinTheJvmConstantLimit() {
        assertEquals(16, KoreaMapData.provinces.size)
        assertEquals(256, KoreaMapData.provinces.sumOf { KoreaMapData.districts(it.code).size })
        assertEquals("ver20260701", KoreaMapData.SOURCE_VERSION)
        assertEquals(64, KoreaMapData.SOURCE_SHA256.length)
        val outlines = KoreaMapData.provinces.map { it.outline } + KoreaMapData.provinces.flatMap { p -> KoreaMapData.districts(p.code).map { it.outline } }
        // ASCII only, so the string's length is its size in the class file's constant pool (limit 65535 bytes)
        assertTrue(outlines.all { o -> o.all { it.code < 128 } && o.length < 60_000 })
        // every area decodes into rings of at least three points
        assertTrue(KoreaShapes.allDistricts.all { d -> d.outline.rings.isNotEmpty() && d.outline.rings.all { it.size >= 6 } })
        // label points lie inside their own outline
        KoreaShapes.allDistricts.forEach { d -> assertTrue(d.outline.contains(d.info.labelX.toDouble(), d.info.labelY.toDouble()), "label of ${d.info.name}") }
    }

    @Test fun projection_roundTrips_andIsIsotropicAt36N() {
        listOf(37.5663 to 126.9779, 33.1144 to 126.2672, 37.2426 to 131.8669, 38.6 to 128.3).forEach { (lat, lng) ->
            val m = KoreaProjection.toMap(lat, lng)
            val g = KoreaProjection.toGeo(m.x, m.y)
            assertTrue(abs(g.lat - lat) < 1e-9 && abs(g.lng - lng) < 1e-9)
        }
        // at 36°N a unit east and a unit north are the same distance on the ground (about 11 m)
        val a = KoreaProjection.toMap(36.0, 127.0)
        val east = KoreaProjection.toMap(36.0, 127.01)
        val north = KoreaProjection.toMap(36.01, 127.0)
        val kmEast = 0.01 * 111.32 * cos(36.0 * PI / 180.0)
        val kmNorth = 0.01 * 111.32
        assertEquals(kmEast / KoreaProjection.KM_PER_UNIT, east.x - a.x, 0.01)
        assertEquals(kmNorth / KoreaProjection.KM_PER_UNIT, a.y - north.y, 0.01)
        assertEquals(111.32, 10_000 * KoreaProjection.KM_PER_UNIT, 1e-9)
    }

    @Test fun pointInPolygon_findsTheDistrict() {
        assertEquals("서울특별시 중구", KoreaRegions.locate(37.5663, 126.9779)?.label, "서울시청")
        assertEquals("부산광역시 동구", KoreaRegions.locate(35.1151, 129.0422)?.label, "부산역")
        assertEquals("제주특별자치도 제주시", KoreaRegions.locate(33.5104, 126.4914)?.label, "제주공항")
        assertEquals("강원특별자치도 강릉시", KoreaRegions.locate(37.7519, 128.8761)?.label, "강릉")
        assertEquals("경기도 수원시 팔달구", KoreaRegions.locate(37.2866, 127.0118)?.label, "수원 화성행궁")
        assertEquals("경상북도 울릉군", KoreaRegions.locate(37.4844, 130.9057)?.label, "울릉도")
        assertEquals("인천광역시 옹진군", KoreaRegions.locate(37.9696, 124.7129)?.label, "백령도")
    }

    @Test fun seaPoints_findNothing_unlessSnappedToANearbyCoast() {
        assertNull(KoreaRegions.locate(36.0, 125.5), "Yellow Sea")
        assertNull(KoreaRegions.locate(35.0, 131.0), "East Sea")
        assertNull(KoreaRegions.locate(35.0, 131.0, snapKm = 3.0), "far from any coast even with snapping")
        // between Dokdo's two islets: nothing strictly, 울릉군 within a kilometre
        assertEquals("경상북도 울릉군", KoreaRegions.locate(37.2426, 131.8669, snapKm = 1.0)?.label)
    }

    @Test fun locationText_findsDistrictCityOrProvince() {
        fun label(t: String) = KoreaRegions.matchText(t)?.label
        assertEquals("서울특별시 성동구", label("서울특별시 성동구 성수동2가"))
        assertEquals("서울특별시 성동구", label("서울 성동"))
        assertEquals("서울특별시 성동구", label("서울특별시성동구"))
        assertEquals("서울특별시", label("대한민국 서울특별시"))
        assertEquals("서울특별시", label("서울 연남동"))
        assertEquals("부산광역시 해운대구", label("부산 해운대"))
        assertEquals("부산광역시 부산진구", label("부산진구 전포동"))
        assertEquals("강원특별자치도 강릉시", label("강릉"))
        assertEquals("경기도 수원시 장안구", label("경기도 수원시 장안구"))
        assertEquals("경기도 수원시", label("수원"))
        assertEquals("경기도 성남시 분당구", label("분당"))
        assertEquals("경기도 광주시", label("경기 광주"))
        assertEquals("전남광주통합특별시 광주", label("광주"))
        assertEquals("전남광주통합특별시 동구", label("광주 동구"))
        assertEquals("전남광주통합특별시 광산구", label("광주광역시 광산구"))
        assertEquals("제주특별자치도", label("제주"))
        assertEquals("제주특별자치도 제주시", label("제주시 애월읍"))
        assertEquals("대구광역시 군위군", label("경북 군위군"), "moved to 대구 in 2023")
        assertEquals("인천광역시 미추홀구", label("인천 남구"), "renamed in 2018")
        assertEquals("강원특별자치도", label("강원도"))
        assertEquals("서울특별시", label("Seoul"))
        assertNull(label("중구"), "five 시·도 have a 중구")
        assertNull(label("고성"), "강원 and 경남 both have 고성군")
        assertNull(label("교토, 일본"))
        assertNull(label(""))
        // the pin goes to the district's label point, which lies inside it
        val r = assertNotNull(KoreaRegions.matchText("서울 성동구"))
        assertEquals("서울특별시 성동구", KoreaRegions.locateMap(r.center.x, r.center.y)?.label)
    }
}
