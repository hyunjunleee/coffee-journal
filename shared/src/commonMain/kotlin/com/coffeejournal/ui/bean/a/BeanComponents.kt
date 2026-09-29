package com.coffeejournal.ui.bean.a

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.Badge
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.MinTouchTarget

/** Web .cat-badge: 직접 내림 / 카페 / 커핑 in the category colour. */
@Composable
internal fun CategoryBadge(category: String, modifier: Modifier = Modifier) {
    Badge(BeanFormat.categoryLabel(category), modifier = modifier, color = Ink.categoryColor(category))
}

/** Web .variety-record-row: name + meta on the left, kind + date on the right; taps open the record. */
@Composable
internal fun RecordRow(name: String, meta: String, kind: String, date: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(name, style = AppType.body)
                if (meta.isNotBlank()) Text(meta, style = AppType.small)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(kind, style = AppType.small, textAlign = TextAlign.End)
                Text(date, style = AppType.count)
            }
        }
        Hairline()
    }
}

/** Compact record button used inside search results and honey lists: name + badge, meta, date. */
@Composable
internal fun RecordLine(record: BeanRecord, meta: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // weighted without fill: a long name wraps and the badge keeps its own width
                    Text(BeanFormat.displayName(record), style = AppType.body, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    CategoryBadge(record.category)
                }
                if (meta.isNotBlank()) Text(meta, style = AppType.small)
            }
            Text(BeanFormat.date(record), style = AppType.count)
        }
        Hairline()
    }
}

/** Web .process-search-input: square search box with the ⌕ glyph. */
@Composable
internal fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, label: String? = null) {
    AppTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, label = label, placeholder = placeholder, capitalizeWords = false,
        trailing = {
            if (value.isNotEmpty()) {
                // the icon stays 16dp; the tap target around it is the full 48dp trailing slot
                Box(
                    Modifier.size(MinTouchTarget).clickable(role = Role.Button, onClick = { onValueChange("") }).semantics { contentDescription = "지우기" },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.close, contentDescription = null, tint = Ink.textFaint, modifier = Modifier.size(16.dp)) }
            } else Icon(AppIcons.search, contentDescription = null, tint = Ink.textFaint, modifier = Modifier.size(16.dp))
        },
    )
}

/** Web .source-bean-row: a name on the left and "N번" (or any trailing text) on the right. */
@Composable
internal fun CountRow(name: String, trailing: String, modifier: Modifier = Modifier, extra: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = AppType.small.copy(color = Ink.text))
                extra?.invoke()
            }
            if (trailing.isNotEmpty()) Text(trailing, style = AppType.count)
        }
        Hairline()
    }
}

/** Web .process-detail-label: small mono label above a detail block. */
@Composable
internal fun DetailLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppType.sectionLabel, modifier = modifier.padding(top = 14.dp, bottom = 4.dp))
}

/** Web .process-detail-card: white card with a title row. */
@Composable
internal fun DetailCard(title: String, modifier: Modifier = Modifier, titleExtra: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    HairlineCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            titleExtra?.invoke()
            Text(title, style = AppType.cardTitle)
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** Web catBadgesWithPlaces: category badges followed by "(place, place)" in faint text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaceBadges(records: List<BeanRecord>, modifier: Modifier = Modifier) {
    FlowRow(modifier.padding(start = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BeanFormat.categoriesWithPlaces(records).forEach { (cat, places) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(cat)
                if (places.isNotEmpty()) Text(" (${places.joinToString(", ")})", style = AppType.faint)
            }
        }
    }
}

/** Selectable square pill used by the flavor category grid and the variety index (web .note-category-card / .variety-index-button). */
@Composable
internal fun SelectCard(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(if (selected) Ink.accentSoft else Ink.surface, RectangleShape)
            .border(Dimens.hairline, if (selected) Ink.accent else Ink.line, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        content = content,
    )
}

/** Confirmation used before deleting anything (design rule: every delete asks first). */
@Composable
internal fun ConfirmDeleteDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = Ink.surface,
        title = { Text("삭제할까요?", style = AppType.title) },
        text = { Text(text, style = AppType.body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("삭제", style = AppType.body.copy(color = Ink.bad)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", style = AppType.body) } },
    )
}
