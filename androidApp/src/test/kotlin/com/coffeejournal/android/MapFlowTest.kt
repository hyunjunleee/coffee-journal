package com.coffeejournal.android

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.CafePlaceRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.KoreaRegions
import com.coffeejournal.domain.rules.MapLinks
import com.coffeejournal.ui.map.KoreaFrames
import com.coffeejournal.ui.map.MapViewportMath
import com.coffeejournal.ui.map.WorldProjection
import com.coffeejournal.ui.platform.installUrlOpener
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Design v2 §1: the SGIS Korea map in 원두 › 로스터리, the location picker (roasteries and cafés), the 시·도 drill-down
 * with system back, the café layer and the Naver / Kakao / Google links, all through the real App().
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MapFlowTest : CoverageFlowBase() {
    private val pickerKorea = "위치 지정용 한국 지도"
    private val pickerWorld = "위치 지정용 세계지도"

    /** The app installs this in CoffeeJournalApplication; the test application does not. */
    @Before
    fun installOpener() = installUrlOpener(ApplicationProvider.getApplicationContext<Application>())

    private fun described(text: String) = hasContentDescription(text, substring = true)

    /** Taps the map whose label contains [label] where [lat]/[lng] is drawn (unzoomed, showing [frame]). */
    private fun tapMap(label: String, frame: Rect, x: Float, y: Float) {
        val m = described(label)
        waitFor(m)
        bringIntoView(m)
        val n = node(m).fetchSemanticsNode()
        val size = Size(n.size.width.toFloat(), n.size.height.toFloat())
        val local = MapViewportMath.toCanvas(x, y, frame, MapViewportMath.fitScale(frame, size), Offset(size.width / 2f, size.height / 2f))
        node(m).performTouchInput { click(local) }
        settle()
    }

    private fun tapKorea(label: String, lat: Double, lng: Double, province: String? = null) {
        val frame = if (province == null) KoreaFrames.national else KoreaFrames.provinceFrame(province)!!
        val p = KoreaProjection.toMap(lat, lng)
        tapMap(label, frame, p.x.toFloat(), p.y.toFloat())
    }

    private fun tapWorld(label: String, lat: Double, lng: Double) {
        val p = WorldProjection.toView(lat, lng)
        tapMap(label, WorldProjection.frame, p.x.toFloat(), p.y.toFloat())
    }

    private fun openRoasteries() {
        tab("tab-bean")
        tapText("로스터리")
        waitForText("한국 로스터리 지도")
    }

    private fun lastIntentUrl(): String? {
        val intent = Shadows.shadowOf(context as Application).nextStartedActivity ?: return null
        assertEquals(Intent.ACTION_VIEW, intent.action)
        return intent.dataString
    }

    /** A browser for https links, so a failed nmap:// launch has somewhere to fall back to. */
    private fun installBrowserOnly() {
        val pm = Shadows.shadowOf(context.packageManager)
        val browser = ComponentName("com.example.browser", "com.example.browser.Main")
        pm.addActivityIfNotPresent(browser)
        pm.addIntentFilterForActivity(browser, IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT); addCategory(Intent.CATEGORY_BROWSABLE); addDataScheme("https")
        })
        Shadows.shadowOf(context as Application).checkActivities(true)
    }

    private fun pin(name: String): SemanticsMatcher = described("$name, ") and hasClickAction()

    @Test
    fun roastery_locationSetByTappingTheMap_pinsIt_andOpensTheMapApps() {
        launchApp()
        openRoasteries()
        clickText("+ 추가")
        waitForText("로스터리 추가")
        typeInto("예: 영천카페 듀잇", "테스트 로스터리")
        waitForText("지정하지 않았어요. 지도에는 지역 이름으로 찾은 곳에 표시돼요.")
        tapText("지도에서 위치 지정")
        waitForText("지도에서 위치 지정")
        waitForText("시·도를 눌러 확대한 뒤, 정확한 위치를 누르세요.")
        // national → 서울 → a point in 성동구 (Seongsu-dong)
        tapKorea(pickerKorea, 37.5445, 127.0560)
        waitFor(button("← 전국 · 서울특별시"))
        tapKorea(pickerKorea, 37.5445, 127.0560, province = "11")
        waitForText("📍 서울특별시 성동구")
        tapText("확인")
        // back on the form: the empty 지역 got the area, the position is shown
        waitForText("로스터리 추가")
        waitFor(field("서울특별시 성동구"))
        waitForText("📍 서울특별시 성동구")
        clickText("저장")
        waitForText("한국 로스터리 지도")
        val saved = misc().single { it.name == "테스트 로스터리" }
        assertEquals("서울특별시 성동구", saved.location)
        assertNotNull(saved.point)
        val p = saved.point!!
        assertTrue("tapped within ~150 m: $p", abs(p.lat - 37.5445) < 0.0015 && abs(p.lng - 127.0560) < 0.0015)

        // the pin is on the national map; tapping it opens 서울 and shows the panel with the map-app links
        waitFor(pin("테스트 로스터리"))
        tap(pin("테스트 로스터리"))
        waitFor(button("← 전국 · 서울특별시"))
        waitForText("📍 서울특별시 성동구")
        tapText("${MapLinks.NAVER_LABEL} ↗")
        assertEquals(MapLinks.naver("테스트 로스터리", "서울특별시 성동구", p).uri, lastIntentUrl())
        tapText("${MapLinks.KAKAO_LABEL} ↗")
        assertEquals(MapLinks.kakao("테스트 로스터리", "서울특별시 성동구", p).uri, lastIntentUrl())
        // without the Naver Map app the web map's search opens
        installBrowserOnly()
        tapText("${MapLinks.NAVER_LABEL} ↗")
        assertEquals("https://map.naver.com/p/search/" + MapLinks.encode("테스트 로스터리 서울특별시 성동구"), lastIntentUrl())

        // editing again: clearing the position takes it off (the location text stays and places it by 시·군·구)
        tap(button("수정") , 0)
        waitForText("로스터리 수정")
        tapText("위치 지우기")
        waitForText("지정하지 않았어요. 지도에는 지역 이름으로 찾은 곳에 표시돼요.")
        clickText("수정 저장")
        waitForText("한국 로스터리 지도")
        waitUntil("position cleared") { misc().single { it.name == "테스트 로스터리" }.point == null }
        waitFor(pin("테스트 로스터리"))
    }

    @Test
    fun koreaMap_drillsIntoAProvince_andBackReturnsToTheNationalMap() {
        runBlocking {
            koinGet<MiscRepository>().upsertAll(listOf(
                com.coffeejournal.domain.model.MiscItem(id = "k1", type = MiscType.SOURCE, name = "강릉 로스터리", scope = Scope.DOMESTIC, location = "강원 강릉시", createdAt = 1),
                com.coffeejournal.domain.model.MiscItem(id = "k2", type = MiscType.SOURCE, name = "속초 로스터리", scope = Scope.DOMESTIC, location = "", lat = 38.2070, lng = 128.5918, createdAt = 2),
            ))
        }
        launchApp()
        openRoasteries()
        val map = "한국 로스터리 지도. 로스터리 2곳 표시"
        waitFor(described(map))
        // nationally the two 강원 roasteries share one chip
        val gangwon = described("강원특별자치도에 2곳. 누르면 확대돼요.") and hasClickAction()
        waitFor(gangwon)
        assertTrue(!has(pin("강릉 로스터리")))
        // tap 충청북도 (no pins there): its 시·군·구 map opens
        tapKorea(map, 36.8, 127.7)
        waitFor(button("← 전국 · 충청북도"))
        waitForText("뒤로 가기나 ‘← 전국’을 누르면 전국 지도로 돌아가요.")
        // system back returns to the national map, not out of the tab
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        waitGone(button("← 전국 · 충청북도"))
        waitForText("한국 로스터리 지도")
        // the 강원 chip opens 강원, where each roastery has its own pin; the button leads back as well
        tap(gangwon)
        waitFor(button("← 전국 · 강원특별자치도"))
        waitFor(pin("강릉 로스터리"))
        waitFor(pin("속초 로스터리"))
        tap(button("← 전국 · 강원특별자치도"))
        waitGone(button("← 전국 · 강원특별자치도"))
        // 해외: the world map
        tapText("해외")
        waitForText("해외 로스터리 지도")
        waitFor(described("해외 로스터리 지도. 표시된 로스터리가 없어요"))
    }

    @Test
    fun overseasRoastery_pickedOnTheWorldMap_getsTheCountryAndGoogleMaps() {
        launchApp()
        openRoasteries()
        clickText("+ 추가")
        waitForText("로스터리 추가")
        typeInto("예: 영천카페 듀잇", "Kyoto Roaster")
        clickText("해외")
        tapText("지도에서 위치 지정")
        waitForText("세계지도에서 로스터리가 있는 곳을 누르세요.", substring = true)
        tapWorld(pickerWorld, 35.5, 138.0)
        waitForText("📍 일본")
        tapText("확인")
        waitFor(field("일본"))
        clickText("저장")
        waitForText("한국 로스터리 지도")
        tapText("해외")
        waitFor(pin("Kyoto Roaster"))
        tap(pin("Kyoto Roaster"))
        val saved = misc().single { it.name == "Kyoto Roaster" }
        tapText("${MapLinks.GOOGLE_LABEL} ↗")
        assertEquals(MapLinks.google("Kyoto Roaster", "일본", saved.point).uri, lastIntentUrl())
        assertTrue(lastIntentUrl() == null)
    }

    @Test
    fun cafe_locationFromTheRecordAndTheCalendar_showsOnTheCafeMap() {
        SampleData.seed()
        launchApp()
        // record detail of the café visit (e4, FELT 청계천)
        tab("tab-calendar")
        tapText("카페")
        tapText("전체 보기")
        waitForText("카페 위치")
        waitFor(hasText("위치 미지정", substring = true))
        tap(button("지도에서 위치 지정"))
        waitForText("FELT 청계천")
        waitForText("시·도를 눌러 확대한 뒤, 정확한 위치를 누르세요.")
        tapKorea(pickerKorea, 37.5690, 126.9780)
        waitFor(button("← 전국 · 서울특별시"))
        // 청계천 at 을지로 (중구 side)
        tapKorea(pickerKorea, 37.5663, 126.9910, province = "11")
        waitForText("📍 서울특별시 중구")
        tapText("저장")
        waitUntil("café saved") { runBlocking { koinGet<CafePlaceRepository>().get("FELT 청계천")?.point } != null }
        waitForText("📍 서울특별시 중구", substring = true)
        val place = runBlocking { koinGet<CafePlaceRepository>().get("felt 청계천") }!!
        assertEquals("서울특별시 중구", KoreaRegions.locate(place.lat!!, place.lng!!)?.label)

        // 원두 › 로스터리 › 방문 카페 지도: the café with its visit count; its visit opens the record
        tab("tab-bean")
        tapText("로스터리")
        tapText("방문 카페 지도")
        waitForText("방문 카페 지도", substring = false)
        val cafePin = described("FELT 청계천, 방문 1회") and hasClickAction()
        waitFor(cafePin)
        tap(cafePin)
        waitForText("방문 1회")
        tap(button("브라질 세하도 내추럴") , 0)
        waitUntil("record detail") { onDetailOf("브라질 세하도 내추럴") }
        // the detail shows where the café is and can change it
        waitForText("카페 위치")
        waitForText("📍 서울특별시 중구", substring = true)
        tap(button("위치 변경"))
        waitForText("정확한 위치를 누르세요.", substring = true)
        tapText("위치 지우기")
        waitForText("아직 위치를 정하지 않았어요.")
        tapText("저장")
        waitUntil("café position removed") { runBlocking { koinGet<CafePlaceRepository>().get("FELT 청계천") } == null }
        waitForText("위치 미지정")
        assertNull(runBlocking { koinGet<CafePlaceRepository>().get("FELT 청계천") })
    }

    @Test
    fun picker_startsOnTheProvinceOfAnExistingPosition() {
        runBlocking {
            koinGet<MiscRepository>().upsert(
                com.coffeejournal.domain.model.MiscItem(id = "b1", type = MiscType.SOURCE, name = "모모스", scope = Scope.DOMESTIC, location = "부산", lat = 35.2270, lng = 129.0880, createdAt = 1)
            )
        }
        launchApp()
        openRoasteries()
        tap(button("수정"), 0)
        waitForText("로스터리 수정")
        waitForText("📍 부산광역시 금정구")
        tapText("지도에서 위치 변경")
        waitFor(button("← 전국 · 부산광역시"))
        waitForText("📍 부산광역시 금정구")
        tapText("취소")
        waitForText("로스터리 수정")
        assertEquals(GeoPoint(35.2270, 129.0880), misc().single { it.id == "b1" }.point)
    }
}
