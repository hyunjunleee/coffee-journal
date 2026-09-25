package com.coffeejournal.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.RoadmapItem
import com.coffeejournal.domain.model.RoadmapPhase
import com.coffeejournal.domain.reference.RoadmapDefaults
import com.coffeejournal.ui.calendar.components.ConfirmDialog
import com.coffeejournal.ui.calendar.components.TodayBox
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.GlyphButton
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton

/** Web renderRoadmap: D-day indicator, collapsible phase cards, checkable/editable items, add row. */
@Composable
internal fun RoadmapSection(
    phases: List<RoadmapPhase>,
    ddayCount: Int?,
    currentPhaseId: String?,
    openPhaseId: String?,
    onTogglePhase: (String) -> Unit,
    onToggleItem: (phaseId: String, itemId: String) -> Unit,
    onEditItem: (phaseId: String, itemId: String, text: String) -> Unit,
    onDeleteItem: (phaseId: String, itemId: String) -> Unit,
    onAddItem: (phaseId: String, text: String) -> Unit,
    onAddPhase: (title: String, range: String, dayStart: Int, dayEnd: Int) -> Unit,
    onDeletePhase: (phaseId: String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (ddayCount != null) {
            val current = phases.firstOrNull { it.id == currentPhaseId }
            TodayBox(
                label = "D-$ddayCount",
                text = if (current != null) "지금은 \"${current.title}\" 단계예요" else "계획된 마지막 단계를 지났어요 — 새 단계를 추가해보세요",
            )
        }
        phases.forEach { phase ->
            PhaseCard(
                phase = phase,
                isCurrent = phase.id == currentPhaseId,
                isOpen = phase.id == openPhaseId,
                onToggle = { onTogglePhase(phase.id) },
                onToggleItem = { onToggleItem(phase.id, it) },
                onEditItem = { id, text -> onEditItem(phase.id, id, text) },
                onDeleteItem = { onDeleteItem(phase.id, it) },
                onAddItem = { onAddItem(phase.id, it) },
                // the starter phase is the web's only phase; phases added here can be removed again
                onDelete = if (phase.id == RoadmapDefaults.STARTER_ID) null else ({ onDeletePhase(phase.id) }),
            )
        }
        AddPhaseRow(defaultStart = ddayCount ?: 0, onAdd = onAddPhase)
    }
}

@Composable
private fun PhaseCard(
    phase: RoadmapPhase,
    isCurrent: Boolean,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onToggleItem: (String) -> Unit,
    onEditItem: (String, String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onAddItem: (String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val done = phase.items.count { it.done }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(Ink.surface)
            .border(BorderStroke(if (isCurrent) Dimens.rule else Dimens.hairline, if (isCurrent) Ink.accent else Ink.line), RectangleShape),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = Dimens.cardPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text((if (isCurrent) "📍 " else "") + phase.title, style = AppType.cardTitle)
                if (phase.range.isNotBlank()) Text(phase.range, style = AppType.monoSmall, modifier = Modifier.padding(top = 2.dp))
            }
            Text("$done/${phase.items.size}", style = AppType.monoValue.copy(color = Ink.accent))
            Spacer(Modifier.width(8.dp))
            Icon(if (isOpen) AppIcons.chevronDown else AppIcons.chevronRight, contentDescription = null, tint = Ink.textFaint, modifier = Modifier.size(14.dp))
        }
        if (isOpen) {
            Hairline()
            Column(Modifier.padding(horizontal = Dimens.cardPadding, vertical = 10.dp)) {
                phase.items.forEach { item ->
                    RoadmapItemRow(item, onToggle = { onToggleItem(item.id) }, onEdit = { onEditItem(item.id, it) }, onDelete = { onDeleteItem(item.id) })
                }
                var draft by rememberSaveable(phase.id) { mutableStateOf("") }
                AppTextField(
                    value = draft, onValueChange = { draft = it }, placeholder = "새 항목 추가 후 Enter",
                    imeAction = ImeAction.Done, onImeAction = { if (draft.isNotBlank()) { onAddItem(draft); draft = "" } },
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (onDelete != null) {
                    GhostButton("단계 삭제", onClick = { confirmDelete = true }, small = true, danger = true, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "단계 삭제", text = "\"${phase.title}\" 단계와 그 안의 항목을 모두 삭제할까요?",
            onConfirm = onDelete, onDismiss = { confirmDelete = false },
        )
    }
}

/** Checkbox, text (tap to edit inline), ✕ (confirmed delete). */
@Composable
private fun RoadmapItemRow(item: RoadmapItem, onToggle: () -> Unit, onEdit: (String) -> Unit, onDelete: () -> Unit) {
    var editing by remember(item.id) { mutableStateOf(false) }
    var draft by remember(item.id) { mutableStateOf(item.text) }
    var confirmDelete by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(18.dp).background(if (item.done) Ink.accent else Ink.surface)
                .border(BorderStroke(Dimens.hairline, if (item.done) Ink.accent else Ink.line), RectangleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) { if (item.done) Icon(AppIcons.check, contentDescription = "완료", tint = Ink.bg, modifier = Modifier.size(12.dp)) }
        Spacer(Modifier.width(10.dp))
        if (editing) {
            InlineItemEditor(
                draft = draft, onDraft = { draft = it }, original = item.text, onCommit = onEdit,
                onClose = { editing = false }, modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                item.text,
                style = AppType.body.copy(
                    color = if (item.done) Ink.textFaint else Ink.textMuted,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                modifier = Modifier.weight(1f).clickable { draft = item.text; editing = true },
            )
            GlyphButton(
                "✕", label = "항목 삭제", onClick = { confirmDelete = true },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = AppType.small.copy(color = Ink.textFaint),
            )
        }
    }
    if (confirmDelete) {
        ConfirmDialog(title = "항목 삭제", text = "\"${item.text}\" 항목을 삭제할까요?", onConfirm = onDelete, onDismiss = { confirmDelete = false })
    }
}

/**
 * The inline editor of a roadmap item. Like the web, which saves on blur as well as on Enter, the text is saved
 * whenever editing ends: Enter / 확인, focus moving elsewhere, or the row leaving the screen (the phase is collapsed,
 * another chip or tab is chosen). A blank or unchanged text keeps the old one.
 */
@Composable
private fun RowScope.InlineItemEditor(
    draft: String,
    onDraft: (String) -> Unit,
    original: String,
    onCommit: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    val latestDraft by rememberUpdatedState(draft)
    val latestOriginal by rememberUpdatedState(original)
    val latestCommit by rememberUpdatedState(onCommit)
    DisposableEffect(Unit) {
        onDispose {
            val text = latestDraft.trim()
            if (text.isNotEmpty() && text != latestOriginal) latestCommit(text)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AppTextField(
        value = draft, onValueChange = onDraft, imeAction = ImeAction.Done, onImeAction = onClose,
        modifier = modifier.focusRequester(focus).onFocusChanged { state ->
            if (hadFocus && !state.hasFocus) onClose()
            hadFocus = state.hasFocus
        },
    )
    Spacer(Modifier.width(6.dp))
    GhostButton("확인", onClick = onClose, small = true)
}

/** App addition: "+ 단계 추가" opens a small inline form for title, range label and day span. */
@Composable
private fun AddPhaseRow(defaultStart: Int, onAdd: (String, String, Int, Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (!open) {
        GhostButton("+ 단계 추가", onClick = { open = true }, small = true)
        return
    }
    var title by rememberSaveable { mutableStateOf("") }
    var range by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf(defaultStart.toString()) }
    var end by rememberSaveable { mutableStateOf((defaultStart + 30).toString()) }
    Column(Modifier.fillMaxWidth().background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(Dimens.cardPadding)) {
        AppTextField(value = title, onValueChange = { title = it }, label = "단계 제목", placeholder = "예: 추출 기초 다지기")
        Spacer(Modifier.height(8.dp))
        AppTextField(value = range, onValueChange = { range = it }, label = "기간 설명", placeholder = "예: 1~3개월")
        Spacer(Modifier.height(8.dp))
        Row {
            AppTextField(value = start, onValueChange = { start = it.filter(Char::isDigit) }, label = "시작 D-day", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            AppTextField(value = end, onValueChange = { end = it.filter(Char::isDigit) }, label = "종료 D-day (미포함)", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row {
            PrimaryButton(
                "저장", small = true, enabled = title.isNotBlank(),
                onClick = {
                    onAdd(title, range, start.toIntOrNull() ?: 0, end.toIntOrNull() ?: 0)
                    title = ""; range = ""; open = false
                },
            )
            Spacer(Modifier.width(8.dp))
            GhostButton("취소", small = true, onClick = { open = false })
        }
    }
}
