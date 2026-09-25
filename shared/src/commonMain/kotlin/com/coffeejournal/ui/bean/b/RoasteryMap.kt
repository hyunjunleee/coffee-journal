package com.coffeejournal.ui.bean.b

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.coffeejournal.domain.model.BeanRecord
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import kotlin.math.roundToInt

/** Web .roastery-map-shell: title + count, the blob "map" with pins, the selected roastery and the unlocated list. */
@Composable
fun RoasteryMapCard(
    model: RoasteryMapModel,
    scope: String,
    records: List<BeanRecord>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val domestic = scope == Scope.DOMESTIC
    Column(modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(if (domestic) "한국 로스터리 지도" else "해외 로스터리 지도", style = AppType.cardTitle, modifier = Modifier.weight(1f))
            Text("${model.items.size}곳 · ${model.cups}잔", style = AppType.count)
        }
        Box(
            Modifier.fillMaxWidth().height(300.dp)
                .background(Brush.linearGradient(listOf(Ink.surface, Ink.surfaceRaised)))
                .border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
                .drawBehind { drawBlobs(domestic) }
                // the drawn shape means nothing to TalkBack; the pins inside are buttons, the full list is below
                .semantics { contentDescription = FlatItemLogic.roasteryMapDescription(domestic, model) },
        ) {
            Text(
                if (domestic) "KOREA" else "WORLD",
                style = AppType.count.copy(fontSize = 30.sp, letterSpacing = 0.2.em, color = Ink.textFaint.copy(alpha = 0.35f)),
                modifier = Modifier.align(Alignment.Center).clearAndSetSemantics { },
            )
            PinLayer(model.pins, selected, onSelect)
        }
        val item = model.items.firstOrNull { it.name == selected }
        if (item != null) {
            HairlineCard(Modifier.padding(top = 10.dp)) {
                Text(item.name, style = AppType.cardTitle)
                if (item.location.isNotBlank()) Text(item.location, style = AppType.small)
                val recs = FlatItemLogic.roasteryRecords(records, item.name).sortedByDescending { it.createdAt }
                if (recs.isEmpty()) EmptyNote("연결된 원두 기록이 없어요.", Modifier.padding(top = 8.dp))
                else recs.forEach { r ->
                    SourceBeanRow(BeanNames.coreBeanName(r.name).ifEmpty { r.name.ifBlank { "이름 없음" } }, right = Dates.ymdCompact(r.createdAt), badge = r.category, onClick = { onOpenEntry(r.entryId) })
                }
            }
        }
        if (model.unlocated.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(12.dp)) {
                Text("위치 미입력 ${model.unlocated.size}곳", style = AppType.small.copy(color = Ink.text, fontWeight = FontWeight.SemiBold))
                Text(model.unlocated.joinToString(" · ") { it.name }, style = AppType.small, modifier = Modifier.padding(top = 5.dp))
                Text("아래 목록에서 지역을 입력하면 지도에 표시돼요.", style = AppType.faint, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

/** The web's CSS blobs: a rotated rounded shape for Korea, a wide oval plus two satellites for the world. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlobs(domestic: Boolean) {
    val w = size.width; val h = size.height
    val stroke = Stroke(1.dp.toPx())
    if (domestic) {
        val tl = Offset(w * 0.34f, h * 0.09f); val sz = Size(w * 0.35f, h * 0.83f)
        val radius = CornerRadius(sz.width * 0.45f, sz.height * 0.22f)
        rotate(4f, pivot = Offset(tl.x + sz.width / 2, tl.y + sz.height / 2)) {
            drawRoundRect(Ink.surfaceRaised, tl, sz, radius)
            drawRoundRect(Ink.line.copy(alpha = 0.5f), tl, sz, radius, style = stroke)
        }
    } else {
        val tl = Offset(w * 0.05f, h * 0.18f); val sz = Size(w * 0.90f, h * 0.64f)
        drawOval(Ink.surfaceRaised, tl, sz)
        drawOval(Ink.line.copy(alpha = 0.5f), tl, sz, style = stroke)
        listOf(Offset(w * 0.62f, h * 0.30f) to Size(w * 0.30f, h * 0.36f), Offset(w * 0.07f, h * 0.26f) to Size(w * 0.26f, h * 0.30f)).forEach { (o, s) ->
            drawOval(Ink.surface, o, s)
            drawOval(Ink.line.copy(alpha = 0.5f), o, s, style = stroke)
        }
    }
}

/**
 * Places each pin chip centred on its percent coordinate, clamped inside the canvas. The chip keeps its small look;
 * a transparent box around it takes the taps, so the target is [Dimens.touch] tall (the ripple stays on the chip).
 */
@Composable
private fun PinLayer(pins: List<RoasteryPin>, selected: String?, onSelect: (String?) -> Unit) {
    Layout(
        content = {
            pins.forEach { pin ->
                val active = pin.item.name == selected
                val press = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .heightIn(min = Dimens.touch)
                        .selectable(selected = active, interactionSource = press, indication = null, role = Role.Button) { onSelect(if (active) null else pin.item.name) }
                        .semantics { contentDescription = "${pin.item.name}, ${pin.count}잔" }
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier
                            .background(if (active) Ink.text else Ink.surface)
                            .border(BorderStroke(1.dp, Ink.text), RectangleShape)
                            .indication(press, ripple())
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(pin.item.name, style = AppType.small.copy(color = if (active) Ink.surface else Ink.text, fontWeight = FontWeight.Medium, fontSize = 11.sp), maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Text(pin.count.toString(), style = AppType.monoSmall.copy(color = if (active) Ink.surface else Ink.textMuted, fontSize = 9.sp))
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val cx = (pins[i].x / 100f * constraints.maxWidth).roundToInt()
                val cy = (pins[i].y / 100f * constraints.maxHeight).roundToInt()
                val x = (cx - p.width / 2).coerceIn(0, maxOf(0, constraints.maxWidth - p.width))
                val y = (cy - p.height / 2).coerceIn(0, maxOf(0, constraints.maxHeight - p.height))
                p.placeRelative(x, y)
            }
        }
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
