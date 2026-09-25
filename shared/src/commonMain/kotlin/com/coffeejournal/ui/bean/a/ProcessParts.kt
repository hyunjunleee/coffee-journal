package com.coffeejournal.ui.bean.a

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.reference.Processes
import com.coffeejournal.domain.rules.BeanRecords
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Ink

/** Web colHtml + categoryBreakdownLine + 직접 내린 원두: the body shared by the detail screen and the misc cards. */
@Composable
internal fun ProcessBreakdownBody(breakdown: ProcessStats.Breakdown, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        CountColumn("국가별로 마셔본 횟수", breakdown.countries)
        CountColumn("품종별로 마셔본 횟수", breakdown.varieties)
        if (breakdown.subs.isNotEmpty()) CountColumn("세부 종류별로 마셔본 횟수", breakdown.subs)
        val where = BeanRecords.categoryBreakdown(breakdown.matching)
        if (where.isNotEmpty()) {
            DetailLabel("어디서 마셨는지")
            WhereBadges(breakdown.matching)
        }
        val brews = ProcessStats.brewGroups(breakdown.matching)
        if (brews.isNotEmpty()) {
            DetailLabel("직접 내린 원두")
            brews.forEach { group ->
                Text(group.displayName, style = AppType.small.copy(color = Ink.text), modifier = Modifier.padding(top = 6.dp))
                val brewVisits = group.visits.filter { it.category.ifBlank { Category.BEAN } == Category.BEAN }
                if (brewVisits.isNotEmpty()) VisitRow(BeanFormat.brewRange(brewVisits), Category.BEAN, null)
                group.visits.filter { it.category.ifBlank { Category.BEAN } != Category.BEAN }
                    .sortedByDescending { it.createdAt }
                    .forEach { v -> VisitRow(BeanFormat.visitLine(v), v.category) { onOpen(BeanFormat.openEntryId(v)) } }
            }
        }
    }
}

@Composable
private fun CountColumn(label: String, list: List<Pair<String, Int>>) {
    DetailLabel(label)
    if (list.isEmpty()) Text("아직 없어요", style = AppType.faint, modifier = Modifier.padding(vertical = 4.dp))
    else list.forEach { (name, count) -> CountRow(name, "${count}번") }
}

/** Web categoryBreakdownLine: "직접 내림 N종 · 카페 N번 · 커핑 N번" rendered as badges + counts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhereBadges(matching: List<BeanRecord>) {
    val brews = matching.filter { it.category.ifBlank { Category.BEAN } == Category.BEAN }.map { BeanFormat.beanKey(it) }.toSet().size
    val cafes = matching.count { it.category == Category.CAFE }
    val cuppings = matching.count { it.category == Category.CUPPING }
    FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Category.BEAN to brews, Category.CAFE to cafes, Category.CUPPING to cuppings).filter { it.second > 0 }.forEach { (cat, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(cat)
                Spacer(Modifier.width(4.dp))
                Text("$n${if (cat == Category.BEAN) "종" else "번"}", style = AppType.small.copy(color = Ink.text))
            }
        }
    }
}

/** Web .map-visit-row: one visit line; tappable when it belongs to a single record. */
@Composable
private fun VisitRow(text: String, category: String, onClick: (() -> Unit)?) {
    val base = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    Row(if (onClick != null) base.clickable(onClick = onClick) else base, verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(category)
        Spacer(Modifier.width(6.dp))
        Text(text, style = AppType.small)
    }
}

/** Card label "이름 (English)" for the etc table rows. */
internal fun Processes.Process.rowLabel(): String = if (en != null) "$name ($en)" else name
