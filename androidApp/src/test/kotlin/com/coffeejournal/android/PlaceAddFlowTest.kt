package com.coffeejournal.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.CafePlaceRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.CafePlace
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.KoreaRegions
import com.coffeejournal.ui.map.KoreaFrames
import com.coffeejournal.ui.map.MapViewportMath
import com.coffeejournal.ui.map.detail.OpenFreeMap
import com.coffeejournal.ui.theme.LeaveTexts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "+ 카페 추가" and "+ 로스터리 추가" where cafés and roasteries are shown (the calendar's café list, the café map, the
 * detail map, the roastery map card and its notes, the record form), through the real App(). Every list and map reads
 * the same Room flows, so what is added in one place is in all the others at once, without leaving or restarting.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class PlaceAddFlowTest : CoverageFlowBase() {
    private val cafeName = "예: OO카페 (서울 성수동)"
    private val roasteryName = "예: 영천카페 듀잇"
    private val roasteryArea = "예: 대한민국 서울특별시"
    private val pickerKorea = "위치 지정용 한국 지도"

    private fun described(text: String) = hasContentDescription(text, substring = true)
    private fun pin(name: String): SemanticsMatcher = described("$name, ") and hasClickAction()
    private fun card(name: String) = hasText(name) and !hasClickAction()
    private fun cafes(): List<CafePlace> = runBlocking { koinGet<CafePlaceRepository>().getAll() }

    /** Taps the Korea map labelled [label] where [lat]/[lng] is drawn (unzoomed, national or [province]). */
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

    /** In the picker: 서울 on the national map, then [lat]/[lng] on the 서울 map, then 저장 / 확인. */
    private fun pickInSeoul(lat: Double, lng: Double, area: String) {
        waitForText("시·도를 눌러 확대한 뒤, 정확한 위치를 누르세요.")
        tapKorea(pickerKorea, 37.5690, 126.9780)
        waitFor(button("← 전국 · 서울특별시"))
        tapKorea(pickerKorea, lat, lng, province = "11")
        waitForText("📍 $area")
    }

    private fun onDetailMap() {
        waitFor(described("상세 지도. "))
        waitForText(OpenFreeMap.ATTRIBUTION)
    }

    private fun openCafeMap() {
        tab("tab-bean")
        tapText("로스터리")
        tapText("카페 지도")
        waitForText("카페 지도", substring = false)
    }

    @Test
    fun cafeRepository_addKeepsOneRowPerName_clearKeepsTheRow_deleteRemovesIt() = runBlocking {
        val repo = koinGet<CafePlaceRepository>()
        repo.add(" 가 볼 카페 ")
        repo.add("가 볼 카페")
        repo.add("  ")
        assertEquals(listOf("가 볼 카페" to null), repo.getAll().map { it.name to it.point })
        // a position set under another spelling lands on the same row, which keeps its name
        repo.set("가 볼 카페 ", GeoPoint(37.5446, 127.0557))
        repo.add("가 볼 카페")
        assertEquals(listOf("가 볼 카페" to GeoPoint(37.5446, 127.0557)), repo.getAll().map { it.name to it.point })
        repo.clear("가 볼 카페")
        assertEquals(listOf("가 볼 카페" to null), repo.getAll().map { it.name to it.point })
        repo.delete("가 볼 카페 ")
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun cafe_addedFromTheCalendar_isOnTheCafeMapAndTheDetailMap_andOneAddedThereIsBackEverywhere() {
        val fake = installFakeDetailMap()
        SampleData.seed()
        launchApp()
        tab("tab-calendar")
        tapText("카페")
        tapText("전체 보기")
        waitForText("카페 위치")
        tapText("+ 카페 추가")
        waitForText("카페 추가")
        // a café there already is (any spelling) is not made twice: the screen says which one it is
        typeInto(cafeName, "felt 청계천")
        waitForText("이미 있는 카페예요: FELT 청계천 · 방문 1회 · 위치 미지정")
        assertTrue("no second café without a position", !has(button("위치 없이 저장")))
        replaceIn("felt 청계천", "테스트 카페 성수")
        waitFor(button("위치 없이 저장"))
        tapText("지도에서 위치 지정")
        waitForText("테스트 카페 성수")
        pickInSeoul(37.5445, 127.0560, "서울특별시 성동구")
        tapText("저장")
        // back where it was added (the name screen made way for the picker): the calendar's list has it
        waitForText("방문 기록 없음 · 📍 서울특별시 성동구")
        assertTrue(!has(hasText("카페 추가")))
        assertEquals(listOf("테스트 카페 성수"), cafes().map { it.name })
        assertNotNull(cafes().single().point)

        // 원두 › 로스터리 › 카페 지도: its pin (no visit count), the visited café without a position in the box below
        openCafeMap()
        val added = described("테스트 카페 성수, 방문 기록 없음") and hasClickAction()
        waitFor(added)
        waitForText("위치 미지정 1곳")
        tap(added)
        waitFor(button("← 전국 · 서울특별시"))
        waitForText("방문 기록 없음")
        // the detail map: the same café as a pin and in the list under the map
        tapText("상세 지도에서 보기")
        onDetailMap()
        assertEquals(listOf("테스트 카페 성수"), fake.pins.map { it.key })
        tapText("선택 해제")
        waitForText("지도에 표시한 카페")
        waitFor(button("테스트 카페 성수"))
        waitForText("위치 미지정 1곳")

        // a café added on the detail map, without a position: listed there at once, and on the café map after back
        tapText("+ 카페 추가")
        waitForText("카페 추가")
        typeInto(cafeName, "가 볼 카페 연남")
        tapText("위치 없이 저장")
        onDetailMap()
        waitForText("위치 미지정 2곳")
        waitForText("가 볼 카페 연남")
        back()
        waitForText("위치 미지정 2곳")
        waitForText("가 볼 카페 연남")
        // and in the calendar's list
        tab("tab-calendar")
        if (!has(hasText("카페 위치"))) tapText("카페")
        waitForText("가 볼 카페 연남")
        waitForText("방문 기록 없음 · 위치 미지정")
        assertEquals(setOf("테스트 카페 성수", "가 볼 카페 연남"), cafes().map { it.name }.toSet())
    }

    @Test
    fun cafeAddedByHand_staysWhenItsPositionIsCleared_andOnlyItCanBeDeleted() {
        SampleData.seed()
        launchApp()
        tab("tab-calendar")
        tapText("카페")
        tapText("+ 카페 추가")
        typeInto(cafeName, "가 볼 카페 연남")
        tapText("위치 없이 저장")
        waitForText("가 볼 카페 연남")
        waitForText("방문 기록 없음 · 위치 미지정")
        assertEquals(listOf(CafePlace("가 볼 카페 연남", null, null, cafes().single().createdAt)), cafes())
        // adding the same café again (another spelling) makes no second one
        tapText("+ 카페 추가")
        typeInto(cafeName, " 가 볼 카페 연남 ")
        waitForText("이미 있는 카페예요: 가 볼 카페 연남 · 방문 기록 없음 · 위치 미지정")
        // a typed name is not thrown away without asking
        tapText("취소")
        waitForText(LeaveTexts.DISCARD_TITLE)
        clickNode(dialogButton(LeaveTexts.LEAVE))
        waitForText("카페 위치")
        assertEquals(1, cafes().size)

        // a position, then 위치 지우기: the café keeps its place in the list
        val row = hasText("가 볼 카페 연남")
        waitFor(row)
        tap(button("지도에서 위치 지정"), count(button("지도에서 위치 지정")) - 1)
        waitForText("가 볼 카페 연남")
        pickInSeoul(37.5620, 126.9230, "서울특별시 마포구")
        tapText("저장")
        waitForText("방문 기록 없음 · 📍 서울특별시 마포구")
        tap(button("위치 변경"), count(button("위치 변경")) - 1)
        waitForText("📍 서울특별시 마포구")
        tapText("위치 지우기")
        waitForText("아직 위치를 정하지 않았어요.")
        tapText("저장")
        waitForText("방문 기록 없음 · 위치 미지정")
        assertNull(cafes().single().point)

        // FELT 청계천 has a visit: no 삭제; the café added by hand has one, confirmed, and it is gone everywhere
        assertEquals(1, count(button("삭제")))
        tapText("삭제")
        waitForText("'가 볼 카페 연남'을(를) 삭제할까요? 방문 기록이 없는 카페라 목록과 지도에서 사라져요.")
        clickNode(dialogButton("삭제"))
        waitGone(row)
        assertTrue(cafes().isEmpty())
        openCafeMap()
        waitForText("위치 미지정 1곳")
        assertTrue(!has(hasText("가 볼 카페 연남")))
    }

    @Test
    fun roastery_fromTheNoteAndTheOverseasMapCard_startsOnItsTab_andIsListedAtOnce() {
        runBlocking {
            koinGet<MiscRepository>().upsert(MiscItem(id = "u1", type = MiscType.SOURCE, name = "위치 모를 로스터리", scope = Scope.DOMESTIC, createdAt = 1))
        }
        launchApp()
        tab("tab-bean")
        tapText("로스터리")
        waitForText("한국 로스터리 지도")
        // a roastery the map cannot place: its form opens from the note, and the 지역 typed there pins it
        waitForText("위치 미입력 1곳")
        tapText("위치 입력")
        waitForText("로스터리 수정")
        typeInto(roasteryArea, "부산 금정구")
        clickText("수정 저장")
        waitForText("한국 로스터리 지도")
        waitGone(hasText("위치 미입력 1곳"))
        waitFor(pin("위치 모를 로스터리"))
        // 해외: "+ 로스터리 추가" of that map starts the form on 해외
        tapText("해외")
        waitForText("해외 로스터리 지도")
        clickText("+ 로스터리 추가")
        waitForText("로스터리 추가")
        assertTrue("해외 chosen", isSelected(button("해외")))
        typeInto(roasteryName, "Kyoto Test Roaster")
        typeInto(roasteryArea, "일본")
        clickText("저장")
        waitForText("해외 로스터리 지도")
        waitFor(pin("Kyoto Test Roaster"))
        waitFor(card("Kyoto Test Roaster"))
        assertEquals(Scope.OVERSEAS, misc().single { it.name == "Kyoto Test Roaster" }.scope)
        // 한국: its own "+ 로스터리 추가" starts on 국내
        tapText("한국")
        waitForText("한국 로스터리 지도")
        clickText("+ 로스터리 추가")
        waitForText("로스터리 추가")
        assertTrue("국내 chosen", isSelected(button("국내")))
    }

    @Test
    fun roastery_addedOnTheDetailMap_isPinnedThere_andListedInTheRoasteryView() {
        val fake = installFakeDetailMap()
        launchApp()
        tab("tab-bean")
        tapText("로스터리")
        tapKorea("한국 로스터리 지도", 37.5690, 126.9780)
        waitFor(button("← 전국 · 서울특별시"))
        tapText("상세 지도")
        onDetailMap()
        waitForText("상세 지도에 표시할 로스터리가 없어요. 아래 ‘+ 로스터리 추가’로 등록하고 위치를 정하면 바로 표시돼요.")
        tapText("+ 로스터리 추가")
        waitForText("로스터리 추가")
        assertTrue("국내 chosen", isSelected(button("국내")))
        typeInto(roasteryName, "성수 테스트 로스터스")
        typeInto(roasteryArea, "서울 성동구")
        clickText("저장")
        onDetailMap()
        waitForText("지도에 표시한 로스터리")
        waitFor(button("성수 테스트 로스터스"))
        assertEquals(listOf("성수 테스트 로스터스"), fake.pins.map { it.key })
        back()
        waitFor(pin("성수 테스트 로스터스"))
        waitFor(card("성수 테스트 로스터스"))
    }

    @Test
    fun cafeRecordForm_setsTheCafePositionRightThere_andANewRoasteryIsFlagged() {
        SampleData.seed()
        launchApp()
        tab("tab-calendar")
        clickText("카페")
        clickText("+ 카페 기록 추가")
        waitForText("새 기록")
        assertTrue("no 위치 지정 without a name", !has(button("위치 지정")))
        typeInto(cafeName, "연남 테스트 카페")
        waitForText("카페 위치 미지정")
        tapText("위치 지정")
        waitForText("연남 테스트 카페")
        pickInSeoul(37.5620, 126.9230, "서울특별시 마포구")
        tapText("저장")
        // back in the form with what was typed; the café is placed already, before the record is saved
        waitForText("새 기록")
        waitForText("📍 서울특별시 마포구")
        waitFor(button("위치 변경"))
        waitFor(field("연남 테스트 카페"))
        val placed = runBlocking { koinGet<CafePlaceRepository>().get("연남 테스트 카페") }?.point
        assertEquals("서울특별시 마포구", placed?.let { KoreaRegions.locate(it.lat, it.lng)?.label })

        // 로스터리: a new one says the save adds it to 로스터리; a registered one (any spelling) does not
        val newHint = hasText("새 로스터리예요. 저장하면 로스터리 목록에도 추가돼요.")
        typeInto("예: 커피정경", "처음 보는 로스터스")
        waitFor(newHint)
        replaceIn("처음 보는 로스터스", "커피리브레")
        waitGone(newHint)
        replaceIn("커피리브레", "처음 보는 로스터스")
        waitFor(newHint)
        typeInto(namePlaceholder, "연남 라떼 테스트")
        clickText("저장")
        waitUntil("cafe detail") { onDetailOf("연남 라떼 테스트") }
        waitForText("📍 서울특별시 마포구", substring = true)
        waitUntil("roastery registered") { misc().any { it.type == MiscType.SOURCE && it.name == "처음 보는 로스터스" } }
    }
}
