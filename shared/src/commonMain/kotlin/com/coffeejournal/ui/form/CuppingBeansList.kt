package com.coffeejournal.ui.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.CuppingBean
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.reference.ScaForm
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.KeyValueRow

/** Web .cupping-saved-list: one accordion per cupping bean. */
@Composable
internal fun CuppingBeansList(en: Entry) {
    val open = remember { mutableStateMapOf<Int, Boolean>() }
    Text("CUPPING BEANS · ${en.cuppingBeans.size}", style = AppType.monoSmall, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
    en.cuppingBeans.forEachIndexed { index, bean ->
        val meta = listOf(bean.country, bean.variety, bean.process).filter { it.isNotBlank() }.joinToString(" · ")
        val title = "${(index + 1).toString().padStart(2, '0')}  ${bean.name}" + if (meta.isNotBlank()) "  ·  $meta" else ""
        Collapsible(title = title, open = open[index] == true, onToggle = { open[index] = open[index] != true }, modifier = Modifier.padding(bottom = 6.dp)) {
            CuppingBeanBody(bean)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CuppingBeanBody(bean: CuppingBean) {
    val facts = listOf(
        "나의 순위" to bean.rank.takeIf { it.isNotBlank() }?.let { "${it}위" },
        "지역" to bean.region,
        "농장" to bean.farmProducer,
        "고도" to bean.altitude,
        "배전" to bean.roast,
        "가격" to EntryDisplay.priceText(bean.price),
    ).filter { !it.second.isNullOrBlank() }
    Column(Modifier.fillMaxWidth()) {
        facts.forEach { (k, v) -> KeyValueRow(k, v!!) }
        NoteBlock("예상 노트", bean.expectedNotes)
        NoteBlock("내가 마신 노트", bean.actualNotes)
        val rows = ScaForm.cuppingEvaluationFields.filter { (k, _) -> bean.evaluation[k]?.isNotBlank() == true || (bean.evaluationScores[k] ?: 0.0) > 0 }
        if (rows.isNotEmpty()) {
            Text("항목별 평가", style = AppType.monoSmall, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            rows.forEach { (k, label) ->
                val score = bean.evaluationScores[k]?.takeIf { it > 0 }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(label + (score?.let { " · ${ScaScoring.format2(it)}" } ?: ""), style = AppType.small, modifier = Modifier.width(150.dp))
                    Text(bean.evaluation[k] ?: "", style = AppType.small.copy(color = com.coffeejournal.ui.theme.Ink.text), modifier = Modifier.weight(1f))
                }
            }
        }
        NoteBlock("메모", bean.memo)
    }
}

@Composable
private fun NoteBlock(label: String, text: String) {
    if (text.isBlank()) return
    Text(label, style = AppType.monoSmall, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
    Text(text, style = AppType.small.copy(color = com.coffeejournal.ui.theme.Ink.text))
}
