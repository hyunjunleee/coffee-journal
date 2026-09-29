package com.coffeejournal.ui.bean

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.rules.CountryLookup
import com.coffeejournal.ui.bean.b.CountryList
import com.coffeejournal.ui.bean.b.FarmCardPositions
import com.coffeejournal.ui.bean.b.FarmSection
import com.coffeejournal.ui.bean.b.FlatItemLogic
import com.coffeejournal.ui.bean.b.MapPalette
import com.coffeejournal.ui.bean.b.MapSelectionPanel
import com.coffeejournal.ui.bean.b.MapStats
import com.coffeejournal.ui.bean.b.MiscItemsViewModel
import com.coffeejournal.ui.bean.b.WorldMapCanvas
import com.coffeejournal.ui.bean.b.ofType
import com.coffeejournal.ui.bean.b.rememberWorldMapState
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

private const val MAP_DESCRIPTION = "전 세계 주요 커피 생산국을 어두운 초록으로 표시했어요. 그중 지금까지 기록한 원두의 원산지는 강조색으로 칠해지고, 나라를 누르면 이름과 주요 산지가 나와요. 연두색 점은 국가별 대표 커피 산지 위치예요(두 손가락으로 확대하면 더 많은 산지가 보여요). 점선은 커피가 자라는 남·북회귀선(적도 기준 위도 23.5˚) 범위고요."

/** 커피 지도 + 농장(생산자): the web #bean-view-map as one vertical page. */
@Composable
fun BeanMapView(nav: NavHostController, data: BeanData) {
    val miscVm = koinViewModel<MiscItemsViewModel>()
    val mapState = rememberWorldMapState()
    // web .origin-country: each country opens and closes on its own, so two can be compared side by side
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var farmQuery by rememberSaveable { mutableStateOf("") }
    var highlightedFarm by remember { mutableStateOf<String?>(null) }
    var jumpTo by remember { mutableStateOf<String?>(null) }
    var panelY by remember { mutableIntStateOf(0) }
    var farmY by remember { mutableIntStateOf(0) }
    val farmCards = remember { FarmCardPositions() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // a café blend counts for the country of each of its beans
    val stats = remember(data.originRecords) { MapStats.compute(data.originRecords) }
    val byCountry = remember(data.originRecords) { MapStats.recordsByCountry(data.originRecords) }
    val triedRegions = remember(stats) { stats.flatMap { (en, s) -> s.regions.keys.map { "$en|$it" } }.toSet() }
    val total = remember(data.entries) { MapStats.totalCups(data.entries) }
    val farms = data.miscItems.ofType(MiscType.FARM)
    val openEntry: (String) -> Unit = { nav.navigate(Route.EntryDetail(it)) }
    fun showCountry(en: String) {
        mapState.selectCountry(en)
        scope.launch { scroll.animateScrollTo((panelY - with(density) { 220.dp.roundToPx() }).coerceAtLeast(0)) }
    }
    fun jumpToFarm(farm: String) {
        highlightedFarm = farm
        // a card hidden by the farm search cannot be shown, so the search is cleared first
        if (farms.any { it.name == farm } && farms.none { it.name == farm && FlatItemLogic.matchesSearch(it, farmQuery) }) farmQuery = ""
        jumpTo = farm
    }
    LaunchedEffect(highlightedFarm) { if (highlightedFarm != null) { delay(1600); highlightedFarm = null } }
    LaunchedEffect(jumpTo) {
        val farm = jumpTo ?: return@LaunchedEffect
        // let a cleared search compose and place the card first
        var target: Int? = null
        repeat(3) { if (target == null) { withFrameNanos { }; target = farmCards.centreTarget(farm, scroll.value) } }
        scroll.animateScrollTo((target ?: farmY).coerceIn(0, scroll.maxValue))
        jumpTo = null
    }

    Column(Modifier.fillMaxSize().onGloballyPositioned { farmCards.onViewport(it) }.verticalScroll(scroll).padding(horizontal = Dimens.gutter)) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.map, contentDescription = null, tint = Ink.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Coffee Map", style = AppType.title)
        }
        Text(MAP_DESCRIPTION, style = AppType.bodyMuted, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
        WorldMapCanvas(
            state = mapState, visited = byCountry.keys, triedRegions = triedRegions,
            onCountryTap = { mapState.selectCountry(it) },
            onRegionTap = { mapState.selectRegion(it.country.en, it.region.name) },
        )
        Legend()
        Text(
            if (stats.isEmpty()) "기록에 \"국가\"를 적어두면 지도에 표시돼요. 진한 초록으로 칠해진 나라는 커피가 나는 나라예요."
            else "지금까지 총 ${stats.size}개국의 원두를 마셔봤어요.",
            style = AppType.monoSmall.copy(fontSize = AppType.small.fontSize), modifier = Modifier.padding(top = 18.dp),
        )
        if (total.total > 0) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text("${total.total}", style = AppType.displayNumber)
                Text("잔 · 지금까지 마신 커피 (직접내림 ${total.brew} · 카페 ${total.cafe} · 커핑 ${total.cupping})", style = AppType.small, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }
        }
        MapSelectionPanel(
            selectedCountry = mapState.selectedCountry, selectedRegion = mapState.selectedRegion, stats = stats, byCountry = byCountry,
            onOpenEntry = openEntry,
            onFarmTap = ::jumpToFarm,
            modifier = Modifier.onGloballyPositioned { panelY = it.positionInParent().y.roundToInt() },
            onRegionTap = { region -> mapState.selectedCountry?.let { mapState.selectRegion(it, region) } },
        )
        CountryList(
            records = data.originRecords, byCountry = byCountry, expanded = expanded.toSet(),
            onToggle = { en -> expanded = if (en in expanded) expanded - en else expanded + en; mapState.selectCountry(en) },
            onOpenEntry = openEntry,
            onUntriedTap = { showCountry(it) },
        )
        FarmSection(
            farms = farms, records = data.originRecords, query = farmQuery, onQueryChange = { farmQuery = it },
            onAdd = { nav.navigate(Route.FlatItemForm(type = MiscType.FARM)) },
            onEdit = { nav.navigate(Route.FlatItemForm(type = MiscType.FARM, itemId = it.id)) },
            onDelete = { miscVm.delete(it.id) },
            onCountryTap = { raw -> CountryLookup.lookup(raw)?.let { showCountry(it.en) } },
            highlightedFarm = highlightedFarm,
            modifier = Modifier.onGloballyPositioned { farmY = it.positionInParent().y.roundToInt() },
            positions = farmCards,
        )
        Spacer(Modifier.height(96.dp))
    }
}

/** Web .map-legend (it wraps like the web's flex-wrap row). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem("커피 생산국") { Box(Modifier.size(11.dp).background(MapPalette.producer)) }
        LegendItem("내가 마셔본 나라") { Box(Modifier.size(11.dp).background(MapPalette.tasted)) }
        LegendItem("주요 산지") { Box(Modifier.size(8.dp).background(MapPalette.dot, CircleShape)) }
        LegendItem("마셔본 산지") { Box(Modifier.size(8.dp).background(MapPalette.triedDot, CircleShape).border(1.dp, MapPalette.tastedStroke, CircleShape)) }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(Modifier.width(6.dp))
        Text(label, style = AppType.faint)
    }
}
