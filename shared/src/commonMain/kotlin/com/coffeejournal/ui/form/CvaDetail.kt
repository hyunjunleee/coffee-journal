package com.coffeejournal.ui.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.reference.CvaForm
import com.coffeejournal.domain.rules.CvaAssessment
import com.coffeejournal.domain.rules.CvaScoring
import com.coffeejournal.ui.form.sections.CvaText
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.KeyValueRow
import com.coffeejournal.ui.theme.fontScaled

/** A saved CVA assessment on the record detail / cupping bean: section table, descriptors, cups, notes, score. */
@Composable
internal fun CvaDetailBlock(a: CvaAssessment, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text("섹션", style = AppType.monoSmall, modifier = Modifier.weight(1f))
            Text("강도", style = AppType.monoSmall, textAlign = TextAlign.End, modifier = Modifier.width(44.dp.fontScaled()))
            Text("품질", style = AppType.monoSmall, textAlign = TextAlign.End, modifier = Modifier.width(44.dp.fontScaled()))
        }
        CvaForm.sections.forEach { s ->
            val i = a.intensity[s.key]
            val q = a.affective[s.key]
            if (i != null || q != null) Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).semantics(mergeDescendants = true) {
                    contentDescription = s.ko + (i?.let { ", 강도 $it" } ?: "") + (q?.let { ", 품질 인상 $it ${CvaForm.qualityLabel(it) ?: ""}" } ?: "")
                },
            ) {
                Text(s.ko, style = AppType.small, modifier = Modifier.weight(1f))
                Text(i?.toString() ?: "-", style = AppType.monoValue, textAlign = TextAlign.End, modifier = Modifier.width(44.dp.fontScaled()))
                Text(q?.toString() ?: "-", style = AppType.monoValue, textAlign = TextAlign.End, modifier = Modifier.width(44.dp.fontScaled()))
            }
        }
        val lines = listOfNotNull(
            a.aromaDescriptors.takeIf { it.isNotEmpty() }?.let { "향 묘사" to CvaText.descriptorLine(CvaForm.olfactoryFlat, it) },
            a.flavorDescriptors.takeIf { it.isNotEmpty() }?.let { "맛 묘사" to CvaText.descriptorLine(CvaForm.olfactoryFlat, it) },
            a.mainTastes.takeIf { it.isNotEmpty() }?.let { "주요 맛" to CvaText.descriptorLine(CvaForm.mainTastes, it) },
            a.mouthfeel.takeIf { it.isNotEmpty() }?.let { "마우스필" to CvaText.descriptorLine(CvaForm.mouthfeel, it) },
            cupsLine(a)?.let { "컵" to it },
        )
        if (lines.isNotEmpty()) {
            Hairline(modifier = Modifier.padding(vertical = 6.dp))
            lines.forEach { (k, v) -> KeyValueRow(k, v) }
        }
        CvaForm.noteBoxes.forEach { box ->
            a.notes[box.key]?.trim()?.takeIf { it.isNotEmpty() }?.let { note ->
                Text(box.ko, style = AppType.monoSmall, modifier = Modifier.padding(top = 6.dp))
                Text(note, style = AppType.small.copy(color = Ink.text))
            }
        }
        Hairline(color = Ink.text, thickness = Dimens.rule, modifier = Modifier.padding(top = 8.dp))
        val score = CvaScoring.affectiveScore(a)
        Row(Modifier.fillMaxWidth().padding(top = 7.dp)) {
            Text("CVA 정동 점수", style = AppType.cardTitle, modifier = Modifier.weight(1f))
            Text(score?.let { CvaScoring.format(it) } ?: "–", style = AppType.cardTitle)
        }
        Text(CvaText.scoreDetail(a), style = AppType.faint)
    }
}

private fun cupsLine(a: CvaAssessment): String? {
    if (a.nonUniformCups == 0 && a.defectiveCups == 0 && a.defects.isEmpty()) return null
    val parts = mutableListOf<String>()
    if (a.nonUniformCups > 0) parts += "균일하지 않은 컵 ${a.nonUniformCups}"
    if (a.defectiveCups > 0) parts += "결점 컵 ${a.defectiveCups}"
    if (a.defects.isNotEmpty()) parts += "결점: " + CvaText.descriptorLine(CvaForm.defects, a.defects)
    return parts.joinToString(" · ")
}
