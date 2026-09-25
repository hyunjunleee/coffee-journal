package com.coffeejournal.ui.extract

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.ui.extract.components.BeanGroupCard
import com.coffeejournal.ui.extract.components.DdaySection
import com.coffeejournal.ui.extract.components.DrinkingRow
import com.coffeejournal.ui.extract.components.GroupActions
import com.coffeejournal.ui.extract.components.SmallPackCardView
import com.coffeejournal.ui.extract.components.searchResultItems
import com.coffeejournal.ui.theme.imeOverlapPadding
import com.coffeejournal.ui.nav.FormMode
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.GlyphButton
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.SubTabs
import com.coffeejournal.ui.theme.TopHeader
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val FILTER_LABELS = mapOf(ExtractFilter.ALL to "전체", ExtractFilter.SMALLPACK to "드립백 / 소량")
private val SMALL_LABELS = mapOf(SmallPackFilter.ALL to "전체", SmallPackFilter.DRIPBAG to "드립백", SmallPackFilter.SAMPLE to "소량")

/** Home tab (web #tab-extract): D-day, drinking cards, actions, filter, search and the bean group list. */
@Composable
fun ExtractTabScreen(nav: NavHostController) {
    val vm = koinViewModel<ExtractViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val summaryDraft by vm.summaryDraft.collectAsStateWithLifecycle()
    val photoStore = koinInject<PhotoStore>()
    val newRecord = { nav.navigate(Route.RecordForm(mode = FormMode.EXTRACT)) }
    val openEntry = { id: String -> nav.navigate(Route.EntryDetail(id)) }

    Box(Modifier.fillMaxSize()) {
        // ends at the keyboard, so the search / D-day / summary fields scroll above it
        LazyColumn(Modifier.fillMaxSize().imeOverlapPadding(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { TopHeader("coffee_journal / 2026", "[ personal coffee archive ]", if (state.loaded) "${state.entryCount} entries" else null) }
            item {
                if (state.loaded) DdaySection(
                    start = state.ddayStart, label = state.ddayLabel, milestone = state.ddayMilestone, onSave = vm::saveDday,
                    modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 12.dp),
                )
            }
            if (state.drinking !is DrinkingState.None) {
                item {
                    val pad = if (state.drinking is DrinkingState.RecentBean) Modifier.padding(horizontal = Dimens.gutter) else Modifier
                    DrinkingRow(state.drinking, onEditPantry = { nav.navigate(Route.PantryEditor(it)) }, modifier = pad.padding(bottom = 12.dp))
                }
            }
            item { ActionRow(onNew = newRecord, onPantry = { nav.navigate(Route.Pantry) }, onBackup = { nav.navigate(Route.Backup) }) }
            item { FilterBlock(state.controls, query, vm::setFilterMode, vm::setSmallMode, vm::setQuery) }
            val search = state.search
            if (search != null) {
                searchResultItems(search, openEntry)
            } else {
                items(state.openedSmallPacks.size, key = { "pack:" + state.openedSmallPacks[it].item.id }) { i ->
                    SmallPackCardView(state.openedSmallPacks[i], Modifier.padding(horizontal = Dimens.gutter, vertical = 5.dp))
                }
                items(state.groups.size, key = { "group:" + state.groups[it].group.key }) { i ->
                    val ui = state.groups[i]
                    val key = ui.group.key
                    BeanGroupCard(
                        ui = ui, summaryDraft = summaryDraft, photoStore = photoStore,
                        actions = GroupActions(
                            onToggle = { vm.toggleGroup(key, ui.expanded) },
                            onOpenEntry = openEntry,
                            onStartEditSummary = { vm.startEditSummary(key, ui.summary) },
                            onSummaryDraft = vm::updateSummaryDraft,
                            onSaveSummary = { vm.saveSummary(key) },
                            onCancelSummary = vm::cancelEditSummary,
                            onOpenPicker = { vm.openBestPicker(key) },
                            onPick = { id -> vm.pickBest(key, id) },
                        ),
                        modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 5.dp),
                    )
                }
                state.emptyText?.let { text -> item { EmptyNote(text, Modifier.padding(horizontal = Dimens.gutter, vertical = 8.dp)) } }
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp).size(52.dp).background(Ink.accent).clickable(onClick = newRecord),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.plus, contentDescription = "새 기록 추가", tint = Ink.bg, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun ActionRow(onNew: () -> Unit, onPantry: () -> Unit, onBackup: () -> Unit) {
    Column(Modifier.padding(horizontal = Dimens.gutter)) {
        PrimaryButton("+ 새 기록 추가", onClick = onNew, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("원두 보관함", onClick = onPantry, modifier = Modifier.weight(1f))
            GhostButton("💾 백업", onClick = onBackup, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FilterBlock(controls: ExtractControls, query: String, onFilter: (String) -> Unit, onSmall: (String) -> Unit, onQuery: (String) -> Unit) {
    Column(Modifier.padding(horizontal = Dimens.gutter).padding(top = 14.dp, bottom = 8.dp)) {
        SubTabs(items = FILTER_LABELS.keys.toList(), selected = controls.filterMode, onSelect = onFilter, labels = FILTER_LABELS)
        if (controls.filterMode == ExtractFilter.SMALLPACK) {
            Spacer(Modifier.height(6.dp))
            SubTabs(items = SMALL_LABELS.keys.toList(), selected = controls.smallMode, onSelect = onSmall, labels = SMALL_LABELS)
        }
        Spacer(Modifier.height(10.dp))
        FieldLabel("원두 이름 검색")
        AppTextField(
            value = query, onValueChange = onQuery, placeholder = "예: 벤사, 게이샤, 리브레",
            trailing = if (query.isNotBlank()) ({
                GlyphButton("×", label = "검색어 지우기", onClick = { onQuery("") }, modifier = Modifier.padding(8.dp), style = AppType.body.copy(color = Ink.textFaint))
            }) else null,
        )
    }
}
