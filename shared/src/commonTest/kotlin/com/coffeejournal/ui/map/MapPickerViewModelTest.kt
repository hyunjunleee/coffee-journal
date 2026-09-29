package com.coffeejournal.ui.map

import com.coffeejournal.data.db.CafePlaceDao
import com.coffeejournal.data.db.CafePlaceEntity
import com.coffeejournal.data.repo.CafePlaceRepository
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.ui.ai.FakeAiHttp
import com.coffeejournal.ui.ai.MemorySecretStore
import com.coffeejournal.ui.map.search.CurrentLocation
import com.coffeejournal.ui.map.search.FakeDevicePlaceSearch
import com.coffeejournal.ui.map.search.KakaoReplies
import com.coffeejournal.ui.map.search.LocateResult
import com.coffeejournal.ui.map.search.LocateTexts
import com.coffeejournal.ui.map.search.PlaceHit
import com.coffeejournal.ui.map.search.PlaceSearchService
import com.coffeejournal.ui.map.search.PlaceSearchTexts
import com.coffeejournal.ui.map.search.PlaceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryCafePlaceDao : CafePlaceDao {
    val rows = MutableStateFlow<Map<String, CafePlaceEntity>>(emptyMap())
    override fun observeAll(): Flow<List<CafePlaceEntity>> = rows.map { it.values.sortedBy { r -> r.name } }
    override suspend fun getAll(): List<CafePlaceEntity> = rows.value.values.sortedBy { it.name }
    override suspend fun upsert(item: CafePlaceEntity) { rows.value = rows.value + (item.name to item) }
    override suspend fun upsertAll(items: List<CafePlaceEntity>) = items.forEach { upsert(it) }
    override suspend fun delete(name: String) { rows.value = rows.value - name }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

/** A phone position that answers [answer] (or never, with [hang]) and says whether its services are on. */
class FakeLocation(
    override var supported: Boolean = true,
    var servicesOn: Boolean = true,
    var answer: LocateResult = LocateResult.NoFix,
    var hang: Boolean = false,
) : CurrentLocation {
    var asked = 0
    override suspend fun servicesOn(): Boolean = servicesOn
    override suspend fun locate(): LocateResult {
        asked++
        if (hang) awaitCancellation()
        return answer
    }
}

/** The picker's search, "현재 위치" and what it saves or hands back, against fake searches and a fake position. */
@OptIn(ExperimentalCoroutinesApi::class)
class MapPickerViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val seongsu = PlaceHit("테스트커피 성수", "서울 성동구 성수이로7길 51", GeoPoint(37.5446, 127.0557), "카페")
    private val device = FakeDevicePlaceSearch(hits = listOf(seongsu))
    private val http = FakeAiHttp()
    private val secrets = MemorySecretStore()
    private val location = FakeLocation()
    private val dao = MemoryCafePlaceDao()
    private val cafes = CafePlaceRepository(dao)

    private fun picker(target: String = MapPickTarget.CAFE, name: String = "테스트커피", scope: String = Scope.DOMESTIC, initial: GeoPoint? = null) =
        MapPickerViewModel(target, name, scope, initial, cafes, PlaceSearchService(device, http, secrets), location)

    @Test fun search_startsFromTheName_picksAPlace_andTheCafeIsSavedWithItsAddress() = runTest {
        val vm = picker(name = " 테스트커피 ")
        runCurrent()
        assertEquals("테스트커피", vm.state.value.query, "the field starts with the place's name")
        vm.search()
        assertIs<MapPickerViewModel.Search.Running>(vm.state.value.search)
        runCurrent()
        val found = assertIs<MapPickerViewModel.Search.Found>(vm.state.value.search)
        assertEquals(listOf(seongsu), found.hits)
        assertEquals(listOf(PlaceSource.DEVICE), found.sources)
        assertEquals("테스트커피", device.asked.single().first)

        vm.pick(seongsu)
        assertEquals(seongsu.point, vm.moves.first(), "the Korea map opens the place's 시·도")
        with(vm.state.value) {
            assertEquals(seongsu.point, point)
            assertEquals("서울 성동구 성수이로7길 51", address)
            assertIs<MapPickerViewModel.Search.Idle>(search, "the list closes")
        }
        var done = false
        vm.saveCafe { done = true }
        advanceUntilIdle()
        assertTrue(done)
        val saved = cafes.get("테스트커피")!!
        assertEquals(seongsu.point, saved.point)
        assertEquals("서울 성동구 성수이로7길 51", saved.address)

        // opened again: the address comes back; a tap on the map drops it, and so does the save
        val again = picker()
        runCurrent()
        assertEquals("서울 성동구 성수이로7길 51", again.state.value.address)
        again.setPoint(GeoPoint(37.55, 127.05))
        assertNull(again.state.value.address)
        again.saveCafe {}
        advanceUntilIdle()
        assertNull(cafes.get("테스트커피")!!.address)
        assertEquals(GeoPoint(37.55, 127.05), cafes.get("테스트커피")!!.point)
    }

    @Test fun emptyQuery_noResults_andFailures_sayWhy() = runTest {
        val vm = picker(target = MapPickTarget.ROASTERY, name = "")
        vm.search()
        assertEquals(MapPickerViewModel.Search.Message(PlaceSearchTexts.EMPTY_QUERY, error = false), vm.state.value.search)
        assertTrue(device.asked.isEmpty())
        device.hits = emptyList()
        vm.setQuery("없는 카페")
        vm.search()
        runCurrent()
        assertEquals(MapPickerViewModel.Search.Message(PlaceSearchTexts.noResults("없는 카페"), PlaceSource.DEVICE, error = false), vm.state.value.search)
        device.supported = false
        http.supported = false
        vm.search()
        runCurrent()
        assertEquals(PlaceSearchTexts.UNAVAILABLE + PlaceSearchTexts.UNAVAILABLE_KAKAO, (vm.state.value.search as MapPickerViewModel.Search.Message).text)
        vm.closeResults()
        assertIs<MapPickerViewModel.Search.Idle>(vm.state.value.search)
    }

    @Test fun kakaoAnswersDomesticSearches_withTheUsersKey() = runTest {
        secrets.put(PlaceSearchService.KAKAO_SECRET, "k")
        http.on("keyword.json") { KakaoReplies.KEYWORD }.on("address.json") { KakaoReplies.EMPTY }
        val vm = picker(target = MapPickTarget.ROASTERY, name = "테스트커피")
        vm.search()
        runCurrent()
        val found = assertIs<MapPickerViewModel.Search.Found>(vm.state.value.search)
        assertEquals(listOf(PlaceSource.KAKAO), found.sources)
        assertEquals("테스트커피 성수", found.hits.first().name)
        assertTrue(device.asked.isEmpty())
    }

    @Test fun roasteryResult_carriesTheAddress_andAnOverseasSearchStaysAbroad() = runTest {
        val vm = picker(target = MapPickTarget.ROASTERY, name = "Kurasu", scope = Scope.OVERSEAS)
        val kyoto = PlaceHit("Kurasu Kyoto", "일본 교토부 교토시 시모교구", GeoPoint(34.9858, 135.7588))
        device.hits = listOf(seongsu, kyoto)
        vm.search()
        runCurrent()
        assertEquals(listOf(kyoto), (vm.state.value.search as MapPickerViewModel.Search.Found).hits, "a place in Korea is no 해외 roastery")
        assertFalse(device.asked.single().second, "asked as 해외")
        vm.pick(kyoto)
        val result = MapPickResult.encode(vm.state.value.point, vm.state.value.address)
        assertEquals(kyoto.point, MapPickResult.decode(result))
        assertEquals("일본 교토부 교토시 시모교구", MapPickResult.address(result))
    }

    @Test fun currentLocation_setsThePoint_andLaterSearchesMeasureFromIt() = runTest {
        val here = GeoPoint(37.5446, 127.0457)
        location.answer = LocateResult.Found(here, accuracyM = 12.0)
        val vm = picker(target = MapPickTarget.ROASTERY, name = "테스트커피")
        vm.locate(granted = true)
        assertTrue(vm.state.value.locating)
        runCurrent()
        with(vm.state.value) {
            assertFalse(locating)
            assertEquals(here, point)
            assertNull(address, "a position from the phone has no address")
            assertEquals(LocateTexts.found(12.0), locateNote)
            assertFalse(locateFailed)
        }
        assertEquals("현재 위치로 정했어요 (오차 약 12 m).", LocateTexts.found(12.0))
        assertEquals(here, vm.moves.first())
        vm.search()
        runCurrent()
        assertEquals(here, device.asked.single().third, "the search starts where the phone is")
        val hit = (vm.state.value.search as MapPickerViewModel.Search.Found).hits.single()
        assertTrue(hit.distanceM!! in 870..890)
    }

    @Test fun currentLocation_refused_off_missing_orSilent() = runTest {
        val vm = picker(target = MapPickTarget.ROASTERY)
        vm.locate(granted = false)
        runCurrent()
        assertEquals(LocateTexts.DENIED, vm.state.value.locateNote)
        assertTrue(vm.state.value.locateFailed)
        assertEquals(0, location.asked, "nothing read without the permission")
        // iOS reports switched-off location services as a refusal
        location.servicesOn = false
        vm.locate(granted = false)
        runCurrent()
        assertEquals(LocateTexts.SERVICES_OFF, vm.state.value.locateNote)
        location.servicesOn = true
        location.answer = LocateResult.ServicesOff
        vm.locate(granted = true)
        runCurrent()
        assertEquals(LocateTexts.SERVICES_OFF, vm.state.value.locateNote)
        // no answer within 15 s
        location.hang = true
        vm.locate(granted = true)
        advanceTimeBy(LocateTexts.TIMEOUT_MS - 1)
        assertTrue(vm.state.value.locating)
        advanceTimeBy(2)
        runCurrent()
        assertFalse(vm.state.value.locating)
        assertEquals(LocateTexts.NO_FIX, vm.state.value.locateNote)
        assertNull(vm.state.value.point)
        location.hang = false
        location.supported = false
        vm.locate(granted = false)
        runCurrent()
        assertEquals(LocateTexts.UNSUPPORTED, vm.state.value.locateNote)
    }
}
