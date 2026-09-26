package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.map.KoreaMap
import com.coffeejournal.ui.map.KoreaMapState
import com.coffeejournal.ui.map.MapLinkButtons
import com.coffeejournal.ui.map.MapPin
import com.coffeejournal.ui.map.WorldPinMap
import com.coffeejournal.ui.map.WorldPinMapState
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink

/**
 * Web .roastery-map-shell: title + count, the map with pins (국내: the SGIS Korea map, tap a 시·도 to zoom into its
 * 시·군·구; 해외: the world map), the selected roastery with its records and map-app links, and the roasteries the map
 * could not place.
 */
@Composable
fun RoasteryMapCard(
    model: RoasteryMapModel,
    scope: String,
    records: List<BeanRecord>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onOpenEntry: (String) -> Unit,
    koreaState: KoreaMapState,
    worldState: WorldPinMapState,
    modifier: Modifier = Modifier,
) {
    val domestic = scope == Scope.DOMESTIC
    val pins = remember(model) { model.pins.map { MapPin(it.item.name, it.item.name, it.at, it.count, "${it.item.name}, ${it.count}잔") } }
    val shaded = remember(model) { model.pins.mapNotNull { it.area }.toSet() }
    val description = FlatItemLogic.roasteryMapDescription(domestic, model)
    val onPin: (String) -> Unit = { key -> onSelect(if (key == selected) null else key) }
    Column(modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(if (domestic) "한국 로스터리 지도" else "해외 로스터리 지도", style = AppType.cardTitle, modifier = Modifier.weight(1f))
            Text("${model.items.size}곳 · ${model.cups}잔", style = AppType.count)
        }
        if (domestic) {
            KoreaMap(koreaState, description, pins, selected, onPin, shaded = shaded, onBackgroundTap = { onSelect(null) })
            Text(
                if (koreaState.isNational) "시·도를 누르면 시·군·구 지도로 확대돼요. 두 손가락으로 늘리고 옮길 수 있어요."
                else "뒤로 가기나 ‘← 전국’을 누르면 전국 지도로 돌아가요.",
                style = AppType.faint, modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            WorldPinMap(worldState, description, pins, selected, onPin, onTap = { onSelect(null) }, shaded = shaded)
        }
        val pin = model.pins.firstOrNull { it.item.name == selected }
        val item = pin?.item ?: model.items.firstOrNull { it.name == selected }
        if (item != null) {
            HairlineCard(Modifier.padding(top = 10.dp)) {
                Text(item.name, style = AppType.cardTitle)
                if (item.location.isNotBlank()) Text(item.location, style = AppType.small)
                if (pin != null && pin.place.isNotBlank()) {
                    // an exact position names its area; otherwise the pin stands at the centre of the area the text named
                    Text(if (pin.exact) "📍 ${pin.place}" else "${pin.place} 중심에 표시", style = AppType.faint)
                }
                val recs = FlatItemLogic.roasteryRecords(records, item.name).sortedByDescending { it.createdAt }
                if (recs.isEmpty()) EmptyNote("연결된 원두 기록이 없어요.", Modifier.padding(top = 8.dp))
                else recs.forEach { r ->
                    SourceBeanRow(BeanNames.coreBeanName(r.name).ifEmpty { r.name.ifBlank { "이름 없음" } }, right = Dates.ymdCompact(r.createdAt), badge = r.category, onClick = { onOpenEntry(r.entryId) })
                }
                MapLinkButtons(item.name, item.location, item.point, overseas = !domestic)
            }
        }
        if (model.unlocated.isNotEmpty()) {
            NoteBox("위치 미입력 ${model.unlocated.size}곳", model.unlocated.joinToString(" · ") { it.name }, "아래 목록에서 지역을 입력하면 지도에 표시돼요.")
        }
        if (model.unmatched.isNotEmpty()) {
            NoteBox(
                "지도에서 찾지 못한 곳 ${model.unmatched.size}곳", model.unmatched.joinToString(" · ") { "${it.name}(${it.location.trim()})" },
                "로스터리를 수정해 지역을 ${if (domestic) "“서울 성동구”처럼" else "나라 이름으로"} 적거나 ‘지도에서 위치 지정’으로 찍으면 표시돼요.",
            )
        }
    }
}

@Composable
private fun NoteBox(title: String, names: String, hint: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(12.dp)) {
        Text(title, style = AppType.small.copy(color = Ink.text, fontWeight = FontWeight.SemiBold))
        Text(names, style = AppType.small, modifier = Modifier.padding(top = 5.dp))
        Text(hint, style = AppType.faint, modifier = Modifier.padding(top = 5.dp))
    }
}

/** Web sourceBeansInfo: beans bought at a roastery with their date span, where they were had, and the breakdown. */
@Composable
fun SourceBeansInfo(matching: List<BeanRecord>) {
    if (matching.isEmpty()) return
    SubLabel("여기서 산 원두")
    FlatItemLogic.beanSpans(matching).forEach { b ->
        val start = Dates.ymdCompact(b.start); val end = Dates.ymdCompact(b.end)
        SourceBeanRow(b.name, right = if (start == end) start else "$start ~ $end", badge = b.category)
    }
    BreakdownLine(matching)
}
