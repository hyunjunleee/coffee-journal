package com.coffeejournal.ui.calendar.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.coffeejournal.ui.calendar.CalendarGrid
import com.coffeejournal.ui.calendar.DayCell
import com.coffeejournal.ui.calendar.MonthGrid
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Ink

private val cellGap = 3.dp
private val milestoneBig = Color(0xFFD9A441)
private val tinyMono = AppType.monoSmall.copy(fontSize = 8.sp, letterSpacing = 0.em, lineHeight = 9.sp)

/** Seven-column month grid with a weekday header row (web #calendar-grid). */
@Composable
internal fun MonthGridView(grid: MonthGrid, onDayClick: (DayCell) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            CalendarGrid.weekdays.forEach { d ->
                Text(d, style = AppType.monoSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val slots: List<DayCell?> = List(grid.leadingBlanks) { null } + grid.cells
        slots.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                week.forEach { cell ->
                    Box(Modifier.weight(1f)) { if (cell != null) DayCellView(cell) { onDayClick(cell) } }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(cellGap))
        }
    }
}

/** One square: category bar, cup badge, milestone, day number, dots and the bean-range band. */
@Composable
private fun DayCellView(cell: DayCell, onClick: () -> Unit) {
    val range = cell.range
    val rangeColor = range?.let { Ink.hex(it.colorHex) }
    val base = Modifier
        .fillMaxWidth()
        .height(60.dp)
        .background(if (cell.hasContent) Ink.surfaceRaised else Color.Transparent)
        .then(if (cell.isToday) Modifier.border(BorderStroke(Dimens.rule, Ink.accent), RectangleShape) else Modifier)
        .drawBehind {
            if (rangeColor != null) {
                val inset = cellGap.toPx()
                val left = if (cell.capLeft) inset else 0f
                val right = if (cell.capRight) size.width - inset else size.width
                val bandHeight = cellGap.toPx()
                drawRect(rangeColor, topLeft = Offset(left, size.height - bandHeight * 2), size = Size(right - left, bandHeight))
            }
        }
    Box(if (cell.hasContent) base.clickable(onClick = onClick) else base) {
        if (cell.categories.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 2.dp).height(3.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                cell.categories.forEach { c -> Box(Modifier.weight(1f).fillMaxHeight().background(Ink.categoryColor(c))) }
            }
        }
        if (cell.showCupBadge) {
            Text("${cell.cupCount}잔", style = tinyMono, modifier = Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 3.dp))
        }
        cell.milestone?.let { m ->
            Text(
                m.text,
                style = tinyMono.copy(color = if (m.big) milestoneBig else Ink.accent, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.align(Alignment.TopStart).padding(top = 5.dp, start = 3.dp),
            )
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                cell.date.day.toString(),
                style = AppType.small.copy(
                    color = if (cell.hasContent) Ink.text else Ink.textFaint,
                    fontWeight = if (cell.isToday) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
            if (cell.dotCategories.isNotEmpty()) {
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    cell.dotCategories.forEach { c -> Box(Modifier.size(6.dp).background(Ink.categoryColor(c))) }
                }
            }
        }
    }
}
