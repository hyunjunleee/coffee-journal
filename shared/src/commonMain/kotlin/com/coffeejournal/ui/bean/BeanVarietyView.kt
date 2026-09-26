package com.coffeejournal.ui.bean

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coffeejournal.ui.bean.a.CountRow
import com.coffeejournal.ui.bean.a.PlaceBadges
import com.coffeejournal.ui.bean.a.SearchField
import com.coffeejournal.ui.bean.a.VarietyStats
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.Seg
import com.coffeejournal.ui.theme.SubTabs

private const val BY_VARIETY = "byVariety"
private const val BY_COUNTRY = "byCountry"

/** 품종: lineage explorer index (→ VarietyDetail) or varieties grouped by country (web bean-view-variety). */
@Composable
fun BeanVarietyView(nav: NavHostController, data: BeanData) {
    var mode by rememberSaveable { mutableStateOf(BY_VARIETY) }
    var query by rememberSaveable { mutableStateOf("") }
    var species by rememberSaveable { mutableStateOf(VarietyStats.ARABICA) }
    var sort by rememberSaveable { mutableStateOf(VarietyStats.SORT_ALPHA) }

    val index = remember(data.records) { VarietyStats.index(data.records) }
    val explorer = remember(index, species, query, sort) { VarietyStats.explorer(index, species, query, sort) }
    val byCountry = remember(data.records) { VarietyStats.byCountry(data.records) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 96.dp)) {
        item {
            SubTabs(
                items = listOf(BY_VARIETY, BY_COUNTRY), selected = mode, onSelect = { mode = it },
                labels = mapOf(BY_VARIETY to "품종별로 보기", BY_COUNTRY to "국가별로 보기"),
            )
            Spacer(Modifier.height(14.dp))
        }
        if (mode == BY_VARIETY) {
            item {
                SearchField(value = query, onValueChange = { query = it }, placeholder = "품종 검색 · 예: Typica, 게이샤, 74110")
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("커피 품종 계보", style = AppType.cardTitle, modifier = Modifier.weight(1f))
                    Text("● 마셔봄 · ○ 아직", style = AppType.monoSmall)
                }
                Spacer(Modifier.height(8.dp))
                SubTabs(
                    items = listOf(VarietyStats.SORT_ALPHA, VarietyStats.SORT_LINEAGE), selected = sort, onSelect = { sort = it },
                    labels = mapOf(VarietyStats.SORT_ALPHA to "알파벳순", VarietyStats.SORT_LINEAGE to "계보순"),
                )
                Spacer(Modifier.height(10.dp))
                Seg(
                    options = VarietyStats.speciesTabs.map { it.first },
                    value = explorer.species,
                    onChange = { species = it },
                    allowClear = false,
                    labels = VarietyStats.speciesTabs.associate { (key, label) ->
                        val n = VarietyStats.speciesCount(index, key)
                        key to if (n > 0) "$label · $n" else label
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
            if (explorer.items.isEmpty()) {
                item { EmptyNote(if (explorer.query.isNotEmpty()) "검색 결과가 없어요." else "아직 품종을 적어둔 기록이 없어요.") }
            } else {
                items(explorer.items, key = { it.key }) { group ->
                    val tried = group.records.isNotEmpty()
                    Column(Modifier.fillMaxWidth().clickable { nav.navigate(Route.VarietyDetail(varietyKey = group.key)) }) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (tried) "● " else "○ ", style = AppType.body.copy(color = if (tried) Ink.text else Ink.textFaint))
                            Text(VarietyStats.label(group), style = AppType.body.copy(color = if (tried) Ink.text else Ink.textMuted), modifier = Modifier.weight(1f))
                            if (tried) Text("${VarietyStats.uniqueRecords(group).size} cups", style = AppType.count)
                        }
                        Hairline()
                    }
                }
            }
        } else {
            item { SectionLabel("나라별로 마셔본 품종", modifier = Modifier.padding(top = 0.dp)) }
            if (byCountry.isEmpty()) {
                item { EmptyNote("아직 품종을 적어둔 기록이 없어요.") }
            } else {
                items(byCountry, key = { it.country }) { group ->
                    HairlineCard(Modifier.padding(bottom = 10.dp)) {
                        Text(group.country, style = AppType.cardTitle)
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            group.rows.forEach { row ->
                                CountRow(name = row.display, trailing = "${row.records.size}번", extra = { PlaceBadges(row.records) })
                            }
                        }
                    }
                }
            }
        }
    }
}
