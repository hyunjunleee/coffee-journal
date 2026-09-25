package com.coffeejournal.ui.form.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coffeejournal.ui.form.CompactField
import com.coffeejournal.ui.form.MinTouch
import com.coffeejournal.ui.form.FormState
import com.coffeejournal.ui.form.RemoveButton
import com.coffeejournal.ui.form.StepForm
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.InputFilters
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.SectionLabel

/** 추출 단계 로그 (실제 추출) — editable rows (web #steps-list). */
@Composable
internal fun StepsLog(state: FormState, update: ((FormState) -> FormState) -> Unit) {
    SectionLabel("추출 단계 로그 (실제 추출)", hint = "(선택)")
    HintText("레시피대로 안 됐어도 괜찮아요 — 실수로 더 붓거나 늦게 부은 것까지 실제 그대로 적으세요. 그래야 레시피랑 뭐가 달랐는지, 그게 맛에 어떤 영향을 줬는지 나중에 비교해볼 수 있어요.")
    Spacer(Modifier.height(8.dp))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widths = StepWidths.fitting(maxWidth, LocalDensity.current.fontScale)
        Column {
            StepsLogHeader(widths)
            state.steps.forEachIndexed { index, step ->
                StepRow(
                    step = step,
                    isLast = index == state.steps.lastIndex,
                    widths = widths,
                    onChange = { changed -> update { s -> s.copy(steps = s.steps.replaceAt(index, changed)) } },
                    onRemove = { update { s -> s.copy(steps = s.steps.filterIndexed { i, _ -> i != index }) } },
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    GhostButton("+ 단계 추가", small = true, onClick = { update { it.copy(steps = it.steps + StepForm()) } })
    if (state.steps.isNotEmpty()) HintText("마지막 행의 대기(초)가 드로우다운 시간이에요.")
    val live = state.steps.map { it.toStep() }.filter { !it.isEmpty }
    StepsSummaryBox(live, state.water, state.time, state.appliedRecipeRef)
}

private val ColumnGap = 6.dp
/** The pour toggle: the drop glyph is unchanged, its tappable box is 48 dp (design §8). */
private val PourToggleWidth = MinTouch

/**
 * Column widths of an editable step row, shared by [StepsLogHeader] so the labels sit over their inputs. They grow
 * with the font scale (a "0:00" in a 56 dp box was cut at twice the size) but never past the row: the pour toggle,
 * the ✕ and the gaps keep their size and the three inputs share what is left.
 */
internal data class StepWidths(val time: Dp, val wait: Dp, val water: Dp) {
    companion object {
        private val Time = 56.dp
        private val Wait = 64.dp
        private val Water = 64.dp

        fun fitting(rowWidth: Dp, fontScale: Float): StepWidths {
            val fixed = PourToggleWidth + MinTouch + ColumnGap * 2
            val room = (rowWidth - fixed) / (Time + Wait + Water)
            val scale = minOf(maxOf(1f, fontScale), room).coerceAtLeast(0.5f)
            return StepWidths(Time * scale, Wait * scale, Water * scale)
        }
    }
}

/** Header over the editable rows: 시점 · 대기(초) · 이번 물량(g); the memo has its own line in every row. */
@Composable
private fun StepsLogHeader(widths: StepWidths) {
    Row(Modifier.fillMaxWidth().background(Ink.surfaceRaised).padding(vertical = 5.dp)) {
        HeaderCell("시점", Modifier.width(widths.time))
        Spacer(Modifier.width(ColumnGap))
        HeaderCell("대기(초)", Modifier.width(widths.wait))
        Spacer(Modifier.width(ColumnGap))
        HeaderCell("이번 물량(g)", Modifier.width(PourToggleWidth + widths.water))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    // 8 dp in, like the text inside a CompactField
    Text(text, style = AppType.monoSmall, maxLines = 1, modifier = modifier.padding(start = 8.dp))
}

@Composable
private fun StepRow(step: StepForm, isLast: Boolean, widths: StepWidths, onChange: (StepForm) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CompactField(step.time, { onChange(step.copy(time = it)) }, Modifier.width(widths.time), placeholder = "0:00")
            Spacer(Modifier.width(ColumnGap))
            CompactField(
                step.wait, { onChange(step.copy(wait = it)) }, Modifier.width(widths.wait),
                placeholder = if (isLast) "드로우다운" else "대기초", keyboardType = KeyboardType.Number, inputFilter = InputFilters::decimal,
            )
            Spacer(Modifier.width(ColumnGap))
            Box(
                Modifier.size(PourToggleWidth).toggleable(value = step.pour, role = Role.Checkbox) { on -> onChange(if (on) step.copy(pour = true) else step.copy(pour = false, water = "")) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.drop, contentDescription = "붓기 단계 여부", tint = if (step.pour) Ink.accent else Ink.line, modifier = Modifier.size(16.dp))
            }
            if (step.pour) {
                CompactField(step.water, { onChange(step.copy(water = it)) }, Modifier.width(widths.water), placeholder = "물량g", keyboardType = KeyboardType.Decimal, inputFilter = InputFilters::decimal)
            } else {
                Text("대기", style = AppType.faint, modifier = Modifier.width(widths.water).padding(start = 8.dp))
            }
            Spacer(Modifier.weight(1f))
            RemoveButton(onRemove, label = "단계 삭제")
        }
        Spacer(Modifier.height(6.dp))
        CompactField(step.note, { onChange(step.copy(note = it)) }, Modifier.fillMaxWidth(), placeholder = "메모 (뜸 / 1차 푸어 등)")
        Spacer(Modifier.height(6.dp))
        Hairline()
    }
}
