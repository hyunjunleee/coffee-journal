package com.coffeejournal.ui.form.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.rules.BrewMath
import com.coffeejournal.domain.rules.Numbers
import com.coffeejournal.ui.form.BrewCalc
import com.coffeejournal.ui.form.CalcForm
import com.coffeejournal.ui.form.Collapsible
import com.coffeejournal.ui.form.FormState
import com.coffeejournal.ui.form.FormTextField
import com.coffeejournal.ui.form.TwoUp
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.InputFilters
import com.coffeejournal.ui.theme.Ink

/**
 * 비율 · 추출수율 계산기 in the recipe area (feature-plan-v2 §2.2): ratio 1:x ↔ dose / water, and extraction yield
 * EY% = TDS% × beverage g ÷ dose g against the SCA classic brewing control chart's ideal box (see [BrewMath.ClassicChart]).
 * "메모에 추가" appends the worked-out line to the notes. Nothing here is saved with the record by itself.
 */
@Composable
internal fun BrewCalculatorSection(state: FormState, update: ((FormState) -> FormState) -> Unit) {
    Collapsible(
        title = "🧮 비율 · 추출수율 계산기", open = state.calcOpen,
        onToggle = { update { s -> s.copy(calcOpen = !s.calcOpen, calc = if (!s.calcOpen) BrewCalc.opened(s.calc, s.dose, s.water) else s.calc) } },
        modifier = Modifier.padding(bottom = 10.dp),
    ) {
        val calc = state.calc
        val set = { f: (CalcForm) -> CalcForm -> update { s -> s.copy(calc = f(s.calc)) } }
        Text("비율", style = AppType.monoSmall, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormTextField(
                calc.dose, { v -> set { BrewCalc.edit(it, BrewMath.Edited.DOSE, v) } }, Modifier.weight(1f).testTag("calc-dose"), label = "원두량 (g)", placeholder = "15",
                keyboardType = KeyboardType.Decimal, inputFilter = InputFilters::decimal,
            )
            FormTextField(
                calc.ratio, { v -> set { BrewCalc.edit(it, BrewMath.Edited.RATIO, v) } }, Modifier.weight(1f).testTag("calc-ratio"), label = "비율 1 :", placeholder = "16",
                keyboardType = KeyboardType.Decimal, inputFilter = InputFilters::decimal,
            )
            FormTextField(
                calc.water, { v -> set { BrewCalc.edit(it, BrewMath.Edited.WATER, v) } }, Modifier.weight(1f).testTag("calc-water"), label = "물량 (g)", placeholder = "240",
                keyboardType = KeyboardType.Decimal, inputFilter = InputFilters::decimal,
            )
        }
        HintText("둘을 적으면 나머지 하나가 계산돼요.")
        val canFill = Numbers.parse(calc.dose) != null || Numbers.parse(calc.water) != null
        Row(Modifier.padding(top = 6.dp)) {
            GhostButton("원두량 · 물량을 레시피에 넣기", small = true, enabled = canFill, onClick = {
                update { s -> s.copy(dose = s.calc.dose.takeIf { Numbers.parse(it) != null } ?: s.dose, water = s.calc.water.takeIf { Numbers.parse(it) != null } ?: s.water) }
            })
        }

        Text("추출수율 (EY)", style = AppType.monoSmall, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        TwoUp(
            { m -> FormTextField(calc.tds, { v -> set { it.copy(tds = v) } }, m.testTag("calc-tds"), label = "농도 TDS (%)", placeholder = "1.35", keyboardType = KeyboardType.Decimal, inputFilter = InputFilters::decimal) },
            { m -> FormTextField(calc.beverage, { v -> set { it.copy(beverage = v) } }, m.testTag("calc-beverage"), label = "추출액 무게 (g)", placeholder = "200", keyboardType = KeyboardType.Decimal, inputFilter = InputFilters::decimal) },
        )
        CalcResult(calc)
        val memo = BrewCalc.memoLine(calc)
        Row(Modifier.padding(top = 8.dp)) {
            GhostButton("메모에 추가", small = true, enabled = memo != null, onClick = {
                update { s -> BrewCalc.memoLine(s.calc)?.let { line -> s.copy(notes = BrewCalc.appendTo(s.notes, line)) } ?: s }
            })
        }
    }
}

@Composable
private fun CalcResult(calc: CalcForm) {
    val ey = BrewCalc.extractionYield(calc)
    val tds = Numbers.parse(calc.tds)?.takeIf { it > 0 }
    Column(Modifier.fillMaxWidth().background(Ink.surfaceRaised).padding(12.dp)) {
        if (ey == null) {
            Text("원두량, TDS, 추출액 무게를 적으면 추출수율이 나와요.", style = AppType.small)
        } else {
            val text = "추출수율 ${BrewMath.fmt2(ey)}%"
            Text(
                text, style = AppType.cardTitle,
                modifier = Modifier.testTag("calc-ey").semantics { contentDescription = "$text, ${BrewMath.eyVerdict(ey)}" },
            )
            Text(
                "= TDS ${calc.tds.trim()}% × 추출액 ${calc.beverage.trim()}g ÷ 원두량 ${calc.dose.trim()}g",
                style = AppType.monoSmall, modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text("추출수율: ${BrewMath.eyVerdict(ey)}", style = AppType.small.copy(color = Ink.text))
        }
        if (tds != null) Text("농도(TDS): ${BrewMath.tdsVerdict(tds)}", style = AppType.small.copy(color = Ink.text))
        Text(
            "기준: SCA가 25 매거진 13호에 실은 고전 추출 조절 차트(Coffee Brewing Control Chart)의 이상적 균형(IDEAL OPTIMUM BALANCE) " +
                "구역, 추출수율 18~22% · TDS 1.15~1.35%. SCA도 새 차트를 연구 중이라 맛의 정답이 아니라 참고로만 보세요.",
            style = AppType.faint, modifier = Modifier.padding(top = 6.dp),
        )
    }
}
