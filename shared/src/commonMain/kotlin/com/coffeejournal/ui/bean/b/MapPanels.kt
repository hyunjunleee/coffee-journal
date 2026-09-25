package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Ink

/** Web #map-selected-info: what the tapped country or region dot shows. */
@Composable
fun MapSelectionPanel(
    selectedCountry: String?,
    selectedRegion: String?,
    stats: Map<String, CountryStat>,
    byCountry: Map<String, List<BeanRecord>>,
    onOpenEntry: (String) -> Unit,
    onFarmTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 10.dp)) {
        if (selectedCountry == null) { Text("나라나 산지 점을 눌러보세요.", style = AppType.faint); return }
        val info = CoffeeCountries.byEn[selectedCountry]
        if (info == null) { Text("$selectedCountry — 커피 산지가 아니에요", style = AppType.small.copy(color = Ink.textFaint)); return }
        val countryRecords = byCountry[selectedCountry].orEmpty()
        if (selectedRegion != null) RegionPanel(info, selectedRegion, countryRecords, onOpenEntry, onFarmTap)
        else CountryPanel(info, stats[selectedCountry], countryRecords, onOpenEntry, onFarmTap)
    }
}

@Composable
private fun PanelTitle(flag: String, title: String, suffix: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(flag, style = AppType.body.copy(fontSize = 16.sp))
        Spacer(Modifier.width(6.dp))
        Text(title, style = AppType.body.copy(fontWeight = FontWeight.SemiBold))
        if (suffix.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Text(suffix, style = AppType.body) }
    }
}

@Composable
private fun CountryPanel(info: CoffeeCountries.Country, stat: CountryStat?, records: List<BeanRecord>, onOpenEntry: (String) -> Unit, onFarmTap: (String) -> Unit) {
    PanelTitle(info.flag, info.ko, "(${info.en})")
    if (records.isNotEmpty()) {
        val regions = stat?.regions.orEmpty()
        val prefix = if (regions.isNotEmpty()) "${regions.size}지역, " else ""
        val suffix = if (regions.isNotEmpty()) " (${regions.values.joinToString(", ") { it.label }})" else ""
        Text("$prefix${records.size}cup 마셔봤어요$suffix", style = AppType.small.copy(color = Ink.accent), modifier = Modifier.padding(top = 4.dp))
    }
    if (info.regions.isNotEmpty()) Text("주요 산지: ${info.regions.joinToString(", ") { it.name }}", style = AppType.faint, modifier = Modifier.padding(top = 4.dp))
    if (records.isNotEmpty()) {
        MapStats.groupByRegionThenFarm(records).forEach { group ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                SubLabel(group.region)
                Spacer(Modifier.width(6.dp))
                Text("${group.total}cup", style = AppType.faint)
            }
            group.farms.forEach { farm -> FarmGroup(farm, showCount = false, onOpenEntry, onFarmTap) }
        }
        BreakdownLine(records)
    }
}

@Composable
private fun RegionPanel(info: CoffeeCountries.Country, region: String, countryRecords: List<BeanRecord>, onOpenEntry: (String) -> Unit, onFarmTap: (String) -> Unit) {
    PanelTitle(info.flag, "${info.ko} · $region")
    val farms = MapStats.farmsForRegion(countryRecords, region)
    val matching = farms.flatMap { it.visits }
    if (matching.isEmpty()) {
        Text("${countryRecords.size}cup 전체", style = AppType.small.copy(color = Ink.accent), modifier = Modifier.padding(top = 4.dp))
        Text("이 지역으로 기록한 원두는 아직 없어요", style = AppType.faint, modifier = Modifier.padding(top = 3.dp))
        return
    }
    Text("${countryRecords.size}cup 전체 · ${matching.size}cup 이 지역", style = AppType.small.copy(color = Ink.accent), modifier = Modifier.padding(top = 4.dp))
    Column(Modifier.padding(top = 6.dp)) {
        farms.forEach { farm -> FarmGroup(farm, showCount = true, onOpenEntry, onFarmTap) }
        BreakdownLine(matching)
    }
}

/** Web .map-farm-group: farm row (tap jumps to the farm card) plus its visit rows. */
@Composable
private fun FarmGroup(farm: FarmVisits, showCount: Boolean, onOpenEntry: (String) -> Unit, onFarmTap: (String) -> Unit) {
    val known = farm.farm != MapStats.UNKNOWN_FARM
    val label = farm.farm + if (farm.subs.isNotEmpty()) " (${farm.subs.joinToString(", ")})" else ""
    SourceBeanRow(label, right = if (showCount) "${farm.visits.size}cup" else null, bold = true, onClick = if (known) ({ onFarmTap(farm.farm) }) else null, modifier = Modifier.padding(start = 10.dp))
    VisitRows(farm.visits, onOpenEntry)
}

/** Web visitRowsHtml: one range line for home brews, one tappable line per cafe / cupping visit. */
@Composable
fun VisitRows(visits: List<BeanRecord>, onOpenEntry: (String) -> Unit) {
    val s = MapStats.visitSummary(visits)
    if (s.brewCount > 0) {
        val extra = buildString {
            append(" (${s.brewCount}번)")
            if (s.processes.isNotEmpty()) append(" · ${s.processes.joinToString(", ")}")
            if (s.varieties.isNotEmpty()) append(" · ${s.varieties.joinToString(", ")}")
        }
        Row(Modifier.padding(start = 20.dp, top = 1.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.brewRange, style = AppType.faint)
            Spacer(Modifier.width(4.dp)); CategoryBadge(Category.BEAN); Spacer(Modifier.width(2.dp))
            Text(extra, style = AppType.faint, modifier = Modifier.weight(1f))
        }
    }
    s.others.forEach { v ->
        val rest = listOf(v.place.trim(), v.process.trim(), v.variety.trim()).filter { it.isNotEmpty() }.joinToString("") { " · $it" }
        Row(
            Modifier.fillMaxWidth().clickable { onOpenEntry(v.entryId) }.padding(start = 20.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Dates.ymdCompact(v.createdAt), style = AppType.faint)
            Spacer(Modifier.width(4.dp)); CategoryBadge(v.category); Spacer(Modifier.width(2.dp))
            Text(rest, style = AppType.faint, modifier = Modifier.weight(1f))
        }
    }
}
