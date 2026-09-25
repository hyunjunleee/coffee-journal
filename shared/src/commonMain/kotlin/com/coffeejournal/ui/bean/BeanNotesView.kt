package com.coffeejournal.ui.bean

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coffeejournal.ui.bean.a.FlavorCategoryGrid
import com.coffeejournal.ui.bean.a.FlavorWheelLegend
import com.coffeejournal.ui.bean.a.NoteStats
import com.coffeejournal.ui.bean.a.SearchField
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.Seg
import com.coffeejournal.ui.theme.SubTabs

/** 커피 노트: expected / actual toggle, flavor wheel legend, 9 flavor families and the note cloud (web renderNoteCloud). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeanNotesView(nav: NavHostController, data: BeanData) {
    var kind by rememberSaveable { mutableStateOf(NoteStats.EXPECTED) }
    var selectedCategory by rememberSaveable { mutableIntStateOf(-1) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(NoteStats.SORT_COUNT) }

    val aggregate = remember(data.records, kind) { NoteStats.aggregate(data.records, kind) }
    val cloud = remember(aggregate, query, sort) { NoteStats.filterAndSort(aggregate.values, query, sort) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 96.dp)) {
        item {
            Seg(
                options = listOf(NoteStats.EXPECTED, NoteStats.ACTUAL),
                value = kind,
                onChange = { kind = it },
                allowClear = false,
                labels = mapOf(NoteStats.EXPECTED to "예상 노트", NoteStats.ACTUAL to "내가 느낀 노트"),
            )
            SectionLabel("플레이버 휠")
            FlavorWheelLegend()
            SectionLabel("향미 분류", modifier = Modifier.padding(top = 6.dp))
            FlavorCategoryGrid(selected = selectedCategory, onSelect = { idx -> selectedCategory = if (selectedCategory == idx) -1 else idx })
            Spacer(Modifier.height(20.dp))
            Text(NoteStats.description(kind), style = AppType.bodyMuted)
            Spacer(Modifier.height(12.dp))
            SearchField(value = query, onValueChange = { query = it }, label = "노트와 내가 쓴 표현 검색", placeholder = "자스민, 노란 꽃차, 풋풋한")
            Spacer(Modifier.height(10.dp))
            SubTabs(
                items = listOf(NoteStats.SORT_COUNT, NoteStats.SORT_ALPHA),
                selected = sort,
                onSelect = { sort = it },
                labels = mapOf(NoteStats.SORT_COUNT to "많이 나온 순", NoteStats.SORT_ALPHA to "가나다순"),
            )
            Spacer(Modifier.height(12.dp))
        }
        item {
            if (cloud.isEmpty()) {
                EmptyNote(NoteStats.emptyText(kind, query))
            } else {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cloud.forEach { info ->
                        Chip(
                            text = "${info.label} ${info.beanCount}",
                            onClick = { nav.navigate(Route.NoteDetail(kind = kind, noteKey = info.key)) },
                        )
                    }
                }
            }
        }
    }
}
