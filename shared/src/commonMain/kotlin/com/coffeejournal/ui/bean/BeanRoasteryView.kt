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
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.SubTabs
import org.koin.compose.viewmodel.koinViewModel

/** 로스터리: scope tabs, the pin "map", and the favourite-first list (web #bean-view-source). */
@Composable
fun BeanRoasteryView(nav: NavHostController, data: BeanData) {
    val vm = koinViewModel<MiscItemsViewModel>()
    var scopeFilter by rememberSaveable { mutableStateOf(Scope.DOMESTIC) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val model = remember(data.miscItems, data.records, scopeFilter) { FlatItemLogic.roasteryMap(data.miscItems, data.records, scopeFilter) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
        SubTabs(
            items = listOf(Scope.DOMESTIC, Scope.OVERSEAS), selected = scopeFilter, onSelect = { scopeFilter = it; selected = null },
            labels = mapOf(Scope.DOMESTIC to "한국", Scope.OVERSEAS to "해외"), modifier = Modifier.padding(top = 12.dp),
        )
        RoasteryMapCard(model, scopeFilter, data.records, selected, onSelect = { selected = it }, onOpenEntry = { nav.navigate(Route.EntryDetail(it)) })
        PrimaryButton("+ 추가", onClick = { nav.navigate(Route.FlatItemForm(type = MiscType.SOURCE)) }, modifier = Modifier.padding(top = 18.dp))
        SectionLabel("로스터리")
        StatusSplitList(model.items, label = "로스터리", favoritable = true) { item ->
            MiscItemCard(
                item, favoritable = true, onToggleFavorite = { vm.toggleFavorite(item) },
                onEdit = { nav.navigate(Route.FlatItemForm(type = MiscType.SOURCE, itemId = item.id)) },
                onDelete = { vm.delete(item.id) },
            ) { SourceBeansInfo(FlatItemLogic.roasteryRecords(data.records, item.name)) }
        }
        Spacer(Modifier.height(96.dp))
    }
}
