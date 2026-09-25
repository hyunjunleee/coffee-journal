package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.bean.a.BeanFormat
import com.coffeejournal.ui.bean.a.WhereBadges
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Badge
import com.coffeejournal.ui.theme.CatDot
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.GlyphButton
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton

const val EMPTY_FLAT_LIST = "아직 등록한 게 없어요. 원두를 등록하면 자동으로도 여기 쌓여요."
const val EMPTY_TRIED = "아직 등록한 게 없어요."
const val EMPTY_CURIOUS = "궁금한 걸 추가해보세요."

/** Web categoryBadgeHtml: 직접 내림 / 카페 / 커핑 in the category colour. */
@Composable
fun CategoryBadge(category: String, modifier: Modifier = Modifier) {
    val cat = category.ifBlank { Category.BEAN }
    Badge(if (cat == Category.BEAN) "직접 내림" else cat, modifier, color = Ink.categoryColor(cat))
}

/** Web .source-beans-label. */
@Composable
fun SubLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppType.monoSmall, modifier = modifier.padding(top = 8.dp, bottom = 3.dp))
}

/** Minimum height of a tappable text row (design §8 touch targets; the rows stay compact otherwise). */
internal val TapRowHeight = 40.dp

/**
 * Web .source-bean-row: name with its category badge inline (the badge wraps under a long name instead of squeezing
 * it), and the web's 11px no-wrap mono value on the right. A tappable row is at least [TapRowHeight] tall.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceBeanRow(name: String, right: String? = null, badge: String? = null, bold: Boolean = false, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val base = modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.heightIn(min = TapRowHeight).clickable(onClick = onClick) else base).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = AppType.small.copy(color = Ink.text, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal))
            if (badge != null) CategoryBadge(badge)
        }
        if (right != null) {
            Spacer(Modifier.width(10.dp))
            Text(right, style = AppType.count.copy(fontSize = 11.sp), softWrap = false, maxLines = 1)
        }
    }
}

/** Web .misc-group-label (e.g. "궁금한 로스터리"). */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppType.sectionLabel, modifier = modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
fun ConfirmDeleteDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = Ink.surface,
        text = { Text(text, style = AppType.body) },
        confirmButton = { PrimaryButton("삭제", onClick = { onConfirm(); onDismiss() }, small = true) },
        dismissButton = { GhostButton("취소", onClick = onDismiss, small = true) },
    )
}

/** [수정] [삭제] row with the delete confirmation built in. */
@Composable
fun EditDeleteActions(onEdit: (() -> Unit)?, onDelete: () -> Unit, confirmText: String = "정말 삭제할까요?", editLabel: String = "수정") {
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onEdit != null) GhostButton(editLabel, onClick = onEdit, small = true)
        GhostButton("삭제", onClick = { confirm = true }, small = true, danger = true)
    }
    if (confirm) ConfirmDeleteDialog(confirmText, onConfirm = onDelete, onDismiss = { confirm = false })
}

/** Web .misc-card for flat list items: ★, scope badge, name, location, notes, extra info and actions. */
@Composable
fun MiscItemCard(
    item: MiscItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    favoritable: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    highlighted: Boolean = false,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    HairlineCard(modifier.padding(bottom = 8.dp).then(if (highlighted) Modifier.border(BorderStroke(1.5.dp, Ink.accent), RectangleShape) else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (favoritable) {
                GlyphButton(
                    if (item.favorite) "★" else "☆",
                    label = if (item.favorite) "즐겨찾기 해제" else "즐겨찾기",
                    onClick = { onToggleFavorite?.invoke() },
                    modifier = Modifier.padding(end = 8.dp),
                    style = AppType.title.copy(color = if (item.favorite) Ink.text else Ink.textFaint),
                    enabled = onToggleFavorite != null,
                )
            }
            if (item.scope.isNotBlank()) { Badge(item.scope); Spacer(Modifier.width(6.dp)) }
            Text(item.name, style = AppType.cardTitle, modifier = Modifier.weight(1f))
        }
        if (item.location.isNotBlank()) Text(item.location, style = AppType.small)
        if (item.notes.isNotBlank()) Text(item.notes, style = AppType.bodyMuted, modifier = Modifier.padding(top = 4.dp))
        extra()
        EditDeleteActions(onEdit = onEdit, onDelete = onDelete, confirmText = "'${item.name}'을(를) 삭제할까요?")
    }
}

/** Tried list, then "궁금한 {label}" list, with the web's empty notes. */
@Composable
fun StatusSplitList(items: List<MiscItem>, label: String, favoritable: Boolean, card: @Composable (MiscItem) -> Unit) {
    if (items.isEmpty()) { EmptyNote(EMPTY_FLAT_LIST); return }
    val (tried, curious) = FlatItemLogic.splitByStatus(items, favoritable)
    if (tried.isEmpty()) EmptyNote(EMPTY_TRIED) else tried.forEach { card(it) }
    GroupLabel("궁금한 $label")
    if (curious.isEmpty()) EmptyNote(EMPTY_CURIOUS) else curious.forEach { card(it) }
}

/** Web .origin-record row: bean name, detail line and "kind · place · date" (tap opens the record). */
@Composable
fun RecordRow(record: BeanRecord, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val detail = listOf(record.region, record.farmProducer, record.altitude, record.variety, record.process).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
    val kind = listOf(MapStats.kindLabel(record), record.place.trim()).filter { it.isNotEmpty() }.joinToString(" · ") + " · " + Dates.md(record.createdAt)
    Column(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(record.name.ifBlank { "이름 없는 원두" }, style = AppType.small.copy(color = Ink.text))
        if (detail.isNotEmpty()) Text(detail, style = AppType.faint)
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatDot(Ink.categoryColor(record.category), size = 6.dp)
            Spacer(Modifier.width(5.dp))
            Text(kind, style = AppType.faint)
        }
    }
}

/** Web categoryBreakdownLine: the "어디서 마셨는지" label, then "[직접 내림] N종 [카페] N번 [커핑] N번". */
@Composable
fun BreakdownLine(records: List<BeanRecord>) {
    val counts = BeanFormat.categoryCounts(records)
    if (counts.isEmpty()) return
    SubLabel("어디서 마셨는지")
    WhereBadges(counts)
}
