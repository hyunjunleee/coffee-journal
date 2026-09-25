package com.coffeejournal.ui.form.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.coffeejournal.ui.form.CompactField
import com.coffeejournal.ui.form.FormState
import com.coffeejournal.ui.form.RemoveButton
import com.coffeejournal.ui.form.StepForm
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.SectionLabel

/** 추출 단계 로그 (실제 추출) — editable rows (web #steps-list). */
@Composable
internal fun StepsLog(state: FormState, update: ((FormState) -> FormState) -> Unit) {
    SectionLabel("추출 단계 로그 (실제 추출)", hint = "(선택)")
    HintText("레시피대로 안 됐어도 괜찮아요 — 실수로 더 붓거나 늦게 부은 것까지 실제 그대로 적으세요. 그래야 레시피랑 뭐가 달랐는지, 그게 맛에 어떤 영향을 줬는지 나중에 비교해볼 수 있어요.")
    Spacer(Modifier.height(8.dp))
    StepHeaderRow("시점", "대기(초)", "이번 물량(g)", "메모")
    state.steps.forEachIndexed { index, step ->
        StepRow(
            step = step,
            isLast = index == state.steps.lastIndex,
            onChange = { changed -> update { s -> s.copy(steps = s.steps.replaceAt(index, changed)) } },
            onRemove = { update { s -> s.copy(steps = s.steps.filterIndexed { i, _ -> i != index }) } },
        )
    }
    Spacer(Modifier.height(8.dp))
    GhostButton("+ 단계 추가", small = true, onClick = { update { it.copy(steps = it.steps + StepForm()) } })
    if (state.steps.isNotEmpty()) HintText("마지막 행의 대기(초)가 드로우다운 시간이에요.")
    val live = state.steps.map { it.toStep() }.filter { !it.isEmpty }
    StepsSummaryBox(live, state.water, state.time, state.appliedRecipeRef)
}

@Composable
private fun StepRow(step: StepForm, isLast: Boolean, onChange: (StepForm) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CompactField(step.time, { onChange(step.copy(time = it)) }, Modifier.width(56.dp), placeholder = "0:00")
            Spacer(Modifier.width(6.dp))
            CompactField(
                step.wait, { onChange(step.copy(wait = it)) }, Modifier.width(64.dp),
                placeholder = if (isLast) "드로우다운" else "대기초", keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(32.dp).clickable { onChange(if (step.pour) step.copy(pour = false, water = "") else step.copy(pour = true)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.drop, contentDescription = "붓기 단계 여부", tint = if (step.pour) Ink.accent else Ink.line, modifier = Modifier.size(16.dp))
            }
            if (step.pour) {
                CompactField(step.water, { onChange(step.copy(water = it)) }, Modifier.width(64.dp), placeholder = "물량g", keyboardType = KeyboardType.Decimal)
            } else {
                Text("대기", style = AppType.faint, modifier = Modifier.width(64.dp).padding(start = 4.dp))
            }
            Spacer(Modifier.weight(1f))
            RemoveButton(onRemove)
        }
        Spacer(Modifier.height(6.dp))
        CompactField(step.note, { onChange(step.copy(note = it)) }, Modifier.fillMaxWidth(), placeholder = "메모 (뜸 / 1차 푸어 등)")
        Spacer(Modifier.height(6.dp))
        Hairline()
    }
}
