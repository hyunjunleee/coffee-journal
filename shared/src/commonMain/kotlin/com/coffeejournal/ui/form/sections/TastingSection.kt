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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.reference.ScaForm
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.ai.AiTexts
import com.coffeejournal.ui.form.FieldBlock
import com.coffeejournal.ui.form.FormState
import com.coffeejournal.ui.form.ScoreForm
import com.coffeejournal.ui.form.FormTextField
import com.coffeejournal.ui.form.SliderRow
import com.coffeejournal.ui.form.TextLink
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.ChipInput
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.Seg
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.SectionLabel

/**
 * 테이스팅: SCA 커핑 폼, 플레이버 휠, 내가 느낀 노트, 추출 관련 메모. [onAskAi] opens the AI note helper (mode B) with a
 * described taste; the notes it finds come back through the form's back stack entry (NoteHelperResult).
 */
@Composable
internal fun TastingSection(state: FormState, update: ((FormState) -> FormState) -> Unit, onAskAi: ((String) -> Unit)? = null) {
    SectionLabel("테이스팅")
    FieldBlock {
        FieldLabel("SCA 커핑 평가 (100점)")
        ScoreFormSeg(state.scoreForm) { form -> update { it.copy(scoreForm = form) } }
        ScoreFormSwitchHint(state)
        Spacer(Modifier.height(8.dp))
        if (state.scoreForm == ScoreForm.CVA) {
            CvaSheet(state.cva, onChange = { v -> update { it.copy(cva = v) } })
        } else {
            ScaSheet(state, update)
        }
    }
    FlavorWheelSection(
        open = state.flavorWheelOpen,
        actualNotes = state.actualNotes,
        onToggleOpen = { update { it.copy(flavorWheelOpen = !it.flavorWheelOpen) } },
        onToggleTerm = { term -> update { it.copy(actualNotes = toggleNote(it.actualNotes, term)) } },
    )
    Spacer(Modifier.height(12.dp))
    FieldBlock {
        var askOpen by rememberSaveable { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("내가 느낀 노트 — 실제로 맛본 것", Modifier.weight(1f))
            if (onAskAi != null) TextLink(AiTexts.ASK_FROM_FORM, Ink.text, { askOpen = !askOpen })
        }
        if (onAskAi != null) AskAiPanel(askOpen, onToggle = { askOpen = !askOpen }, onAsk = onAskAi)
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

/**
 * "✦ AI에게 묻기" and its panel: the taste in the user's own words, then the helper's answer screen. A panel in the
 * form rather than a dialog, like the brew timer's grams input: a text field in a dialog kept Compose from ever going
 * idle in the Robolectric flow tests (docs/dev/implementation-notes.md).
 */
@Composable
private fun AskAiPanel(open: Boolean, onToggle: () -> Unit, onAsk: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    if (!open) return
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
            .background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(12.dp)
            .testTag("ask-ai-panel"),
    ) {
        Text(AiTexts.DESCRIBE_TITLE, style = AppType.cardTitle)
        Spacer(Modifier.height(6.dp))
        AppTextField(value = text, onValueChange = { text = it }, placeholder = AiTexts.DESCRIBE_PLACEHOLDER, singleLine = false, minLines = 2)
        HintText(AiTexts.DESCRIBE_HINT)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(AiTexts.DESCRIBE_SEND, small = true, enabled = text.isNotBlank(), onClick = {
                onAsk(text.trim())
                text = ""
                onToggle()
            })
            GhostButton("닫기", small = true, onClick = onToggle)
        }
    }
}

/** "SCA 2004 | CVA": which cupping form this tasting (or cupping bean) is scored on. */
@Composable
internal fun ScoreFormSeg(value: String, onChange: (String) -> Unit) {
    Seg(options = listOf(ScoreForm.SCA2004, ScoreForm.CVA), value = value, onChange = onChange, allowClear = false, labels = ScoreForm.labels)
}

/** Only the chosen form is saved: say so when the other one holds scores (switching back keeps them until saving). */
@Composable
private fun ScoreFormSwitchHint(state: FormState) {
    val text = when {
        state.scoreForm == ScoreForm.CVA && ScaScoring.isScored(state.attributes) ->
            "저장하면 SCA 2004 점수(${ScaScoring.effectiveTotal(state.attributes)?.let(ScaScoring::format2) ?: "–"})는 지워져요. 2004로 돌아가면 그대로 있어요."
        state.scoreForm == ScoreForm.SCA2004 && !state.cva.isEmpty -> "저장하면 CVA 평가는 지워져요. CVA로 돌아가면 그대로 있어요."
        else -> null
    }
    if (text != null) HintText(text)
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
            // "+ 오렌지" adds the note (a button); once added it is only a ✓ mark
            Chip(text = note, prefix = if (added) "✓ " else "+ ", selected = added, onClick = if (added) null else ({ onAdd(note) }), toggle = false)
        }
    }
}

/** Case-insensitive toggle used by the flavor wheel. */
internal fun toggleNote(notes: List<String>, term: String): List<String> {
    val idx = notes.indexOfFirst { it.equals(term, ignoreCase = true) }
    return if (idx == -1) notes + term else notes.filterIndexed { i, _ -> i != idx }
}
