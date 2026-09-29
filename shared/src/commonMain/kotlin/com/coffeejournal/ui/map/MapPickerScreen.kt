package com.coffeejournal.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.coffeejournal.data.repo.CafePlaceRepository
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.KoreaRegions
import com.coffeejournal.ui.map.detail.DetailMapPick
import com.coffeejournal.ui.map.detail.DetailMapRoutes
import com.coffeejournal.ui.map.detail.rememberDetailMapSupported
import com.coffeejournal.ui.map.search.CurrentLocation
import com.coffeejournal.ui.map.search.LocateResult
import com.coffeejournal.ui.map.search.LocateTexts
import com.coffeejournal.ui.map.search.MorePlaces
import com.coffeejournal.ui.map.search.PlaceHit
import com.coffeejournal.ui.map.search.PlaceSearchResult
import com.coffeejournal.ui.map.search.PlaceSearchService
import com.coffeejournal.ui.map.search.PlaceSearchTexts
import com.coffeejournal.ui.map.search.PlaceSource
import com.coffeejournal.ui.platform.rememberLocationPermissionRequest
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.BlockBackWhile
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the location picker sets: a roastery in its form (handed back), or a café's position (saved directly). */
object MapPickTarget {
    const val ROASTERY = "roastery"
    const val CAFE = "cafe"
}

/**
 * The picker hands a roastery's position back to its form through the form's SavedStateHandle under [KEY]:
 * "lat,lng", "lat,lng<TAB>address" when the point came from a search, or [CLEARED] when the position was removed.
 * A result without an address reads as before, so older results (and Route.MapPicker's point) still decode.
 */
object MapPickResult {
    const val KEY = "map-pick-result"
    const val CLEARED = "clear"
    private const val ADDRESS = '\t'

    fun encode(p: GeoPoint?, address: String? = null): String {
        if (p == null) return CLEARED
        val line = address?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
        return "${p.lat},${p.lng}" + (line?.let { "$ADDRESS$it" } ?: "")
    }

    /** The point, or null for [CLEARED] or anything unreadable. */
    fun decode(s: String?): GeoPoint? {
        val parts = s?.substringBefore(ADDRESS)?.split(',') ?: return null
        if (parts.size != 2) return null
        return GeoPoint.of(parts[0].trim().toDoubleOrNull(), parts[1].trim().toDoubleOrNull())
    }

    /** The searched place's address handed back with the point, or null (a tapped point, an older result, [CLEARED]). */
    fun address(s: String?): String? =
        s?.takeIf { decode(it) != null }?.substringAfter(ADDRESS, "")?.trim()?.takeIf { it.isNotEmpty() }

    /** The area a picked point names for an empty location field: "서울특별시 성동구", or the country abroad. */
    fun placeName(p: GeoPoint, overseas: Boolean): String? =
        if (!overseas) KoreaRegions.locate(p.lat, p.lng, snapKm = 3.0)?.label
        else WorldPlaces.countryAt(WorldProjection.toView(p))?.let { WorldPlaces.displayName(it) }
}

class MapPickerViewModel(
    val target: String,
    val name: String,
    val scope: String,
    initial: GeoPoint?,
    private val cafePlaces: CafePlaceRepository,
    private val places: PlaceSearchService,
    private val location: CurrentLocation,
) : ViewModel() {
    /** The place search under the field: nothing shown, running, its places, or why there are none. */
    sealed interface Search {
        data object Idle : Search
        data object Running : Search
        /**
         * [sources]: which searches answered (the first leading). [next]: Kakao's next page ("더 보기"), [loadingMore]
         * while it loads, [moreFailed] when it did not come.
         */
        data class Found(
            val hits: List<PlaceHit>,
            val sources: List<PlaceSource>,
            val notice: String? = null,
            val next: MorePlaces? = null,
            val loadingMore: Boolean = false,
            val moreFailed: Boolean = false,
        ) : Search

        data class Message(val text: String, val source: PlaceSource? = null, val error: Boolean = true) : Search
    }

    data class State(
        val point: GeoPoint? = null,
        /** The searched place's address for [point]; a point tapped on a map or taken from the phone has none. */
        val address: String? = null,
        val loaded: Boolean = false,
        val saving: Boolean = false,
        /** The search field: the place's name to start with. */
        val query: String = "",
        val search: Search = Search.Idle,
        val locating: Boolean = false,
        /** The last "현재 위치" answer: how close the point is, or ([locateFailed]) why there is none. */
        val locateNote: String? = null,
        val locateFailed: Boolean = false,
        /** Where "현재 위치" found the phone: later searches start there and show each place's distance. */
        val here: GeoPoint? = null,
    )

    private val _state = MutableStateFlow(State(point = initial, loaded = target != MapPickTarget.CAFE, query = name.trim()))
    val state: StateFlow<State> = _state
    val overseas: Boolean get() = target == MapPickTarget.ROASTERY && scope == Scope.OVERSEAS
    val locationSupported: Boolean get() = location.supported

    /** A point from a search or the phone: the screen opens its 시·도 on the Korea map, as for the detail map's. */
    private val _moves = Channel<GeoPoint>(Channel.CONFLATED)
    val moves: Flow<GeoPoint> = _moves.receiveAsFlow()

    private var searchJob: Job? = null
    private var locateJob: Job? = null

    init {
        if (target == MapPickTarget.CAFE) viewModelScope.launch {
            val existing = runCatching { cafePlaces.get(name) }.getOrNull()
            val saved = existing?.takeIf { p -> p.point != null }
            _state.update { it.copy(point = saved?.point ?: it.point, address = if (saved != null) saved.address else it.address, loaded = true) }
        }
    }

    /** A point tapped on a map, from the detail map, or none: it has no address. */
    fun setPoint(p: GeoPoint?) = _state.update { if (it.saving) it else it.copy(point = p, address = null, locateNote = null, locateFailed = false) }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    /** 검색: the typed words in this picker's scope (국내 / 해외), near the phone once "현재 위치" found it. */
    fun search() {
        val s = _state.value
        val q = s.query.trim()
        if (s.saving) return
        if (q.isEmpty()) {
            _state.update { it.copy(search = Search.Message(PlaceSearchTexts.EMPTY_QUERY, error = false)) }
            return
        }
        searchJob?.cancel()
        _state.update { it.copy(search = Search.Running) }
        searchJob = viewModelScope.launch {
            val shown = when (val r = places.search(q, domestic = !overseas, near = s.here)) {
                is PlaceSearchResult.Found ->
                    if (r.hits.isNotEmpty()) Search.Found(r.hits, r.sources, r.notice, r.next)
                    else Search.Message(listOfNotNull(r.notice, PlaceSearchTexts.noResults(q)).joinToString(" "), r.source, error = false)
                is PlaceSearchResult.Failed -> Search.Message(PlaceSearchTexts.message(r.error, domestic = !overseas), r.source)
            }
            _state.update { it.copy(search = shown) }
        }
    }

    /** "더 보기": Kakao's next page under the places shown, each place once. */
    fun more() {
        val found = _state.value.search as? Search.Found ?: return
        val next = found.next ?: return
        if (found.loadingMore || _state.value.saving) return
        _state.update { it.copy(search = found.copy(loadingMore = true, moreFailed = false)) }
        searchJob = viewModelScope.launch {
            val r = places.more(next)
            _state.update { st ->
                val now = st.search as? Search.Found ?: return@update st
                st.copy(
                    search = when (r) {
                        is PlaceSearchResult.Found -> {
                            val shown = now.hits.map { it.name.trim().lowercase() to it.point }.toSet()
                            now.copy(hits = now.hits + r.hits.filter { (it.name.trim().lowercase() to it.point) !in shown }, next = r.next, loadingMore = false)
                        }
                        is PlaceSearchResult.Failed -> now.copy(loadingMore = false, moreFailed = true)
                    },
                )
            }
        }
    }

    /** A found place becomes the point, with its address; the list closes. */
    fun pick(hit: PlaceHit) {
        if (_state.value.saving) return
        _state.update { it.copy(point = hit.point, address = hit.address.trim().ifEmpty { null }, search = Search.Idle, locateNote = null, locateFailed = false) }
        _moves.trySend(hit.point)
    }

    fun closeResults() = _state.update { it.copy(search = Search.Idle) }

    /**
     * "현재 위치", after the permission answer ([granted]): one position within [LocateTexts.TIMEOUT_MS] becomes the
     * point (without an address), or the reason there is none is shown. A refusal with the phone's location services
     * off (how iOS reports them) says so instead.
     */
    fun locate(granted: Boolean) {
        val s = _state.value
        if (s.saving || s.locating) return
        locateJob?.cancel()
        _state.update { it.copy(locating = true, locateNote = null, locateFailed = false) }
        locateJob = viewModelScope.launch {
            val result = try {
                when {
                    !location.supported -> null
                    !granted -> if (location.servicesOn()) LocateResult.Denied else LocateResult.ServicesOff
                    else -> withTimeoutOrNull(LocateTexts.TIMEOUT_MS) { location.locate() } ?: LocateResult.NoFix
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LocateResult.NoFix
            }
            if (result is LocateResult.Found) {
                _state.update {
                    if (it.saving) it.copy(locating = false)
                    else it.copy(point = result.point, address = null, here = result.point, locating = false, locateNote = LocateTexts.found(result.accuracyM))
                }
                _moves.trySend(result.point)
            } else {
                val why = when (result) {
                    null -> LocateTexts.UNSUPPORTED
                    LocateResult.Denied -> LocateTexts.DENIED
                    LocateResult.ServicesOff -> LocateTexts.SERVICES_OFF
                    else -> LocateTexts.NO_FIX
                }
                _state.update { it.copy(locating = false, locateNote = why, locateFailed = true) }
            }
        }
    }

    /** Café: stores (or removes) the position with its address, then [onDone]. */
    fun saveCafe(onDone: () -> Unit) {
        val s = _state.value
        if (s.saving || !s.loaded) return
        searchJob?.cancel()
        locateJob?.cancel()
        _state.update { it.copy(saving = true, locating = false) }
        viewModelScope.launch {
            try {
                if (s.point != null) cafePlaces.set(name, s.point, s.address) else cafePlaces.clear(name)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

/**
 * Route.MapPicker: "지도에서 위치 지정". Above the map, a search by name or address (the place's name to start with)
 * and "현재 위치"; a found place opens its 시·도 and keeps its address. 국내: tap a 시·도 on the national map, then the
 * exact spot in its 시·군·구 map (pinch to zoom further); 해외: tap the world map. Shows the 시·도 / 시·군·구 (or
 * country) found for the point. A roastery's point goes back to its form (the form's 저장 keeps it, and an empty 지역
 * gets the address); a café's is saved here with its address.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapPickerScreen(nav: NavHostController, vm: MapPickerViewModel, results: SavedStateHandle? = null) {
    val s by vm.state.collectAsStateWithLifecycle()
    val leave = dropUnlessResumed { nav.popBackStack() }
    BlockBackWhile(s.saving)
    val overseas = vm.overseas
    val koreaState = rememberKoreaMapState()
    val worldState = rememberWorldPinMapState()
    val detailMap = rememberDetailMapSupported()
    // a point from the search or the phone opens its 시·도, like one from the detail map
    LaunchedEffect(vm) {
        vm.moves.collect { p -> if (!overseas) KoreaRegions.locate(p.lat, p.lng, 3.0)?.let { koreaState.showProvince(it.provinceCode) } }
    }
    val askLocation = rememberLocationPermissionRequest { granted -> vm.locate(granted) }
    // the detail map's crosshair point ("이 위치로 지정") becomes the picked point, shown on the SGIS map as well
    if (results != null) {
        val fromDetail by results.getStateFlow<String?>(DetailMapPick.KEY, null).collectAsStateWithLifecycle()
        LaunchedEffect(fromDetail) {
            val p = MapPickResult.decode(fromDetail ?: return@LaunchedEffect)
            results.remove<String>(DetailMapPick.KEY)
            if (p != null) {
                vm.setPoint(p)
                if (!overseas) KoreaRegions.locate(p.lat, p.lng, 3.0)?.let { koreaState.showProvince(it.provinceCode) }
            }
        }
    }
    // once the starting point is known (a café's is loaded), the map opens on its 시·도
    var framed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(s.loaded) {
        if (!s.loaded || framed) return@LaunchedEffect
        framed = true
        s.point?.takeIf { !overseas && KoreaProjection.inKoreaBox(it) }?.let { KoreaRegions.locate(it.lat, it.lng, 3.0) }?.let { koreaState.showProvince(it.provinceCode) }
    }
    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        ScreenTitleBar("지도에서 위치 지정", onBack = { if (!s.saving) leave() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            Spacer(Modifier.height(14.dp))
            Text(vm.name.ifBlank { "이름 없음" }, style = AppType.cardTitle)
            PickerSearch(
                s, onQuery = vm::setQuery, onSearch = vm::search, onPick = vm::pick, onClose = vm::closeResults,
                // nothing is asked of a phone that has no location at all
                onLocate = { if (vm.locationSupported) askLocation() else vm.locate(granted = false) },
                onMore = vm::more, domestic = !overseas,
                modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
            )
            HintText(
                if (overseas) "세계지도에서 로스터리가 있는 곳을 누르세요. 두 손가락으로 늘려 더 정확히 찍을 수 있어요."
                else if (koreaState.isNational) "시·도를 눌러 확대한 뒤, 정확한 위치를 누르세요."
                else "정확한 위치를 누르세요. 두 손가락으로 늘려 더 정확히 찍을 수 있어요.",
                Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            val point = s.point
            if (overseas) {
                WorldPinMap(
                    worldState, "위치 지정용 세계지도. 누른 곳이 로스터리 위치가 돼요.", pins = emptyList(), selectedKey = null, onPinTap = {},
                    onTap = { vm.setPoint(WorldProjection.toGeo(it.x, it.y).let { g -> GeoPoint.of(g.lat, g.lng) }) },
                    marker = point?.let { WorldProjection.toView(it) },
                )
            } else {
                KoreaMap(
                    koreaState, "위치 지정용 한국 지도. 시·도를 누르면 확대되고, 확대한 지도에서 누른 곳이 위치가 돼요.",
                    pins = emptyList(), selectedKey = null, onPinTap = {},
                    marker = point?.takeIf { KoreaProjection.inKoreaBox(it) }?.let { KoreaProjection.toMap(it) },
                    onPointTap = { vm.setPoint(KoreaProjection.toGeo(it.x, it.y)) },
                )
            }
            if (detailMap) {
                GhostButton(
                    "상세 지도에서 정확히", small = true,
                    onClick = {
                        val bounds = if (!overseas && !koreaState.isNational) koreaState.visibleBounds() else null
                        nav.navigate(DetailMapRoutes.pick(vm.scope, vm.name, point, bounds))
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
                HintText("길·건물·지명이 보이는 지도(OpenStreetMap, 인터넷 필요)에서 가운데 십자로 정확한 자리를 맞출 수 있어요.")
            }
            Spacer(Modifier.height(12.dp))
            if (point == null) {
                Text("아직 위치를 정하지 않았어요.", style = AppType.small)
            } else {
                val found = MapPickResult.placeName(point, overseas)
                Text(found?.let { "📍 $it" } ?: "📍 지역을 찾지 못했어요 (바다 위일 수 있어요)", style = AppType.body)
                s.address?.let { Text(it, style = AppType.small, modifier = Modifier.padding(top = 2.dp).testTag("picked-address")) }
                Text(CafeMapLogic.coords(point), style = AppType.monoSmall, modifier = Modifier.padding(top = 2.dp))
            }
            s.locateNote?.let {
                Text(it, style = AppType.small.copy(color = if (s.locateFailed) Ink.bad else Ink.textMuted), modifier = Modifier.padding(top = 6.dp).testTag("locate-note"))
            }
            if (vm.target == MapPickTarget.ROASTERY) {
                HintText("확인하면 로스터리 폼으로 돌아가요. 폼에서 저장해야 반영돼요.", Modifier.padding(top = 8.dp))
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    if (vm.target == MapPickTarget.CAFE) "저장" else "확인",
                    enabled = s.loaded && !s.saving,
                    onClick = {
                        if (vm.target == MapPickTarget.CAFE) vm.saveCafe(leave)
                        else {
                            nav.previousBackStackEntry?.savedStateHandle?.set(MapPickResult.KEY, MapPickResult.encode(s.point, s.address))
                            leave()
                        }
                    },
                )
                GhostButton("위치 지우기", enabled = point != null && !s.saving, onClick = { vm.setPoint(null) })
                GhostButton("취소", enabled = !s.saving, onClick = leave)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}
