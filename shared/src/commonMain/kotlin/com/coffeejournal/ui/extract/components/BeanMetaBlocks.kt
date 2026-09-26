package com.coffeejournal.ui.extract.components

import com.coffeejournal.domain.rules.CvaScoring
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.extract.ExtractGrouping
import com.coffeejournal.ui.extract.GroupUi
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.PrimaryButton

/** Web "☕ 이 원두 총정리" card plus the manual editor (no AI button). */
@Composable
fun BeanSummaryBlock(
    summary: String?, editing: Boolean, draft: String,
    onStartEdit: () -> Unit, onDraft: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit,
) {
    if (summary != null && !editing) {
        HairlineCard(Modifier.padding(bottom = 8.dp)) {
            Text("☕ 이 원두 총정리", style = AppType.cardTitle)
            Spacer(Modifier.height(6.dp))
            Text(summary, style = AppType.body)
        }
    }
    if (editing) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            AppTextField(
                value = draft, onValueChange = onDraft, singleLine = false, minLines = 4,
                placeholder = "이 원두를 마시면서 어땠는지 자유롭게 적어보세요",
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("저장", small = true, onClick = onSave)
                GhostButton("취소", small = true, onClick = onCancel)
            }
        }
    } else {
        Row(Modifier.padding(bottom = 8.dp)) {
            GhostButton(if (summary != null) "✏️ 내용 고치기" else "✏️ 직접 정리하기", small = true, onClick = onStartEdit)
        }
    }
}

/** Web "⭐ 이 원두의 베스트 레시피" card, the pick button (5+ records) and the inline picker. */
@Composable
fun BestRecipeBlock(ui: GroupUi, onOpenPicker: () -> Unit, onPick: (String) -> Unit) {
    val best = ui.bestEntry
    if (best != null) {
        HairlineCard(Modifier.padding(bottom = 8.dp)) {
            Text("⭐ 이 원두의 베스트 레시피", style = AppType.cardTitle)
            Spacer(Modifier.height(6.dp))
            Text(bestLine(best), style = AppType.body)
            Spacer(Modifier.height(6.dp))
            Text("다시 고르기", style = AppType.small.copy(color = Ink.accent), modifier = Modifier.clickable(onClick = onOpenPicker))
        }
    }
    if (ui.showPickButton) {
        Row(Modifier.padding(bottom = 8.dp)) { GhostButton("⭐ 베스트 레시피 고르기", small = true, onClick = onOpenPicker) }
    }
    if (ui.pickerOpen) {
        HairlineCard(Modifier.padding(bottom = 8.dp)) {
            Text("이 원두로 마셨던 것 중 가장 좋았던 걸 골라주세요", style = AppType.bodyMuted)
            Spacer(Modifier.height(6.dp))
            ui.group.recipeEntries.forEach { en ->
                Hairline()
                Row(Modifier.fillMaxWidth().clickable { onPick(en.id) }.padding(vertical = 9.dp)) {
                    Text(
                        "${Dates.isoDate(Dates.toLocalDate(en.createdAt))} · ${ExtractGrouping.recipeSummaryLine(en).ifBlank { "레시피 정보 없음" }}",
                        style = AppType.small.copy(color = Ink.text), modifier = Modifier.weight(1f),
                    )
                    CvaScoring.scoreText(en)?.let { Text(it, style = AppType.monoValue) }
                }
            }
        }
    }
}

private fun bestLine(en: Entry): String {
    val score = CvaScoring.scoreText(en)?.let { " · $it" } ?: ""
    return "${Dates.isoDate(Dates.toLocalDate(en.createdAt))} 기록 · ${ExtractGrouping.recipeSummaryLine(en)}$score"
}
