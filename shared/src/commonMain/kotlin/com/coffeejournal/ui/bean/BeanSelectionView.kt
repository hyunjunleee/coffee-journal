package com.coffeejournal.ui.bean

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.ui.bean.b.BreakdownLine
import com.coffeejournal.ui.bean.b.FlatItemLogic
import com.coffeejournal.ui.bean.b.MiscItemCard
import com.coffeejournal.ui.bean.b.MiscItemsViewModel
import com.coffeejournal.ui.bean.b.SourceBeanRow
import com.coffeejournal.ui.bean.b.StatusSplitList
import com.coffeejournal.ui.bean.b.SubLabel
import com.coffeejournal.ui.bean.b.ofType
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.SectionLabel
import org.koin.compose.viewmodel.koinViewModel

/** 생두 수입사: importer cards with per-country counts and the beans seen from them (web #bean-view-selection). */
@Composable
fun BeanSelectionView(nav: NavHostController, data: BeanData) {
    val vm = koinViewModel<MiscItemsViewModel>()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
        PrimaryButton("+ 추가", onClick = { nav.navigate(Route.FlatItemForm(type = MiscType.SELECTION)) }, modifier = Modifier.padding(top = 16.dp))
        SectionLabel("생두 수입사")
        StatusSplitList(data.miscItems.ofType(MiscType.SELECTION), label = "생두 수입사", favoritable = false) { item ->
            MiscItemCard(
                item,
                onEdit = { nav.navigate(Route.FlatItemForm(type = MiscType.SELECTION, itemId = item.id)) },
                onDelete = { vm.delete(item.id) },
            ) { SelectionInfo(FlatItemLogic.selectionRecords(data.records, item.name)) }
        }
        Spacer(Modifier.height(96.dp))
    }
}

/** Web selectionCountryInfo: counts per country, the beans (with category badge) and where they were had. */
@Composable
private fun SelectionInfo(matching: List<BeanRecord>) {
    if (matching.isEmpty()) return
    SubLabel("국가별로 마셔본 횟수")
    FlatItemLogic.countryCounts(matching).forEach { (country, n) -> SourceBeanRow(country, right = "${n}번") }
    val beans = FlatItemLogic.distinctBeans(matching)
    if (beans.isNotEmpty()) {
        SubLabel("원두")
        beans.forEach { (name, cat) -> SourceBeanRow(name, badge = cat) }
    }
    BreakdownLine(matching)
}
