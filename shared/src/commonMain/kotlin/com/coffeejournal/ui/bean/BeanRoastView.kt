package com.coffeejournal.ui.bean

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coffeejournal.domain.rules.CountryLookup
import com.coffeejournal.domain.rules.RoastFamily
import com.coffeejournal.ui.bean.a.BeanFormat
import com.coffeejournal.ui.bean.a.RecordRow
import com.coffeejournal.ui.bean.a.RoastStats
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.Seg
import com.coffeejournal.ui.theme.SubTabs

/** 배전도: records split into 라이트계 / 중간 / 다크계, with a 블렌드 filter for dark roasts (web renderRoastFamilyView). */
@Composable
fun BeanRoastView(nav: NavHostController, data: BeanData) {
    var family by rememberSaveable { mutableStateOf(RoastFamily.LIGHT) }
    var darkFilter by rememberSaveable { mutableStateOf(RoastStats.FILTER_ALL) }
    var sort by rememberSaveable { mutableStateOf(RoastStats.SORT_LATEST) }

    val counts = remember(data.records) { RoastStats.counts(data.records) }
    val records = remember(data.records, family, darkFilter, sort) { RoastStats.list(data.records, family, darkFilter, sort) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 96.dp)) {
        item {
            Column {
                Text("기록에 입력된 배전도를 라이트계·중간·다크계로 나눠서 보여줘요.", style = AppType.bodyMuted)
                Spacer(Modifier.height(14.dp))
                Seg(
                    options = RoastStats.families,
                    value = family,
                    onChange = { family = it },
                    allowClear = false,
                    labels = RoastStats.families.associateWith { RoastStats.label(RoastFamily.label(it), counts.of(it)) },
                )
                if (family == RoastFamily.DARK) {
                    Spacer(Modifier.height(10.dp))
                    SubTabs(
                        items = listOf(RoastStats.FILTER_ALL, RoastStats.FILTER_BLEND),
                        selected = darkFilter,
                        onSelect = { darkFilter = it },
                        labels = mapOf(
                            RoastStats.FILTER_ALL to RoastStats.label("전체", counts.dark),
                            RoastStats.FILTER_BLEND to RoastStats.label("블렌드", counts.darkBlend),
                        ),
                    )
                }
                Spacer(Modifier.height(10.dp))
                SubTabs(
                    items = listOf(RoastStats.SORT_LATEST, RoastStats.SORT_OLDEST),
                    selected = sort,
                    onSelect = { sort = it },
                    labels = mapOf(RoastStats.SORT_LATEST to "최신순", RoastStats.SORT_OLDEST to "오래된 순"),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        if (records.isEmpty()) {
            item { EmptyNote("이 배전도에 해당하는 기록이 아직 없어요.") }
        } else {
            items(records, key = { RoastStats.dedupeKey(it) }) { record ->
                val meta = listOf(
                    CountryLookup.bilingual(record.country), // web formatCountryBilingual: 국가 미상 when blank
                    record.region.trim(), record.variety.trim(), record.roast.trim(),
                ).filter { it.isNotEmpty() }.joinToString(" · ")
                RecordRow(
                    name = record.name.ifBlank { "이름 없는 원두" },
                    meta = meta,
                    kind = BeanFormat.kindWithPlace(record),
                    date = BeanFormat.date(record),
                    onClick = { nav.navigate(Route.EntryDetail(BeanFormat.openEntryId(record))) },
                )
            }
        }
    }
}
