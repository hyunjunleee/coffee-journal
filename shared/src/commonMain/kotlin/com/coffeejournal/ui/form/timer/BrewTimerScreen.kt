package com.coffeejournal.ui.form.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.domain.model.RecipeStep
import com.coffeejournal.domain.rules.Numbers
import com.coffeejournal.domain.rules.Prices
import com.coffeejournal.domain.rules.RecipeSteps
import com.coffeejournal.ui.form.FormTextField
import com.coffeejournal.ui.form.TextLink
import com.coffeejournal.ui.form.sections.StepHeaderRow
import com.coffeejournal.ui.form.sections.StepsSummaryBox
import com.coffeejournal.ui.platform.KeepScreenOn
import com.coffeejournal.ui.platform.rememberStepBuzz
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FitText
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.GlyphButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HairlineCard
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.InputFilters
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.MinTouchTarget
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.SectionLabel
import com.coffeejournal.ui.theme.fontScaled
import com.coffeejournal.ui.theme.imeOverlapPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** "M:SS" of a whole number of seconds. */
private fun clock(sec: Long): String = RecipeSteps.formatSec(sec.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()) ?: "0:00"

/**
 * Route.BrewTimer — the full-screen brew timer opened from the record form's step log (feature-plan-v2 §2.1). The
 * finished brew goes back to the form as step-log rows through the form's back stack entry ([BrewTimerResult.KEY]).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BrewTimerScreen(nav: NavHostController, recipeJson: String?, formHasLog: Boolean) {
    val vm = koinViewModel<BrewTimerViewModel> { parametersOf(BrewTimerArgs(BrewTimerResult.decodeRecipe(recipeJson), formHasLog)) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val buzz = rememberStepBuzz()
    // the timer ticks on a background dispatcher, so its buzzes are taken on the main thread (see App)
    LaunchedEffect(vm) { withContext(Dispatchers.Main) { vm.events.collect { buzz() } } }
    KeepScreenOn(ui.status == TimerStatus.RUNNING)

    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val deliver = { steps: List<RecipeStep> ->
        nav.previousBackStackEntry?.savedStateHandle?.set(BrewTimerResult.KEY, BrewTimerResult.encode(steps))
        nav.popBackStack()
        Unit
    }
    val hasRecord = ui.rows.isNotEmpty()
    val leave = { if (hasRecord) confirmLeave = true else nav.popBackStack() }
    BackHandler(enabled = hasRecord) { confirmLeave = true }

    Column(Modifier.fillMaxSize().background(Ink.bg).statusBarsPadding()) {
        ScreenTitleBar(title = "추출 타이머", onBack = { leave() })
        // ends at the keyboard, so the grams field stays above it
        Column(Modifier.weight(1f).imeOverlapPadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            vm.args.recipe?.let { GuidanceCard(ui) }
            TimeDisplay(ui)
            Spacer(Modifier.height(12.dp))
            val grams = ui.gramsPanel
            if (!ui.finished) {
                Controls(ui, onStart = vm::start, onPause = vm::pause, onReset = { confirmReset = true })
                Spacer(Modifier.height(12.dp))
                // the next pour never waits for the last one's grams: the panel sits under the button
                PourButton(ui, onStart = vm::startPour, onEnd = vm::endPour)
                if (grams != null) {
                    Spacer(Modifier.height(10.dp))
                    GramsPanel(grams, onType = vm::setGrams, onConfirm = vm::confirmGrams, onKeepPouring = vm::keepPouring)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrewTimerEngine.quickNotes.forEach { label -> QuickNoteButton(label, onClick = { vm.note(label) }, modifier = Modifier.weight(1f)) }
                }
                HintText(
                    "붓기 시작·끝으로 부은 구간을, 뜸·스월·드로우다운으로 그 사이 대기를 적어요. 끝나면 단계 로그로 옮겨져요. " +
                        "부은 물은 어림값이 먼저 들어가고, 기록에서 푸어를 누르면 고칠 수 있어요. 잘못 누른 붓기는 옆의 ✕로 지워요.",
                )
                LiveRows(ui, onEditGrams = vm::editGrams, onRemove = vm::removePour, onUndoRemove = vm::undoRemovePour)
            } else {
                if (grams != null) {
                    Spacer(Modifier.height(12.dp))
                    GramsPanel(grams, onType = vm::setGrams, onConfirm = vm::confirmGrams, onKeepPouring = vm::keepPouring)
                }
                FinishedPreview(ui, vm.args, onEditGrams = vm::editGrams)
            }
            Spacer(Modifier.height(96.dp))
        }
        Column(Modifier.fillMaxWidth().background(Ink.bg).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
            Hairline(color = Ink.text, thickness = Dimens.rule)
            Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ui.finished) {
                    PrimaryButton(
                        "단계 로그로 옮기기", onClick = { if (vm.args.formHasLog) confirmReplace = true else deliver(ui.steps) },
                        enabled = ui.steps.isNotEmpty(), modifier = Modifier.weight(1f),
                    )
                    GhostButton("이어서 추출", onClick = vm::resumeBrewing)
                } else {
                    PrimaryButton("추출 끝", onClick = vm::finish, enabled = hasRecord, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (confirmReplace) {
        ConfirmDialog(
            text = "지금 단계 로그를 타이머 기록으로 바꿀까요?\n폼에 적혀 있던 단계는 지워져요.",
            confirm = "바꾸기", onConfirm = { confirmReplace = false; deliver(ui.steps) }, onDismiss = { confirmReplace = false },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            text = "타이머를 처음으로 되돌릴까요?\n지금까지 적은 붓기와 대기도 지워져요.",
            confirm = "초기화", onConfirm = { confirmReset = false; vm.reset() }, onDismiss = { confirmReset = false },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            text = "타이머 기록을 옮기지 않고 나갈까요?\n지금까지 적은 붓기와 대기는 사라져요.",
            confirm = "나가기", onConfirm = { confirmLeave = false; nav.popBackStack() }, onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
private fun TimeDisplay(ui: BrewTimerUi) {
    val status = when {
        ui.finished -> "추출 끝"
        ui.pouring -> "붓는 중"
        ui.status == TimerStatus.RUNNING -> "진행 중"
        ui.status == TimerStatus.PAUSED -> "일시정지"
        else -> "대기"
    }
    val min = ui.elapsedSec / 60
    val sec = ui.elapsedSec % 60
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        FitText(
            clock(ui.elapsedSec), style = AppType.displayNumber.copy(fontSize = 72.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().testTag("timer-display")
                .semantics { contentDescription = "경과 시간 ${min}분 ${sec}초, $status" },
            minFontSize = 24.sp, textAlign = TextAlign.Center,
        )
        val poured = (if (ui.pouredEstimated) "≈ " else "") + "${Prices.trimNumber(ui.pouredSoFar)}g"
        Text(
            "[ $status ]" + if (ui.pouredSoFar > 0) " · 부은 물 $poured" else "",
            style = AppType.monoSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 뜸 / 스월 / 드로우다운: a hairline button whose one-line label shrinks rather than breaks at large font sizes. */
@Composable
private fun QuickNoteButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.heightIn(min = 44.dp)
            .border(androidx.compose.foundation.BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        FitText(label, style = AppType.body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Controls(ui: BrewTimerUi, onStart: () -> Unit, onPause: () -> Unit, onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (ui.status) {
            TimerStatus.RUNNING -> GhostButton("일시정지", onClick = onPause, modifier = Modifier.weight(1f))
            TimerStatus.PAUSED -> GhostButton("계속", onClick = onStart, modifier = Modifier.weight(1f))
            TimerStatus.IDLE -> GhostButton("시작", onClick = onStart, modifier = Modifier.weight(1f))
        }
        GhostButton("초기화", onClick = onReset, enabled = ui.status != TimerStatus.IDLE || ui.rows.isNotEmpty(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PourButton(ui: BrewTimerUi, onStart: () -> Unit, onEnd: () -> Unit) {
    if (ui.pouring) {
        PrimaryButton("붓기 끝", onClick = onEnd, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp))
    } else {
        PrimaryButton(
            "💧 붓기 시작", onClick = onStart, enabled = ui.status != TimerStatus.PAUSED,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        )
    }
}

/** A finished pour's grams as the log shows them: "≈ 60g" while they are the estimate. */
private fun gramsLabel(row: TimerRow): String = if (row.estimated) "≈ ${row.grams}g" else "${row.grams}g"

/**
 * One row of the log (while brewing or in the preview after 추출 끝). A finished pour is a button that opens its grams
 * in the panel ([selected] while it is there); every row has the touch height, so the rows keep one rhythm.
 */
@Composable
private fun LogRow(tag: String, editable: Boolean, selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val base = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).background(if (selected) Ink.accentSoft else Color.Transparent)
    val row = if (editable) base.clickable(role = Role.Button, onClickLabel = "물량 고치기", onClick = onClick).semantics { this.selected = selected } else base
    Row(row.testTag(tag).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    Hairline()
}

/** The applied recipe: the step under way, the countdown to the next one and its target water. */
@Composable
private fun GuidanceCard(ui: BrewTimerUi) {
    val g = ui.guidance ?: return
    HairlineCard(Modifier.padding(top = 12.dp)) {
        Text("레시피 · ${g.recipeName}", style = AppType.monoSmall)
        Spacer(Modifier.height(6.dp))
        val current = g.current
        if (current != null) {
            val water = current.step.water.takeIf { it.isNotBlank() }?.let { " · ${it}g" } ?: ""
            Text("지금: ${current.step.note.ifBlank { "${current.index + 1}단계" }}$water", style = AppType.body)
        }
        val next = g.next
        when {
            next != null -> {
                val target = g.nextWater?.let { w ->
                    " · 목표 ${w}g" + (g.cumulativeTarget?.let { c -> " (누적 ${Prices.trimNumber(c)}g)" } ?: "")
                } ?: ""
                Text(
                    "다음: ${next.step.note.ifBlank { "${next.index + 1}단계" }} ${next.step.time}까지 ${g.secondsToNext ?: 0}초$target",
                    style = AppType.body.copy(color = Ink.text), modifier = Modifier.padding(top = 2.dp).testTag("timer-next"),
                )
            }
            !g.finished && g.secondsToNext != null -> Text("레시피 끝까지 ${g.secondsToNext}초", style = AppType.body, modifier = Modifier.padding(top = 2.dp).testTag("timer-next"))
            else -> Text("레시피 시간이 끝났어요" + (g.endSec?.let { " (${clock(it.toLong())})" } ?: ""), style = AppType.bodyMuted, modifier = Modifier.padding(top = 2.dp).testTag("timer-next"))
        }
    }
}

/**
 * The rows so far, newest last; an estimated amount is muted ("≈ 60g") until it is typed or confirmed. Every pour has a
 * faint ✕ for a 붓기 시작·끝 tapped by mistake (a pour still going is cancelled); where it was, a line offers 되돌리기
 * until the log changes again.
 */
@Composable
private fun LiveRows(ui: BrewTimerUi, onEditGrams: (Int) -> Unit, onRemove: (Int) -> Unit, onUndoRemove: () -> Unit) {
    if (ui.rows.isEmpty() && ui.removed == null) return
    SectionLabel("기록")
    var pourNo = 0
    ui.rows.forEachIndexed { i, row ->
        if (ui.removed?.at == i) RemovedPourLine(ui.removed, onUndoRemove)
        if (row.pour) pourNo++
        val inProgress = i == ui.rows.lastIndex && ui.pouring
        val what = buildAnnotatedString {
            when {
                row.pour -> {
                    append((listOf("${pourNo}차 푸어") + row.notes).joinToString(", "))
                    when {
                        inProgress -> append(" · 붓는 중")
                        row.grams.isNotBlank() -> withStyle(SpanStyle(color = if (row.estimated) Ink.textMuted else Ink.text)) { append(" · ${gramsLabel(row)}") }
                    }
                }
                row.notes.isNotEmpty() -> append(row.notes.joinToString(", "))
                else -> append("대기")
            }
        }
        LogRow("timer-row-$i", editable = row.pour && !inProgress, selected = ui.gramsPanel?.rowIndex == i, onClick = { onEditGrams(i) }) {
            Text(clock(row.startMs / 1000), style = AppType.monoValue, softWrap = false, modifier = Modifier.width(52.dp.fontScaled(1.5f)))
            Text(what, style = AppType.small.copy(color = if (row.pour) Ink.text else Ink.textMuted), modifier = Modifier.weight(1f))
            if (row.pour) {
                GlyphButton(
                    "✕", label = "${pourNo}차 푸어 삭제", onClick = { onRemove(i) },
                    modifier = Modifier.size(Dimens.touch).wrapContentSize(Alignment.Center), style = AppType.small.copy(color = Ink.textFaint),
                )
            }
        }
    }
    if (ui.removed?.at == ui.rows.size) RemovedPourLine(ui.removed, onUndoRemove)
}

/** Where a removed pour was: what happened, faint, and 되돌리기. */
@Composable
private fun RemovedPourLine(removed: BrewTimerUi.Removed, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("timer-removed"), verticalAlignment = Alignment.CenterVertically) {
        Text("${removed.pourNumber}차 푸어를 지웠어요", style = AppType.faint, modifier = Modifier.weight(1f))
        TextLink("되돌리기", Ink.textMuted, onUndo)
    }
    Hairline()
}

/**
 * After 추출 끝: the rows as they will land in the step log, with the summary and the diff against the recipe. A pour
 * still opens its grams; an estimate shows "≈" here and goes to the step log as its plain number.
 */
@Composable
private fun FinishedPreview(ui: BrewTimerUi, args: BrewTimerArgs, onEditGrams: (Int) -> Unit) {
    SectionLabel("단계 로그로 옮길 내용")
    StepHeaderRow("시점", "대기(초)", "물량(g)", "메모")
    ui.steps.forEachIndexed { k, s ->
        val i = ui.stepRows.getOrNull(k)
        val row = i?.let { ui.rows.getOrNull(it) }
        val pour = row?.pour == true
        LogRow("timer-step-$k", editable = pour, selected = i != null && ui.gramsPanel?.rowIndex == i, onClick = { i?.let(onEditGrams) }) {
            Text(s.time, style = AppType.monoValue, softWrap = false, modifier = Modifier.width(52.dp.fontScaled(1.5f)))
            Text(if (s.wait.isNotBlank()) "${s.wait}s" else "-", style = AppType.monoValue, softWrap = false, modifier = Modifier.width(60.dp.fontScaled(1.5f)))
            Text(
                if (row != null && pour && s.water.isNotBlank()) gramsLabel(row) else if (s.water.isNotBlank()) "${s.water}g" else "-",
                style = AppType.monoValue.copy(color = if (row?.estimated == true) Ink.textMuted else Ink.text),
                softWrap = false, modifier = Modifier.width(60.dp.fontScaled(1.5f)),
            )
            Text(s.note, style = AppType.small, modifier = Modifier.weight(1f))
        }
    }
    StepsSummaryBox(ui.steps, "", "", args.recipe)
    if (args.formHasLog) HintText("옮기면 폼에 적혀 있던 단계 로그를 이 기록으로 바꿔요.")
}

/**
 * The grams of one pour, under the pour button (not a dialog, so the running time and the recipe countdown stay in
 * view). The pour already has its estimate, shown pre-filled in a lighter color (the empty field's placeholder), so
 * nothing waits for it: the next pour can start at once, and a tap on the field types the real amount straight over the
 * estimate, in ink. What is typed counts as it is typed; emptying the field gives the estimate back; 확인 closes the
 * panel and, if nothing was typed, accepts the estimate.
 */
@Composable
private fun GramsPanel(p: BrewTimerUi.GramsPanel, onType: (String) -> Unit, onConfirm: () -> Unit, onKeepPouring: () -> Unit) {
    var grams by remember(p.rowIndex) { mutableStateOf(if (p.estimated) "" else p.grams) }
    val valid = grams.isBlank() || (Numbers.parse(grams) ?: 0.0) > 0
    HairlineCard(Modifier.testTag("grams-panel")) {
        Text("${p.pourNumber}차 푸어 물량", style = AppType.cardTitle)
        Text("이번에 부은 물을 적어 주세요 (g).", style = AppType.bodyMuted)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FormTextField(
                value = grams, onValueChange = { grams = it; onType(it) }, placeholder = p.estimate, placeholderColor = Ink.textMuted,
                modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal, inputFilter = InputFilters::decimal,
            )
            PrimaryButton("확인", enabled = valid, onClick = onConfirm)
        }
        HintText(
            if (p.suggestion != null) "적지 않으면 레시피 목표 ${p.estimate}g으로 기록해요."
            else "적지 않으면 붓는 시간으로 어림한 ${p.estimate}g(초당 ${BrewTimerEngine.POUR_GRAMS_PER_SECOND}g)으로 기록해요.",
        )
        if (p.canKeepPouring) Row(Modifier.padding(top = 6.dp)) { GhostButton("붓기 계속", small = true, onClick = onKeepPouring) }
    }
}

@Composable
private fun ConfirmDialog(text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text, style = AppType.body) },
        confirmButton = { PrimaryButton(confirm, small = true, onClick = onConfirm) },
        dismissButton = { GhostButton("취소", small = true, onClick = onDismiss) },
        shape = RectangleShape, containerColor = Ink.bg,
    )
}
