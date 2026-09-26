package com.coffeejournal.ui.form.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.RecipeRef
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.reference.GenericSteps
import com.coffeejournal.domain.rules.RecipeSteps
import com.coffeejournal.ui.form.AutocompleteField
import com.coffeejournal.ui.form.FormMapper
import com.coffeejournal.ui.form.FormNumbers
import com.coffeejournal.ui.form.FormState
import com.coffeejournal.ui.form.FormSuggestions
import com.coffeejournal.ui.form.FormTextField
import com.coffeejournal.ui.form.TwoUp
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.InputFilters
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.fontScaled
import com.coffeejournal.ui.theme.SectionLabel

/** 레시피 필드 + 추출 예시(읽기 전용) + 추출 단계 로그. */
@Composable
internal fun RecipeSection(state: FormState, suggestions: FormSuggestions, update: ((FormState) -> FormState) -> Unit, onOpenTimer: () -> Unit) {
    SectionLabel("레시피")
    TwoUp(
        { m -> AutocompleteField(state.dripper, { v -> update { it.copy(dripper = v) } }, suggestions.drippers, m, label = "드리퍼", placeholder = "칼리타 웨이브") },
        { m -> AutocompleteField(state.filter, { v -> update { it.copy(filter = v) } }, suggestions.filters, m, label = "필터", placeholder = "칼리타 웨이브 필터 / 표백") },
    )
    if (state.isCafe) return
    TwoUp(
        { m -> FormTextField(state.grind, { v -> update { it.copy(grind = v) } }, m, label = "분쇄도", placeholder = "중간 / 클릭수 등") },
        { m -> FormTextField(state.dose, { v -> update { it.copy(dose = v) } }, m, label = "원두량 (g)", placeholder = "20", keyboardType = KeyboardType.Decimal, inputFilter = InputFilters::decimal) },
    )
    TwoUp(
        { m -> FormTextField(state.water, { v -> update { it.copy(water = v) } }, m, label = "물량 (g)", placeholder = "320", keyboardType = KeyboardType.Decimal, inputFilter = InputFilters::decimal) },
        { m ->
            FormTextField(
                state.temp, { v -> update { it.copy(temp = v, tempHint = "") } }, m, label = "물 온도 (°C)", placeholder = "88",
                keyboardType = KeyboardType.Decimal, hint = state.tempHint.takeIf { it.isNotBlank() }, inputFilter = InputFilters::decimal,
            )
        },
    )
    TwoUp(
        { m ->
            val computed = FormMapper.stepsTime(state)
            FormTextField(
                state.time, { v -> update { it.copy(time = v) } }, m, label = "총 추출시간", placeholder = "단계에서 자동 계산",
                hint = "마지막 드로우다운까지 자동 계산돼요.", inputFilter = { typed -> computed ?: typed },
            )
        },
        { m -> AutocompleteField(state.waterType, { v -> update { it.copy(waterType = v) } }, suggestions.waters, m, label = "사용한 물", placeholder = "예: 정수기 물, 스파클 정수") },
    )
    BrewCalculatorSection(state, update)
    RecipeRefSteps(state.appliedRecipeRef)
    StepsLog(state, update, onOpenTimer)
}

/** Web renderRecipeRefSteps: the applied recipe's steps, or the generic example, read-only. */
@Composable
private fun RecipeRefSteps(ref: RecipeRef?) {
    val usingRecipe = ref != null && ref.steps.isNotEmpty()
    val steps: List<RecipeStep> = if (usingRecipe) ref!!.steps else GenericSteps.example
    SectionLabel(if (usingRecipe) "추출 예시: ${ref!!.name} (참고, 수정 불가)" else "추출 예시 (참고용)")
    StepHeaderRow("시점", "대기(초)", "물량(g)", "메모")
    val narrow = 52.dp.fontScaled(StepColumnCap)
    val wide = 60.dp.fontScaled(StepColumnCap)
    steps.forEach { s ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            // mono values never break ("0:0 / 0" at large font sizes)
            Text(s.time.ifBlank { "-" }, style = AppType.monoValue, softWrap = false, modifier = Modifier.width(narrow))
            Text(if (s.wait.isNotBlank()) "${s.wait}s" else "-", style = AppType.monoValue, softWrap = false, modifier = Modifier.width(wide))
            Text(if (s.water.isNotBlank()) "${s.water}g" else "-", style = AppType.monoValue, softWrap = false, modifier = Modifier.width(wide))
            Text(s.note.ifBlank { "-" }, style = AppType.small, modifier = Modifier.weight(1f))
        }
        Hairline()
    }
}

@Composable
internal fun StepHeaderRow(a: String, b: String, c: String, d: String) {
    Row(Modifier.fillMaxWidth().background(Ink.surfaceRaised).padding(horizontal = 4.dp, vertical = 5.dp)) {
        Text(a, style = AppType.monoSmall, modifier = Modifier.width(52.dp.fontScaled(StepColumnCap)))
        Text(b, style = AppType.monoSmall, modifier = Modifier.width(60.dp.fontScaled(StepColumnCap)))
        Text(c, style = AppType.monoSmall, modifier = Modifier.width(60.dp.fontScaled(StepColumnCap)))
        Text(d, style = AppType.monoSmall, modifier = Modifier.weight(1f))
    }
}

/** The step columns grow with the font up to this factor; the memo column keeps the rest. */
private const val StepColumnCap = 1.5f

/** Web #steps-summary: totals, recipe warnings and the diff against the applied recipe. */
@Composable
internal fun StepsSummaryBox(rawSteps: List<RecipeStep>, targetWater: String, targetTime: String, rawRef: RecipeRef?) {
    if (rawSteps.isEmpty()) return
    // Stored records (e.g. restored from a backup) may still hold "NaN"-like numbers; they count as missing here.
    val steps = FormNumbers.finiteSteps(rawSteps)
    val ref = FormNumbers.finiteRef(rawRef)
    val summary = RecipeSteps.summary(steps)
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).background(Ink.surfaceRaised).padding(12.dp)) {
        Text(RecipeSteps.summaryLine(summary), style = AppType.small.copy(color = Ink.text))
        RecipeSteps.warnings(summary, FormNumbers.finiteText(targetWater), FormNumbers.safeTime(targetTime)).forEach { Text(it, style = AppType.small.copy(color = Ink.bad), modifier = Modifier.padding(top = 4.dp)) }
        if (ref != null && ref.steps.isNotEmpty()) {
            val diffs = RecipeSteps.diff(steps, ref)
            if (diffs.isEmpty()) {
                Text("✓ ${ref.name} 그대로 부었어요", style = AppType.small.copy(color = Ink.good), modifier = Modifier.padding(top = 6.dp))
            } else {
                Text("${ref.name} 대비 이번에 어긋난 부분", style = AppType.monoSmall, modifier = Modifier.padding(top = 6.dp))
                diffs.forEach { Text(it, style = AppType.small.copy(color = Ink.text), modifier = Modifier.padding(top = 2.dp)) }
            }
        }
    }
}
