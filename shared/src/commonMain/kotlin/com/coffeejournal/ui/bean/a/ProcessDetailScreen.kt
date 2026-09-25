package com.coffeejournal.ui.bean.a

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.ui.bean.BeanViewModel
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.ScreenTitleBar
import org.koin.compose.viewmodel.koinViewModel

/** Route.ProcessDetail: one processing method — honey subtypes, or country/variety/subtype/place breakdowns (web renderProcessDetail). */
@Composable
fun ProcessDetailScreen(nav: NavHostController, name: String, seg: String?) {
    val vm = koinViewModel<BeanViewModel>()
    val data by vm.data.collectAsStateWithLifecycle()
    val breakdown = remember(data.records, name, seg) { ProcessStats.breakdown(data.records, name, seg) }
    val open: (String) -> Unit = { id -> nav.navigate(Route.EntryDetail(id)) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitleBar(title = name, onBack = { nav.popBackStack() })
        when {
            breakdown.matching.isEmpty() -> LazyColumn(contentPadding = PaddingValues(Dimens.gutter)) {
                item {
                    DetailCard(title = name) {
                        Text(
                            "아직 이 가공방식으로 기록한 원두가 없어요. \"가공 방식\"을 \"기타\"로 하고 \"$name\"라고 적으면 여기 잡혀요.",
                            style = AppType.bodyMuted, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            seg == "허니" -> HoneyDetail(breakdown, open)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = 12.dp, bottom = 96.dp)) {
                item { DetailCard(title = name) { ProcessBreakdownBody(breakdown, open) } }
            }
        }
    }
}

/** Honey: subtype list (블랙/레드/옐로/화이트/골든, 괄호 안 텍스트, 세부 미기록) then the records of the selected subtype. */
@Composable
private fun HoneyDetail(breakdown: ProcessStats.Breakdown, onOpen: (String) -> Unit) {
    val groups = remember(breakdown) { ProcessStats.honeyGroups(breakdown.matching) }
    var selectedSub by rememberSaveable { mutableStateOf("") }
    val selected = groups.firstOrNull { it.first == selectedSub }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = 12.dp, bottom = 96.dp)) {
        item {
            DetailCard(title = "허니 세부 종류") {
                Spacer(Modifier.height(6.dp))
                groups.forEach { (sub, records) ->
                    val on = sub == selectedSub
                    Column(Modifier.fillMaxWidth().background(if (on) Ink.accentSoft else Ink.surface).clickable { selectedSub = if (on) "" else sub }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(sub, style = AppType.body, modifier = Modifier.weight(1f))
                            Text("${records.size}번 ›", style = AppType.count)
                        }
                        Hairline()
                    }
                }
            }
        }
        if (selected != null) {
            item { DetailLabel("${selected.first}로 마신 기록", Modifier.padding(top = 6.dp)) }
            items(selected.second, key = { it.entryId + "|" + it.name + "|" + it.createdAt }) { record ->
                val meta = listOf(BeanFormat.categoryLabel(record.category), record.place.trim(), record.country.trim())
                    .filter { it.isNotEmpty() }.joinToString(" · ")
                RecordLine(record, meta) { onOpen(BeanFormat.openEntryId(record)) }
            }
        }
    }
}
