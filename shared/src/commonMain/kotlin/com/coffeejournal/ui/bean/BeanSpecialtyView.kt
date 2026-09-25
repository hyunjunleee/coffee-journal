package com.coffeejournal.ui.bean

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coffeejournal.ui.bean.a.BeanFormat
import com.coffeejournal.ui.bean.a.CategoryBadge
import com.coffeejournal.ui.bean.a.CompetitionStats
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Badge
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink

/** ✦ Competition Lots: beans recorded with a cup score of 80+ (web renderSpecialtyList). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeanSpecialtyView(nav: NavHostController, data: BeanData) {
    val lots = remember(data.records) { CompetitionStats.lots(data.records) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "대회 컵 점수를 기록한 원두를 점수순으로 모아봐요. 현재 기록은 CoE 컵 점수 80점 이상부터 표시돼요.",
                style = AppType.bodyMuted, modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (lots.isEmpty()) {
            item { EmptyNote("아직 대회 컵 점수 80점 이상으로 기록한 원두가 없어요.") }
        } else {
            items(lots, key = { it.record.entryId + "|" + it.record.name }) { lot ->
                val record = lot.record
                HairlineCard(onClick = { nav.navigate(Route.EntryDetail(BeanFormat.openEntryId(record))) }) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        Text(BeanFormat.displayName(record), style = AppType.cardTitle)
                        CategoryBadge(record.category)
                        Badge(CompetitionStats.badge(lot), color = Ink.text, filled = true)
                    }
                    val origin = listOf(record.country.trim(), record.region.trim()).filter { it.isNotEmpty() }.joinToString(" · ")
                    if (origin.isNotEmpty()) Text(origin, style = AppType.small, modifier = Modifier.padding(top = 4.dp))
                    if (record.expectedNotes.isNotBlank()) Text(record.expectedNotes.trim(), style = AppType.body, modifier = Modifier.padding(top = 6.dp))
                    if (record.roasterDesc.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(record.roasterDesc.trim(), style = AppType.bodyMuted)
                    }
                }
            }
        }
    }
}
