package com.coffeejournal.ui.bean

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.coffeejournal.domain.reference.Processes
import com.coffeejournal.ui.bean.a.BeanFormat
import com.coffeejournal.ui.bean.a.CountRow
import com.coffeejournal.ui.bean.a.ProcessMiscSection
import com.coffeejournal.ui.bean.a.ProcessMiscViewModel
import com.coffeejournal.ui.bean.a.ProcessStats
import com.coffeejournal.ui.bean.a.RecordLine
import com.coffeejournal.ui.bean.a.SearchField
import com.coffeejournal.ui.bean.a.SelectCard
import com.coffeejournal.ui.bean.a.rowLabel
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.SectionLabel
import org.koin.compose.viewmodel.koinViewModel

/** 가공 방식: search, the 4 main cards + 기타 table (→ ProcessDetail) and my own process list (web bean-view-process). */
@Composable
fun BeanProcessView(nav: NavHostController, data: BeanData) {
    val miscVm = koinViewModel<ProcessMiscViewModel>()
    var query by rememberSaveable { mutableStateOf("") }
    var etcOpen by rememberSaveable { mutableStateOf(false) }
    val results = remember(data.records, query) { ProcessStats.search(data.records, query) }
    val openEntry: (String) -> Unit = { id -> nav.navigate(Route.EntryDetail(id)) }
    val openProcess: (Processes.Process) -> Unit = { p -> nav.navigate(Route.ProcessDetail(name = p.name, seg = p.seg)) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 96.dp)) {
        item {
            SectionLabel("가공 방식 종류", modifier = Modifier.padding(top = 4.dp))
            SearchField(value = query, onValueChange = { query = it }, placeholder = "가공 방식 검색 · 예: 레드 허니, 더블 퍼멘티드")
            Spacer(Modifier.height(12.dp))
        }
        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                item { EmptyNote("“${query.trim()}”와 일치하는 가공 방식이 없어요.") }
            } else {
                items(results.size, key = { "s" + results[it].label }) { i ->
                    val group = results[i]
                    HairlineCard(Modifier.padding(bottom = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(group.label, style = AppType.cardTitle, modifier = Modifier.weight(1f))
                            Text(if (group.records.isEmpty()) "아직 안 마셔봄" else "${group.records.size}번", style = AppType.count)
                        }
                        group.records.forEach { record -> RecordLine(record, meta = "") { openEntry(BeanFormat.openEntryId(record)) } }
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Processes.main4.chunked(2).forEach { pair ->
                        // both cards of a row as tall as the taller one (web CSS grid rows stretch)
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { p ->
                                val tried = remember(data.records) { ProcessStats.isTried(data.records, p) }
                                SelectCard(selected = tried, onClick = { openProcess(p) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    Text(p.name, style = AppType.cardTitle)
                                    Text(p.en ?: "", style = AppType.faint)
                                    if (tried) Text("✓ 마셔봄", style = AppType.monoSmall.copy(color = Ink.good), modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }
                Text(
                    if (etcOpen) "기타 가공 방식 접기 ▴" else "기타 가공 방식 더보기 ▾",
                    style = AppType.small,
                    modifier = Modifier.fillMaxWidth().clickable { etcOpen = !etcOpen }.padding(vertical = 12.dp),
                )
                if (etcOpen) {
                    HairlineCard(padding = PaddingValues(horizontal = Dimens.cardPadding, vertical = 4.dp)) {
                        Processes.etc.forEach { p ->
                            val tried = remember(data.records) { ProcessStats.isTried(data.records, p) }
                            CountRow(
                                name = p.rowLabel() + if (tried) " ✓" else "",
                                trailing = "",
                                modifier = Modifier.clickable { openProcess(p) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        item {
            ProcessMiscSection(
                miscItems = data.miscItems,
                records = data.records,
                onSave = { id, name, notes -> miscVm.save(id, data.miscItems, name, notes) },
                onDelete = miscVm::delete,
                onOpen = openEntry,
            )
        }
    }
}
