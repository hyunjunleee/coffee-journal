package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.BeanMode
import com.coffeejournal.domain.model.Blend
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HairlineCard

const val EMPTY_BLENDS = "아직 블렌드 기록이 없어요. 원두 섞어 마신 거, 그램수까지 남겨보세요."

/** Web blendCardHtml / entryBlendCardHtml / cuppingBlendCardHtml as one card per merged item. */
@Composable
fun BlendItemCard(item: BlendItem, onEdit: (Blend) -> Unit, onDelete: (Blend) -> Unit, onOpenEntry: (String) -> Unit, modifier: Modifier = Modifier) {
    HairlineCard(modifier.padding(bottom = 8.dp)) {
        when (item) {
            is BlendItem.Custom -> {
                val b = item.blend
                Text(BlendSources.customTitle(b), style = AppType.cardTitle)
                Text(BlendSources.customDate(b), style = AppType.faint, modifier = Modifier.padding(bottom = 6.dp))
                BlendSources.componentLines(b.beans).forEach { Text(it, style = AppType.small) }
                if (b.notes.isNotBlank()) Text(b.notes, style = AppType.bodyMuted, modifier = Modifier.padding(top = 6.dp))
                EditDeleteActions(onEdit = { onEdit(b) }, onDelete = { onDelete(b) }, confirmText = "이 블렌드 기록을 삭제할까요?")
            }
            is BlendItem.FromEntry -> {
                val en = item.entry
                val custom = en.beanMode == BeanMode.CUSTOM_BLEND
                Text(en.name.ifBlank { "이름 없는 블렌드" }, style = AppType.cardTitle)
                Text("${if (custom) "내가 만든 블렌드" else "카페 블렌드"} · ${Dates.ymdCompact(en.createdAt)}", style = AppType.faint, modifier = Modifier.padding(bottom = 6.dp))
                if (custom) BlendSources.componentLines(en.blendComponents).forEach { Text(it, style = AppType.small) }
                else {
                    Text(BlendSources.commercialLine(en), style = AppType.small)
                    BlendSources.cafeBlendLines(en).forEach { Text(it, style = AppType.small) }
                }
                if (en.notes.isNotBlank()) Text(en.notes, style = AppType.bodyMuted, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 10.dp)) { GhostButton("추출 기록 열기", small = true, onClick = { onOpenEntry(en.id) }) }
            }
            is BlendItem.FromCupping -> {
                val en = item.entry; val bean = item.bean
                Text(bean.name.ifBlank { "이름 없는 블렌드" }, style = AppType.cardTitle)
                Text("커핑한 블렌드 · ${Dates.ymdCompact(en.createdAt)} · ${en.cuppingPlace.ifBlank { "장소 미입력" }}", style = AppType.faint, modifier = Modifier.padding(bottom = 6.dp))
                Text(bean.blendComponentsText.ifBlank { "구성 미입력" }, style = AppType.small)
                if (bean.memo.isNotBlank()) Text(bean.memo, style = AppType.bodyMuted, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 10.dp)) { GhostButton("커핑 기록 열기", small = true, onClick = { onOpenEntry(en.id) }) }
            }
        }
    }
}
