package com.coffeejournal.ui.bean.a

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.reference.NoteCategories
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.CatDot
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.Ink

/** Static flavor wheel: a nine-segment ring in the web's family colours next to the legend (web .flavor-wheel-wrap). */
@Composable
internal fun FlavorWheelLegend(modifier: Modifier = Modifier) {
    val families = NoteCategories.all
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(112.dp)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val sweep = 360f / families.size
            families.forEachIndexed { i, family ->
                drawArc(
                    color = Ink.hex(family.colorHex),
                    startAngle = -90f + i * sweep,
                    sweepAngle = sweep - 1.5f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            families.forEach { family ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatDot(Ink.hex(family.colorHex))
                    Spacer(Modifier.width(6.dp))
                    Text(family.name, style = AppType.small)
                }
            }
        }
    }
}

/** Web .note-category-grid: two columns of family cards; the selected one opens its 세부 종류 panel below. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlavorCategoryGrid(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val families = NoteCategories.all
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        families.chunked(2).forEach { pair ->
            // both cards of a row as tall as the taller one (web CSS grid rows stretch)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { family ->
                    val idx = families.indexOf(family)
                    SelectCard(selected = idx == selected, onClick = { onSelect(idx) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CatDot(Ink.hex(family.colorHex))
                            Spacer(Modifier.width(6.dp))
                            Text(family.name, style = AppType.small.copy(color = Ink.text))
                        }
                        Text(family.subs.joinToString(" · ") { it.name }, style = AppType.faint, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        val family = families.getOrNull(selected)
        if (family != null) {
            Spacer(Modifier.height(6.dp))
            DetailCard(
                title = "${family.name} 세부 종류",
                titleExtra = { CatDot(Ink.hex(family.colorHex)); Spacer(Modifier.width(6.dp)) },
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    family.subs.forEach { sub -> Chip(text = sub.name) }
                }
            }
        }
    }
}
