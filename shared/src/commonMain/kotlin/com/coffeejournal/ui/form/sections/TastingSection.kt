package com.coffeejournal.ui.form.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.reference.ScaForm
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.form.FieldBlock
import com.coffeejournal.ui.form.FormState
import com.coffeejournal.ui.form.FormTextField
import com.coffeejournal.ui.form.SliderRow
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.ChipInput
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.SectionLabel

/** 테이스팅: SCA 커핑 폼, 플레이버 휠, 내가 느낀 노트, 추출 관련 메모. */
@Composable
internal fun TastingSection(state: FormState, update: ((FormState) -> FormState) -> Unit) {
    SectionLabel("테이스팅")
    FieldBlock {
        FieldLabel("SCA 커핑 평가 (100점)")
        ScaSheet(state, update)
    }
    FlavorWheelSection(
        open = state.flavorWheelOpen,
        actualNotes = state.actualNotes,
        onToggleOpen = { update { it.copy(flavorWheelOpen = !it.flavorWheelOpen) } },
        onToggleTerm = { term -> update { it.copy(actualNotes = toggleNote(it.actualNotes, term)) } },
    )
    Spacer(Modifier.height(12.dp))
    FieldBlock {
        FieldLabel("내가 느낀 노트 — 실제로 맛본 것")
        ChipInput(
            chips = state.actualNotes, onChipsChange = { v -> update { it.copy(actualNotes = v) } },
            input = state.actualInput, onInputChange = { v -> update { it.copy(actualInput = v) } },
        )
        NotesSuggestRow(state.expectedNotes, state.actualNotes) { note -> update { it.copy(actualNotes = it.actualNotes + note) } }
    }
    FormTextField(
        value = state.notes, onValueChange = { v -> update { it.copy(notes = v) } }, label = "추출 관련 메모",
        placeholder = "맛, 개선할 점, 다음에 시도할 것 등", singleLine = false, minLines = 3,
    )
}

/** Web buildAttrGrid: 10 attributes, intensity rows under their parent, a memo per attribute, TOTAL SCORE. */
@Composable
private fun ScaSheet(state: FormState, update: ((FormState) -> FormState) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("SCA CUPPING FORM", style = AppType.monoSmall, modifier = Modifier.weight(1f))
            Text("드래그하여 조절", style = AppType.faint)
        }
        ScaForm.attrs.forEach { attr ->
            val intensity = ScaForm.intensities.firstOrNull { it.after == attr.key }
            AttrSlider(attr, state.attributes[attr.key]) { v -> update { it.copy(attributes = it.attributes + (attr.key to v)) } }
            if (intensity != null) {
                AttrSlider(intensity, state.attributes[intensity.key], secondary = true) { v -> update { it.copy(attributes = it.attributes + (intensity.key to v)) } }
            }
            FormTextField(
                value = state.attributeNotes[attr.key] ?: "",
                onValueChange = { v -> update { it.copy(attributeNotes = it.attributeNotes + (attr.key to v)) } },
                placeholder = "${attr.label} Memo", singleLine = false, modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Hairline(color = Ink.text, thickness = Dimens.rule)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("TOTAL SCORE", style = AppType.cardTitle, modifier = Modifier.weight(1f))
            Text(ScaScoring.effectiveTotal(state.attributes)?.let { ScaScoring.format2(it) } ?: "–", style = AppType.displayNumber)
        }
    }
}

@Composable
private fun AttrSlider(attr: ScaForm.Attr, value: Double?, secondary: Boolean = false, onChange: (Double) -> Unit) {
    val effective = value?.takeIf { it > 0 }
    SliderRow(
        label = attr.label, value = effective, min = attr.min, max = attr.max, step = attr.step,
        readout = ScaScoring.readout(attr, effective), onChange = onChange, secondary = secondary,
    )
}

/** Web renderNotesSuggestRow: every 예상 노트 as a one-tap chip, already-added ones marked ✓. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotesSuggestRow(expected: List<String>, actual: List<String>, onAdd: (String) -> Unit) {
    if (expected.isEmpty()) return
    val lower = actual.map { it.lowercase() }
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("봉투 노트에서 추천:", style = AppType.faint, modifier = Modifier.padding(top = 6.dp))
        expected.forEach { note ->
            val added = note.lowercase() in lower
            Chip(text = note, prefix = if (added) "✓ " else "+ ", selected = added, onClick = if (added) null else ({ onAdd(note) }))
        }
    }
}

/** Case-insensitive toggle used by the flavor wheel. */
internal fun toggleNote(notes: List<String>, term: String): List<String> {
    val idx = notes.indexOfFirst { it.equals(term, ignoreCase = true) }
    return if (idx == -1) notes + term else notes.filterIndexed { i, _ -> i != idx }
}
