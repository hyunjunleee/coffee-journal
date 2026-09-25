package com.coffeejournal.ui.extract.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeejournal.ui.extract.DrinkingState
import com.coffeejournal.ui.extract.InfoLine
import com.coffeejournal.ui.extract.OpenedBagCard
import com.coffeejournal.ui.extract.RecentBeanCard
import com.coffeejournal.ui.extract.SmallPackCard
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.domain.rules.BeanNames

/** Web #weekly-bean: opened pantry bags as a horizontal strip, or the single most recent bean. */
@Composable
fun DrinkingRow(state: DrinkingState, onEditPantry: (String) -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        DrinkingState.None -> Unit
        is DrinkingState.RecentBean -> RecentBeanCardView(state.card, modifier.fillMaxWidth())
        is DrinkingState.OpenedBags -> LazyRow(
            modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Dimens.gutter),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.cards, key = { it.item.id }) { card -> OpenedBagCardView(card, onEdit = { onEditPantry(card.item.id) }) }
        }
    }
}

@Composable
private fun OpenedBagCardView(card: OpenedBagCard, onEdit: () -> Unit) {
    HairlineCard(Modifier.widthIn(min = 260.dp, max = 300.dp)) {
        Eyebrow(card.eyebrow)
        Text(card.item.name.ifBlank { "이름 없음" }, style = AppType.cardTitle)
        InfoLines(card.infoLines)
        Spacer(Modifier.height(6.dp))
        Text(card.remainingLine, style = AppType.monoValue)
        if (card.priceText.isNotBlank()) Text(card.priceText, style = AppType.small)
        if (card.windowText.isNotBlank()) Text(card.windowText, style = AppType.small)
        Spacer(Modifier.height(8.dp))
        Row { GhostButton("수정", small = true, onClick = onEdit) }
    }
}

@Composable
private fun RecentBeanCardView(card: RecentBeanCard, modifier: Modifier = Modifier) {
    HairlineCard(modifier) {
        Eyebrow(card.eyebrow)
        Text(BeanNames.displayName(card.entry.name), style = AppType.cardTitle)
        InfoLines(card.infoLines)
        Spacer(Modifier.height(6.dp))
        Text(card.remainingLine, style = AppType.monoValue)
        if (card.description != null) {
            Spacer(Modifier.height(8.dp))
            Text(card.description, style = AppType.bodyMuted)
        }
    }
}

/** Web "소량 · 개봉" card shown above the small-pack list. */
@Composable
fun SmallPackCardView(card: SmallPackCard, modifier: Modifier = Modifier) {
    HairlineCard(modifier) {
        Eyebrow("소량 · 개봉")
        Text(card.item.name.ifBlank { "이름 없음" }, style = AppType.cardTitle)
        if (card.statsLine.isNotBlank()) Text(card.statsLine, style = AppType.small)
        if (card.windowText.isNotBlank()) Text(card.windowText, style = AppType.small)
    }
}

@Composable
fun Eyebrow(text: String) {
    Text(text, style = AppType.sectionLabel)
    Spacer(Modifier.height(4.dp))
}

/** "로스터리 X" style lines (web .bean-info-line). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun InfoLines(lines: List<InfoLine>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    Column(modifier) {
        lines.forEach { line ->
            // a value too long to follow its label goes under it whole ("Nordic Approach", not "Approac / h")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(line.label, style = AppType.monoSmall)
                Text(line.value, style = AppType.small.copy(color = Ink.text))
            }
        }
    }
}
