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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.reference.CvaForm
import com.coffeejournal.domain.rules.CvaAssessment
import com.coffeejournal.domain.rules.CvaScoring
import com.coffeejournal.ui.form.FormTextField
import com.coffeejournal.ui.form.SliderRow
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.Seg
import kotlin.math.roundToInt

/**
 * The SCA CVA sheet laid out like the SCA's combined form (결합 평가): for each box the descriptive part (SCA-103:
 * intensity 0–15 and the check-all-that-apply terms) and the affective part (SCA-104: impression of quality 1–9), then
 * the cups and the affective score. Used for a record's tasting and for each cupping bean.
 */
@Composable
internal fun CvaSheet(value: CvaAssessment, onChange: (CvaAssessment) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text("SCA CVA · 묘사 + 정동 평가", style = AppType.monoSmall, modifier = Modifier.weight(1f))
            Text("SCA-103 · 104", style = AppType.faint)
        }
        Text(
            "강도 0~15: 낮은 0 · 중간 · 높은 15\n품질 인상 1~9: " +
                CvaForm.qualityLabels.mapIndexed { i, l -> "${i + 1} $l" }.joinToString(" · "),
            style = AppType.faint,
        )
        CvaForm.noteBoxes.forEach { box ->
            Spacer(Modifier.height(12.dp))
            Hairline()
            Text(box.ko, style = AppType.cardTitle, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            CvaBox(box, value, onChange)
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
        CvaCups(value, onChange)
        CvaResult(value)
    }
}

@Composable
private fun CvaBox(box: CvaForm.NoteBox, value: CvaAssessment, onChange: (CvaAssessment) -> Unit) {
    val sections = box.sections.map { CvaForm.section(it) }
    val intensitySections = sections.filter { it.intensity }
    // a box of one section labels its two rows 강도 / 품질 인상; a box of two names the sections under each heading
    val single = sections.size == 1
    if (intensitySections.isNotEmpty()) {
        if (!single) SubHead("묘사 · 강도")
        intensitySections.forEach { s ->
            val v = value.intensity[s.key]
            SliderRow(
                label = if (single) "강도" else s.ko, value = v?.toDouble(), min = CvaForm.INTENSITY_MIN.toDouble(), max = CvaForm.INTENSITY_MAX.toDouble(), step = 1.0,
                readout = v?.toString() ?: "–", description = "${s.ko} 강도",
                onChange = { d -> onChange(value.copy(intensity = value.intensity + (s.key to d.roundToInt()))) },
            )
        }
    }
    when (box.key) {
        "aroma" -> DescriptorPicker("향 묘사", CvaForm.olfactory, value.aromaDescriptors, CvaForm.MAX_OLFACTORY) { onChange(value.copy(aromaDescriptors = it)) }
        "flavor" -> {
            DescriptorPicker("맛 묘사", CvaForm.olfactory, value.flavorDescriptors, CvaForm.MAX_OLFACTORY) { onChange(value.copy(flavorDescriptors = it)) }
            DescriptorPicker("주요 맛", CvaForm.mainTastes, value.mainTastes, CvaForm.MAX_MAIN_TASTES) { onChange(value.copy(mainTastes = it)) }
        }
        "mouthfeel" -> DescriptorPicker("마우스필 묘사", CvaForm.mouthfeel, value.mouthfeel, CvaForm.MAX_MOUTHFEEL) { onChange(value.copy(mouthfeel = it)) }
    }
    if (!single) SubHead("정동 · 품질 인상")
    sections.forEach { s ->
        val v = value.affective[s.key]
        SliderRow(
            label = if (single) "품질 인상" else s.ko, value = v?.toDouble(), min = CvaForm.AFFECTIVE_MIN.toDouble(), max = CvaForm.AFFECTIVE_MAX.toDouble(), step = 1.0,
            readout = v?.toString() ?: "–", description = "${s.ko} 품질 인상",
            onChange = { d -> onChange(value.copy(affective = value.affective + (s.key to d.roundToInt()))) },
        )
    }
    FormTextField(
        value = value.notes[box.key] ?: "",
        onValueChange = { t -> onChange(value.copy(notes = value.notes + (box.key to t))) },
        placeholder = "${box.ko} 노트", singleLine = false, modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SubHead(text: String) {
    Text(text, style = AppType.monoSmall, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

/**
 * Check-all-that-apply chips, a category box followed by its narrower boxes on one line as on the form. At most [max]
 * terms: once full, the others are shown but do nothing until one is unticked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DescriptorPicker(title: String, list: List<CvaForm.Descriptor>, selected: List<String>, max: Int, onChange: (List<String>) -> Unit) {
    SubHead("$title (최대 ${max}개 · ${selected.size}/$max)")
    val toggle = { id: String ->
        when {
            id in selected -> onChange(selected - id)
            selected.size < max -> onChange(selected + id)
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { group ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                (listOf(group) + group.children).forEach { d ->
                    Chip(text = d.ko, selected = d.id in selected, onClick = { toggle(d.id) })
                }
            }
        }
    }
}

private val CUP_COUNTS = (0..CvaForm.CUPS).map { it.toString() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CvaCups(value: CvaAssessment, onChange: (CvaAssessment) -> Unit) {
    FieldLabel("균일하지 않은 컵 (5컵 중)", Modifier.padding(top = 10.dp))
    Seg(options = CUP_COUNTS, value = value.nonUniformCups.toString(), allowClear = false, onChange = { onChange(value.copy(nonUniformCups = it.toInt())) })
    FieldLabel("결점이 있는 컵 (5컵 중)", Modifier.padding(top = 10.dp))
    Seg(options = CUP_COUNTS, value = value.defectiveCups.toString(), allowClear = false, onChange = { onChange(value.copy(defectiveCups = it.toInt())) })
    FieldLabel("결점 종류", Modifier.padding(top = 10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CvaForm.defects.forEach { d ->
            val on = d.id in value.defects
            Chip(text = d.ko, selected = on, onClick = { onChange(value.copy(defects = if (on) value.defects - d.id else value.defects + d.id)) })
        }
    }
    HintText("결점 컵은 결점 종류를 함께 골라야 점수에서 빠져요 (SCA-104 5.4.1). 결점 컵은 균일하지 않은 컵으로도 세요.")
}

@Composable
private fun CvaResult(value: CvaAssessment) {
    Spacer(Modifier.height(10.dp))
    Hairline(color = Ink.text, thickness = Dimens.rule)
    val score = CvaScoring.affectiveScore(value)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("CVA 정동 점수", style = AppType.cardTitle, modifier = Modifier.weight(1f))
        Text(score?.let { CvaScoring.format(it) } ?: "–", style = AppType.displayNumber, modifier = Modifier.testTag("cva-score"))
    }
    Text(CvaText.scoreDetail(value), style = AppType.faint)
}

/** Readable lines of a CVA assessment, shared by the form and the record detail. */
internal object CvaText {
    /** "품질 인상 합 52 × 0.65625 + 52.75 − 균일하지 않은 컵 2점 × 1 = 85.00" or how many sections are still missing. */
    fun scoreDetail(a: CvaAssessment): String {
        val count = CvaScoring.affectiveCount(a)
        val sum = CvaScoring.affectiveSum(a) ?: return "품질 인상 8개 중 ${count}개를 매겼어요. 모두 매기면 점수가 나와요."
        val u = a.nonUniformCups.coerceIn(0, CvaForm.CUPS)
        val d = CvaScoring.countedDefectiveCups(a)
        val deductions = buildString {
            if (u > 0) append(" − 균일하지 않은 컵 2점 × $u")
            if (d > 0) append(" − 결점 컵 4점 × $d")
        }
        val score = CvaScoring.affectiveScore(a)?.let { CvaScoring.format(it) } ?: "–"
        return "품질 인상 합 $sum × 0.65625 + 52.75$deductions → 0.25점 단위 $score"
    }

    fun descriptorLine(list: List<CvaForm.Descriptor>, ids: List<String>): String = ids.joinToString(", ") { CvaForm.label(list, it) }
}
