package com.coffeejournal.ui.form.sections

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.reference.FlavorWheel
import com.coffeejournal.domain.reference.FlavorWheelExtras
import com.coffeejournal.ui.about.Credits
import com.coffeejournal.ui.form.Collapsible
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.CatDot
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink

/** The wheel section's own texts for the app's unofficial notes ([FlavorWheelExtras]). */
object FlavorWheelTexts {
    /** Heading of the block of unofficial notes in each category card. */
    const val EXTRAS_LABEL = "공식 휠 밖 · 자주 쓰는 노트"

    /** What TalkBack reads for an unofficial note chip. */
    fun unofficial(term: String): String = "$term, 비공식 노트"
}

/**
 * Web renderFlavorIndex: a ring of the 9 categories and every term of the wheel as a toggle chip; after each category's
 * groups, apart from them, the app's own unofficial notes of that category as grey chips ([FlavorWheelExtras]).
 */
@Composable
internal fun FlavorWheelSection(open: Boolean, actualNotes: List<String>, onToggleOpen: () -> Unit, onToggleTerm: (String) -> Unit) {
    Collapsible(title = "🎨 SCA Coffee Taster's Flavor Wheel", open = open, onToggle = onToggleOpen) {
        HintText("Start at the center and move outward. Select a descriptor to add it to your tasting notes.")
        // the grey notes are the app's, not the licensed wheel's (the attribution at the bottom covers the wheel only)
        HintText(Credits.FLAVOR_WHEEL_EXTRAS_NOTE)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WheelRing(Modifier.size(140.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                FlavorWheel.categories.forEach { cat ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        CatDot(Ink.hex(cat.colorHex))
                        Spacer(Modifier.width(6.dp))
                        Text(cat.name, style = AppType.small)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val lower = actualNotes.map { it.lowercase() }
        FlavorWheel.categories.forEach { cat -> CategoryCard(cat, lower, onToggleTerm) }
        // CC BY-NC-ND 4.0 asks for the attribution where the wheel is used
        HintText(Credits.FLAVOR_WHEEL_ATTRIBUTION)
    }
}

@Composable
private fun WheelRing(modifier: Modifier) {
    val colors = FlavorWheel.categories.map { Ink.hex(it.colorHex) }
    // the descriptor chips below are the accessible way to pick notes; the ring itself only needs a label
    Canvas(modifier.semantics { contentDescription = "플레이버 휠 ${FlavorWheel.categories.size}개 계열: " + FlavorWheel.categories.joinToString(", ") { it.name } + ". 아래 항목을 눌러 노트를 추가해요." }) {
        val sweep = 360f / colors.size
        val stroke = size.minDimension * 0.22f
        val inset = stroke / 2 + 2f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        colors.forEachIndexed { i, c ->
            drawArc(color = c, startAngle = -90f + i * sweep, sweepAngle = sweep - 1.5f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = stroke))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryCard(cat: FlavorWheel.Category, lowerActual: List<String>, onToggleTerm: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatDot(Ink.hex(cat.colorHex))
            Spacer(Modifier.width(7.dp))
            Text(cat.name, style = AppType.cardTitle)
        }
        // the inner tier's label is a descriptor too (a taster may stop at the center), unless a group already has it
        FlavorWheel.innerTerm(cat)?.let { term ->
            Chip(text = term, selected = term.lowercase() in lowerActual, onClick = { onToggleTerm(term) }, modifier = Modifier.padding(top = 6.dp))
        }
        cat.groups.forEach { group ->
            Text(group.name, style = AppType.monoSmall, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.terms.forEach { term ->
                    Chip(text = term, selected = term.lowercase() in lowerActual, onClick = { onToggleTerm(term) })
                }
            }
        }
        val extras = FlavorWheelExtras.terms(cat.name)
        if (extras.isNotEmpty()) {
            // not SCA/WCR content: a block of its own after the wheel's groups, grey chips that pick like the others
            Text(FlavorWheelTexts.EXTRAS_LABEL, style = AppType.monoSmall.copy(color = Ink.textFaint), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                extras.forEach { term ->
                    Chip(
                        text = term,
                        selected = term.lowercase() in lowerActual,
                        onClick = { onToggleTerm(term) },
                        muted = true,
                        modifier = Modifier.semantics { contentDescription = FlavorWheelTexts.unofficial(term) },
                    )
                }
            }
        }
    }
}
