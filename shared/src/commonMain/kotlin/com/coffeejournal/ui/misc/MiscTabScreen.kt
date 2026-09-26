package com.coffeejournal.ui.misc

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.form.TextLink
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Badge
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.SubTabs
import com.coffeejournal.ui.theme.TopHeader
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MiscTabScreen(nav: NavHostController) {
    val vm = koinViewModel<MiscViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<MiscItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        // the web header is global: every tab shows the record count
        TopHeader(title = "coffee_journal / 2026", tagline = "[ personal coffee archive ]", right = state.entryCount?.let { "$it entries" })
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().testTag("misc-list"), contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = 12.dp, bottom = 96.dp)) {
                item(key = "type-tabs") {
                    SubTabs(items = MiscListLogic.typeTabs, selected = state.type, onSelect = vm::selectType, labels = MiscListLogic.typeTabLabels)
                }
                item(key = "title") {
                    SectionLabel(MiscListLogic.title(state.type))
                }
                if (state.type == MiscListLogic.ALL) {
                    item(key = "sort-tabs") {
                        SubTabs(items = MiscListLogic.sortTabs, selected = state.sort, onSelect = vm::selectSort, labels = MiscListLogic.sortTabLabels, scrollable = false)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (state.isEmpty) {
                    item(key = "empty") { EmptyNote(MiscListLogic.emptyText(state.type)) }
                }
                state.sections.forEachIndexed { sectionIndex, section ->
                    section.label?.let { label ->
                        item(key = "label-$sectionIndex") { GroupLabel(label) }
                    }
                    if (section.items.isEmpty() && section.emptyText != null) {
                        item(key = "section-empty-$sectionIndex") { EmptyNote(section.emptyText, Modifier.padding(bottom = 10.dp)) }
                    }
                    items(section.items, key = { "item-${it.id}" }) { item ->
                        MiscCard(
                            item = item,
                            showType = state.type == MiscListLogic.ALL,
                            photoPath = vm::photoPath,
                            onEdit = { nav.navigate(Route.MiscForm(type = item.type, itemId = item.id)) },
                            onDelete = { deleteTarget = item },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                // not on the web: where the content, icons and libraries come from (출처 · 라이선스)
                item(key = "about") {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        TextLink("출처 · 오픈소스 라이선스 →", Ink.textMuted, { nav.navigate(Route.About) })
                    }
                }
            }
            // web: '+ 추가' is hidden on 전체, so the type is picked first (there is no type choice in the form)
            MiscListLogic.formType(state.type)?.let { formType ->
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 20.dp)
                        .size(52.dp)
                        .background(Ink.accent)
                        .clickable { nav.navigate(Route.MiscForm(type = formType)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.plus, contentDescription = "추가", tint = Ink.bg, modifier = Modifier.size(22.dp))
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            shape = RectangleShape,
            containerColor = Ink.surface,
            titleContentColor = Ink.text,
            textContentColor = Ink.textMuted,
            title = { Text("삭제", style = AppType.title) },
            text = { Text("'${target.name}'을(를) 삭제할까요? 사진도 함께 지워져요.", style = AppType.body) },
            confirmButton = {
                TextButton(onClick = { vm.delete(target.id); deleteTarget = null }) { Text("삭제", style = AppType.body.copy(color = Ink.bad)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소", style = AppType.body.copy(color = Ink.textMuted)) } },
        )
    }
}

/** Web .misc-group-label. */
@Composable
private fun GroupLabel(text: String) {
    Text(text, style = AppType.monoSmall.copy(color = Ink.textMuted), modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
}

/** Web .misc-card: photos, type badge + name, since, notes, 수정/삭제. */
@Composable
private fun MiscCard(item: MiscItem, showType: Boolean, photoPath: (String) -> String, onEdit: () -> Unit, onDelete: () -> Unit) {
    HairlineCard {
        if (item.photos.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Bottom) {
                AsyncImage(
                    model = "file://" + photoPath(item.photos[0]),
                    contentDescription = "${item.name} 사진 (대표)",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(120.dp).background(Ink.surfaceRaised),
                )
                item.photos.getOrNull(1)?.let { second ->
                    Spacer(Modifier.width(8.dp))
                    AsyncImage(
                        model = "file://" + photoPath(second),
                        contentDescription = "${item.name} 사진",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).background(Ink.surfaceRaised),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showType) {
                Badge(MiscListLogic.name(item.type))
                Spacer(Modifier.width(8.dp))
            }
            Text(item.name, style = AppType.cardTitle, modifier = Modifier.weight(1f))
        }
        MiscListLogic.sinceLabel(item.since)?.let { since ->
            Spacer(Modifier.height(2.dp))
            Text(since, style = AppType.monoSmall)
        }
        if (item.notes.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(item.notes, style = AppType.bodyMuted)
        }
        Spacer(Modifier.height(10.dp))
        // the buttons keep their small look but reserve a 48dp touch target each (design §8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("수정", small = true, icon = AppIcons.edit, onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize())
            GhostButton("삭제", small = true, icon = AppIcons.trash, danger = true, onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize())
        }
    }
}
