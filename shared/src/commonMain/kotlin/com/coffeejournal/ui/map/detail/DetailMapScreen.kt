package com.coffeejournal.ui.map.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import com.coffeejournal.ui.bean.b.RoasteryPanel
import com.coffeejournal.ui.map.CafeMapLogic
import com.coffeejournal.ui.map.CafeSpotPanel
import com.coffeejournal.ui.map.MapPickResult
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.platform.openUrl
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.SectionLabel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/** What the detail map sends where, for its info text and the 출처 screen. */
object DetailMapPrivacy {
    const val NOTE =
        "상세 지도는 화면에 보이는 지역의 지도 조각(OpenStreetMap 데이터)을 OpenFreeMap(tiles.openfreemap.org)에서 받아 와요. " +
            "요청에는 여느 인터넷 요청처럼 기기의 IP 주소와 앱·Android 버전(User-Agent)이 함께 가지만, 기록·로스터리·카페 정보는 보내지 않아요. " +
            "받은 지도 조각은 다시 볼 때를 위해 기기에 잠시 저장(캐시)되고, 미리 내려받지는 않아요."
}

/** The map's TalkBack label: what it is, what is pinned, and how to use it. */
internal fun detailMapDescription(pick: Boolean, layer: String, pins: Int): String =
    if (pick) "상세 지도. 지도를 움직여 가운데 십자 위치를 맞추면 그곳이 위치가 돼요."
    else {
        val what = if (layer == DetailMapLayer.CAFE) "카페" else "로스터리"
        "상세 지도. " + (if (pins == 0) "표시된 ${what}가 없어요" else "$what ${pins}곳 표시") + ". 지도 아래에 목록이 있어요."
    }

private fun controllerSaver(online: () -> Boolean): Saver<DetailMapController, Any> = listSaver(
    save = { listOf(it.camera.lat, it.camera.lng, it.camera.zoom) },
    restore = { v -> DetailMapController(DetailCamera(v[0], v[1], v[2])).also { if (!online()) it.reportOffline() } },
)

/**
 * Route.DetailMap: the street-level map (OpenStreetMap vector tiles from OpenFreeMap, drawn by the platform's
 * [DetailMapRenderer]) with the roastery or café pins; a pin opens the same panel as on the SGIS map. In pick mode a
 * crosshair marks the centre and "이 위치로 지정" hands it to the location picker. Without network, or when the
 * renderer cannot start, a notice leads back to the SGIS map (which needs no network).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailMapScreen(nav: NavHostController, vm: DetailMapViewModel, renderer: DetailMapRenderer = koinInject()) {
    val route = vm.route
    val pick = vm.pick
    val overseas = !vm.domestic
    val content by vm.content.collectAsStateWithLifecycle()
    val leave = dropUnlessResumed { nav.popBackStack() }
    var selected by rememberSaveable { mutableStateOf(route.focus) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.bg)) {
        val screenW = maxWidth.value.toDouble()
        val screenH = maxHeight
        val saver = remember(renderer) { controllerSaver { renderer.isOnline() } }
        val controller = rememberSaveable(saver = saver) {
            // the map gets a little over half the screen; the fit only needs its rough size
            val start = DetailMapCamera.start(DetailCamera.decode(route.camera), GeoBounds.decode(route.bounds), overseas, screenW, screenH.value * 0.55)
            DetailMapController(start).also { if (!renderer.isOnline()) it.reportOffline() }
        }
        val status = controller.status
        Column(Modifier.fillMaxSize()) {
            ScreenTitleBar(if (pick) "상세 지도에서 위치 지정" else "상세 지도", onBack = leave)
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                if (status.showsMap) {
                    key(controller.attempt) {
                        renderer.MapView(
                            style = DetailMapStyle.json,
                            controller = controller,
                            pins = content.pins,
                            selectedKey = selected,
                            onPinTap = { selected = it },
                            onMapTap = { selected = null },
                            modifier = Modifier.fillMaxSize().semantics { contentDescription = detailMapDescription(pick, route.layer, content.pins.size) },
                        )
                    }
                    if (status == DetailMapStatus.Loading) {
                        LaunchedEffect(controller.attempt) {
                            delay(renderer.slowAfterMs)
                            controller.reportSlow()
                        }
                        Text(
                            "지도를 불러오는 중…", style = AppType.small,
                            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).background(Ink.bg.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    if (pick) Crosshair(Modifier.align(Alignment.Center))
                    MapAttribution(Modifier.align(Alignment.BottomStart))
                    if (status == DetailMapStatus.Slow) {
                        SlowNotice(overseas, onRetry = { controller.retry(renderer.isOnline()) }, onBack = leave, modifier = Modifier.align(Alignment.TopCenter))
                    }
                } else {
                    DetailMapFallback(status, overseas, onBack = leave, onRetry = { controller.retry(renderer.isOnline()) })
                }
            }
            Hairline(color = Ink.text, thickness = Dimens.rule)
            Column(
                Modifier.fillMaxWidth().heightIn(max = screenH * 0.42f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter),
            ) {
                Spacer(Modifier.height(10.dp))
                if (pick) PickPanel(nav, route, controller, overseas, enabled = status.showsMap, leave = leave)
                else ViewPanel(nav, route, content, selected, onSelect = { pin ->
                    selected = pin?.key
                    if (pin != null) controller.moveTo(pin.camera)
                }, domestic = !overseas, mapShown = status.showsMap)
                HintText(DetailMapPrivacy.NOTE, Modifier.padding(top = 14.dp))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickPanel(nav: NavHostController, route: Route.DetailMap, controller: DetailMapController, overseas: Boolean, enabled: Boolean, leave: () -> Unit) {
    val p = controller.settled.point
    if (route.name.isNotBlank()) Text(route.name, style = AppType.cardTitle)
    HintText("지도를 움직여 가운데 십자(+)를 정확한 자리에 맞추세요. 두 손가락으로 늘려 더 가까이 볼 수 있어요.")
    val found = remember(p) { p?.let { MapPickResult.placeName(it, overseas) } }
    if (p != null) {
        Text(found?.let { "📍 $it" } ?: "📍 지역을 찾지 못했어요 (바다 위일 수 있어요)", style = AppType.body, modifier = Modifier.padding(top = 8.dp))
        Text(CafeMapLogic.coords(p), style = AppType.monoSmall, modifier = Modifier.padding(top = 2.dp))
    }
    FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("이 위치로 지정", enabled = enabled && p != null, onClick = {
            val point = p ?: return@PrimaryButton
            nav.previousBackStackEntry?.savedStateHandle?.set(DetailMapPick.KEY, MapPickResult.encode(point))
            leave()
        })
        GhostButton("취소", onClick = leave)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewPanel(
    nav: NavHostController,
    route: Route.DetailMap,
    content: DetailMapContent,
    selected: String?,
    onSelect: (DetailPin?) -> Unit,
    domestic: Boolean,
    mapShown: Boolean,
) {
    if (!content.loaded) return
    val cafes = route.layer == DetailMapLayer.CAFE
    val spot = if (cafes) content.cafes.firstOrNull { it.key == selected } else null
    val model = content.roasteries
    val item = if (!cafes) model?.items?.firstOrNull { it.name == selected } else null
    when {
        spot != null -> {
            CafeSpotPanel(spot, nav)
            GhostButton("선택 해제", small = true, onClick = { onSelect(null) }, modifier = Modifier.padding(top = 8.dp))
        }
        item != null && model != null -> {
            RoasteryPanel(
                item, model.pins.firstOrNull { it.item.name == item.name }, content.records, domestic,
                onOpenEntry = { nav.navigate(Route.EntryDetail(it)) },
            )
            GhostButton("선택 해제", small = true, onClick = { onSelect(null) }, modifier = Modifier.padding(top = 8.dp))
        }
        else -> {
            // without the map the list still opens each place's panel (and its map-app links)
            if (mapShown) HintText("두 손가락으로 늘리고 옮겨 길·건물·지명을 볼 수 있어요. 핀을 누르면 정보가 여기에 나와요.")
            val what = if (cafes) "카페" else "로스터리"
            if (content.pins.isEmpty()) {
                EmptyNote("상세 지도에 표시할 ${what}가 없어요. 위치를 지정하거나 지역을 적으면 여기에도 표시돼요.", Modifier.padding(top = 10.dp))
            } else {
                SectionLabel("지도에 표시한 $what", hint = "${content.pins.size}곳")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content.pins.forEach { pin -> GhostButton(DetailMapPins.labelOf(pin), small = true, onClick = { onSelect(pin) }) }
                }
            }
        }
    }
}

/** The OpenMapTiles / OpenStreetMap attribution, always on the map; it opens OpenStreetMap's copyright page. */
@Composable
private fun MapAttribution(modifier: Modifier) {
    Text(
        OpenFreeMap.ATTRIBUTION,
        style = AppType.small.copy(fontSize = 10.sp, lineHeight = 13.sp, color = Ink.textMuted),
        modifier = modifier
            .background(Ink.bg.copy(alpha = 0.88f))
            .clickable(role = Role.Button, onClickLabel = "OpenStreetMap 저작권 안내 열기") { openUrl(OpenFreeMap.OSM_COPYRIGHT_URL) }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** The picker's crosshair: ink arms with a white edge around a red dot (the SGIS picker's marker colour). */
@Composable
private fun Crosshair(modifier: Modifier) {
    Canvas(modifier.size(48.dp).clearAndSetSemantics { }) {
        val c = center
        val arm = 20.dp.toPx()
        val gap = 6.dp.toPx()
        listOf(Ink.surface to 4.dp.toPx(), Ink.text to 2.dp.toPx()).forEach { (color, width) ->
            drawLine(color, Offset(c.x - arm, c.y), Offset(c.x - gap, c.y), width)
            drawLine(color, Offset(c.x + gap, c.y), Offset(c.x + arm, c.y), width)
            drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y - gap), width)
            drawLine(color, Offset(c.x, c.y + gap), Offset(c.x, c.y + arm), width)
        }
        drawCircle(Ink.surface, 4.5.dp.toPx(), c)
        drawCircle(Ink.bad, 3.dp.toPx(), c)
    }
}

private fun backLabel(overseas: Boolean) = if (overseas) "← 세계지도로 돌아가기" else "← 한국 지도로 돌아가기"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlowNotice(overseas: Boolean, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(8.dp).fillMaxWidth().background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(10.dp)) {
        Text("지도를 불러오지 못하고 있어요. 인터넷 연결을 확인해 주세요.", style = AppType.small.copy(color = Ink.text))
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GhostButton("다시 시도", small = true, onClick = onRetry)
            GhostButton(backLabel(overseas), small = true, onClick = onBack)
        }
    }
}

/** No network, or no renderer: says why, and leads back to the SGIS map (it works offline). */
@Composable
private fun DetailMapFallback(status: DetailMapStatus, overseas: Boolean, onBack: () -> Unit, onRetry: () -> Unit) {
    val unsupported = status is DetailMapStatus.Failed && status.reason == DetailMapRenderer.UNSUPPORTED
    val (title, body) = when {
        status == DetailMapStatus.Offline -> "인터넷에 연결되어 있지 않아요" to
            "상세 지도는 보고 있는 지역의 도로·건물·지명을 인터넷(OpenFreeMap)에서 받아 와서 그려요. 연결한 뒤 ‘다시 시도’를 누르세요."
        unsupported -> "이 기기에서는 상세 지도를 아직 쓸 수 없어요" to "시·도와 시·군·구 경계가 있는 기본 지도로 위치를 보고 지정할 수 있어요."
        else -> "상세 지도를 표시하지 못했어요" to "기기에서 지도 그리기를 시작하지 못했어요. 잠시 뒤 다시 시도해 보세요."
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = AppType.cardTitle)
        Text(body, style = AppType.bodyMuted, modifier = Modifier.padding(top = 6.dp))
        Text(
            if (overseas) "세계지도는 인터넷 없이도 그대로 볼 수 있어요." else "한국 지도(시·도 · 시·군·구)는 인터넷 없이도 그대로 볼 수 있어요.",
            style = AppType.small, modifier = Modifier.padding(top = 6.dp),
        )
        PrimaryButton(backLabel(overseas), onClick = onBack, modifier = Modifier.padding(top = 16.dp))
        if (!unsupported) GhostButton("다시 시도", onClick = onRetry, modifier = Modifier.padding(top = 10.dp))
    }
}
