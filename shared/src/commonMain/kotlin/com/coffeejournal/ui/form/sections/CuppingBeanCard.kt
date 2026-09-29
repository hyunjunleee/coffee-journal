package com.coffeejournal.ui.form.sections

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.reference.Processes
import com.coffeejournal.domain.reference.RoastLevels
import com.coffeejournal.domain.reference.ScaForm
import com.coffeejournal.domain.rules.Prices
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.form.AltitudeField
import com.coffeejournal.ui.form.AutocompleteField
import com.coffeejournal.ui.form.Collapsible
import com.coffeejournal.ui.form.CuppingBeanForm
import com.coffeejournal.ui.form.FormMapper
import com.coffeejournal.ui.form.FormSuggestions
import com.coffeejournal.ui.form.FormTextField
import com.coffeejournal.ui.form.OriginOptions
import com.coffeejournal.ui.form.PresetField
import com.coffeejournal.ui.form.RemoveButton
import com.coffeejournal.ui.form.ScoreForm
import com.coffeejournal.ui.form.SliderRow
import com.coffeejournal.ui.form.TwoUp
import com.coffeejournal.ui.form.VarietyFields
import com.coffeejournal.ui.theme.InputFilters
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.ChipInput
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Seg

/** One cupping bean (web addCuppingBeanRow). */
@Composable
internal fun CuppingBeanCard(
    bean: CuppingBeanForm,
    index: Int,
    suggestions: FormSuggestions,
    nameFocus: FocusRequester?,
    error: String?,
    onChange: (CuppingBeanForm) -> Unit,
    onRemove: () -> Unit,
) {
    HairlineCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text("${index + 1}".padStart(2, '0'), style = AppType.monoSmall, modifier = Modifier.padding(top = 18.dp, end = 8.dp))
            FormTextField(
                value = bean.name, onValueChange = { onChange(bean.copy(name = it)) }, modifier = Modifier.weight(1f),
                placeholder = "원두 이름 (예: 에티오피아 예가체프)", focusRequester = nameFocus, error = error,
            )
            RemoveButton(onRemove, label = "원두 삭제", modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Seg(
            options = listOf(BeanMode.SINGLE, BeanMode.BLEND), value = bean.beanMode, allowClear = false,
            onChange = { onChange(bean.copy(beanMode = it)) }, labels = mapOf(BeanMode.SINGLE to "단일 원두", BeanMode.BLEND to "블렌드"),
        )
        if (bean.beanMode == BeanMode.BLEND) {
            Spacer(Modifier.height(8.dp))
            FormTextField(
                value = bean.blendComponentsText, onValueChange = { onChange(bean.copy(blendComponentsText = it)) },
                placeholder = "블렌드 구성 (알면 입력, 예: 에티오피아 + 콜롬비아)",
            )
        }
        Spacer(Modifier.height(10.dp))
        CuppingBeanGrid(bean, suggestions, onChange)
        CuppingBeanProcessRoast(bean, onChange)
        CuppingBeanNotes(bean, onChange)
    }
}

@Composable
private fun CuppingBeanGrid(bean: CuppingBeanForm, suggestions: FormSuggestions, onChange: (CuppingBeanForm) -> Unit) {
    TwoUp(
        { m -> PresetField(bean.country, { onChange(bean.copy(country = it)) }, OriginOptions.countries, m, placeholder = "국가") },
        { m -> PresetField(bean.region, { onChange(bean.copy(region = it)) }, OriginOptions.regions(bean.country), m, placeholder = "지역") },
    )
    PresetField(
        bean.subRegion, { onChange(bean.copy(subRegion = it)) }, OriginOptions.subRegions(bean.country, bean.region),
        Modifier.fillMaxWidth().padding(bottom = 10.dp), placeholder = "세부 지역 (예: 벤사 › 코코세)",
    )
    TwoUp(
        { m -> AutocompleteField(bean.roastery, { onChange(bean.copy(roastery = it)) }, suggestions.roasteries, m, placeholder = "로스터리 (선택)") },
        { m -> AutocompleteField(bean.farmProducer, { onChange(bean.copy(farmProducer = it)) }, suggestions.farms, m, placeholder = "농장(생산자)") },
    )
    // web .cupping-bean-card-grid: altitude alone, variety across the whole row (grid-column: 1 / -1)
    TwoUp({ m -> AltitudeField(bean.altitude, { onChange(bean.copy(altitude = it)) }, m, placeholder = "재배 고도") })
    VarietyFields(
        bean.variety, bean.heirloomNumbers, { onChange(bean.copy(variety = it)) }, { onChange(bean.copy(heirloomNumbers = it)) },
        Modifier.fillMaxWidth().padding(bottom = 10.dp), placeholder = "품종 (예: Heirloom, Mundo Novo)",
    )
    TwoUp(
        { m ->
            FormTextField(
                bean.price, { onChange(bean.copy(price = it)) }, m, placeholder = "원두 가격 (원, 예: 15,000)", keyboardType = KeyboardType.Number,
                onFocusChanged = { focused -> if (!focused && bean.price.isNotBlank()) onChange(bean.copy(price = Prices.formatInput(bean.price))) },
            )
        },
        { m -> FormTextField(bean.rank, { onChange(bean.copy(rank = it)) }, m, placeholder = "나의 순위", keyboardType = KeyboardType.Number, inputFilter = InputFilters::decimal) },
    )
}

@Composable
private fun CuppingBeanProcessRoast(bean: CuppingBeanForm, onChange: (CuppingBeanForm) -> Unit) {
    Seg(
        options = Processes.formSegments, value = bean.process,
        onChange = { v -> onChange(bean.copy(process = v, processSub = if (v in Processes.mainSegments && v != bean.process) "" else bean.processSub)) },
    )
    if (bean.process == FormMapper.PROCESS_OTHER) {
        Spacer(Modifier.height(8.dp))
        FormTextField(bean.processOther, { onChange(bean.copy(processOther = it)) }, placeholder = "기타 가공방식")
    } else if (bean.process in Processes.mainSegments) {
        Spacer(Modifier.height(8.dp))
        FormTextField(bean.processSub, { onChange(bean.copy(processSub = it)) }, placeholder = "세부 종류 (선택, 예: 드래곤 아이, 더블 퍼멘티드)")
    }
    Spacer(Modifier.height(10.dp))
    Seg(options = RoastLevels.all, value = bean.roast, onChange = { onChange(bean.copy(roast = it)) })
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun CuppingBeanNotes(bean: CuppingBeanForm, onChange: (CuppingBeanForm) -> Unit) {
    FieldLabel("예상 노트")
    ChipInput(
        chips = bean.expectedNotes, onChipsChange = { onChange(bean.copy(expectedNotes = it)) },
        input = bean.expectedInput, onInputChange = { onChange(bean.copy(expectedInput = it)) },
        placeholder = "노트 입력 후 Enter (예: 오렌지)",
    )
    Spacer(Modifier.height(12.dp))
    FieldLabel("내가 마신 노트")
    ChipInput(
        chips = bean.actualNotes, onChipsChange = { onChange(bean.copy(actualNotes = it)) },
        input = bean.actualInput, onInputChange = { onChange(bean.copy(actualInput = it)) },
        placeholder = "노트 입력 후 Enter (예: 라즈베리)",
    )
    Spacer(Modifier.height(12.dp))
    Collapsible(title = "항목별 평가 (선택)", open = bean.evaluationOpen, onToggle = { onChange(bean.copy(evaluationOpen = !bean.evaluationOpen)) }) {
        ScoreFormSeg(bean.scoreForm) { onChange(bean.copy(scoreForm = it)) }
        Spacer(Modifier.height(8.dp))
        if (bean.scoreForm == ScoreForm.CVA) {
            CvaSheet(bean.cva, onChange = { onChange(bean.copy(cva = it)) })
        } else ScaForm.cuppingEvaluationFields.forEach { (key, label) ->
            val score = bean.evaluationScores[key]
            SliderRow(
                label = label, value = score, min = 6.0, max = 10.0, step = 0.25,
                readout = score?.let { ScaScoring.format2(it) } ?: "–",
                onChange = { v -> onChange(bean.copy(evaluationScores = bean.evaluationScores + (key to v))) },
            )
            FormTextField(
                value = bean.evaluation[key] ?: "", onValueChange = { onChange(bean.copy(evaluation = bean.evaluation + (key to it))) },
                placeholder = "$label 메모", singleLine = false, modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    FormTextField(
        value = bean.memo, onValueChange = { onChange(bean.copy(memo = it)) }, label = "메모", singleLine = false, minLines = 2,
        placeholder = "온도 변화, 질감, 비교, 수업에서 들은 내용 등을 자유롭게 적어보세요.",
    )
}
