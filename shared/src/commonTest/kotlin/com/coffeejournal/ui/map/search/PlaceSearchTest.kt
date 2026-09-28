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

    const val EMPTY = """{"meta": {"total_count": 0, "pageable_count": 0, "is_end": true}, "documents": []}"""
    const val WRONG_KEY = """{"errorType": "AccessDeniedError", "message": "wrong appKey(abc) format"}"""
    const val DISABLED = """{"errorType": "NotAuthorizedError", "message": "App(123456) disabled OPEN_MAP_AND_LOCAL service."}"""
    const val QUOTA = """{"errorType": "RequestThrottled", "message": "API limit has been exceeded."}"""
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
        assertEquals(PlaceSource.DEVICE, r.source)
        assertEquals(listOf("기기 카페"), r.hits.map { it.name })
        assertEquals(Triple("기기 카페", true, null), device.asked.single())
        assertTrue(http.requests.isEmpty())
    }

    @Test fun withAKey_domesticGoesToKakao_addressesFirst_abroadStaysOnThePhone() = runTest {
        kakaoKey()
        http.on("keyword.json") { KakaoReplies.KEYWORD }.on("address.json") { KakaoReplies.ADDRESS }
        assertEquals(PlaceSource.KAKAO, service.sourceFor(domestic = true))
        assertEquals(PlaceSource.DEVICE, service.sourceFor(domestic = false))
        val r = assertIs<PlaceSearchResult.Found>(service.search("테스트커피", domestic = true))
        assertEquals(PlaceSource.KAKAO, r.source)
        assertEquals(listOf("테스트빌딩", "서울 성동구 성수동1가", "테스트커피 성수", "테스트커피 로스터리"), r.hits.map { it.name })
        assertEquals(listOf("KakaoAK kakao-test-key-9f3a"), http.requests.map { it.headers["Authorization"] }.distinct())
        assertTrue(device.asked.isEmpty())

        http.requests.clear()
        device.hits = listOf(PlaceHit("Kurasu Kyoto", "일본 교토부 교토시", GeoPoint(34.9858, 135.7588)))
        val abroad = assertIs<PlaceSearchResult.Found>(service.search("Kurasu", domestic = false))
        assertEquals(PlaceSource.DEVICE, abroad.source)
        assertTrue(http.requests.isEmpty(), "해외 never goes to Kakao")
    }

    @Test fun aRefusedKey_fallsBackToThePhone_withANotice() = runTest {
        kakaoKey()
        http.on("keyword.json", status = 401) { KakaoReplies.WRONG_KEY }
        val r = assertIs<PlaceSearchResult.Found>(service.search("카페", domestic = true))
        assertEquals(PlaceSource.DEVICE, r.source)
        assertEquals(PlaceSearchTexts.FALLBACK_KEY, r.notice)
        assertEquals(1, http.requests.size, "no address search after a refused key")

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
