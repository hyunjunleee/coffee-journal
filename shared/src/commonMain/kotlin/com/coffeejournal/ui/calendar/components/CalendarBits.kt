package com.coffeejournal.ui.calendar.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.BeanRange
import com.coffeejournal.domain.rules.CuppingTypes
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.calendar.CalendarGrid
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.CatDot
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink

/** "YYYY년 M월" with [오늘][‹][›] (web .calendar-header). */
@Composable
internal fun MonthHeader(title: String, onToday: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = AppType.title, modifier = Modifier.weight(1f))
        GhostButton("오늘", onClick = onToday, small = true)
        Spacer(Modifier.width(6.dp))
        NavSquare(AppIcons.chevronLeft, "이전 달", onPrev)
        Spacer(Modifier.width(6.dp))
        NavSquare(AppIcons.chevronRight, "다음 달", onNext)
    }
}

@Composable
private fun NavSquare(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = Ink.text, modifier = Modifier.size(16.dp)) }
}

/** Accent-soft callout with a tiny mono label (web .calendar-today-bean, reused for the roadmap indicator). */
@Composable
internal fun TodayBox(label: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(Ink.accentSoft).border(BorderStroke(Dimens.rule, Ink.accent), RectangleShape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(label.uppercase(), style = AppType.monoSmall.copy(color = Ink.accent, fontSize = 9.5.sp, letterSpacing = 0.12.em))
        Spacer(Modifier.height(3.dp))
        Text(text, style = AppType.body.copy(fontWeight = FontWeight.Medium))
    }
}

/** "■ 이름 (N잔)" for every range overlapping the visible month (web #calendar-bean-legend). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RangeLegend(ranges: List<BeanRange>, modifier: Modifier = Modifier) {
    if (ranges.isEmpty()) return
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ranges.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Ink.hex(r.colorHex)))
                Spacer(Modifier.width(6.dp))
                Text("${r.name} (${r.count}잔)", style = AppType.small)
            }
        }
    }
}

/** Cafe / cupping list row: dot, name, "YYYY.MM.DD · 유형 · 장소", bean count, memo excerpt, SCA total. */
@Composable
internal fun EntryListRow(entry: Entry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cat = CalendarGrid.category(entry)
    val date = Dates.ymdPadded(entry.createdAt)
    val title: String
    val subtitle: String
    if (entry.isCupping) {
        title = entry.name.ifBlank { entry.cuppingPlace.ifBlank { "커핑 기록" } }
        subtitle = listOf(date, CuppingTypes.effective(entry), entry.cuppingPlace).filter { it.isNotBlank() }.joinToString(" · ")
    } else {
        title = BeanNames.displayName(entry.name)
        subtitle = listOf(date, entry.cafeName, entry.region).filter { it.isNotBlank() }.joinToString(" · ")
    }
    val beanCount = entry.cuppingBeans.count { it.name.isNotBlank() }
    val memo = entry.notes.replace(Regex("\\s+"), " ").trim().let { if (it.length > 80) it.take(80) + "…" else it }
    HairlineCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            CatDot(Ink.categoryColor(cat), Modifier.padding(top = 7.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = AppType.cardTitle)
                Text(subtitle, style = AppType.small, modifier = Modifier.padding(top = 2.dp))
                if (entry.isCupping && beanCount > 0) Text("원두 ${beanCount}종", style = AppType.faint, modifier = Modifier.padding(top = 2.dp))
                if (memo.isNotEmpty()) Text(memo, style = AppType.bodyMuted, modifier = Modifier.padding(top = 6.dp))
            }
            scoreText(entry)?.let { s ->
                Spacer(Modifier.width(8.dp))
                Text(s, style = AppType.monoValue.copy(color = Ink.accent))
            }
        }
    }
}

/** 원두 filter side list row: bean name, recipe name, "M.D HH:MM" (web .calendar-brew-record). */
@Composable
internal fun BrewRow(entry: Entry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(BeanNames.displayName(entry.name), style = AppType.body)
            entry.recipeRef?.name?.takeIf { it.isNotBlank() }?.let { Text(it, style = AppType.faint) }
        }
        Text(Dates.mdHm(entry.createdAt), style = AppType.monoSmall)
    }
}

/** Every delete goes through this confirmation (design §2.3 item 1). */
@Composable
internal fun ConfirmDialog(title: String, text: String, confirmLabel: String = "삭제", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = Ink.surface,
        title = { Text(title, style = AppType.title) },
        text = { Text(text, style = AppType.bodyMuted) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, style = AppType.body.copy(color = Ink.bad)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", style = AppType.body) } },
    )
}
