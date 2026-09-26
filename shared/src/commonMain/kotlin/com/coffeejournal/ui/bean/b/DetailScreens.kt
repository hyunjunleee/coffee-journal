package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.ui.map.MapLinkButtons
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.SectionLabel
import org.koin.compose.viewmodel.koinViewModel

/** Route.CountryDetail: the map panel for one country plus every record from it, as a stand-alone page. */
@Composable
fun CountryDetailScreen(nav: NavHostController, en: String) {
    val vm = koinViewModel<BeanExtraDataViewModel>()
    val data by vm.data.collectAsStateWithLifecycle()
    val stats = remember(data.records) { MapStats.compute(data.records) }
    val byCountry = remember(data.records) { MapStats.recordsByCountry(data.records) }
    val country = CoffeeCountries.byEn[en]
    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        ScreenTitleBar(country?.let { "${it.flag} ${it.ko}" } ?: en, onBack = { nav.popBackStack() })
        // nothing until the records have loaded, instead of a flash of the empty copy
        if (data.loaded) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            MapSelectionPanel(en, null, stats, byCountry, onOpenEntry = { nav.navigate(Route.EntryDetail(it)) }, onFarmTap = {})
            if (country != null) {
                val entry = MapStats.countryEntry(country, byCountry[en].orEmpty())
                SectionLabel("기록")
                if (entry.records.isEmpty()) EmptyNote("아직 이 나라 원두 기록이 없어요.")
                entry.regions.forEach { (region, list) ->
                    Text("$region · ${list.size}", style = AppType.monoSmall, modifier = Modifier.padding(top = 6.dp))
                    list.forEach { r -> RecordRow(r, onClick = { nav.navigate(Route.EntryDetail(r.entryId)) }) }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** Route.RoasteryDetail: one roastery's location, beans bought there and linked records. */
@Composable
fun RoasteryDetailScreen(nav: NavHostController, name: String) {
    val vm = koinViewModel<BeanExtraDataViewModel>()
    val data by vm.data.collectAsStateWithLifecycle()
    val item = data.miscItems.firstOrNull { it.type == MiscType.SOURCE && it.name == name }
    val recs = remember(data.records, name) { FlatItemLogic.roasteryRecords(data.records, name).sortedByDescending { it.createdAt } }
    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        ScreenTitleBar(name, onBack = { nav.popBackStack() })
        if (data.loaded) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            Spacer(Modifier.height(12.dp))
            if (item?.location?.isNotBlank() == true) Text(item.location, style = AppType.small)
            if (item?.notes?.isNotBlank() == true) Text(item.notes, style = AppType.bodyMuted, modifier = Modifier.padding(top = 4.dp))
            if (item != null) MapLinkButtons(item.name, item.location, item.point, overseas = item.scope == Scope.OVERSEAS)
            SourceBeansInfo(recs)
            SectionLabel("연결된 기록")
            if (recs.isEmpty()) EmptyNote("연결된 원두 기록이 없어요.")
            recs.forEach { r -> RecordRow(r, onClick = { nav.navigate(Route.EntryDetail(r.entryId)) }) }
            Spacer(Modifier.height(96.dp))
        }
    }
}
