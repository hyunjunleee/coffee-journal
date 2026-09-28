package com.coffeejournal.ui.bean

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.ui.bean.b.FlatItemLogic
import com.coffeejournal.ui.bean.b.MiscItemCard
import com.coffeejournal.ui.bean.b.MiscItemsViewModel
import com.coffeejournal.ui.bean.b.RoasteryMapCard
import com.coffeejournal.ui.bean.b.SourceBeansInfo
import com.coffeejournal.ui.bean.b.StatusSplitList
import com.coffeejournal.ui.map.CafeMapSection
import com.coffeejournal.ui.map.detail.rememberDetailMapSupported
import com.coffeejournal.ui.map.rememberKoreaMapState
import com.coffeejournal.ui.map.rememberWorldPinMapState
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.Seg
import com.coffeejournal.ui.theme.SubTabs
import org.koin.compose.viewmodel.koinViewModel

/** Layers of the 한국 map: roasteries, or the cafés (of café records, and added by hand; design v2 §1.4). */
private object MapLayer {
    const val ROASTERY = "로스터리"
    const val CAFE = "카페"
}

/**
 * 로스터리: scope tabs, the map (국내: SGIS Korea map with a 로스터리 | 카페 toggle; 해외: world map), and the
 * favourite-first list (web #bean-view-source). Each layer adds its own kind right under its map: "+ 로스터리 추가"
 * (in the tab's 국내/해외) or "+ 카페 추가".
 */
@Composable
fun BeanRoasteryView(nav: NavHostController, data: BeanData) {
    val vm = koinViewModel<MiscItemsViewModel>()
    var scopeFilter by rememberSaveable { mutableStateOf(Scope.DOMESTIC) }
    var layer by rememberSaveable { mutableStateOf(MapLayer.ROASTERY) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val koreaState = rememberKoreaMapState()
    val worldState = rememberWorldPinMapState()
    val model = remember(data.miscItems, data.records, scopeFilter) { FlatItemLogic.roasteryMap(data.miscItems, data.records, scopeFilter) }
    val cafes = scopeFilter == Scope.DOMESTIC && layer == MapLayer.CAFE
    val detailMap = rememberDetailMapSupported()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
        SubTabs(
            items = listOf(Scope.DOMESTIC, Scope.OVERSEAS), selected = scopeFilter, onSelect = { scopeFilter = it; selected = null },
            labels = mapOf(Scope.DOMESTIC to "한국", Scope.OVERSEAS to "해외"), modifier = Modifier.padding(top = 12.dp),
        )
        if (scopeFilter == Scope.DOMESTIC) {
            // "지도" in the labels keeps them apart from the 로스터리 sub tab above
            Seg(
                options = listOf(MapLayer.ROASTERY, MapLayer.CAFE), value = layer, onChange = { layer = it; selected = null },
                allowClear = false, labels = mapOf(MapLayer.ROASTERY to "로스터리 지도", MapLayer.CAFE to "카페 지도"),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (cafes) {
            CafeMapSection(nav, koreaState)
        } else {
            RoasteryMapCard(
                model, scopeFilter, data.records, selected, onSelect = { selected = it }, onOpenEntry = { nav.navigate(Route.EntryDetail(it)) },
                koreaState = koreaState, worldState = worldState,
                onOpenDetailMap = if (detailMap) { route -> nav.navigate(route) } else null,
                onEdit = { item -> nav.navigate(Route.FlatItemForm(type = MiscType.SOURCE, itemId = item.id)) },
                onAdd = { nav.navigate(Route.FlatItemForm(type = MiscType.SOURCE, scope = scopeFilter)) },
            )
            SectionLabel("로스터리")
            StatusSplitList(model.items, label = "로스터리", favoritable = true) { item ->
                MiscItemCard(
                    item, favoritable = true, onToggleFavorite = { vm.toggleFavorite(item) },
                    onEdit = { nav.navigate(Route.FlatItemForm(type = MiscType.SOURCE, itemId = item.id)) },
                    onDelete = { vm.delete(item.id) },
                ) { SourceBeansInfo(FlatItemLogic.roasteryRecords(data.records, item.name)) }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}
