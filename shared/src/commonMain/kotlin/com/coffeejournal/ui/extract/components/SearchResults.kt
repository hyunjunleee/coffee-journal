package com.coffeejournal.ui.extract.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.Category
import com.coffeejournal.ui.extract.ExtractGrouping
import com.coffeejournal.ui.extract.SearchGroup
import com.coffeejournal.ui.extract.SearchResult
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Badge
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink

/** Web renderAllBeanSearchResults: every category, grouped by core name. */
fun LazyListScope.searchResultItems(result: SearchResult, onOpenEntry: (String) -> Unit) {
    if (result.groups.isEmpty()) {
        item { EmptyNote("“${result.query}”와 일치하는 원두 기록이 없어요.", Modifier.padding(horizontal = Dimens.gutter, vertical = 8.dp)) }
        return
    }
    item { Text("전체 기록에서 ${result.total}건 찾았어요.", style = AppType.small, modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 8.dp)) }
    items(result.groups.size, key = { result.groups[it].key }) { i ->
        SearchGroupCard(result.groups[i], onOpenEntry, Modifier.padding(horizontal = Dimens.gutter, vertical = 5.dp))
    }
}

@Composable
private fun SearchGroupCard(group: SearchGroup, onOpenEntry: (String) -> Unit, modifier: Modifier = Modifier) {
    HairlineCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(group.displayName, style = AppType.cardTitle, modifier = Modifier.weight(1f))
            Text("${group.records.size}개 기록", style = AppType.monoSmall)
        }
        Spacer(Modifier.height(6.dp))
        group.records.forEach { record ->
            val row = ExtractGrouping.searchRow(record)
            Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge(row.badge, color = Ink.categoryColor(record.category.ifBlank { Category.BEAN }))
                        if (row.cuppingType.isNotBlank()) Text("· ${row.cuppingType}", style = AppType.small)
                    }
                    Text(listOf(row.dateText, row.place).filter { it.isNotBlank() }.joinToString(" · "), style = AppType.faint)
                }
                GhostButton("기록 보기", small = true, onClick = { onOpenEntry(row.entryId) })
            }
        }
    }
}
