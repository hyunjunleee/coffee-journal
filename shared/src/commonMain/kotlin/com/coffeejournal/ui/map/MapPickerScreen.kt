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
import androidx.compose.ui.unit.dp
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
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.BlockBackWhile
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the location picker sets: a roastery in its form (handed back), or a café's position (saved directly). */
object MapPickTarget {
    const val ROASTERY = "roastery"
    const val CAFE = "cafe"
}

/**
 * The picker hands a roastery's position back to its form through the form's SavedStateHandle under [KEY]:
 * "lat,lng", or [CLEARED] when the position was removed.
 */
object MapPickResult {
    const val KEY = "map-pick-result"
    const val CLEARED = "clear"

    fun encode(p: GeoPoint?): String = if (p == null) CLEARED else "${p.lat},${p.lng}"

    /** The point, or null for [CLEARED] or anything unreadable. */
    fun decode(s: String?): GeoPoint? {
        val parts = s?.split(',') ?: return null
        if (parts.size != 2) return null
        return GeoPoint.of(parts[0].trim().toDoubleOrNull(), parts[1].trim().toDoubleOrNull())
    }

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
) : ViewModel() {
    data class State(val point: GeoPoint? = null, val loaded: Boolean = false, val saving: Boolean = false)

    private val _state = MutableStateFlow(State(point = initial, loaded = target != MapPickTarget.CAFE))
    val state: StateFlow<State> = _state
    val overseas: Boolean get() = target == MapPickTarget.ROASTERY && scope == Scope.OVERSEAS

    init {
        if (target == MapPickTarget.CAFE) viewModelScope.launch {
            val existing = runCatching { cafePlaces.get(name) }.getOrNull()
            _state.update { it.copy(point = existing?.point ?: it.point, loaded = true) }
        }
    }

    fun setPoint(p: GeoPoint?) = _state.update { if (it.saving) it else it.copy(point = p) }

    /** Café: stores (or removes) the position, then [onDone]. */
    fun saveCafe(onDone: () -> Unit) {
        val s = _state.value
        if (s.saving || !s.loaded) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                if (s.point != null) cafePlaces.set(name, s.point) else cafePlaces.clear(name)
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
 * Route.MapPicker: "지도에서 위치 지정". 국내: tap a 시·도 on the national map, then the exact spot in its 시·군·구 map
 * (pinch to zoom further); 해외: tap the world map. Shows the 시·도 / 시·군·구 (or country) found for the point.
 * A roastery's point goes back to its form (the form's 저장 keeps it); a café's is saved here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapPickerScreen(nav: NavHostController, vm: MapPickerViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val leave = dropUnlessResumed { nav.popBackStack() }
    BlockBackWhile(s.saving)
    val overseas = vm.overseas
    val koreaState = rememberKoreaMapState()
    val worldState = rememberWorldPinMapState()
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
            Spacer(Modifier.height(12.dp))
            if (point == null) {
                Text("아직 위치를 정하지 않았어요.", style = AppType.small)
            } else {
                val found = MapPickResult.placeName(point, overseas)
                Text(found?.let { "📍 $it" } ?: "📍 지역을 찾지 못했어요 (바다 위일 수 있어요)", style = AppType.body)
                Text(CafeMapLogic.coords(point), style = AppType.monoSmall, modifier = Modifier.padding(top = 2.dp))
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
                            nav.previousBackStackEntry?.savedStateHandle?.set(MapPickResult.KEY, MapPickResult.encode(s.point))
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
