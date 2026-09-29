package com.coffeejournal.android

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.backup.BackupService
import com.coffeejournal.data.repo.CafePlaceRepository
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.rules.MapLinks
import com.coffeejournal.ui.guide.KeyHowTos
import com.coffeejournal.ui.map.search.KakaoKeyCheck
import com.coffeejournal.ui.map.search.LocateResult
import com.coffeejournal.ui.map.search.LocateTexts
import com.coffeejournal.ui.map.search.PlaceSearchService
import com.coffeejournal.ui.map.search.PlaceSearchTexts
import com.coffeejournal.ui.map.search.PlaceSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The location picker's search and "현재 위치" through the real App(): a café found by name is saved with its
 * address, a roastery's searched address fills its empty 지역, the phone's position (after the permission prompt) sets
 * the point or a refusal says why, the messages for nothing found / failure / no search, and the optional Kakao key in
 * 설정. The phone's
 * search and position are fakes ([FakePlaceSearch], [FakeCurrentLocation]); Kakao is the fake HTTP layer.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class PlaceSearchFlowTest : CoverageFlowBase() {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val search get() = PlaceSetup.search
    private val location get() = PlaceSetup.location
    private val cafes get() = koinGet<CafePlaceRepository>()

    @Before
    fun noPermissionYet() = PlaceSetup.denyLocation(app)

    private fun openRoasteryPicker(name: String) {
        tab("tab-bean")
        tapText("로스터리")
        waitForText("한국 로스터리 지도")
        clickText("+ 로스터리 추가")
        waitForText("로스터리 추가")
        typeInto("예: 영천카페 듀잇", name)
        tapText("지도에서 위치 지정")
        waitForText(PlaceSearchTexts.FIELD)
    }

    private fun searchNow() = tap(button(PlaceSearchTexts.SEARCH))

    /** Answers the pending permission prompt as the system would. */
    private fun answerPermission(granted: Boolean) {
        waitUntil("permission prompt") { shadowOf(compose.activity).lastRequestedPermission != null }
        val request = shadowOf(compose.activity).lastRequestedPermission
        assertEquals(
            "the prompt asks for precise and approximate location",
            setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), request.requestedPermissions.toSet(),
        )
        if (granted) PlaceSetup.grantLocation(app) else PlaceSetup.denyLocation(app)
        val result = if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        compose.runOnUiThread {
            @Suppress("DEPRECATION")
            compose.activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, IntArray(request.requestedPermissions.size) { result })
        }
        settle()
    }

    @Test
    fun cafe_foundByName_isSavedWithItsAddress_andTheCafeListShowsIt() {
        SampleData.seed()
        search.hits = listOf(PlaceFixtures.FELT, PlaceFixtures.FELT_OTHER)
        launchApp()
        tab("tab-calendar")
        tapText("카페")
        tapText("전체 보기")
        waitForText("카페 위치")
        tap(button("지도에서 위치 지정"))
        // the field starts with the café's name; nothing is searched until 검색
        waitFor(field("FELT 청계천"))
        assertTrue(search.asked.isEmpty())
        searchNow()
        waitFor(button(PlaceFixtures.FELT.name))
        assertEquals(listOf(Triple("FELT 청계천", true, null)), search.asked.toList())
        assertTrue(has(hasText(PlaceFixtures.FELT.address)))
        assertTrue(has(hasText("카페")))
        waitFor(hasTestTag("search-source") and hasText(PlaceSearchTexts.source(PlaceSource.DEVICE)))

        // picking it opens its 시·도, keeps its address and closes the list
        tap(button(PlaceFixtures.FELT.name))
        waitFor(button("← 전국 · 서울특별시"))
        waitGone(hasTestTag("search-results"))
        waitForText("📍 서울특별시 중구")
        waitFor(hasTestTag("picked-address") and hasText(PlaceFixtures.FELT.address))
        tapText("저장")
        waitUntil("café saved with its address") { runBlocking { cafes.get("FELT 청계천") }?.address == PlaceFixtures.FELT.address }
        assertEquals(PlaceFixtures.FELT.point, runBlocking { cafes.get("felt 청계천") }!!.point)
        // back on the calendar's 카페 위치: where the café is, with the address
        waitForText("카페 위치")
        waitForText(PlaceFixtures.FELT.address)

        // changed on the map afterwards: the point moves and the address goes
        tap(button("위치 변경"))
        waitFor(hasTestTag("picked-address") and hasText(PlaceFixtures.FELT.address))
        tapText("위치 지우기")
        waitForText("아직 위치를 정하지 않았어요.")
        assertFalse(has(hasTestTag("picked-address")))
        tapText("취소")
        waitForText("카페 위치")
        assertEquals(PlaceFixtures.FELT.address, runBlocking { cafes.get("FELT 청계천") }!!.address)
    }

    @Test
    fun roastery_searchedAddress_fillsAnEmptyLocationField_butNotATypedOne() {
        search.hits = listOf(PlaceFixtures.BUILDING)
        launchApp()
        openRoasteryPicker("테스트 로스터리")
        waitFor(field("테스트 로스터리"))
        replaceIn("테스트 로스터리", "성수이로7길 51")
        searchNow()
        tap(button(PlaceFixtures.BUILDING.name))
        waitForText("📍 서울특별시 성동구")
        tapText("확인")
        waitForText("로스터리 추가")
        waitFor(field(PlaceFixtures.BUILDING.address))
        waitForText("📍 서울특별시 성동구")
        clickText("저장")
        waitForText("한국 로스터리 지도")
        val saved = misc().single { it.name == "테스트 로스터리" }
        assertEquals(PlaceFixtures.BUILDING.address, saved.location)
        assertEquals(PlaceFixtures.BUILDING.point, saved.point)

        // what the user wrote in 지역 stays
        tap(button("수정"), 0)
        waitForText("로스터리 수정")
        replaceIn(PlaceFixtures.BUILDING.address, "성수동 골목")
        tapText("지도에서 위치 변경")
        search.hits = listOf(PlaceFixtures.MOMOS)
        searchNow()
        tap(button(PlaceFixtures.MOMOS.name))
        waitForText("📍 부산광역시 영도구")
        tapText("확인")
        waitForText("로스터리 수정")
        waitFor(field("성수동 골목"))
        assertFalse(has(field(PlaceFixtures.MOMOS.address)))
        clickText("수정 저장")
        waitForText("한국 로스터리 지도")
        waitUntil("moved to Busan") { misc().single { it.name == "테스트 로스터리" }.point == PlaceFixtures.MOMOS.point }
        assertEquals("성수동 골목", misc().single { it.name == "테스트 로스터리" }.location)
    }

    @Test
    fun currentLocation_asksForThePermission_andAllowedItSetsThePoint() {
        val here = GeoPoint(37.5446, 127.0557)
        location.answer = LocateResult.Found(here, accuracyM = 18.0)
        launchApp()
        openRoasteryPicker("테스트 로스터리")
        assertNull("nothing asked before the tap", shadowOf(compose.activity).lastRequestedPermission)
        tap(button(PlaceSearchTexts.HERE))
        answerPermission(granted = true)
        // one position, the 시·도 opens, the note says how close it is
        waitFor(hasTestTag("locate-note") and hasText(LocateTexts.found(18.0)))
        waitFor(button("← 전국 · 서울특별시"))
        waitForText("📍 서울특별시 성동구")
        assertEquals(1, location.asked)
        assertFalse("a position from the phone has no address", has(hasTestTag("picked-address")))

        // a search now starts from here and says how far each place is
        search.hits = listOf(PlaceFixtures.BUILDING)
        searchNow()
        waitForText("주소 · 현재 위치에서 0 m")
        assertEquals(here, search.asked.single().third)

        // the form gets the point and, for its empty 지역, the area
        tapText("확인")
        waitForText("로스터리 추가")
        waitFor(field("서울특별시 성동구"))
    }

    @Test
    fun currentLocation_refused_saysWhy_andReadsNothing() {
        launchApp()
        openRoasteryPicker("테스트 로스터리")
        tap(button(PlaceSearchTexts.HERE))
        answerPermission(granted = false)
        waitFor(hasTestTag("locate-note") and hasText(LocateTexts.DENIED))
        assertEquals("nothing read without the permission", 0, location.asked)
        assertTrue(has(hasText("아직 위치를 정하지 않았어요.")))
    }

    @Test
    fun currentLocation_alreadyAllowed_noPrompt_andNoFixSaysSo() {
        PlaceSetup.grantLocation(app)
        location.answer = LocateResult.NoFix
        launchApp()
        openRoasteryPicker("테스트 로스터리")
        tap(button(PlaceSearchTexts.HERE))
        waitFor(hasTestTag("locate-note") and hasText(LocateTexts.NO_FIX))
        assertNull("no prompt", shadowOf(compose.activity).lastRequestedPermission)
        location.answer = LocateResult.ServicesOff
        tap(button(PlaceSearchTexts.HERE))
        waitFor(hasTestTag("locate-note") and hasText(LocateTexts.SERVICES_OFF))
        location.supported = false
        tap(button(PlaceSearchTexts.HERE))
        waitFor(hasTestTag("locate-note") and hasText(LocateTexts.UNSUPPORTED))
        assertEquals(2, location.asked)
    }

    @Test
    fun search_saysWhenNothingIsFound_whenItFails_andWhenThePhoneHasNoSearch() {
        launchApp()
        openRoasteryPicker("없는 로스터리")
        val message = { text: String -> hasTestTag("search-message") and hasAnyDescendant(hasText(text)) }
        searchNow()
        waitFor(message(PlaceSearchTexts.noResults("없는 로스터리")))
        search.fail = true
        searchNow()
        waitFor(message(PlaceSearchTexts.FAILED))
        // no phone search and no connection to OpenStreetMap either
        search.supported = false
        AiSetup.http.supported = false
        searchNow()
        waitFor(message(PlaceSearchTexts.UNAVAILABLE + PlaceSearchTexts.UNAVAILABLE_KAKAO))
        replaceIn("없는 로스터리", "")
        searchNow()
        waitFor(message(PlaceSearchTexts.EMPTY_QUERY))
        assertEquals(2, search.asked.size)
    }

    @Test
    fun aNameWithAnArea_withoutKakao_listsTheNameAroundTheArea_andTheTipOpensKakaosHowTo() {
        // the phone knows only the area; OpenStreetMap finds nothing for both words, two cafés for the name alone
        search.hits = listOf(PlaceFixtures.JANGCHUNG)
        AiSetup.http.on("q=${MapLinks.encode("테스트커피 장충")}&") { PhotonFixtures.EMPTY }
            .on("q=${MapLinks.encode("테스트커피")}&") { PhotonFixtures.CAFES }
        launchApp()
        openRoasteryPicker("테스트커피 장충")
        searchNow()
        waitFor(button("테스트커피 약수점"))
        // the nearer café first, each with its distance from the area, then the area itself
        val names = compose.onAllNodes(hasAnyAncestor(hasTestTag("search-results")) and hasClickAction())
            .fetchSemanticsNodes().mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }
        assertEquals(listOf("테스트커피 약수점", "테스트커피 무악점", PlaceFixtures.JANGCHUNG.name), names.filter { it.startsWith("테스트커피") || it == PlaceFixtures.JANGCHUNG.name })
        assertTrue(has(hasText("장충에서", substring = true)))
        assertEquals(PlaceFixtures.JANGCHUNG.point, search.asked.last().third)
        waitFor(hasTestTag("search-source") and hasText(PlaceSearchTexts.source(listOf(PlaceSource.DEVICE, PlaceSource.OSM))))
        assertTrue(has(hasText(PlaceSearchTexts.OSM_CREDIT)))
        assertFalse("only Kakao pages", has(hasTestTag("search-more")))

        // without a Kakao key the tip says what one adds; its how-to opens over the picker and closes back to it
        assertTrue(has(hasTestTag("kakao-tip") and hasAnyDescendant(hasText(PlaceSearchTexts.KAKAO_TIP))))
        tap(button(PlaceSearchTexts.KAKAO_TIP_LINK))
        waitFor(hasTestTag("key-guide"))
        assertTrue(has(hasText(KeyHowTos.KAKAO.title)) && has(hasText(KeyHowTos.KAKAO.sections.first().heading)))
        tap(hasTestTag("key-guide-close"))
        waitGone(hasTestTag("key-guide"))
        tap(button("테스트커피 약수점"))
        waitForText("📍 서울특별시 중구")
    }

    @Test
    fun withKakao_more_addsTheNextPage_untilTheLast() {
        AiSetup.secrets.values[PlaceSearchService.KAKAO_SECRET] = "kakao-rest-key-9f3a"
        AiSetup.http.on("page=2") { KakaoFixtures.PAGE_TWO }.on("keyword.json") { KakaoFixtures.KEYWORD_MORE }.on("address.json") { KakaoFixtures.EMPTY }
        launchApp()
        openRoasteryPicker("테스트커피")
        searchNow()
        waitFor(button("테스트커피 성수"))
        assertFalse("Kakao answered: no tip", has(hasTestTag("kakao-tip")))
        tap(hasTestTag("search-more"))
        waitFor(button("테스트커피 둘째"))
        assertTrue(has(button("테스트커피 성수")))
        assertFalse("the last page", has(hasTestTag("search-more")))
        assertTrue(AiSetup.http.requests.any { "keyword.json" in it.url && "&page=2" in it.url })
        assertTrue(search.asked.isEmpty())
    }

    @Test
    fun kakaoKey_savedMaskedAndChecked_searchesDomesticPlaces_staysOffTheBackup_andClears() {
        SampleData.seed()
        launchApp()
        tap(hasTestTag("open-settings"))
        waitFor(hasTestTag("place-search-settings"))
        val inSection = { m: SemanticsMatcher -> m and hasAnyAncestor(hasTestTag("place-search-settings")) }
        waitForText(PlaceSearchTexts.INTRO)
        assertTrue(has(hasText(PlaceSearchTexts.KAKAO_GUIDE)) && has(hasText(PlaceSearchTexts.KAKAO_PRIVACY)))
        typeInto(PlaceSearchTexts.KAKAO_PLACEHOLDER, " kakao-rest-key-9f3a\n")
        tap(inSection(button(PlaceSearchTexts.SAVE)))
        waitFor(hasTestTag("kakao-key") and hasText("저장됨 …9f3a"))
        assertEquals("kakao-rest-key-9f3a", AiSetup.secrets.values[PlaceSearchService.KAKAO_SECRET])
        assertFalse(has(hasText("kakao-rest-key-9f3a", substring = true)))

        // 키 확인: one keyword search for one place
        AiSetup.http.on("keyword.json") { KakaoFixtures.KEYWORD }.on("address.json") { KakaoFixtures.EMPTY }
        tap(inSection(button(PlaceSearchTexts.CHECK)))
        waitFor(hasTestTag("kakao-key-check") and hasText(KakaoKeyCheck.OK.label))
        assertTrue(AiSetup.http.requests.single().url.contains("size=1"))
        assertEquals("KakaoAK kakao-rest-key-9f3a", AiSetup.http.requests.single().headers["Authorization"])

        // a 국내 search now goes to Kakao, not to the phone
        back()
        tab("tab-calendar")
        tapText("카페")
        tapText("전체 보기")
        waitForText("카페 위치")
        tap(button("지도에서 위치 지정"))
        waitFor(field("FELT 청계천"))
        searchNow()
        waitFor(button("테스트커피 성수"))
        waitFor(hasTestTag("search-source") and hasText(PlaceSearchTexts.source(PlaceSource.KAKAO)))
        assertTrue(search.asked.isEmpty())
        assertTrue(AiSetup.http.requests.any { it.url.contains("keyword.json?query=FELT%20%EC%B2%AD%EA%B3%84%EC%B2%9C") })
        tapText("취소")
        waitForText("카페 위치")

        // never in a backup
        val json = runBlocking { koinGet<BackupService>().export().json }
        assertFalse(json.contains("kakao-rest-key") || json.contains("kakao_local"))

        // 지우기 asks first
        tap(hasTestTag("open-settings"))
        waitFor(hasTestTag("kakao-key"))
        tap(inSection(button(PlaceSearchTexts.CLEAR)))
        waitForText("저장한 카카오 REST API 키를 이 휴대폰에서 지울까요? 국내 검색은 키 없이 찾는 방식으로 돌아가요.")
        clickNode(dialogButton(PlaceSearchTexts.CLEAR))
        waitGone(hasTestTag("kakao-key"))
        waitFor(field(PlaceSearchTexts.KAKAO_PLACEHOLDER))
        assertNull(AiSetup.secrets.values[PlaceSearchService.KAKAO_SECRET])
    }
}

/** SYNTHETIC Kakao Local replies in the documented shapes (2026-09-28); the places and addresses are made up. */
object KakaoFixtures {
    const val KEYWORD = """
    {"meta": {"total_count": 1, "pageable_count": 1, "is_end": true},
     "documents": [
      {"id": "1001", "place_name": "테스트커피 성수", "category_name": "음식점 > 카페 > 커피전문점", "category_group_code": "CE7",
       "category_group_name": "카페", "phone": "", "address_name": "서울 성동구 성수동2가 300-1",
       "road_address_name": "서울 성동구 성수이로7길 51", "x": "127.0557", "y": "37.5446", "place_url": "", "distance": ""}
     ]}
    """
    const val EMPTY = """{"meta": {"total_count": 0, "pageable_count": 0, "is_end": true}, "documents": []}"""

    /** [KEYWORD] with a page after it, and that page. */
    val KEYWORD_MORE = KEYWORD.replace("\"is_end\": true", "\"is_end\": false")
    const val PAGE_TWO = """
    {"meta": {"total_count": 2, "pageable_count": 2, "is_end": true},
     "documents": [
      {"id": "1002", "place_name": "테스트커피 둘째", "category_group_name": "카페", "address_name": "서울 성동구 성수동1가 1",
       "road_address_name": "서울 성동구 왕십리로 1", "x": "127.0443", "y": "37.5447"}
     ]}
    """
}

/** SYNTHETIC Photon (OpenStreetMap) replies in the documented GeoJSON shape (2026-09-29); the cafés are made up. */
object PhotonFixtures {
    /** Two cafés carrying the name: one about 1 km from 장충동, one about 4 km. */
    const val CAFES = """
    {"type": "FeatureCollection", "features": [
      {"type": "Feature", "geometry": {"type": "Point", "coordinates": [126.9606, 37.5738]},
       "properties": {"osm_value": "cafe", "type": "house", "name": "테스트커피 무악점", "street": "통일로12길", "district": "무악동",
        "city": "서울특별시", "country": "대한민국", "countrycode": "KR"}},
      {"type": "Feature", "geometry": {"type": "Point", "coordinates": [127.0110, 37.5545]},
       "properties": {"osm_value": "cafe", "type": "house", "name": "테스트커피 약수점", "street": "다산로", "housenumber": "100",
        "district": "약수동", "city": "서울특별시", "country": "대한민국", "countrycode": "KR"}}
    ]}
    """
    const val EMPTY = """{"type": "FeatureCollection", "features": []}"""
}
