package com.coffeejournal.ui.bean.a

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.domain.reference.Varieties
import com.coffeejournal.domain.rules.CountryLookup
import com.coffeejournal.ui.bean.BeanViewModel
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.ScreenTitleBar
import org.koin.compose.viewmodel.koinViewModel

/** Route.VarietyDetail: the variety profile — type, lineage, traits and my records (web .variety-profile). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VarietyDetailScreen(nav: NavHostController, varietyKey: String) {
    val vm = koinViewModel<BeanViewModel>()
    val data by vm.data.collectAsStateWithLifecycle()
    val index = remember(data.records) { VarietyStats.index(data.records) }
    val group = index[varietyKey] ?: VarietyStats.Group(varietyKey, Varieties.displayNames[varietyKey] ?: varietyKey, emptyList())
    val info = remember(varietyKey) { VarietyStats.lineage(varietyKey) }
    val unique = remember(group) { VarietyStats.uniqueRecords(group) }
    val recordGroups = remember(index, group) { VarietyStats.recordGroups(index, group) }
    val name = VarietyStats.label(group)

    Column(Modifier.fillMaxSize()) {
        ScreenTitleBar(title = name, onBack = { nav.popBackStack() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = 16.dp, bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(info.type, style = AppType.sectionLabel)
                        Text(name, style = AppType.title)
                    }
                    Text("${unique.size} cups", style = AppType.displayNumber.copy(fontSize = AppType.title.fontSize))
                }
                Spacer(Modifier.height(16.dp))
                Text("VARIETY LINEAGE", style = AppType.sectionLabel)
                Spacer(Modifier.height(8.dp))
                LineageRow(info, current = Varieties.displayNames[group.key] ?: group.display)
                Spacer(Modifier.height(14.dp))
                Text(info.desc, style = AppType.body)
                if (info.traits.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        info.traits.forEach { Chip(text = it) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("내가 마신 기록", style = AppType.cardTitle, modifier = Modifier.weight(1f))
                    Text("${VarietyStats.origins(unique)} origins", style = AppType.count)
                }
                Hairline(color = Ink.text, thickness = Dimens.rule)
                if (unique.isEmpty()) Text("아직 이 품종으로 마신 기록이 없어요.", style = AppType.bodyMuted, modifier = Modifier.padding(vertical = 12.dp))
            }
            recordGroups.forEach { rg ->
                item(key = "g" + rg.title) {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(rg.title, style = AppType.small.copy(color = Ink.text), modifier = Modifier.weight(1f))
                        Text("${rg.records.size} cups", style = AppType.count)
                    }
                    if (rg.records.isEmpty()) Text(rg.emptyText, style = AppType.faint, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(rg.records.size, key = { i -> rg.title + "|" + rg.records[i].entryId + "|" + rg.records[i].name + "|" + i }) { i ->
                    val r = rg.records[i]
                    val meta = listOf(
                        r.country.trim().takeIf { it.isNotEmpty() }?.let { CountryLookup.bilingual(it) } ?: "",
                        r.region.trim(), r.process.trim(),
                    ).filter { it.isNotEmpty() }.joinToString(" · ")
                    RecordRow(
                        name = r.name.ifBlank { "이름 없는 원두" }, meta = meta, kind = BeanFormat.kindWithPlace(r), date = BeanFormat.date(r),
                        onClick = { nav.navigate(Route.EntryDetail(BeanFormat.openEntryId(r))) },
                    )
                }
            }
            if (info.note != null) {
                item { Text(info.note!!, style = AppType.faint, modifier = Modifier.padding(top = 16.dp)) }
            }
        }
    }
}

/** parent1 · symbol · parent2 → current (web .variety-lineage). */
@Composable
private fun LineageRow(info: Varieties.Lineage, current: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LineageNode(info.parents.getOrElse(0) { "" }, info.parentNotes.getOrElse(0) { "" }, false, Modifier.weight(1f))
        Text(info.symbol, style = AppType.title, modifier = Modifier.padding(horizontal = 4.dp))
        LineageNode(info.parents.getOrElse(1) { "" }, info.parentNotes.getOrElse(1) { "" }, false, Modifier.weight(1f))
        Text("→", style = AppType.title, modifier = Modifier.padding(horizontal = 4.dp))
        LineageNode(current, "현재 품종", true, Modifier.weight(1.2f))
    }
}

@Composable
private fun LineageNode(name: String, note: String, current: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(if (current) Ink.accent else Ink.surface, RectangleShape)
            .border(Dimens.hairline, if (current) Ink.accent else Ink.line, RectangleShape)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = AppType.small.copy(color = if (current) Ink.bg else Ink.text), textAlign = TextAlign.Center)
        if (note.isNotBlank()) Text(note, style = AppType.faint.copy(color = if (current) Ink.line else Ink.textFaint), textAlign = TextAlign.Center)
    }
}
