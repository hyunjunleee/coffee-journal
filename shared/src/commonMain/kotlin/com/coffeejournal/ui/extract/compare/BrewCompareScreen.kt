package com.coffeejournal.ui.extract.compare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.coffeejournal.data.repo.BeanMetaRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.deriveOffMain
import com.coffeejournal.ui.theme.fontScaled
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Built off the main thread from the records and the chosen best recipes. Null until the first build. */
class BrewCompareViewModel(beanKey: String, entries: EntryRepository, beanMeta: BeanMetaRepository) : ViewModel() {
    val table: StateFlow<CompareTable?> = combine(entries.observeAll(), beanMeta.observeBest()) { e, b -> e to b }
        .deriveOffMain { (e, best) -> BrewCompare.build(e, beanKey, best[beanKey]) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val LabelWidth = 64.dp
private val ColumnWidth = 104.dp

/**
 * Route.BrewCompare — one bean's brews side by side (feature-plan-v2 §2.2). Row labels stay put while the record
 * columns scroll sideways together (one shared scroll state), so a 320 dp phone shows two columns and slides to the rest.
 */
@Composable
fun BrewCompareScreen(nav: NavHostController, beanKey: String) {
    val vm = koinViewModel<BrewCompareViewModel> { parametersOf(beanKey) }
    val table by vm.table.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Ink.bg).statusBarsPadding()) {
        ScreenTitleBar(title = "추출 비교", onBack = { nav.popBackStack() })
        val t = table
        if (t != null) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            Text(t.beanName, style = AppType.title, modifier = Modifier.padding(top = 14.dp))
            if (t.columns.size < 2) {
                Spacer(Modifier.height(12.dp))
                EmptyNote("비교하려면 이 원두로 내린 기록이 2개 이상 있어야 해요.")
            } else {
                Text(
                    when (t.reference) {
                        null -> "베스트 레시피도 점수도 없어서, 다른 값 표시 없이 나란히 보여줘요."
                        else -> "기준: ${t.reference.label} (첫 열) · 기준과 다른 값은 굵게, 바탕색으로 표시해요."
                    },
                    style = AppType.small, modifier = Modifier.padding(top = 4.dp),
                )
                HintText("표를 옆으로 밀면 다른 기록이 나와요. 날짜를 누르면 그 기록을 열어요.")
                Spacer(Modifier.height(10.dp))
                CompareGrid(t, onOpen = { id -> nav.navigate(Route.EntryDetail(id)) })
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun CompareGrid(t: CompareTable, onOpen: (String) -> Unit) {
    val scroll = rememberScrollState()
    val labelW = LabelWidth.fontScaled(1.5f)
    val colW = ColumnWidth.fontScaled()
    Column(Modifier.fillMaxWidth().border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).testTag("compare-table")) {
        // header: one button per record
        Row(Modifier.fillMaxWidth().background(Ink.surfaceRaised)) {
            Text("기록", style = AppType.monoSmall, modifier = Modifier.width(labelW).padding(8.dp))
            ScrollingCells(scroll) {
                t.columns.forEach { c ->
                    Column(
                        Modifier.width(colW).heightIn(min = 48.dp)
                            .background(if (c.isReference) Ink.accent else Ink.surfaceRaised)
                            .clickable(role = Role.Button, onClickLabel = "기록 열기") { onOpen(c.entryId) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(if (c.isReference) "기준" else "비교", style = AppType.monoSmall.copy(color = if (c.isReference) Ink.bg else Ink.textFaint))
                        Text(c.dateText, style = AppType.monoValue.copy(color = if (c.isReference) Ink.bg else Ink.text))
                    }
                }
            }
        }
        t.rows.forEach { row ->
            Hairline()
            // every cell of a row as tall as its tallest one, so the marked cells read as whole boxes
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
                Text(row.label, style = AppType.monoSmall, modifier = Modifier.width(labelW).padding(horizontal = 8.dp, vertical = 8.dp))
                ScrollingCells(scroll, Modifier.fillMaxHeight()) {
                    row.cells.forEachIndexed { i, cell -> Cell(row.label, cell, colW, isReference = t.columns[i].isReference) }
                }
            }
        }
    }
}

@Composable
private fun ScrollingCells(scroll: ScrollState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.horizontalScroll(scroll)) { content() }
}

@Composable
private fun Cell(label: String, cell: CompareCell, width: Dp, isReference: Boolean) {
    val description = buildString {
        append("$label ${cell.text}")
        if (isReference) append(", 기준")
        else if (cell.differs) append(", 기준과 다름" + (cell.delta?.let { " ($it)" } ?: ""))
    }
    Column(
        Modifier.width(width).fillMaxHeight().background(if (cell.differs) Ink.accentSoft else Ink.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(
            cell.text,
            style = (if (label == "노트") AppType.small.copy(color = Ink.text) else AppType.monoValue)
                .copy(fontWeight = if (cell.differs) FontWeight.Bold else FontWeight.Normal),
        )
        cell.delta?.let { Text(it, style = AppType.monoSmall.copy(color = Ink.accent)) }
    }
}
