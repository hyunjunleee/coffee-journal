package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.reference.CoffeeCountries
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.Ink

/** Web #coffee-country-list: visited countries per zone as an accordion (each opens on its own), then the producers still to try. */
@Composable
fun CountryList(
    records: List<BeanRecord>,
    byCountry: Map<String, List<BeanRecord>>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    onUntriedTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visited = byCountry.keys
    Column(modifier.fillMaxWidth()) {
        SectionHeading(
            "경험해본 산지",
            if (visited.isEmpty()) "아직 기록이 없어요" else "${visited.size} / ${CoffeeCountries.all.size} countries · ${records.size} coffees",
        )
        Hairline(color = Ink.text, thickness = 1.dp)
        MapStats.zoneSections(byCountry).forEach { zone ->
            Text(zone.english.uppercase(), style = AppType.sectionLabel, modifier = Modifier.padding(top = 13.dp, bottom = 2.dp))
            zone.countries.forEach { entry ->
                CountryAccordion(entry, entry.country.en in expanded, onToggle = { onToggle(entry.country.en) }, onOpenEntry = onOpenEntry)
                Hairline()
            }
        }
        UntriedSection(visited, onUntriedTap)
    }
}

@Composable
private fun CountryAccordion(entry: CountryListEntry, open: Boolean, onToggle: () -> Unit, onOpenEntry: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .clickable(onClickLabel = if (open) "접기" else "펼치기", role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (open) "펼쳐짐" else "접힘" }
                .padding(vertical = 10.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${entry.country.flag} ${entry.country.ko}", style = AppType.body)
                Text(entry.country.en, style = AppType.faint)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${entry.records.size} coffees", style = AppType.monoValue, textAlign = TextAlign.End)
                if (entry.latest.isNotEmpty()) Text("최근 ${entry.latest}", style = AppType.faint)
            }
            Spacer(Modifier.width(10.dp))
            Text(if (open) "⌃" else "⌄", style = AppType.body.copy(color = Ink.textFaint), modifier = Modifier.clearAndSetSemantics { })
        }
        if (open) {
            Column(Modifier.padding(start = 2.dp, end = 2.dp, bottom = 10.dp)) {
                if (entry.tags.isNotEmpty()) Text(entry.tags.joinToString(" · "), style = AppType.small, modifier = Modifier.padding(bottom = 6.dp))
                entry.regions.forEach { (region, list) ->
                    Text("$region · ${list.size}", style = AppType.monoSmall, modifier = Modifier.padding(top = 6.dp))
                    list.forEach { r -> RecordRow(r, onClick = { onOpenEntry(r.entryId) }) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UntriedSection(visited: Set<String>, onTap: (String) -> Unit) {
    val zones = MapStats.untriedByZone(visited)
    val total = zones.sumOf { it.second.size }
    if (total == 0) return
    SectionHeading("경험할 생산국", "$total countries")
    Hairline(color = Ink.text, thickness = 1.dp)
    zones.forEach { (english, countries) ->
        Text(english.uppercase(), style = AppType.sectionLabel, modifier = Modifier.padding(top = 13.dp, bottom = 4.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            countries.forEach { c ->
                Box(Modifier.heightIn(min = TapRowHeight).clickable(onClickLabel = "지도에서 보기", role = Role.Button) { onTap(c.en) }, contentAlignment = Alignment.CenterStart) {
                    Text("○ ${c.flag} ${c.ko} ${c.en}", style = AppType.faint)
                }
            }
        }
    }
}

/**
 * A list heading with a count on the right. The heading keeps its width and the count wraps under it when both do
 * not fit (a large font used to squeeze the heading into one letter per line).
 */
@Composable
private fun SectionHeading(title: String, count: String) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = AppType.cardTitle, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(10.dp))
        Text(count, style = AppType.count, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
