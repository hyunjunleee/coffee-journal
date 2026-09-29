package com.coffeejournal.ui.form.sections

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.reference.Processes
import com.coffeejournal.domain.reference.RoastLevels
import com.coffeejournal.domain.rules.BlendBeans
import com.coffeejournal.ui.form.AltitudeField
import com.coffeejournal.ui.form.AutocompleteField
import com.coffeejournal.ui.form.BeanForm
import com.coffeejournal.ui.form.CompactField
import com.coffeejournal.ui.form.FieldBlock
import com.coffeejournal.ui.form.FormMapper
import com.coffeejournal.ui.form.FormState
import com.coffeejournal.ui.form.FormSuggestions
import com.coffeejournal.ui.form.FormTextField
import com.coffeejournal.ui.form.OriginOptions
import com.coffeejournal.ui.form.PresetField
import com.coffeejournal.ui.form.RemoveButton
import com.coffeejournal.ui.form.TwoUp
import com.coffeejournal.ui.form.VarietyFields
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.ChipInput
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.InputFilters
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.Seg
import com.coffeejournal.ui.theme.fontScaled

/**
 * 원두 정보 그리드 (web #bean-info-fields): one block of green-coffee fields per bean — "+ 원두 추가 (블렌드)" adds
 * another block just like it, and two or more make a café blend, each bean with its share — then what belongs to the
 * bag once: 원두 총량 · 입고 시기, 예상 노트, 가게 설명.
 */
@Composable
internal fun BeanInfoSection(state: FormState, suggestions: FormSuggestions, update: ((FormState) -> FormState) -> Unit) {
    val count = state.beanCount
    val first = state.bean(0)
    for (i in 0 until count) {
        BeanBlock(
            bean = state.bean(i), index = i, count = count, first = first, isCafe = state.isCafe, suggestions = suggestions,
            // applied to the latest state, so a quick second edit of the same block is not lost
            change = { f -> update { s -> if (i < s.beanCount) s.withBean(i, f(s.bean(i))) else s } },
            onRemove = { update { FormMapper.removeBlendBean(it, i) } },
        )
    }
    // a custom blend's beans are its rows above (the user's own beans, by weight)
    if (!state.isCustomBlend) {
        val sum = if (count > 1) FormMapper.blendPercentSum(state) else null
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("+ 원두 추가 (블렌드)", small = true, onClick = { update(FormMapper::addBlendBean) })
            Spacer(Modifier.weight(1f))
            if (sum != null) Text("합계 ${BlendBeans.formatPercent(sum)}%", style = AppType.monoValue, modifier = Modifier.testTag("blend-percent-sum"))
        }
        if (sum != null && !BlendBeans.isWhole(sum)) {
            HintText("비율을 더하면 100%가 아니에요. 모르는 비율이 있으면 그대로 저장해도 괜찮아요.", Modifier.padding(bottom = 10.dp))
        }
    }
    if (!state.isCafe) {
        TwoUp(
            { m -> FormTextField(state.bagWeight, { v -> update { it.copy(bagWeight = v) } }, m, label = "원두 총량 (g, 선택)", placeholder = "100", keyboardType = KeyboardType.Number, inputFilter = InputFilters::decimal) },
            { m -> FormTextField(state.arrival, { v -> update { it.copy(arrival = v) } }, m, label = "입고 시기", placeholder = "예: 2026.1") },
        )
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

/**
 * What a field shows in grey while it is empty: in bean 1 the usual example; in a later bean only bean 1's value where
 * it takes it over ([fromFirst]), so the grey there always is a value that will be saved, never an example.
 */
private class Grey(val text: String, val fromFirst: Boolean)

private fun grey(index: Int, own: String, firstValue: String, example: String): Grey = when {
    index == 0 -> Grey(example, false)
    own.isBlank() && firstValue.isNotBlank() -> Grey(firstValue, true)
    else -> Grey("", false)
}

/**
 * One bean's fields; the same block for every bean of a blend. With two or more it has a head ("원두 2 · 비율 %",
 * ✕ on the later ones). A later bean's 로스터리, 로스팅 정도 and 로스팅 날짜 show bean 1's in grey until it gets its
 * own (like the brew timer's estimated grams): typing replaces the grey value, emptying the field takes bean 1's again.
 * A later bean shows no example placeholders, so nothing grey there could be taken for a value it would get.
 */
@Composable
private fun BeanBlock(
    bean: BeanForm,
    index: Int,
    count: Int,
    first: BeanForm,
    isCafe: Boolean,
    suggestions: FormSuggestions,
    change: ((BeanForm) -> BeanForm) -> Unit,
    onRemove: () -> Unit,
) {
    // the block as one node, so a later bean's fields can be told apart (tests)
    Column(Modifier.fillMaxWidth().testTag("bean-$index")) { BeanBlockFields(bean, index, count, first, isCafe, suggestions, change, onRemove) }
}

@Composable
private fun BeanBlockFields(
    bean: BeanForm,
    index: Int,
    count: Int,
    first: BeanForm,
    isCafe: Boolean,
    suggestions: FormSuggestions,
    change: ((BeanForm) -> BeanForm) -> Unit,
    onRemove: () -> Unit,
) {
    fun example(text: String): String = if (index == 0) text else ""
    if (count > 1) BeanBlockHead(bean, index, first, change, onRemove)
    val roastery = grey(index, bean.roastery, first.roastery, "예: 커피정경")
    // a roastery typed for this bean that is not registered yet (an inherited one is bean 1's, hinted there)
    val newRoastery = suggestions.loaded && FormMapper.isNewRoastery(bean.roastery, suggestions.roasteries)
    TwoUp(
        { m ->
            AutocompleteField(
                bean.roastery, { v -> change { it.copy(roastery = v) } }, suggestions.roasteries, m, label = "로스터리",
                placeholder = roastery.text, placeholderColor = if (roastery.fromFirst) Ink.textMuted else Ink.textFaint,
                hint = if (newRoastery) "새 로스터리예요. 저장하면 로스터리 목록에도 추가돼요." else null,
            )
        },
        { m -> AutocompleteField(bean.selection, { v -> change { it.copy(selection = v) } }, suggestions.selections, m, label = "생두 수입사", placeholder = example("예: Nordic Approach")) },
    )
    TwoUp(
        { m -> PresetField(bean.country, { v -> change { it.copy(country = v) } }, OriginOptions.countries, m, label = "국가", placeholder = example("에티오피아")) },
        { m -> PresetField(bean.region, { v -> change { it.copy(region = v) } }, OriginOptions.regions(bean.country), m, label = "지역", placeholder = example("시다모")) },
    )
    PresetField(
        bean.subRegion, { v -> change { it.copy(subRegion = v) } }, OriginOptions.subRegions(bean.country, bean.region),
        Modifier.fillMaxWidth().padding(bottom = 10.dp), label = "세부 지역", placeholder = example("벤사 › 코코세"),
    )
    TwoUp(
        { m -> AutocompleteField(bean.farmProducer, { v -> change { it.copy(farmProducer = v) } }, suggestions.farms, m, label = "농장(생산자)", placeholder = example("예: 라 에스메랄다(페드로 가족)")) },
        { m -> FormTextField(bean.washingStation, { v -> change { it.copy(washingStation = v) } }, m, label = "워싱 스테이션", placeholder = example("예: 아리차")) },
    )
    TwoUp(
        { m -> AltitudeField(bean.altitude, { v -> change { it.copy(altitude = v) } }, m, label = "재배 고도", placeholder = example("1900-2100")) },
        { m ->
            VarietyFields(
                bean.variety, bean.heirloomNumbers, { v -> change { it.copy(variety = v) } }, { v -> change { it.copy(heirloomNumbers = v) } }, m,
                label = "품종", placeholder = example("예: Heirloom, Mundo Novo"),
            )
        },
    )
    if (!isCafe) {
        TwoUp(
            { m -> FormTextField(bean.moisture, { v -> change { it.copy(moisture = v) } }, m, label = "수분율 (%)", placeholder = example("11.3"), keyboardType = KeyboardType.Decimal, inputFilter = InputFilters::decimal) },
            { m -> FormTextField(bean.density, { v -> change { it.copy(density = v) } }, m, label = "밀도 (g/L)", placeholder = example("850"), keyboardType = KeyboardType.Number, inputFilter = InputFilters::decimal) },
        )
        TwoUp({ m -> FormTextField(bean.score, { v -> change { it.copy(score = v) } }, m, label = "CoE 컵 점수", placeholder = example("87.5"), keyboardType = KeyboardType.Decimal, inputFilter = InputFilters::decimal) })
    }
    ProcessAndRoast(bean, index, first, change)
    if (!isCafe) {
        val roastDate = grey(index, bean.roastDate, first.roastDate, "예: 2026. 7. 11")
        TwoUp({ m ->
            FormTextField(
                bean.roastDate, { v -> change { it.copy(roastDate = v) } }, m, label = "로스팅 날짜",
                placeholder = roastDate.text, placeholderColor = if (roastDate.fromFirst) Ink.textMuted else Ink.textFaint,
            )
        })
    }
}

/** "원두 2" · its share (%) · ✕ (not on bean 1: a blend always keeps its first bean). */
@Composable
private fun BeanBlockHead(bean: BeanForm, index: Int, first: BeanForm, change: ((BeanForm) -> BeanForm) -> Unit, onRemove: () -> Unit) {
    if (index > 0) Hairline(Modifier.padding(top = 4.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).testTag("bean-block-$index"), verticalAlignment = Alignment.CenterVertically) {
        Text("원두 ${index + 1}", style = AppType.cardTitle, modifier = Modifier.weight(1f))
        Text("비율", style = AppType.fieldLabel, modifier = Modifier.padding(end = 6.dp))
        CompactField(
            value = bean.percent, onValueChange = { v -> change { it.copy(percent = v) } }, placeholder = if (index == 0) "%" else "",
            keyboardType = KeyboardType.Decimal, inputFilter = InputFilters::decimal, textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp.fontScaled()),
        )
        Text("%", style = AppType.small, modifier = Modifier.padding(start = 4.dp))
        if (index > 0) RemoveButton(onClick = onRemove, label = "원두 ${index + 1} 삭제") else Spacer(Modifier.width(8.dp))
    }
    if (index > 0 && listOf(first.roastery, first.roast, first.roastDate).any { it.isNotBlank() }) {
        HintText("회색 글자는 원두 1의 로스터리·로스팅이에요. 그대로 두면 같게 저장되고, 다르면 새로 적어 주세요.", Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun ProcessAndRoast(bean: BeanForm, index: Int, first: BeanForm, change: ((BeanForm) -> BeanForm) -> Unit) {
    FieldBlock {
        FieldLabel("가공 방식")
        Seg(
            options = Processes.formSegments, value = bean.process,
            onChange = { v -> change { it.copy(process = v, processSub = if (v != it.process) "" else it.processSub) } },
        )
        // a later bean has no examples: its field says what it is in a label instead
        if (bean.process == FormMapper.PROCESS_OTHER) {
            Spacer(Modifier.height(8.dp))
            if (index == 0) FormTextField(bean.processOther, { v -> change { it.copy(processOther = v) } }, placeholder = "예: 카보닉 마세레이션, 웻헐드 등")
            else FormTextField(bean.processOther, { v -> change { it.copy(processOther = v) } }, label = "기타 가공 방식")
        } else if (bean.process in Processes.mainSegments) {
            Spacer(Modifier.height(8.dp))
            if (index == 0) FormTextField(bean.processSub, { v -> change { it.copy(processSub = v) } }, placeholder = "세부 종류 (선택, 예: 드래곤 아이, 더블 퍼멘티드)")
            else FormTextField(bean.processSub, { v -> change { it.copy(processSub = v) } }, label = "세부 종류 (선택)")
        }
    }
    FieldBlock {
        FieldLabel("로스팅 정도")
        Seg(
            options = RoastLevels.all, value = bean.roast, onChange = { v -> change { it.copy(roast = v) } },
            inherited = if (index > 0) first.roast.takeIf { it.isNotBlank() } else null, inheritedState = "원두 1과 같음",
        )
    }
}
