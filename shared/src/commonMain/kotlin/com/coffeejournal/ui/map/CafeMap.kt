package com.coffeejournal.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.coffeejournal.data.repo.CafePlaceRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.domain.model.CafePlace
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.domain.rules.KoreaRegions
import com.coffeejournal.domain.rules.MapXY
import com.coffeejournal.ui.map.detail.DetailMapLayer
import com.coffeejournal.ui.map.detail.DetailMapPins
import com.coffeejournal.ui.map.detail.DetailMapRoutes
import com.coffeejournal.ui.map.detail.rememberDetailMapSupported
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.deriveOffMain
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel

/** One visited café: its café records (newest first) and, once set on the map, its position. */
data class CafeSpot(val key: String, val name: String, val visits: List<Entry>, val place: CafePlace?) {
    val point: GeoPoint? get() = place?.point
    /** Map units on the Korea map, when the position lies in Korea. */
    val at: MapXY? get() = point?.takeIf { KoreaProjection.inKoreaBox(it) }?.let { KoreaProjection.toMap(it) }
}

object CafeMapLogic {
    /** Café records (category 카페 with a café name) grouped by café name, most visited first. */
    fun spots(entries: List<Entry>, places: List<CafePlace>): List<CafeSpot> {
        val byKey = places.associateBy { CafePlace.key(it.name) }
        return entries.filter { it.isCafe && it.cafeName.isNotBlank() }
            .groupBy { CafePlace.key(it.cafeName) }
            .map { (key, list) ->
                val visits = list.sortedByDescending { it.createdAt }
                CafeSpot(key, visits.first().cafeName.trim(), visits, byKey[key])
            }
            .sortedWith(compareByDescending<CafeSpot> { it.visits.size }.thenBy { it.name })
    }

    /** "서울특별시 성동구" for a position (the nearest 시·군·구 within 3 km of a coast), else its coordinates. */
    fun placeLabel(p: GeoPoint): String =
        KoreaRegions.locate(p.lat, p.lng, snapKm = 3.0)?.label ?: coords(p)

    fun coords(p: GeoPoint): String = "${fixed4(p.lat)}, ${fixed4(p.lng)}"

    private fun fixed4(v: Double): String {
        val scaled = kotlin.math.round(v * 10_000.0).toLong()
        val neg = scaled < 0
        val abs = if (neg) -scaled else scaled
        return (if (neg) "-" else "") + (abs / 10_000) + "." + (abs % 10_000).toString().padStart(4, '0')
    }

    fun mapDescription(spots: List<CafeSpot>): String {
        val placed = spots.count { it.at != null }
        val rest = spots.size - placed
        return "방문 카페 지도. " + (if (placed == 0) "표시된 카페가 없어요" else "카페 ${placed}곳 표시") +
            (if (rest > 0) ", 위치 미지정 ${rest}곳" else "") + ". 시·도를 누르면 시·군·구 지도로 확대돼요."
    }
}

/** Café positions and the visited cafés, derived off the main thread. */
class CafeMapViewModel(entries: EntryRepository, places: CafePlaceRepository) : ViewModel() {
    val places: StateFlow<List<CafePlace>?> = places.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val spots: StateFlow<List<CafeSpot>?> = combine(entries.observeAll(), places.observeAll()) { e, p -> e to p }
        .deriveOffMain { (e, p) -> CafeMapLogic.spots(e, p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private fun NavHostController.pickCafe(name: String) = navigate(Route.MapPicker(target = MapPickTarget.CAFE, name = name))

/** 원두 › 로스터리 › 한국 › 방문 카페: café pins with their visit counts, the chosen café's visits, and the rest. */
@Composable
fun CafeMapSection(nav: NavHostController, koreaState: KoreaMapState, modifier: Modifier = Modifier) {
    val vm = koinViewModel<CafeMapViewModel>()
    val loaded by vm.spots.collectAsStateWithLifecycle()
    val spots = loaded ?: return
    val detailMap = rememberDetailMapSupported()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val pins = remember(spots) {
        spots.mapNotNull { s -> s.at?.let { MapPin(s.key, s.name, it, s.visits.size, "${s.name}, 방문 ${s.visits.size}회") } }
    }
    val shaded = remember(spots) { spots.mapNotNull { s -> s.point?.let { KoreaRegions.locate(it.lat, it.lng, 3.0)?.provinceCode } }.toSet() }
    Column(modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text("방문 카페 지도", style = AppType.cardTitle, modifier = Modifier.weight(1f))
            Text("${spots.size}곳 · ${spots.sumOf { it.visits.size }}회", style = AppType.count)
        }
        KoreaMap(
            koreaState, CafeMapLogic.mapDescription(spots), pins, selected,
            onPinTap = { key -> selected = if (key == selected) null else key },
            shaded = shaded, onBackgroundTap = { selected = null },
            // "상세 지도": the selected café, else what the 시·도 map shows
            onOpenDetail = if (detailMap) { bounds ->
                val pin = spots.firstOrNull { it.key == selected }?.let { DetailMapPins.cafes(listOf(it)).firstOrNull() }
                nav.navigate(if (pin != null) DetailMapRoutes.pin(DetailMapLayer.CAFE, Scope.DOMESTIC, pin) else DetailMapRoutes.area(DetailMapLayer.CAFE, Scope.DOMESTIC, bounds))
            } else null,
        )
        if (spots.isEmpty()) {
            EmptyNote("아직 카페 기록이 없어요. 카페 기록에 카페 이름을 적으면 여기에 모여요.", Modifier.padding(top = 10.dp))
            return@Column
        }
        spots.firstOrNull { it.key == selected }?.let { spot ->
            val detailPin = if (detailMap) DetailMapPins.cafes(listOf(spot)).firstOrNull() else null
            CafeSpotPanel(
                spot, nav, Modifier.padding(top = 10.dp),
                onOpenDetail = detailPin?.let { p -> { nav.navigate(DetailMapRoutes.pin(DetailMapLayer.CAFE, Scope.DOMESTIC, p)) } },
            )
        }
        val unplaced = spots.filter { it.at == null }
        if (unplaced.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(12.dp)) {
                Text("위치 미지정 ${unplaced.size}곳", style = AppType.small.copy(color = Ink.text, fontWeight = FontWeight.SemiBold))
                Text("‘지도에서 위치 지정’으로 카페를 지도에 찍어 두면 방문 횟수와 함께 표시돼요.", style = AppType.faint, modifier = Modifier.padding(top = 5.dp))
                unplaced.forEach { spot -> CafeLocationLine(spot.name, spot.visits.size, null) { nav.pickCafe(spot.name) } }
            }
        }
    }
}

/**
 * The selected café (the café map's panel and the detail map's): where it is, its visits, the map-app links, the
 * picker, and "상세 지도에서 보기" when [onOpenDetail] is given.
 */
@Composable
fun CafeSpotPanel(spot: CafeSpot, nav: NavHostController, modifier: Modifier = Modifier, onOpenDetail: (() -> Unit)? = null) {
    HairlineCard(modifier) {
        Text(spot.name, style = AppType.cardTitle)
        spot.point?.let { Text("📍 ${CafeMapLogic.placeLabel(it)}", style = AppType.small) }
        // the address of the place picked from a search (schema v3)
        spot.place?.address?.takeIf { spot.point != null }?.let { Text(it, style = AppType.faint) }
        Text("방문 ${spot.visits.size}회", style = AppType.faint)
        spot.visits.forEach { en -> VisitRow(en) { nav.navigate(Route.EntryDetail(en.id)) } }
        MapLinkButtons(spot.name, spot.point?.let { KoreaRegions.locate(it.lat, it.lng, 3.0)?.label } ?: "", spot.point, overseas = false)
        GhostButton("지도에서 위치 변경", small = true, onClick = { nav.pickCafe(spot.name) }, modifier = Modifier.padding(top = 8.dp))
        if (onOpenDetail != null) GhostButton("상세 지도에서 보기", small = true, onClick = onOpenDetail, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun VisitRow(en: Entry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(BeanNames.displayName(en.name).ifBlank { "이름 없음" }, style = AppType.body, modifier = Modifier.weight(1f))
        Text(Dates.ymdCompact(en.createdAt), style = AppType.monoSmall)
    }
}

/**
 * A café name, its visit count and where it is (with its address when it was picked from a search), and the button
 * that opens the location picker.
 */
@Composable
private fun CafeLocationLine(name: String, visits: Int?, point: GeoPoint?, address: String? = null, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = AppType.body)
            val where = point?.let { "📍 ${CafeMapLogic.placeLabel(it)}" } ?: "위치 미지정"
            Text(listOfNotNull(visits?.let { "방문 ${it}회" }, where).joinToString(" · "), style = AppType.faint)
            if (point != null) address?.let { Text(it, style = AppType.faint) }
        }
        Spacer(Modifier.width(8.dp))
        GhostButton(if (point == null) "지도에서 위치 지정" else "위치 변경", small = true, onClick = onPick)
    }
}

/** Record detail of a café visit: where the café is, with the picker and the map-app links. */
@Composable
fun CafePlaceRow(nav: NavHostController, cafeName: String, modifier: Modifier = Modifier) {
    val name = cafeName.trim()
    if (name.isEmpty()) return
    val vm = koinViewModel<CafeMapViewModel>()
    val places by vm.places.collectAsStateWithLifecycle()
    val all = places ?: return
    val place = remember(all, name) { all.firstOrNull { CafePlace.key(it.name) == CafePlace.key(name) } }
    val point = place?.point
    Column(modifier.fillMaxWidth()) {
        SectionLabel("카페 위치")
        CafeLocationLine(name, null, point, place?.address) { nav.pickCafe(name) }
        MapLinkButtons(name, point?.let { KoreaRegions.locate(it.lat, it.lng, 3.0)?.label } ?: "", point, overseas = false)
    }
}

/** Calendar › 카페: every café in the list once, with its visits and position and the picker button. */
@Composable
fun CafeLocationsBlock(nav: NavHostController, entries: List<Entry>, modifier: Modifier = Modifier) {
    val vm = koinViewModel<CafeMapViewModel>()
    val places by vm.places.collectAsStateWithLifecycle()
    val all = places ?: return
    val spots = remember(entries, all) { CafeMapLogic.spots(entries, all) }
    if (spots.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        SectionLabel("카페 위치")
        spots.forEach { s -> CafeLocationLine(s.name, s.visits.size, s.point, s.place?.address) { nav.pickCafe(s.name) } }
    }
}
