package com.coffeejournal.ui.map.search

import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.ui.ai.FakeAiHttp
import com.coffeejournal.ui.ai.MemorySecretStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SYNTHETIC Kakao Local replies in the documented shapes (2026-09-28); every place, address and id is made up. */
object KakaoReplies {
    const val KEYWORD = """
    {"meta": {"total_count": 3, "pageable_count": 3, "is_end": true, "same_name": {"region": [], "keyword": "테스트커피", "selected_region": ""}},
     "documents": [
      {"id": "1001", "place_name": "테스트커피 성수", "category_name": "음식점 > 카페 > 커피전문점", "category_group_code": "CE7",
       "category_group_name": "카페", "phone": "", "address_name": "서울 성동구 성수동2가 300-1",
       "road_address_name": "서울 성동구 성수이로7길 51", "x": "127.0557", "y": "37.5446", "place_url": "http://place.map.kakao.com/1001", "distance": ""},
      {"id": "1002", "place_name": "테스트커피 로스터리", "category_name": "음식점 > 카페", "category_group_code": "", "category_group_name": "",
       "phone": "", "address_name": "부산 영도구 봉래동5가 1", "road_address_name": "", "x": "129.0450", "y": "35.0930", "place_url": "", "distance": ""},
      {"id": "1003", "place_name": "좌표 없는 곳", "category_name": "", "category_group_name": "", "address_name": "어딘가", "road_address_name": "", "x": "", "y": ""}
     ]}
    """

    const val ADDRESS = """
    {"meta": {"total_count": 1, "pageable_count": 1, "is_end": true},
     "documents": [
      {"address_name": "서울 성동구 성수동2가 300-1", "address_type": "REGION_ADDR", "x": "127.05571", "y": "37.54462",
       "address": {"address_name": "서울 성동구 성수동2가 300-1"},
       "road_address": {"address_name": "서울 성동구 성수이로7길 51", "building_name": "테스트빌딩", "zone_no": "04799"}},
      {"address_name": "서울 성동구 성수동1가", "address_type": "REGION", "x": "127.0443", "y": "37.5447", "address": {}, "road_address": null}
     ]}
    """

    /** The same two places as [KEYWORD] with a next page ("더 보기"). */
    val KEYWORD_MORE = KEYWORD.replace("\"is_end\": true", "\"is_end\": false")

    /** The area 장충동1가 as an address match. */
    const val JANGCHUNG = """
    {"meta": {"total_count": 1, "pageable_count": 1, "is_end": true},
     "documents": [{"address_name": "서울 중구 장충동1가", "address_type": "REGION", "x": "127.0024", "y": "37.5604", "address": {}, "road_address": null}]}
    """

    const val EMPTY = """{"meta": {"total_count": 0, "pageable_count": 0, "is_end": true}, "documents": []}"""
    const val WRONG_KEY = """{"errorType": "AccessDeniedError", "message": "wrong appKey(abc) format"}"""
    const val DISABLED = """{"errorType": "NotAuthorizedError", "message": "App(123456) disabled OPEN_MAP_AND_LOCAL service."}"""
    const val QUOTA = """{"errorType": "RequestThrottled", "message": "API limit has been exceeded."}"""
}

/** SYNTHETIC Photon (OpenStreetMap) replies in the documented GeoJSON shape (2026-09-29); every name is made up. */
object PhotonReplies {
    /** Two cafés of one brand, a bean shop, an area and a nameless building. */
    const val PLACES = """
    {"type": "FeatureCollection", "features": [
      {"type": "Feature", "geometry": {"type": "Point", "coordinates": [126.9606, 37.5738]},
       "properties": {"osm_key": "amenity", "osm_value": "cafe", "type": "house", "name": "테스트커피",
        "street": "통일로12길", "district": "무악동", "city": "서울특별시", "country": "대한민국", "countrycode": "KR"}},
      {"type": "Feature", "geometry": {"type": "Point", "coordinates": [126.9491, 37.5411]},
       "properties": {"osm_key": "amenity", "osm_value": "cafe", "type": "house", "name": "테스트커피 도화점", "housenumber": "17",
        "street": "새창로2길", "district": "도화동", "city": "서울특별시", "country": "대한민국", "countrycode": "KR"}},
      {"type": "Feature", "geometry": {"type": "Point", "coordinates": [127.0100, 37.5580]},
       "properties": {"osm_key": "shop", "osm_value": "coffee", "type": "house", "name": "테스트 원두상점",
        "street": "동호로", "housenumber": "5", "city": "서울특별시", "country": "대한민국", "countrycode": "KR"}},
      {"type": "Feature", "geometry": {"type": "Point", "coordinates": [127.0030, 37.5558]},
       "properties": {"osm_key": "boundary", "osm_value": "administrative", "type": "district", "name": "장충동",
        "city": "서울특별시", "country": "대한민국", "countrycode": "KR"}},
      {"type": "Feature", "geometry": {"type": "Point", "coordinates": [127.0557, 37.5446]},
       "properties": {"osm_key": "building", "osm_value": "commercial", "type": "house",
        "street": "성수이로", "housenumber": "51", "city": "서울특별시", "country": "대한민국", "countrycode": "KR"}},
      {"type": "Feature", "geometry": {"type": "Point", "coordinates": ["x", 37.0]}, "properties": {"name": "좌표가 이상한 곳"}}
    ]}
    """

    const val KYOTO = """
    {"type": "FeatureCollection", "features": [
      {"type": "Feature", "geometry": {"type": "Point", "coordinates": [135.7588, 34.9858]},
       "properties": {"osm_value": "cafe", "type": "house", "name": "Test Kyoto Cafe", "street": "夷川通", "district": "中京区",
        "city": "京都市", "state": "京都府", "country": "日本", "countrycode": "JP"}}
    ]}
    """

    const val EMPTY = """{"type": "FeatureCollection", "features": []}"""
}

/** A phone search that answers with [hits] (or throws [failWith]) and records what it was asked. */
class FakeDevicePlaceSearch(
    override var supported: Boolean = true,
    var hits: List<PlaceHit> = emptyList(),
    var failWith: Exception? = null,
    var hang: Boolean = false,
) : DevicePlaceSearch {
    val asked = mutableListOf<Triple<String, Boolean, GeoPoint?>>()

    override suspend fun search(query: String, domestic: Boolean, near: GeoPoint?): List<PlaceHit> {
        asked += Triple(query, domestic, near)
        if (hang) awaitCancellation()
        failWith?.let { throw it }
        return hits
    }
}

class PlaceSearchTest {
    private val http = FakeAiHttp()
    private val secrets = MemorySecretStore()
    private val device = FakeDevicePlaceSearch(hits = listOf(PlaceHit("기기 카페", "서울특별시 중구 을지로 1", GeoPoint(37.5663, 126.9910))))
    private val service = PlaceSearchService(device, http, secrets)

    private suspend fun kakaoKey() = secrets.put(PlaceSearchService.KAKAO_SECRET, "kakao-test-key-9f3a")

    // ───────────── Kakao replies ─────────────

    @Test fun keywordReply_readsPlacesWithRoadAddressAndCategory() {
        val hits = KakaoLocal.parseKeyword(KakaoReplies.KEYWORD)!!
        assertEquals(
            listOf(
                PlaceHit("테스트커피 성수", "서울 성동구 성수이로7길 51", GeoPoint(37.5446, 127.0557), "카페"),
                // no road address: the lot address; no category group: the last step of the category path
                PlaceHit("테스트커피 로스터리", "부산 영도구 봉래동5가 1", GeoPoint(35.0930, 129.0450), "카페"),
            ),
            hits,
            "a place without coordinates is left out",
        )
        assertEquals(emptyList(), KakaoLocal.parseKeyword(KakaoReplies.EMPTY))
        assertNull(KakaoLocal.parseKeyword("<html>busy</html>"))
        assertNull(KakaoLocal.parseKeyword(KakaoReplies.WRONG_KEY))
    }

    @Test fun addressReply_namesABuildingAfterItself_andAnAreaAfterItsAddress() {
        val hits = KakaoLocal.parseAddress(KakaoReplies.ADDRESS)!!
        assertEquals(
            listOf(
                PlaceHit("테스트빌딩", "서울 성동구 성수이로7길 51", GeoPoint(37.54462, 127.05571), "주소"),
                PlaceHit("서울 성동구 성수동1가", "서울 성동구 성수동1가", GeoPoint(37.5447, 127.0443), "지역"),
            ),
            hits,
        )
    }

    @Test fun requests_carryTheKeyInTheHeaderOnly_andTheQueryEncoded() {
        val r = KakaoLocal.keywordRequest("my-key", "모모스 커피&1", near = null)
        assertEquals("https://dapi.kakao.com/v2/local/search/keyword.json?query=%EB%AA%A8%EB%AA%A8%EC%8A%A4%20%EC%BB%A4%ED%94%BC%261&size=15", r.url)
        assertEquals(mapOf("Authorization" to "KakaoAK my-key"), r.headers)
        assertNull(r.body, "a GET")
        assertFalse("my-key" in r.toString(), "the key is never printed")
        val near = KakaoLocal.keywordRequest("my-key", "카페", GeoPoint(37.5, 127.0))
        assertTrue(near.url.endsWith("&x=127.0&y=37.5&sort=distance"), near.url)
        assertEquals("https://dapi.kakao.com/v2/local/search/address.json?query=%EC%84%B1%EC%88%98&size=10", KakaoLocal.addressRequest("k", "성수").url)
    }

    // ───────────── which search answers ─────────────

    @Test fun withoutAKey_thePhoneSearches_andNothingGoesToKakao() = runTest {
        assertEquals(PlaceSource.DEVICE, service.sourceFor(domestic = true))
        val r = assertIs<PlaceSearchResult.Found>(service.search("  기기 카페 ", domestic = true))
        assertEquals(listOf(PlaceSource.DEVICE), r.sources, "OpenStreetMap did not answer, so it is not credited")
        assertEquals(listOf("기기 카페"), r.hits.map { it.name })
        assertEquals(Triple("기기 카페", true, null), device.asked.single())
        assertTrue(http.requests.none { "dapi.kakao.com" in it.url })
        assertTrue(http.requests.single().url.startsWith(OsmPhoton.URL + "?q=%EA%B8%B0%EA%B8%B0%20%EC%B9%B4%ED%8E%98&"))
    }

    // ───────────── OpenStreetMap (Photon) ─────────────

    @Test fun photonReply_readsCafesShopsAreasAndAddresses_inTheCountrysWay() {
        val hits = OsmPhoton.parse(PhotonReplies.PLACES)!!
        assertEquals(
            listOf(
                PlaceHit("테스트커피", "서울특별시 통일로12길 (무악동)", GeoPoint(37.5738, 126.9606), "카페"),
                PlaceHit("테스트커피 도화점", "서울특별시 새창로2길 17 (도화동)", GeoPoint(37.5411, 126.9491), "카페"),
                PlaceHit("테스트 원두상점", "서울특별시 동호로 5", GeoPoint(37.5580, 127.0100), "커피 원두"),
                PlaceHit("장충동", "서울특별시", GeoPoint(37.5558, 127.0030), "지역"),
                PlaceHit("서울특별시 성수이로 51", "서울특별시 성수이로 51", GeoPoint(37.5446, 127.0557), "주소"),
            ),
            hits,
            "a feature without readable coordinates is left out",
        )
        assertEquals(
            PlaceHit("Test Kyoto Cafe", "夷川通, 中京区, 京都市, 京都府, 日本", GeoPoint(34.9858, 135.7588), "카페"),
            OsmPhoton.parse(PhotonReplies.KYOTO)!!.single(),
        )
        assertEquals(emptyList(), OsmPhoton.parse(PhotonReplies.EMPTY))
        assertNull(OsmPhoton.parse("<html>Too Many Requests</html>"))
    }

    @Test fun photonRequests_keepToKorea_orAroundAPlace_andNameTheApp() {
        val korea = OsmPhoton.request("프릳츠", domestic = true, near = null)
        assertEquals("https://photon.komoot.io/api/?q=%ED%94%84%EB%A6%B3%EC%B8%A0&limit=15&bbox=124.5,33.0,131.9,38.7", korea.url)
        assertNull(korea.body, "a GET")
        assertTrue(korea.headers["User-Agent"]!!.startsWith("CoffeeJournal"), "no personal details, only the app")
        val around = OsmPhoton.request("카페", domestic = true, near = GeoPoint(37.5, 127.0)).url
        assertTrue(around.endsWith("&lat=37.5&lon=127.0&bbox=126.75,37.25,127.25,37.75"), around)
        assertEquals("https://photon.komoot.io/api/?q=Kurasu&limit=15", OsmPhoton.request("Kurasu", domestic = false, near = null).url)
    }

    @Test fun keyless_putsThePhonesAndOpenStreetMapsPlacesTogether_placesBeforeAreas() = runTest {
        device.hits = listOf(
            PlaceHit("서울특별시 중구 장충동", "서울특별시 중구 장충동", GeoPoint(37.5580, 127.0050)),
            PlaceHit("기기 카페", "서울특별시 중구 을지로 1", GeoPoint(37.5663, 126.9910)),
        )
        http.on("photon.komoot.io") { PhotonReplies.PLACES }
        val r = assertIs<PlaceSearchResult.Found>(service.search("카페", domestic = true))
        assertEquals(listOf(PlaceSource.DEVICE, PlaceSource.OSM), r.sources)
        assertEquals(
            listOf("기기 카페", "테스트커피", "테스트커피 도화점", "테스트 원두상점", "서울특별시 중구 장충동", "장충동", "서울특별시 성수이로 51"),
            r.hits.map { it.name },
        )
        assertNull(r.next, "only Kakao pages")

        // the phone failing still gives OpenStreetMap's places, and the other way round
        device.failWith = PlaceSearchException("grpc failed")
        assertEquals(listOf(PlaceSource.OSM), assertIs<PlaceSearchResult.Found>(service.search("카페", domestic = true)).sources)
        device.failWith = null
        val phoneOnly = PlaceSearchService(device, FakeAiHttp().on("photon.komoot.io", status = 429) { "Too Many Requests" }, secrets)
        assertEquals(listOf(PlaceSource.DEVICE), assertIs<PlaceSearchResult.Found>(phoneOnly.search("카페", domestic = true)).sources)
    }

    @Test fun anAddress_listsAddressesAndAreasFirst() = runTest {
        device.hits = listOf(PlaceHit("기기 카페", "서울특별시 성동구 성수이로7길 49", GeoPoint(37.5445, 127.0556)))
        http.on("photon.komoot.io") { PhotonReplies.PLACES }
        val r = assertIs<PlaceSearchResult.Found>(service.search("성수이로 51", domestic = true))
        assertEquals("서울특별시 성수이로 51", r.hits.first().name)
        assertTrue(PlaceSearchService.looksLikeAddress("성수이로7길 51"))
        assertTrue(PlaceSearchService.looksLikeAddress("장충동"))
        assertTrue(PlaceSearchService.looksLikeAddress("부산 영도구 봉래동5가 1").not(), "부산 has no ending: not every word is an address word")
        assertTrue(PlaceSearchService.looksLikeAddress("영도구 봉래동5가 300-1"))
        assertFalse(PlaceSearchService.looksLikeAddress("프릳츠 장충"))
        assertFalse(PlaceSearchService.looksLikeAddress("모모스커피"))
        assertFalse(PlaceSearchService.looksLikeAddress("  "))
    }

    @Test fun abroad_usesOpenStreetMapToo_withoutTheKoreaBox() = runTest {
        device.hits = emptyList()
        http.on("photon.komoot.io") { PhotonReplies.KYOTO }
        val r = assertIs<PlaceSearchResult.Found>(service.search("Test Kyoto", domestic = false))
        assertEquals(listOf("Test Kyoto Cafe"), r.hits.map { it.name })
        assertFalse("bbox" in http.requests.single().url)
    }

    // ───────────── a name around an area: "프릳츠 장충" ─────────────

    @Test fun keyless_aNameWithAnArea_findsTheNameAroundTheArea_nearestFirst() = runTest {
        // the phone knows only the area; OpenStreetMap finds nothing for both words together
        device.hits = listOf(PlaceHit("서울특별시 중구 장충동", "서울특별시 중구 장충동", GeoPoint(37.5580, 127.0050)))
        http.on("q=%ED%94%84%EB%A6%B3%EC%B8%A0%20%EC%9E%A5%EC%B6%A9&") { PhotonReplies.EMPTY }
            .on("q=%ED%85%8C%EC%8A%A4%ED%8A%B8%EC%BB%A4%ED%94%BC%20%EC%9E%A5%EC%B6%A9&") { PhotonReplies.EMPTY }
            .on("q=%ED%85%8C%EC%8A%A4%ED%8A%B8%EC%BB%A4%ED%94%BC&") { PhotonReplies.PLACES }
        val r = assertIs<PlaceSearchResult.Found>(service.search("테스트커피 장충", domestic = true))
        // the two cafés carrying the name, nearer first (about 4.3 and 5.3 km), then the area itself; the bean shop
        // without the name and the areas and addresses found around it stay out
        assertEquals(listOf("테스트커피", "테스트커피 도화점", "서울특별시 중구 장충동"), r.hits.map { it.name })
        val near = r.hits.filter { it.distanceFrom == "장충" }
        assertEquals(2, near.size)
        assertTrue(near[0].distanceM!! < near[1].distanceM!!, "nearest first: $near")
        assertTrue(near[0].distanceM!! in 4_000..4_600, "${near[0].distanceM}")
        assertEquals("서울특별시 중구 장충동", r.hits.last().name, "the area comes after the places around it")
        assertEquals(GeoPoint(37.5580, 127.0050), device.asked.last().third, "the name is searched around the area")
        assertEquals("장충에서 ${PlaceSearchService.distanceLabel(near.first().distanceM!!)}", PlaceSearchTexts.distance(near.first()))
        assertTrue(PlaceSource.OSM in r.sources)
    }

    @Test fun noSecondSearch_forOneWord_anAddress_orWhenAPlaceCarriesEveryWord() = runTest {
        device.hits = listOf(PlaceHit("FELT 청계천점", "서울 중구 청계천로 100", GeoPoint(37.5663, 126.9910), "카페"))
        service.search("FELT 청계천", domestic = true)
        service.search("FELT", domestic = true)
        device.hits = listOf(PlaceHit("서울특별시 성동구 성수이로7길 51", "서울특별시 성동구 성수이로7길 51", GeoPoint(37.5446, 127.0557)))
        service.search("성수이로7길 51", domestic = true)
        assertEquals(listOf("FELT 청계천", "FELT", "성수이로7길 51"), device.asked.map { it.first })
    }

    @Test fun withAKey_aNameWithAnArea_asksKakaoForTheNameAroundTheArea() = runTest {
        kakaoKey()
        val both = "query=%ED%85%8C%EC%8A%A4%ED%8A%B8%EC%BB%A4%ED%94%BC%20%EC%9E%A5%EC%B6%A9&"
        val area = "query=%EC%9E%A5%EC%B6%A9&"
        val name = "keyword.json?query=%ED%85%8C%EC%8A%A4%ED%8A%B8%EC%BB%A4%ED%94%BC&"
        http.on("keyword.json?$both") { KakaoReplies.EMPTY }.on("address.json?$both") { KakaoReplies.EMPTY }
            .on("keyword.json?$area") { KakaoReplies.EMPTY }.on("address.json?$area") { KakaoReplies.JANGCHUNG }
            .on(name) { KakaoReplies.KEYWORD_MORE }
        val r = assertIs<PlaceSearchResult.Found>(service.search("테스트커피 장충", domestic = true))
        assertEquals(listOf(PlaceSource.KAKAO), r.sources)
        assertEquals(listOf("테스트커피 성수", "테스트커피 로스터리"), r.hits.map { it.name })
        assertTrue(r.hits.all { it.distanceFrom == "장충" })
        val around = http.requests.single { name in it.url }.url
        assertTrue(around.endsWith("&x=127.0024&y=37.5604&sort=distance"), around)
        assertEquals(MorePlaces("테스트커피", GeoPoint(37.5604, 127.0024), page = 2, from = "장충"), r.next)
        assertTrue(device.asked.isEmpty() && http.requests.none { "photon" in it.url }, "Kakao alone answers")
    }

    @Test fun more_asksKakaoForTheNextPage_untilTheLast() = runTest {
        kakaoKey()
        http.on("page=2") { KakaoReplies.KEYWORD_MORE }.on("page=3") { KakaoReplies.KEYWORD }
        val two = assertIs<PlaceSearchResult.Found>(service.more(MorePlaces("테스트커피", null, page = 2)))
        assertEquals(MorePlaces("테스트커피", null, page = 3), two.next)
        assertTrue(http.requests.single().url.contains("&size=15&page=2"))
        val three = assertIs<PlaceSearchResult.Found>(service.more(two.next!!))
        assertNull(three.next, "is_end: no more")
        service.clearKakaoKey()
        assertEquals(PlaceSearchError.KAKAO_KEY, assertIs<PlaceSearchResult.Failed>(service.more(two.next!!)).error)
    }

    @Test fun withAKey_domesticGoesToKakao_placesFirst_abroadStaysKeyless() = runTest {
        kakaoKey()
        http.on("keyword.json") { KakaoReplies.KEYWORD }.on("address.json") { KakaoReplies.ADDRESS }
        assertEquals(PlaceSource.KAKAO, service.sourceFor(domestic = true))
        assertEquals(PlaceSource.DEVICE, service.sourceFor(domestic = false))
        val r = assertIs<PlaceSearchResult.Found>(service.search("테스트커피", domestic = true))
        assertEquals(PlaceSource.KAKAO, r.source)
        assertEquals(listOf("테스트커피 성수", "테스트커피 로스터리", "테스트빌딩", "서울 성동구 성수동1가"), r.hits.map { it.name })
        assertEquals(listOf("KakaoAK kakao-test-key-9f3a"), http.requests.map { it.headers["Authorization"] }.distinct())
        assertTrue(device.asked.isEmpty())
        assertNull(r.next, "is_end: nothing more")

        // an address: Kakao's address matches first
        val address = assertIs<PlaceSearchResult.Found>(service.search("성수이로7길 51", domestic = true))
        assertEquals(listOf("테스트빌딩", "서울 성동구 성수동1가", "테스트커피 성수", "테스트커피 로스터리"), address.hits.map { it.name })

        http.requests.clear()
        device.hits = listOf(PlaceHit("Kurasu Kyoto", "일본 교토부 교토시", GeoPoint(34.9858, 135.7588)))
        val abroad = assertIs<PlaceSearchResult.Found>(service.search("Kurasu", domestic = false))
        assertEquals(PlaceSource.DEVICE, abroad.source)
        assertTrue(http.requests.none { "dapi.kakao.com" in it.url }, "해외 never goes to Kakao")
    }

    @Test fun aRefusedKey_fallsBackToThePhone_withANotice() = runTest {
        kakaoKey()
        http.on("keyword.json", status = 401) { KakaoReplies.WRONG_KEY }
        val r = assertIs<PlaceSearchResult.Found>(service.search("카페", domestic = true))
        assertEquals(PlaceSource.DEVICE, r.source)
        assertEquals(PlaceSearchTexts.FALLBACK_KEY, r.notice)
        assertEquals(1, http.requests.count { "dapi.kakao.com" in it.url }, "no address search after a refused key")

        device.supported = false
        val none = assertIs<PlaceSearchResult.Failed>(service.search("카페", domestic = true))
        assertEquals(PlaceSearchError.KAKAO_KEY, none.error)
    }

    @Test fun aDisabledService_aSpentQuota_andAServerError_fallBackToo() = runTest {
        kakaoKey()
        http.on("keyword.json", status = 403) { KakaoReplies.DISABLED }
        assertEquals(PlaceSearchTexts.FALLBACK_DISABLED, assertIs<PlaceSearchResult.Found>(service.search("카페", domestic = true)).notice)
        val busy = PlaceSearchService(device, FakeAiHttp().on("keyword.json", status = 502) { "Bad Gateway" }, secrets)
        assertEquals(PlaceSearchTexts.FALLBACK_FAILED, assertIs<PlaceSearchResult.Found>(busy.search("카페", domestic = true)).notice)
        val spent = PlaceSearchService(device, FakeAiHttp().on("keyword.json", status = 429) { KakaoReplies.QUOTA }, secrets)
        assertEquals(PlaceSearchTexts.FALLBACK_QUOTA, assertIs<PlaceSearchResult.Found>(spent.search("카페", domestic = true)).notice)
        device.supported = false
        http.on("photon.komoot.io", status = 503) { "busy" }
        assertEquals(PlaceSearchError.KAKAO_DISABLED, assertIs<PlaceSearchResult.Failed>(service.search("카페", domestic = true)).error)
    }

    @Test fun offline_isAFailure_notAFallback() = runTest {
        kakaoKey()
        http.offline("dapi.kakao.com")
        val r = assertIs<PlaceSearchResult.Failed>(service.search("카페", domestic = true))
        assertEquals(PlaceSearchError.FAILED, r.error)
        assertEquals(PlaceSource.KAKAO, r.source)
        assertTrue(device.asked.isEmpty())
        assertEquals(PlaceSearchTexts.FAILED, PlaceSearchTexts.message(r.error, domestic = true))
    }

    @Test fun aFailedAddressStep_stillGivesThePlaces() = runTest {
        kakaoKey()
        http.on("keyword.json") { KakaoReplies.KEYWORD }.on("address.json", status = 400) { """{"message": "bad"}""" }
        val r = assertIs<PlaceSearchResult.Found>(service.search("테스트커피", domestic = true))
        assertEquals(listOf("테스트커피 성수", "테스트커피 로스터리"), r.hits.map { it.name })
    }

    @Test fun thePhoneFailing_orMissing_orSlow_isReported() = runTest {
        device.failWith = PlaceSearchException("grpc failed")
        assertEquals(PlaceSearchError.FAILED, assertIs<PlaceSearchResult.Failed>(service.search("카페", domestic = true)).error)
        device.failWith = null
        device.hang = true
        assertEquals(PlaceSearchError.FAILED, assertIs<PlaceSearchResult.Failed>(service.search("카페", domestic = true)).error, "timed out")
        device.hang = false
        device.supported = false
        assertEquals(PlaceSource.OSM, service.sourceFor(domestic = false), "OpenStreetMap still answers without the phone's search")
        http.supported = false
        val none = assertIs<PlaceSearchResult.Failed>(service.search("카페", domestic = true))
        assertEquals(PlaceSearchError.UNAVAILABLE, none.error)
        assertNull(service.sourceFor(domestic = false))
        assertEquals(PlaceSearchTexts.UNAVAILABLE + PlaceSearchTexts.UNAVAILABLE_KAKAO, PlaceSearchTexts.message(none.error, domestic = true))
        assertEquals(PlaceSearchTexts.UNAVAILABLE, PlaceSearchTexts.message(none.error, domestic = false))
    }

    @Test fun aKeyThatCannotBeKept_isNotUsed() = runTest {
        val locked = MemorySecretStore(supported = false).also { it.values[PlaceSearchService.KAKAO_SECRET] = "k" }
        val s = PlaceSearchService(device, http, locked)
        assertNull(s.kakaoKey())
        assertEquals(PlaceSource.DEVICE, s.sourceFor(domestic = true))
        s.saveKakaoKey("new")
        assertEquals("k", locked.values[PlaceSearchService.KAKAO_SECRET], "nothing written to a store that cannot keep it")
    }

    // ───────────── results, key check ─────────────

    @Test fun tidy_keepsTheScope_dropsRepeats_andMeasuresFromHere() {
        val seoul = PlaceHit("A", "서울", GeoPoint(37.5446, 127.0557))
        val again = PlaceHit("a ", "서울", GeoPoint(37.54461, 127.05571))
        val tokyo = PlaceHit("B", "도쿄", GeoPoint(35.6762, 139.6503))
        val sea = PlaceHit("C", "서해", GeoPoint(36.0, 125.5))
        assertEquals(listOf(seoul), PlaceSearchService.tidy(listOf(seoul, again, tokyo, sea), domestic = true, near = null))
        assertEquals(listOf(tokyo, sea), PlaceSearchService.tidy(listOf(seoul, tokyo, sea), domestic = false, near = null))
        val here = GeoPoint(37.5446, 127.0457)
        val measured = PlaceSearchService.tidy(listOf(seoul), domestic = true, near = here).single()
        assertTrue(measured.distanceM!! in 870..890, "about 880 m east: ${measured.distanceM}")
        assertEquals("현재 위치에서 880 m", PlaceSearchTexts.fromHere(880))
        assertEquals(PlaceSearchService.MAX_HITS, PlaceSearchService.tidy(List(40) { i -> PlaceHit("$i", "", GeoPoint(37.5 + i * 0.001, 127.0)) }, true, null).size)
    }

    @Test fun distanceLabels() {
        assertEquals("0 m", PlaceSearchService.distanceLabel(0))
        assertEquals("999 m", PlaceSearchService.distanceLabel(999))
        assertEquals("1.2 km", PlaceSearchService.distanceLabel(1_250))
        assertEquals("12 km", PlaceSearchService.distanceLabel(11_600))
        // Seoul City Hall → Busan City Hall, about 325 km
        assertTrue(PlaceSearchService.distanceM(GeoPoint(37.5663, 126.9779), GeoPoint(35.1798, 129.0750)) in 320_000..330_000)
    }

    @Test fun keyCheck_readsKakaosAnswer() = runTest {
        assertEquals(KakaoKeyCheck.REJECTED, service.checkKakaoKey(), "no key saved")
        service.saveKakaoKey(" kakao-\nkey ")
        assertEquals("kakao-key", secrets.values[PlaceSearchService.KAKAO_SECRET], "pasted spaces and line breaks go")
        fun checking(http: FakeAiHttp) = PlaceSearchService(device, http, secrets)
        val ok = FakeAiHttp().on("keyword.json") { KakaoReplies.KEYWORD }
        assertEquals(KakaoKeyCheck.OK, checking(ok).checkKakaoKey())
        assertTrue(ok.requests.single().url.contains("size=1"), "one place is enough")
        assertEquals(KakaoKeyCheck.REJECTED, checking(FakeAiHttp().on("keyword.json", status = 401) { KakaoReplies.WRONG_KEY }).checkKakaoKey())
        assertEquals(KakaoKeyCheck.DISABLED, checking(FakeAiHttp().on("keyword.json", status = 403) { KakaoReplies.DISABLED }).checkKakaoKey())
        assertEquals(KakaoKeyCheck.QUOTA, checking(FakeAiHttp().on("keyword.json", status = 429) { KakaoReplies.QUOTA }).checkKakaoKey())
        assertEquals(KakaoKeyCheck.FAILED, checking(FakeAiHttp().on("keyword.json", status = 500) { "" }).checkKakaoKey())
        assertEquals(KakaoKeyCheck.OFFLINE, checking(FakeAiHttp().offline("keyword.json")).checkKakaoKey())
        service.clearKakaoKey()
        assertNull(service.kakaoKey())
    }
}
