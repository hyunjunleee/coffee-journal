package com.coffeejournal.ui.form.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.reference.Processes
import com.coffeejournal.domain.reference.RoastLevels
import com.coffeejournal.ui.form.AutocompleteField
import com.coffeejournal.ui.form.FieldBlock
import com.coffeejournal.ui.form.FormMapper
import com.coffeejournal.ui.form.FormState
import com.coffeejournal.ui.form.FormSuggestions
import com.coffeejournal.ui.form.FormTextField
import com.coffeejournal.ui.form.TwoUp
import com.coffeejournal.ui.theme.ChipInput
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.Seg

/** 원두 정보 그리드 (web #bean-info-fields): 로스터리 … 로스팅 날짜, 예상 노트, 가게 설명. */
@Composable
internal fun BeanInfoSection(state: FormState, suggestions: FormSuggestions, update: ((FormState) -> FormState) -> Unit) {
    TwoUp(
        { m -> AutocompleteField(state.roastery, { v -> update { it.copy(roastery = v) } }, suggestions.roasteries, m, label = "로스터리", placeholder = "예: 커피정경") },
        { m -> AutocompleteField(state.selection, { v -> update { it.copy(selection = v) } }, suggestions.selections, m, label = "생두 수입사", placeholder = "예: Nordic Approach") },
    )
    TwoUp(
        { m -> FormTextField(state.country, { v -> update { it.copy(country = v) } }, m, label = "국가", placeholder = "브라질") },
        { m -> AutocompleteField(state.region, { v -> update { it.copy(region = v) } }, regionOptions(state.country), m, label = "지역", placeholder = "Cerrado") },
    )
    TwoUp(
        { m -> AutocompleteField(state.farmProducer, { v -> update { it.copy(farmProducer = v) } }, suggestions.farms, m, label = "농장(생산자)", placeholder = "예: 라 에스메랄다(페드로 가족)") },
        { m -> FormTextField(state.washingStation, { v -> update { it.copy(washingStation = v) } }, m, label = "워싱 스테이션", placeholder = "예: 아리차") },
    )
    TwoUp(
        { m -> FormTextField(state.altitude, { v -> update { it.copy(altitude = v) } }, m, label = "재배 고도", placeholder = "800~1,100m") },
        { m -> FormTextField(state.variety, { v -> update { it.copy(variety = v) } }, m, label = "품종", placeholder = "예: Heirloom(74110), Mundo Novo") },
    )
    if (!state.isCafe) {
        TwoUp(
            { m -> FormTextField(state.moisture, { v -> update { it.copy(moisture = v) } }, m, label = "수분율 (%)", placeholder = "11.3", keyboardType = KeyboardType.Decimal) },
            { m -> FormTextField(state.density, { v -> update { it.copy(density = v) } }, m, label = "밀도 (g/L)", placeholder = "850", keyboardType = KeyboardType.Number) },
        )
        TwoUp({ m -> FormTextField(state.score, { v -> update { it.copy(score = v) } }, m, label = "CoE 컵 점수", placeholder = "87.5", keyboardType = KeyboardType.Decimal) })
    }
    ProcessAndRoast(state, update)
    if (!state.isCafe) {
        TwoUp(
            { m -> FormTextField(state.bagWeight, { v -> update { it.copy(bagWeight = v) } }, m, label = "원두 총량 (g, 선택)", placeholder = "100", keyboardType = KeyboardType.Number) },
            { m -> FormTextField(state.arrival, { v -> update { it.copy(arrival = v) } }, m, label = "입고 시기", placeholder = "예: 2026.1") },
        )
        TwoUp({ m -> FormTextField(state.roastDate, { v -> update { it.copy(roastDate = v) } }, m, label = "로스팅 날짜", placeholder = "예: 2026. 7. 11") })
    }
    FieldBlock {
        FieldLabel("이 원두의 예상 노트 (원두 봉투에 적힌 것)")
        ChipInput(
            chips = state.expectedNotes, onChipsChange = { v -> update { it.copy(expectedNotes = v) } },
            input = state.expectedInput, onInputChange = { v -> update { it.copy(expectedInput = v) } },
        )
    }
    FormTextField(
        value = state.roasterDesc, onValueChange = { v -> update { it.copy(roasterDesc = v) } },
        label = "가게에 적힌 원두 설명 (선택 — 원두 봉투/메뉴판에 적힌 설명을 그대로)",
        placeholder = "예: 자스민과 라벤더 같은 플로럴을 시작으로 얼그레이와 오렌지필을 연상시키는...",
        singleLine = false, minLines = 3, modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun ProcessAndRoast(state: FormState, update: ((FormState) -> FormState) -> Unit) {
    FieldBlock {
        FieldLabel("가공 방식")
        Seg(
            options = Processes.formSegments, value = state.process,
            onChange = { v -> update { it.copy(process = v, processSub = if (v != it.process) "" else it.processSub) } },
        )
        if (state.process == FormMapper.PROCESS_OTHER) {
            Spacer(Modifier.height(8.dp))
            FormTextField(state.processOther, { v -> update { it.copy(processOther = v) } }, placeholder = "예: 카보닉 마세레이션, 웻헐드 등")
        } else if (state.process in Processes.mainSegments) {
            Spacer(Modifier.height(8.dp))
            FormTextField(state.processSub, { v -> update { it.copy(processSub = v) } }, placeholder = "세부 종류 (선택, 예: 드래곤 아이, 더블 퍼멘티드)")
        }
    }
    FieldBlock {
        FieldLabel("로스팅 정도")
        Seg(options = RoastLevels.all, value = state.roast, onChange = { v -> update { it.copy(roast = v) } })
    }
}
