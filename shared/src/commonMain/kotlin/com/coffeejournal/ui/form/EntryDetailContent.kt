package com.coffeejournal.ui.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.reference.ScaForm
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Packages
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.form.sections.StepsSummaryBox
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Badge
import com.coffeejournal.ui.theme.CatDot
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.KeyValueRow
import com.coffeejournal.ui.theme.SectionLabel

/** Web entryHtml, top to bottom. */
@Composable
internal fun EntryDetailContent(en: Entry, siblings: List<Entry>, isBest: Boolean, photoPath: (String) -> String) {
    DetailHeader(en, siblings, isBest)
    if (en.bagPhotos.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            en.bagPhotos.take(2).forEach { name ->
                AsyncImage(
                    model = photoPath(name), contentDescription = "원두 봉투 사진", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(180.dp).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape),
                )
            }
        }
    }
    SectionLabel("정보")
    EntryDisplay.infoRows(en).let { rows ->
        val split = rows.indexOfFirst { it.first == "가공" }.let { if (it < 0) rows.size else it }
        rows.take(split).forEach { (k, v) -> KeyValueRow(k, v) }
        if (en.isCupping && en.cuppingBeans.isNotEmpty()) CuppingBeansList(en)
        rows.drop(split).forEach { (k, v) -> KeyValueRow(k, v) }
    }
    if (en.roasterDesc.isNotBlank()) {
        Text(en.roasterDesc, style = AppType.bodyMuted, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).background(Ink.surfaceRaised).padding(12.dp))
    }
    DetailSca(en)
    DetailSteps(en)
    DetailNotes(en)
    en.groundsPhoto?.let { name ->
        SectionLabel("추출 후 가루 사진")
        AsyncImage(model = photoPath(name), contentDescription = "가루 사진", modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun DetailHeader(en: Entry, siblings: List<Entry>, isBest: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        CatDot(Ink.categoryColor(en.category))
        Spacer(Modifier.width(8.dp))
        Text(BeanNames.displayName(en.name), style = AppType.title, modifier = Modifier.weight(1f, fill = false))
        val pkg = Packages.entryPackageType(en)
        if (pkg != PackageType.STANDARD) { Spacer(Modifier.width(6.dp)); Badge(PackageType.label(pkg)) }
        if (isBest) { Spacer(Modifier.width(6.dp)); Badge("⭐ 베스트", color = Ink.accent, filled = true) }
    }
    EntryDisplay.beanInfoLines(en, siblings).forEach { line ->
        Row(Modifier.padding(top = 3.dp)) {
            Text(line.label, style = AppType.monoSmall, modifier = Modifier.width(84.dp))
            Text(line.value, style = AppType.small.copy(color = Ink.text))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(EntryDisplay.subtitle(en, siblings), style = AppType.small, modifier = Modifier.weight(1f))
        EntryDisplay.scaTotalText(en)?.let { Text(it, style = AppType.monoValue) }
    }
}

/** SCA 항목별 점수 + 강도 + 메모 + TOTAL (web attrsHtml). */
@Composable
private fun DetailSca(en: Entry) {
    val attrs = en.attributes
    if (ScaForm.attrs.none { (attrs[it.key] ?: 0.0) > 0 }) return
    SectionLabel("SCA CUPPING FORM")
    Column(Modifier.fillMaxWidth().background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(12.dp)) {
        ScaForm.attrs.forEach { a ->
            val score = attrs[a.key] ?: 0.0
            if (score > 0) ScoreRow(a.label, ScaScoring.format2(score))
            ScaForm.intensities.firstOrNull { it.after == a.key }?.let { i ->
                val v = attrs[i.key] ?: 0.0
                if (v > 0) ScoreRow(i.label, ScaScoring.format1(v), secondary = true)
            }
            en.attributeNotes[a.key]?.trim()?.takeIf { it.isNotEmpty() }?.let { Text(it, style = AppType.small, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) }
        }
        Hairline(color = Ink.text, thickness = Dimens.rule, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 7.dp)) {
            Text("TOTAL SCORE", style = AppType.cardTitle, modifier = Modifier.weight(1f))
            Text(ScaScoring.total(attrs)?.let { ScaScoring.format2(it) } ?: "–", style = AppType.cardTitle)
        }
    }
}

@Composable
private fun ScoreRow(label: String, value: String, secondary: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = if (secondary) AppType.faint else AppType.small, modifier = Modifier.weight(1f))
        Text(value, style = AppType.monoValue)
    }
}

/** 단계 타임라인 + 요약 + 레시피 대비 차이 (web stepsHtml). */
@Composable
private fun DetailSteps(en: Entry) {
    if (en.steps.isEmpty()) return
    SectionLabel("추출 단계")
    en.steps.forEach { s ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(26.dp).background(if (s.isPour) Ink.accent else Ink.line))
            Spacer(Modifier.width(10.dp))
            Text(s.note.ifBlank { if (s.isPour) "푸어" else "대기" }, style = AppType.small.copy(color = Ink.text), modifier = Modifier.weight(1f))
            Text(s.time, style = AppType.monoValue, modifier = Modifier.width(48.dp))
            Text(if (s.water.isNotBlank()) "${s.water}g" else "", style = AppType.monoValue, modifier = Modifier.width(56.dp))
            Text(if (s.wait.isNotBlank()) "${s.wait}s" else "", style = AppType.monoValue, modifier = Modifier.width(44.dp))
        }
    }
    StepsSummaryBox(en.steps, en.water, en.time, en.recipeRef)
}

/** 메모, with the "Final Evaluation" / "최종 평가" section split out (web splitFinalEvaluation). */
@Composable
private fun DetailNotes(en: Entry) {
    val sections = EntryDisplay.splitFinalEvaluation(en.notes)
    if (sections.hasFinal) {
        SectionLabel("FINAL EVALUATION")
        Text(sections.final.ifBlank { "아직 작성하지 않았어요." }, style = AppType.body, modifier = Modifier.fillMaxWidth().background(Ink.surfaceRaised).padding(12.dp))
    }
    if (sections.before.isNotBlank()) {
        SectionLabel(if (en.isCupping) "전체적인 경험" else "메모")
        Text(sections.before, style = AppType.body)
    }
}
