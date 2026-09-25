package com.coffeejournal.ui.extract

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.NoteCanon
import com.coffeejournal.domain.rules.PantryRules
import com.coffeejournal.ui.extract.components.Eyebrow
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.SubTabs
import org.koin.compose.viewmodel.koinViewModel

private val SORT_LABELS = mapOf(PantryListing.SORT_REGISTERED to "등록순", PantryListing.SORT_PEAK to "예상 피크 빠른 순")

/** Web #bean-pantry-panel as a full screen: unopened bags, then the opened ("마시는 중") ones. */
@Composable
fun PantryScreen(nav: NavHostController) {
    val vm = koinViewModel<PantryViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmOpen by remember { mutableStateOf<PantryItem?>(null) }
    var confirmDelete by remember { mutableStateOf<PantryItem?>(null) }
    val edit = { item: PantryItem -> nav.navigate(Route.PantryEditor(item.id)) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitleBar(
            title = "원두 보관함 · ${state.unopened.size}봉", onBack = { nav.popBackStack() },
            action = { GhostButton("+ 원두 추가", small = true, onClick = { nav.navigate(Route.PantryEditor()) }, modifier = Modifier.padding(end = 8.dp)) },
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                SubTabs(
                    items = SORT_LABELS.keys.toList(), selected = state.sortMode, onSelect = vm::setSortMode, labels = SORT_LABELS,
                    modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 10.dp),
                )
            }
            if (state.loaded && state.unopened.isEmpty()) {
                item { EmptyNote("아직 개봉하지 않은 원두가 없습니다.", Modifier.padding(horizontal = Dimens.gutter, vertical = 6.dp)) }
            }
            items(state.unopened, key = { "u:" + it.id }) { item ->
                PantryCard(item, opened = false, modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 5.dp)) {
                    GhostButton("수정", small = true, onClick = { edit(item) })
                    GhostButton("개봉함", small = true, onClick = { confirmOpen = item })
                    GhostButton("삭제", small = true, danger = true, onClick = { confirmDelete = item })
                }
            }
            if (state.opened.isNotEmpty()) {
                item { SectionLabel("마시는 중 (개봉)", Modifier.padding(horizontal = Dimens.gutter)) }
                items(state.opened, key = { "o:" + it.id }) { item ->
                    PantryCard(item, opened = true, modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 5.dp)) {
                        GhostButton("수정", small = true, onClick = { edit(item) })
                        GhostButton("개봉 취소", small = true, onClick = { vm.cancelOpen(item) })
                        GhostButton("삭제", small = true, danger = true, onClick = { confirmDelete = item })
                    }
                }
            }
        }
    }

    confirmOpen?.let { item ->
        ConfirmDialog(PantryListing.openConfirmText(item), confirmLabel = "개봉함", onConfirm = { vm.open(item); confirmOpen = null }, onDismiss = { confirmOpen = null })
    }
    confirmDelete?.let { item ->
        ConfirmDialog("'${item.name}' 항목을 보관함에서 삭제할까요?", confirmLabel = "삭제", danger = true, onConfirm = { vm.delete(item); confirmDelete = null }, onDismiss = { confirmDelete = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PantryCard(item: PantryItem, opened: Boolean, modifier: Modifier = Modifier, actions: @Composable () -> Unit) {
    HairlineCard(modifier) {
        if (opened) Eyebrow("개봉 " + Dates.ymdCompact(item.openedAt ?: item.createdAt))
        Text(item.name.ifBlank { "이름 없음" }, style = AppType.cardTitle)
        val meta = PantryListing.metaLine(item)
        if (meta.isNotBlank()) Text(meta, style = AppType.small)
        val window = PantryRules.drinkWindowText(item)
        if (window.isNotBlank()) Text(window, style = AppType.small)
        val notes = NoteCanon.parseChips(item.expectedNotes)
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Expected Notes", style = AppType.monoSmall)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { notes.forEach { Chip(it) } }
        }
        if (item.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(item.notes, style = AppType.bodyMuted)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

@Composable
private fun ConfirmDialog(text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = Ink.surface,
        text = { Text(text, style = AppType.body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, style = AppType.body.copy(color = if (danger) Ink.bad else Ink.accent)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", style = AppType.body) } },
    )
}
