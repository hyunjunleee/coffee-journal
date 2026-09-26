package com.coffeejournal.ui.calendar.components

import com.coffeejournal.domain.rules.CvaScoring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import kotlinx.datetime.number
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.calendar.CalendarGrid
import com.coffeejournal.ui.calendar.DayCell
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.CatDot
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.Ink

/** Web #calendar-day-panel: "YYYY.MM.DD 마신 원두" followed by the day's records and lab blends. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayPanelSheet(cell: DayCell, onDismiss: () -> Unit, onEntry: (Entry) -> Unit, onBlend: (Blend) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RectangleShape, containerColor = Ink.surface, dragHandle = null) {
        val d = cell.date
        Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter).padding(top = 18.dp, bottom = 32.dp)) {
            Text("${d.year}.${Dates.pad2(d.month.number)}.${Dates.pad2(d.day)} 마신 원두", style = AppType.monoSmall)
            Spacer(Modifier.height(6.dp))
            cell.entries.forEachIndexed { i, en ->
                if (i > 0) Hairline()
                PanelRow(
                    color = Ink.categoryColor(CalendarGrid.category(en)),
                    name = BeanNames.displayName(en.name),
                    meta = CalendarGrid.category(en),
                    right = scoreText(en),
                    onClick = { onEntry(en) },
                )
            }
            cell.blends.forEach { b ->
                if (cell.entries.isNotEmpty() || b !== cell.blends.first()) Hairline()
                PanelRow(
                    color = Ink.categoryColor(Category.BEAN),
                    name = b.name.ifBlank { "이름 없는 블렌드" },
                    meta = "원두 · 블렌드",
                    right = null,
                    onClick = { onBlend(b) },
                )
            }
        }
    }
}

internal fun scoreText(entry: Entry): String? {
    if (entry.isCupping) return null
    // "83.50 / 100" for an SCA 2004 score, "CVA 84.25 / 100" for a CVA tasting
    return CvaScoring.scoreText(entry)
}

@Composable
private fun PanelRow(color: Color, name: String, meta: String, right: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatDot(color)
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = AppType.body, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(6.dp))
            Text(meta, style = AppType.faint)
        }
        if (right != null) {
            Spacer(Modifier.width(8.dp))
            Text(right, style = AppType.monoValue.copy(color = Ink.accent))
        }
    }
}
