package com.coffeejournal.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.coffeejournal.ui.bean.b.ConfirmDeleteDialog
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
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.deriveOffMain
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * One café: its café records (newest first; none yet for a café added with "+ 카페 추가") and, once set on the map, its
 * position.
 */
data class CafeSpot(val key: String, val name: String, val visits: List<Entry>, val place: CafePlace?) {
    val point: GeoPoint? get() = place?.point
    /** Map units on the Korea map, when the position lies in Korea. */
    val at: MapXY? get() = point?.takeIf { KoreaProjection.inKoreaBox(it) }?.let { KoreaProjection.toMap(it) }
    /** "방문 3회", or "방문 기록 없음" for a café added by hand. */
    val visitText: String get() = if (visits.isEmpty()) "방문 기록 없음" else "방문 ${visits.size}회"

    /**
     * Only a café without visits can be deleted: it exists through its cafe_places row alone. A visited café comes
     * from its records and stays; the picker's "위치 지우기" removes its position.
     */
    val deletable: Boolean get() = visits.isEmpty() && place != null
}

object CafeMapLogic {
    /**
     * Every café, most visited first: the café records (category 카페 with a café name) grouped by café name, and the
     * cafés added by hand that no record names yet (a cafe_places row). One café per name, matched like
     * [CafePlace.key]; a visited café is named as its newest visit spells it.
     */
    fun spots(entries: List<Entry>, places: List<CafePlace>): List<CafeSpot> {
        val byKey = places.filter { CafePlace.key(it.name).isNotEmpty() }.associateBy { CafePlace.key(it.name) }
        val visited = entries.filter { it.isCafe && it.cafeName.isNotBlank() }
            .groupBy { CafePlace.key(it.cafeName) }
            .map { (key, list) ->
                val visits = list.sortedByDescending { it.createdAt }
                CafeSpot(key, visits.first().cafeName.trim(), visits, byKey[key])
            }
        val named = visited.mapTo(HashSet()) { it.key }
        val added = byKey.filterKeys { it !in named }.map { (key, p) -> CafeSpot(key, p.name.trim(), emptyList(), p) }
        return (visited + added).sortedWith(compareByDescending<CafeSpot> { it.visits.size }.thenBy { it.name })
    }

    /**
     * The calendar's café list: the cafés of the records shown ([shown], a month's or every café record, counted
     * there), then the cafés of [all] that have no visit at all.
     */
    fun listed(shown: List<Entry>, places: List<CafePlace>, all: List<CafeSpot>): List<CafeSpot> =
        spots(shown, places).filter { it.visits.isNotEmpty() } + all.filter { it.visits.isEmpty() }

    /** The café a typed name already is (any spelling, like [CafePlace.key]), so "+ 카페 추가" never makes a second one. */
    fun find(spots: List<CafeSpot>, name: String): CafeSpot? {
        val key = CafePlace.key(name)
        return if (key.isEmpty()) null else spots.firstOrNull { it.key == key }
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
        return "카페 지도. " + (if (placed == 0) "표시된 카페가 없어요" else "카페 ${placed}곳 표시") +
            (if (rest > 0) ", 위치 미지정 ${rest}곳" else "") + ". 시·도를 누르면 시·군·구 지도로 확대돼요."
    }
}

/** Café positions and every café, derived off the main thread; deletes a café added by hand. */
class CafeMapViewModel(entries: EntryRepository, private val cafes: CafePlaceRepository) : ViewModel() {
    val places: StateFlow<List<CafePlace>?> = cafes.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val spots: StateFlow<List<CafeSpot>?> = combine(entries.observeAll(), cafes.observeAll()) { e, p -> e to p }
        .deriveOffMain { (e, p) -> CafeMapLogic.spots(e, p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Deletes the café when it has no visits ([CafeSpot.deletable]); every list and map drops it at once. */
    fun delete(spot: CafeSpot) {
        if (spot.deletable) viewModelScope.launch { cafes.delete(spot.name) }
    }
}

private fun NavHostController.pickCafe(name: String) = navigate(Route.MapPicker(target = MapPickTarget.CAFE, name = name))

/** "+ 카페 추가": the café name screen, which then opens the picker (or saves the café without a position). */
fun NavHostController.addCafe() = navigate(Route.CafeAdd)

/**
 * 원두 › 로스터리 › 한국 › 카페 지도: café pins with their visit counts, the chosen café's visits, the cafés not placed
 * yet, and "+ 카페 추가".
 */
@Composable
fun CafeMapSection(nav: NavHostController, koreaState: KoreaMapState, modifier: Modifier = Modifier) {
    val vm = koinViewModel<CafeMapViewModel>()
    val loaded by vm.spots.collectAsStateWithLifecycle()
    val spots = loaded ?: return
    val detailMap = rememberDetailMapSupported()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val pins = remember(spots) {
        spots.mapNotNull { s -> s.at?.let { MapPin(s.key, s.name, it, s.visits.size.takeIf { n -> n > 0 }, "${s.name}, ${s.visitText}") } }
    }
    val shaded = remember(spots) { spots.mapNotNull { s -> s.point?.let { KoreaRegions.locate(it.lat, it.lng, 3.0)?.provinceCode } }.toSet() }
    Column(modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text("카페 지도", style = AppType.cardTitle, modifier = Modifier.weight(1f))
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
            EmptyNote("아직 카페가 없어요. 아래 ‘+ 카페 추가’로 바로 등록하거나, 카페 기록을 남기면 카페 이름별로 모여요.", Modifier.padding(top = 10.dp))
        }
        spots.firstOrNull { it.key == selected }?.let { spot ->
            val detailPin = if (detailMap) DetailMapPins.cafes(listOf(spot)).firstOrNull() else null
            CafeSpotPanel(
                spot, nav, Modifier.padding(top = 10.dp),
                onOpenDetail = detailPin?.let { p -> { nav.navigate(DetailMapRoutes.pin(DetailMapLayer.CAFE, Scope.DOMESTIC, p)) } },
            )
        }
        val unplaced = spots.filter { it.at == null }
        if (unplaced.isNotEmpty()) UnplacedCafes(nav, unplaced, Modifier.padding(top = 10.dp))
        PrimaryButton("+ 카페 추가", onClick = { nav.addCafe() }, modifier = Modifier.padding(top = 18.dp))
    }
}

/**
 * The cafés without a position on the Korea map (the café map's box and the detail map's), each with the picker and,
 * for a café added by hand, 삭제.
 */
@Composable
fun UnplacedCafes(nav: NavHostController, unplaced: List<CafeSpot>, modifier: Modifier = Modifier) {
    val vm = koinViewModel<CafeMapViewModel>()
    Column(modifier.fillMaxWidth().border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(12.dp)) {
        Text("위치 미지정 ${unplaced.size}곳", style = AppType.small.copy(color = Ink.text, fontWeight = FontWeight.SemiBold))
        Text("‘지도에서 위치 지정’으로 찍어 두면 지도에 핀으로 표시돼요.", style = AppType.faint, modifier = Modifier.padding(top = 5.dp))
        unplaced.forEach { spot ->
            CafeLocationLine(spot.name, spot.visitText, spot.point, onPick = { nav.pickCafe(spot.name) }, onDelete = if (spot.deletable) { { vm.delete(spot) } } else null)
        }
    }
}

/**
 * The selected café (the café map's panel and the detail map's): where it is, its visits, the map-app links, the
 * picker, "상세 지도에서 보기" when [onOpenDetail] is given, and 삭제 for a café added by hand.
 */
@Composable
fun CafeSpotPanel(spot: CafeSpot, nav: NavHostController, modifier: Modifier = Modifier, onOpenDetail: (() -> Unit)? = null) {
    var confirm by remember { mutableStateOf(false) }
    HairlineCard(modifier) {
        Text(spot.name, style = AppType.cardTitle)
        spot.point?.let { Text("📍 ${CafeMapLogic.placeLabel(it)}", style = AppType.small) }
        Text(spot.visitText, style = AppType.faint)
        spot.visits.forEach { en -> VisitRow(en) { nav.navigate(Route.EntryDetail(en.id)) } }
        MapLinkButtons(spot.name, spot.point?.let { KoreaRegions.locate(it.lat, it.lng, 3.0)?.label } ?: "", spot.point, overseas = false)
        GhostButton("지도에서 위치 변경", small = true, onClick = { nav.pickCafe(spot.name) }, modifier = Modifier.padding(top = 8.dp))
        if (onOpenDetail != null) GhostButton("상세 지도에서 보기", small = true, onClick = onOpenDetail, modifier = Modifier.padding(top = 8.dp))
        if (spot.deletable) GhostButton("삭제", small = true, danger = true, onClick = { confirm = true }, modifier = Modifier.padding(top = 8.dp))
    }
    if (confirm) {
        val vm = koinViewModel<CafeMapViewModel>()
        ConfirmDeleteDialog(deleteText(spot.name), onConfirm = { vm.delete(spot) }, onDismiss = { confirm = false })
    }
}

/** The confirmation of 삭제, which only a café without visits has ([CafeSpot.deletable]). */
private fun deleteText(name: String) = "'$name'을(를) 삭제할까요? 방문 기록이 없는 카페라 목록과 지도에서 사라져요."

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
 * A café name, its visits ("방문 2회" / "방문 기록 없음") and where it is, with the button that opens the location
 * picker, and 삭제 (confirmed) when [onDelete] is given.
 */
@Composable
private fun CafeLocationLine(name: String, visits: String?, point: GeoPoint?, onPick: () -> Unit, onDelete: (() -> Unit)? = null) {
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = AppType.body)
            val where = point?.let { "📍 ${CafeMapLogic.placeLabel(it)}" } ?: "위치 미지정"
            Text(listOfNotNull(visits, where).joinToString(" · "), style = AppType.faint)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GhostButton(if (point == null) "지도에서 위치 지정" else "위치 변경", small = true, onClick = onPick)
            if (onDelete != null) GhostButton("삭제", small = true, danger = true, onClick = { confirm = true })
        }
    }
    if (confirm && onDelete != null) {
        ConfirmDeleteDialog(deleteText(name), onConfirm = onDelete, onDismiss = { confirm = false })
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
        CafeLocationLine(name, null, point, onPick = { nav.pickCafe(name) })
        MapLinkButtons(name, point?.let { KoreaRegions.locate(it.lat, it.lng, 3.0)?.label } ?: "", point, overseas = false)
    }
}

/**
 * Calendar › 카페: every café in the list once (with its visits there), then the cafés without visits, each with its
 * position, the picker button and (a café added by hand) 삭제; "+ 카페 추가" at the end.
 */
@Composable
fun CafeLocationsBlock(nav: NavHostController, entries: List<Entry>, modifier: Modifier = Modifier) {
    val vm = koinViewModel<CafeMapViewModel>()
    val places by vm.places.collectAsStateWithLifecycle()
    val every by vm.spots.collectAsStateWithLifecycle()
    val rows = places ?: return
    val all = every ?: return
    val spots = remember(entries, rows, all) { CafeMapLogic.listed(entries, rows, all) }
    Column(modifier.fillMaxWidth()) {
        SectionLabel("카페 위치")
        if (spots.isEmpty()) Text("아직 등록된 카페가 없어요. 가 볼 카페를 미리 등록해 둘 수도 있어요.", style = AppType.small)
        spots.forEach { s ->
            CafeLocationLine(s.name, s.visitText, s.point, onPick = { nav.pickCafe(s.name) }, onDelete = if (s.deletable) { { vm.delete(s) } } else null)
        }
        GhostButton("+ 카페 추가", small = true, onClick = { nav.addCafe() }, modifier = Modifier.padding(top = 12.dp))
    }
}
