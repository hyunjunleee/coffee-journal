package com.coffeejournal.android

import android.app.Application
import android.content.Intent
import androidx.compose.ui.geometry.Offset
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
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.MapLinks
import com.coffeejournal.ui.map.KoreaFrames
import com.coffeejournal.ui.map.MapViewportMath
import com.coffeejournal.ui.map.detail.DetailCamera
import com.coffeejournal.ui.map.detail.DetailMapCamera
import com.coffeejournal.ui.map.detail.DetailMapStyle
import com.coffeejournal.ui.map.detail.OpenFreeMap
import com.coffeejournal.ui.map.detail.PinPrecision
import com.coffeejournal.ui.platform.installUrlOpener
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * feature-plan-v2 §1.7: the detail map (OpenStreetMap) opened from the SGIS Korea map, the roastery and café panels
 * and the location picker, through the real App(). MapLibre's native renderer does not run on the JVM, so a
 * [FakeDetailMapRenderer] takes its place; the last test checks that the real renderer falls back cleanly instead.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class DetailMapFlowTest : CoverageFlowBase() {
    @Before
    fun installOpener() = installUrlOpener(ApplicationProvider.getApplicationContext<Application>())

    private fun described(text: String) = hasContentDescription(text, substring = true)
    private fun pin(name: String): SemanticsMatcher = described("$name, ") and hasClickAction()
    private fun mapPin(label: String): SemanticsMatcher = hasContentDescription("지도 핀 $label") and hasClickAction()

    private fun seedRoasteries() = runBlocking {
        koinGet<MiscRepository>().upsertAll(listOf(
            MiscItem(id = "d1", type = MiscType.SOURCE, name = "커피 리브레", scope = Scope.DOMESTIC, location = "서울 성동구", createdAt = 1, lat = 37.5446, lng = 127.0557),
            MiscItem(id = "d2", type = MiscType.SOURCE, name = "프릳츠 도화", scope = Scope.DOMESTIC, location = "서울 마포구", createdAt = 2),
            MiscItem(id = "d3", type = MiscType.SOURCE, name = "모모스", scope = Scope.DOMESTIC, location = "부산 금정구", createdAt = 3, lat = 35.2270, lng = 129.0880),
        ))
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

    /** Taps the Korea map (label [label]) where [lat]/[lng] is drawn, unzoomed. */
    private fun tapKorea(label: String, lat: Double, lng: Double, province: String? = null) {
        val m = described(label)
        waitFor(m)
        bringIntoView(m)
        val n = node(m).fetchSemanticsNode()
        val size = Size(n.size.width.toFloat(), n.size.height.toFloat())
        val frame = if (province == null) KoreaFrames.national else KoreaFrames.provinceFrame(province)!!
        val p = KoreaProjection.toMap(lat, lng)
        val local = MapViewportMath.toCanvas(p.x.toFloat(), p.y.toFloat(), frame, MapViewportMath.fitScale(frame, size), Offset(size.width / 2f, size.height / 2f))
        node(m).performTouchInput { click(local) }
        settle()
    }

    private fun onDetailMap() {
        waitFor(described("상세 지도. "))
        waitForText(OpenFreeMap.ATTRIBUTION)
    }

    @Test
    fun koreaMap_provinceView_opensTheDetailMapThere_andAPinShowsItsPanelWithTheMapApps() {
        val fake = installFakeDetailMap()
        seedRoasteries()
        launchApp()
        openRoasteries()
        // nationally there is no 상세 지도 button; the 서울 chip opens 서울, where it appears
        assertTrue(!has(button("상세 지도")))
        tap(described("서울특별시에 2곳") and hasClickAction())
        waitFor(button("← 전국 · 서울특별시"))
        tapText("상세 지도")
        onDetailMap()
        waitForText("상세 지도")
        // it opened on what the 서울 map showed, with the SGIS map's roasteries as pins (and the archive style)
        val start = fake.starts.single()
        assertTrue("start $start", start.lat in 37.40..37.72 && start.lng in 126.75..127.22 && start.zoom in 8.0..12.5)
        assertEquals(DetailMapStyle.json, fake.style)
        assertEquals(setOf("커피 리브레", "프릳츠 도화", "모모스"), fake.pins.map { it.key }.toSet())
        assertEquals(PinPrecision.EXACT, fake.pins.single { it.key == "커피 리브레" }.precision)
        assertEquals(PinPrecision.DISTRICT, fake.pins.single { it.key == "프릳츠 도화" }.precision)
        // the list under the map, then a pin on the map: the roastery's panel with Naver / Kakao
        waitForText("지도에 표시한 로스터리")
        tap(mapPin("커피 리브레"))
        waitForText("서울 성동구")
        waitForText("📍 서울특별시 성동구")
        waitForText("연결된 원두 기록이 없어요.")
        tapText("${MapLinks.NAVER_LABEL} ↗")
        assertEquals(MapLinks.naver("커피 리브레", "서울 성동구", GeoPoint(37.5446, 127.0557)).uri, lastIntentUrl())
        tapText("${MapLinks.KAKAO_LABEL} ↗")
        assertEquals(MapLinks.kakao("커피 리브레", "서울 성동구", GeoPoint(37.5446, 127.0557)).uri, lastIntentUrl())
        assertEquals("커피 리브레", fake.selected)
        // 선택 해제, then a pin from the list moves the camera to it
        tapText("선택 해제")
        waitForText("지도에 표시한 로스터리")
        tapText("모모스")
        waitForText("부산 금정구")
        waitUntil("moved to 모모스") { fake.controller!!.camera == DetailCamera(35.2270, 129.0880, PinPrecision.EXACT.zoom) }
        // the attribution opens OpenStreetMap's copyright page
        tap(hasText(OpenFreeMap.ATTRIBUTION) and hasClickAction())
        assertEquals(OpenFreeMap.OSM_COPYRIGHT_URL, lastIntentUrl())
        // back: the SGIS map, still on 서울
        back()
        waitFor(button("← 전국 · 서울특별시"))
    }

    @Test
    fun roasteryPanel_opensTheDetailMapOnThePin_andCafePanelLikewise() {
        val fake = installFakeDetailMap()
        seedRoasteries()
        runBlocking {
            koinGet<EntryRepository>().upsert(Entry(id = "c1", createdAt = 5, category = Category.CAFE, name = "브라질 세하도 내추럴", cafeName = "FELT 청계천"))
            koinGet<CafePlaceRepository>().set("FELT 청계천", GeoPoint(37.5663, 126.9910))
        }
        launchApp()
        openRoasteries()
        tap(described("서울특별시에 2곳") and hasClickAction())
        tap(pin("커피 리브레"))
        tapText("상세 지도에서 보기")
        onDetailMap()
        // centred on the roastery at street level, already selected
        assertEquals(DetailCamera(37.5446, 127.0557, PinPrecision.EXACT.zoom), fake.starts.single())
        waitForText("📍 서울특별시 성동구")
        waitFor(button("선택 해제"))
        assertEquals("커피 리브레", fake.selected)
        back()
        // the café map: the café's panel has the button too, and the detail map shows cafés
        tapText("방문 카페 지도")
        val cafePin = described("FELT 청계천, 방문 1회") and hasClickAction()
        tap(cafePin)
        tapText("상세 지도에서 보기")
        onDetailMap()
        waitForText("방문 1회")
        assertEquals(listOf("felt 청계천"), fake.pins.map { it.key })
        assertEquals(DetailCamera(37.5663, 126.9910, PinPrecision.EXACT.zoom), fake.starts.last())
        tap(button("브라질 세하도 내추럴"))
        waitUntil("record detail") { onDetailOf("브라질 세하도 내추럴") }
    }

    @Test
    fun offline_saysSo_andLeadsBackToTheKoreaMap_retryOnceOnline() {
        val fake = installFakeDetailMap(FakeDetailMapRenderer(online = false))
        seedRoasteries()
        launchApp()
        openRoasteries()
        tap(described("서울특별시에 2곳") and hasClickAction())
        tapText("상세 지도")
        waitForText("인터넷에 연결되어 있지 않아요")
        waitForText("한국 지도(시·도 · 시·군·구)는 인터넷 없이도 그대로 볼 수 있어요.")
        assertTrue("no map without network", fake.starts.isEmpty())
        // still offline: 다시 시도 keeps the notice
        tapText("다시 시도")
        waitForText("인터넷에 연결되어 있지 않아요")
        assertTrue(fake.starts.isEmpty())
        // back on the SGIS map, which works offline
        tapText("← 한국 지도로 돌아가기")
        waitFor(button("← 전국 · 서울특별시"))
        waitFor(pin("커피 리브레"))
        // online again: 다시 시도 shows the map
        tapText("상세 지도")
        waitForText("인터넷에 연결되어 있지 않아요")
        fake.online = true
        tapText("다시 시도")
        onDetailMap()
        assertEquals(1, fake.starts.size)
    }

    @Test
    fun rendererFailure_andSlowLoading_areShown() {
        installFakeDetailMap(FakeDetailMapRenderer(failWith = "style"))
        seedRoasteries()
        launchApp()
        openRoasteries()
        tap(described("서울특별시에 2곳") and hasClickAction())
        tapText("상세 지도")
        waitForText("상세 지도를 표시하지 못했어요")
        waitFor(button("← 한국 지도로 돌아가기"))
        back()
        waitFor(button("← 전국 · 서울특별시"))
        // a map that does not finish loading: after a while the notice appears over it, with the way back
        installFakeDetailMap(FakeDetailMapRenderer(loads = false, slowAfterMs = 300))
        tapText("상세 지도")
        waitForText("지도를 불러오지 못하고 있어요. 인터넷 연결을 확인해 주세요.")
        waitFor(described("상세 지도. "))
        tapText("← 한국 지도로 돌아가기")
        waitFor(button("← 전국 · 서울특별시"))
    }

    @Test
    fun picker_crosshairOfTheDetailMap_setsTheExactPoint_andTheFormFillsTheArea() {
        val fake = installFakeDetailMap()
        launchApp()
        openRoasteries()
        clickText("+ 추가")
        waitForText("로스터리 추가")
        typeInto("예: 영천카페 듀잇", "테스트 로스터리")
        tapText("지도에서 위치 지정")
        waitForText("시·도를 눌러 확대한 뒤, 정확한 위치를 누르세요.")
        // the SGIS tap-to-place is unchanged; the detail map starts on the 시·도 shown
        tapKorea("위치 지정용 한국 지도", 37.5445, 127.0560)
        waitFor(button("← 전국 · 서울특별시"))
        tapText("상세 지도에서 정확히")
        waitForText("상세 지도에서 위치 지정")
        waitFor(described("가운데 십자"))
        val start = fake.starts.single()
        assertTrue("start $start", start.lat in 37.40..37.72 && start.lng in 126.75..127.22)
        waitForText("테스트 로스터리")
        // pan so the crosshair is on the roastery's door
        compose.runOnUiThread { fake.pan(DetailCamera(37.54472, 127.05583, 17.2)) }
        waitForText("📍 서울특별시 성동구")
        waitForText("37.5447, 127.0558")
        tapText("이 위치로 지정")
        // back in the picker: the point is set (and shown on the SGIS map of its 시·도)
        waitForText("지도에서 위치 지정")
        waitForText("📍 서울특별시 성동구")
        waitFor(button("← 전국 · 서울특별시"))
        tapText("확인")
        waitForText("로스터리 추가")
        waitFor(field("서울특별시 성동구"))
        clickText("저장")
        waitForText("한국 로스터리 지도")
        val saved = misc().single { it.name == "테스트 로스터리" }
        assertEquals("서울특별시 성동구", saved.location)
        assertEquals(GeoPoint(37.54472, 127.05583), saved.point)
    }

    @Test
    fun picker_cancelInTheDetailMap_keepsThePoint() {
        installFakeDetailMap()
        runBlocking {
            koinGet<MiscRepository>().upsert(MiscItem(id = "b1", type = MiscType.SOURCE, name = "모모스", scope = Scope.DOMESTIC, location = "부산", lat = 35.2270, lng = 129.0880, createdAt = 1))
        }
        launchApp()
        openRoasteries()
        tap(button("수정"), 0)
        waitForText("로스터리 수정")
        tapText("지도에서 위치 변경")
        waitFor(button("← 전국 · 부산광역시"))
        tapText("상세 지도에서 정확히")
        waitForText("상세 지도에서 위치 지정")
        waitForText("📍 부산광역시 금정구")
        tapText("취소")
        waitFor(button("← 전국 · 부산광역시"))
        waitForText("📍 부산광역시 금정구")
        tapText("취소")
        waitForText("로스터리 수정")
        assertEquals(GeoPoint(35.2270, 129.0880), misc().single { it.id == "b1" }.point)
    }

    /** Without a fake: MapLibre's native library cannot load on the JVM, and the screen says so instead of crashing. */
    @Test
    fun realRenderer_withoutItsNativeLibrary_fallsBack() {
        seedRoasteries()
        launchApp()
        openRoasteries()
        tap(described("서울특별시에 2곳") and hasClickAction())
        tapText("상세 지도")
        waitUntil("fallback") { has(hasText("상세 지도를 표시하지 못했어요")) || has(hasText("인터넷에 연결되어 있지 않아요")) }
        waitFor(button("← 한국 지도로 돌아가기"))
        tapText("← 한국 지도로 돌아가기")
        waitFor(button("← 전국 · 서울특별시"))
        assertTrue(DetailMapCamera.MAX_ZOOM > DetailMapCamera.FIT_MAX_ZOOM)
    }
}
