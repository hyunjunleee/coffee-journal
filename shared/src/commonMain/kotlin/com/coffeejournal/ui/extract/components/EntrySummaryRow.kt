package com.coffeejournal.ui.extract.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeejournal.ui.extract.EntryRow
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Badge
import com.coffeejournal.ui.theme.CatDot
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.Ink

/** Collapsed summary of one record inside a bean group; tapping opens the detail screen. */
@Composable
fun EntrySummaryRow(row: EntryRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CatDot(Ink.categoryColor(row.category))
            Text(row.dateText, style = AppType.monoValue)
            if (row.categoryText.isNotBlank()) Text(row.categoryText, style = AppType.small)
            row.packageBadge?.let { Badge(it) }
            if (row.isBest) Badge("⭐ 베스트", color = Ink.accent)
            Spacer(Modifier.weight(1f))
            row.scoreText?.let { Text(it, style = AppType.monoValue) }
        }
        row.blendLabel?.let { label ->
            Spacer(Modifier.height(3.dp))
            Row { Spacer(Modifier.width(14.dp)); Text(label, style = AppType.small.copy(color = Ink.text)) }
        }
        if (row.recipeLine.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Row { Spacer(Modifier.width(14.dp)); Text(row.recipeLine, style = AppType.small.copy(color = Ink.text)) }
        }
        if (row.notePreview.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Row { Spacer(Modifier.width(14.dp)); Text(row.notePreview, style = AppType.faint) }
        }
    }
    Hairline()
}
