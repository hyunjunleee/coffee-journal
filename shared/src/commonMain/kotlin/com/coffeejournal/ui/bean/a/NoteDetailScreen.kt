package com.coffeejournal.ui.bean.a

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.ui.bean.BeanViewModel
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.ScreenTitleBar
import org.koin.compose.viewmodel.koinViewModel

/** Route.NoteDetail: every note combination this note appeared in, with the linked records (web renderNoteDetail). */
@Composable
fun NoteDetailScreen(nav: NavHostController, kind: String, noteKey: String) {
    val vm = koinViewModel<BeanViewModel>()
    val data by vm.data.collectAsStateWithLifecycle()
    val entry = remember(data.records, kind, noteKey) { NoteStats.aggregate(data.records, kind)[noteKey] }
    val combinations = remember(entry) { entry?.let { NoteStats.combinations(it, kind) } ?: emptyList() }
    var openGroups by remember { mutableStateOf(setOf(0)) }

    Column(Modifier.fillMaxSize()) {
        val title = when {
            entry != null -> "“${entry.label}”와 함께 기록된 노트 조합"
            data.loaded -> "노트 조합"
            else -> ""
        }
        ScreenTitleBar(title = title, onBack = { nav.popBackStack() })
        when {
            // nothing until the records have loaded, instead of a flash of the empty copy
            !data.loaded -> Unit
            entry == null -> EmptyNote("이 노트로 기록된 원두가 아직 없어요.", Modifier.padding(Dimens.gutter))
            else -> NoteCombinations(nav, kind, combinations, openGroups, onToggle = { index -> openGroups = if (index in openGroups) openGroups - index else openGroups + index })
        }
    }
}

@Composable
private fun NoteCombinations(nav: NavHostController, kind: String, combinations: List<NoteStats.Combination>, openGroups: Set<Int>, onToggle: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = 12.dp, bottom = 96.dp)) {
        combinations.forEachIndexed { index, group ->
            val open = index in openGroups
            item(key = "head$index") {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onToggle(index) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (open) AppIcons.chevronDown else AppIcons.chevronRight, contentDescription = null, tint = Ink.textFaint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(group.labels.joinToString(" · "), style = AppType.body, modifier = Modifier.weight(1f))
                    Text("${group.beanCount}개 원두", style = AppType.count)
                }
                Hairline()
            }
            if (open) {
                items(group.records.size, key = { "r$index-$it" }) { i ->
                    NoteContextCard(group.records[i], kind) { id -> nav.navigate(Route.EntryDetail(id)) }
                }
            }
        }
    }
}

/** Web .note-context-record: bean + badge, date · place, the note text and a link to the original record. */
@Composable
private fun NoteContextCard(record: BeanRecord, kind: String, onOpen: (String) -> Unit) {
    HairlineCard(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // weighted without fill: a long name wraps and the badge keeps its own width
                    Text(BeanFormat.displayName(record), style = AppType.cardTitle, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    CategoryBadge(record.category)
                }
                Text("${BeanFormat.date(record)} · ${BeanFormat.notePlace(record)}", style = AppType.small)
            }
            GhostButton("원래 기록 보기", small = true, onClick = { onOpen(BeanFormat.openEntryId(record)) })
        }
        if (kind == NoteStats.EXPECTED) {
            Spacer(Modifier.height(10.dp))
            Text("예상 노트", style = AppType.sectionLabel)
            Text(NoteStats.text(record, NoteStats.EXPECTED).ifEmpty { "기록 없음" }, style = AppType.body)
        }
        Spacer(Modifier.height(10.dp))
        Text("내가 쓴 커피 노트", style = AppType.sectionLabel)
        Text(NoteStats.text(record, NoteStats.ACTUAL).ifEmpty { "기록 없음" }, style = AppType.body)
    }
}
