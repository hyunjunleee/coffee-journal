package com.coffeejournal.ui.extract.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.domain.rules.ScaScoring
import com.coffeejournal.ui.extract.ExtractGrouping
import com.coffeejournal.ui.extract.GroupUi
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.Ink

/** Callbacks for the expanded part of a bean group. */
class GroupActions(
    val onToggle: () -> Unit,
    val onOpenEntry: (String) -> Unit,
    val onStartEditSummary: () -> Unit,
    val onSummaryDraft: (String) -> Unit,
    val onSaveSummary: () -> Unit,
    val onCancelSummary: () -> Unit,
    val onOpenPicker: () -> Unit,
    val onPick: (String) -> Unit,
)

/** Web .bean-group: collapsible header, then summary / best recipe / record rows. */
@Composable
fun BeanGroupCard(ui: GroupUi, summaryDraft: String, photoStore: PhotoStore, actions: GroupActions, modifier: Modifier = Modifier) {
    HairlineCard(modifier, padding = PaddingValues(0.dp)) {
        GroupHeader(ui, photoStore, onClick = actions.onToggle)
        if (ui.expanded) {
            Hairline()
            Column(Modifier.background(Ink.bg).padding(horizontal = Dimens.cardPadding, vertical = 10.dp)) {
                BeanSummaryBlock(
                    summary = ui.summary, editing = ui.editingSummary, draft = summaryDraft,
                    onStartEdit = actions.onStartEditSummary, onDraft = actions.onSummaryDraft,
                    onSave = actions.onSaveSummary, onCancel = actions.onCancelSummary,
                )
                BestRecipeBlock(ui, onOpenPicker = actions.onOpenPicker, onPick = actions.onPick)
                Spacer(Modifier.height(6.dp))
                Hairline()
                ui.group.entries.forEach { listed ->
                    val row = ExtractGrouping.entryRow(listed.entry, isBest = !listed.isHomeCuppingProjection && listed.entry.id == ui.bestEntryId)
                    EntrySummaryRow(row, onClick = { actions.onOpenEntry(listed.entry.id) })
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(ui: GroupUi, photoStore: PhotoStore, onClick: () -> Unit) {
    val g = ui.group
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Dimens.cardPadding), verticalAlignment = Alignment.Top) {
        g.thumbnailPhoto?.let { photo ->
            AsyncImage(
                model = "file://" + photoStore.pathFor(photo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).background(Ink.surfaceRaised),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(g.displayName, style = AppType.cardTitle)
            if (g.customBlendLine != null) Text(g.customBlendLine, style = AppType.small)
            else InfoLines(g.infoLines)
            Spacer(Modifier.height(4.dp))
            Text(g.dateRangeLabel, style = AppType.monoSmall)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (g.count > 1) Text("${g.count}개 기록", style = AppType.monoSmall)
            g.highestScore?.let { Text("최고 ${ScaScoring.format2(it)}", style = AppType.monoValue) }
            Text(if (ui.expanded) "▴" else "▾", style = AppType.small)
        }
    }
}
