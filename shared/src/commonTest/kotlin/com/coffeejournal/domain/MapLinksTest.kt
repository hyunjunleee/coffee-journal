package com.coffeejournal.domain

import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.rules.MapLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Naver Map / Kakao Map / Google Maps links in their documented formats, percent-encoded as UTF-8. */
class MapLinksTest {
    private val libre = GeoPoint(37.5446, 127.0557)

    @Test fun encoding_isRfc3986Utf8() {
        assertEquals("%EC%BB%A4%ED%94%BC%20%EB%A6%AC%EB%B8%8C%EB%A0%88", MapLinks.encode("커피 리브레"))
        assertEquals("A-z_0.9~%2C%26%3D%3F%2F%23%2B", MapLinks.encode("A-z_0.9~,&=?/#+"))
        assertEquals("37.5446", MapLinks.coord(37.5446))
        assertEquals("-122.6765", MapLinks.coord(-122.6765))
        assertEquals("127", MapLinks.coord(127.0))
        assertEquals("0.0000001", MapLinks.coord(1e-7))
    }

    @Test fun query_dropsTheCountryAndBlanks() {
        assertEquals("커피 리브레 서울특별시 성동구", MapLinks.query(" 커피 리브레 ", "대한민국 서울특별시  성동구"))
        assertEquals("프릳츠", MapLinks.query("프릳츠", ""))
    }

    @Test fun naver_placeWithPosition_searchWithout_webFallback() {
        val place = MapLinks.naver("커피 리브레", "서울 성동구", libre)
        assertEquals("nmap://place?lat=37.5446&lng=127.0557&name=%EC%BB%A4%ED%94%BC%20%EB%A6%AC%EB%B8%8C%EB%A0%88&appname=com.coffeejournal.app", place.uri)
        assertEquals("https://map.naver.com/p/search/%EC%BB%A4%ED%94%BC%20%EB%A6%AC%EB%B8%8C%EB%A0%88%20%EC%84%9C%EC%9A%B8%20%EC%84%B1%EB%8F%99%EA%B5%AC", place.fallback)
        val search = MapLinks.naver("프릳츠", "", null)
        assertEquals("nmap://search?query=%ED%94%84%EB%A6%B3%EC%B8%A0&appname=com.coffeejournal.app", search.uri)
        assertEquals("https://map.naver.com/p/search/%ED%94%84%EB%A6%B3%EC%B8%A0", search.fallback)
        // outside the scheme's documented range (lat 31.43–44.35, lng 122.37–132.00) a position cannot be shown: search
        val kyoto = MapLinks.naver("Kurasu Kyoto", "교토, 일본", GeoPoint(34.9858, 135.7588))
        assertEquals("nmap://search?query=Kurasu%20Kyoto%20%EA%B5%90%ED%86%A0%2C%20%EC%9D%BC%EB%B3%B8&appname=com.coffeejournal.app", kyoto.uri)
    }

    @Test fun kakao_mapLinkWithPosition_searchWithout() {
        assertEquals("https://map.kakao.com/link/map/%EC%BB%A4%ED%94%BC%20%EB%A6%AC%EB%B8%8C%EB%A0%88,37.5446,127.0557", MapLinks.kakao("커피 리브레", "", libre).uri)
        // a comma in the name must not split the path's fields
        assertEquals("https://map.kakao.com/link/map/A%2CB,37.5446,127.0557", MapLinks.kakao("A,B", "", libre).uri)
        assertEquals("https://map.kakao.com/link/search/%ED%94%84%EB%A6%B3%EC%B8%A0%20%EC%84%9C%EC%9A%B8", MapLinks.kakao("프릳츠", "서울", null).uri)
        assertNull(MapLinks.kakao("프릳츠", "", null).fallback, "an https link opens the app or the browser by itself")
    }

    @Test fun google_searchByPositionOrName_andOverseasOrder() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=45.5231%2C-122.6765", MapLinks.google("Coava", "", GeoPoint(45.5231, -122.6765)).uri)
        assertEquals("https://www.google.com/maps/search/?api=1&query=Kurasu%20Kyoto%20%EA%B5%90%ED%86%A0%2C%20%EC%9D%BC%EB%B3%B8", MapLinks.google("Kurasu Kyoto", "교토, 일본", null).uri)
        assertEquals(listOf(MapLinks.NAVER_LABEL, MapLinks.KAKAO_LABEL), MapLinks.forPlace("a", "", null, overseas = false).map { it.label })
        assertEquals(listOf(MapLinks.GOOGLE_LABEL, MapLinks.NAVER_LABEL, MapLinks.KAKAO_LABEL), MapLinks.forPlace("a", "", null, overseas = true).map { it.label })
    }
}
