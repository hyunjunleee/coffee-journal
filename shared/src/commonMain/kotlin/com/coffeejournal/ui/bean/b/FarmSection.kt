package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton

/** Farm (producer) cards under the coffee map (web #bean-view-map lower half). */
@Composable
fun FarmSection(
    farms: List<MiscItem>,
    records: List<BeanRecord>,
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (MiscItem) -> Unit,
    onDelete: (MiscItem) -> Unit,
    onCountryTap: (String) -> Unit,
    highlightedFarm: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        PrimaryButton("+ 추가", onClick = onAdd, modifier = Modifier.padding(top = 24.dp))
        Row(Modifier.padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) { Text("농장(생산자)", style = AppType.sectionLabel) }
        AppTextField(
            value = query, onValueChange = onQueryChange, placeholder = "농장·생산자 검색",
            trailing = { Icon(AppIcons.search, contentDescription = null, tint = Ink.textFaint) },
        )
        Spacer(Modifier.height(10.dp))
        val filtered = farms.filter { FlatItemLogic.matchesSearch(it, query) }
        StatusSplitList(filtered, label = "농장(생산자)", favoritable = false) { item ->
            MiscItemCard(item, onEdit = { onEdit(item) }, onDelete = { onDelete(item) }, highlighted = item.name == highlightedFarm) {
                FarmCountryInfo(FlatItemLogic.farmRecords(records, item.name), onCountryTap)
            }
        }
    }
}

/** Web farmCountryInfo: "국가별로 마셔본 횟수 (눌러서 지도 보기)" rows that jump to the map. */
@Composable
fun FarmCountryInfo(matching: List<BeanRecord>, onCountryTap: (String) -> Unit) {
    if (matching.isEmpty()) return
    SubLabel("국가별로 마셔본 횟수 (눌러서 지도 보기)")
    FlatItemLogic.countryCounts(matching).forEach { (country, n) ->
        SourceBeanRow(country, right = "${n}번", onClick = { onCountryTap(country) })
    }
    BreakdownLine(matching)
}
