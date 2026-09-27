package com.coffeejournal.ui.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coffeejournal.ui.form.TextLink
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.platform.openUrl
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Badge
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Display
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.GhostButton
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.MinTouchTarget
import com.coffeejournal.ui.theme.Motion
import com.coffeejournal.ui.theme.PrimaryButton
import com.coffeejournal.ui.theme.ScreenTitleBar
import com.coffeejournal.ui.theme.SectionLabel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Route.NoteHelper: the question, then the answer tied to its sources ([n] marks that lead to the numbered source
 * list, grey sentences that no source backs, quote checks), mode B's candidate notes, or what went wrong.
 */
@Composable
fun NoteHelperScreen(nav: NavHostController, route: Route.NoteHelper) {
    val args = remember(route) { NoteHelperArgs(NoteMode.of(route.mode), route.query, route.returnToForm) }
    val vm = koinViewModel<NoteHelperViewModel> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    val picked by vm.picked.collectAsStateWithLifecycle()
    val focus = koinInject<AiSettingsFocus>()
    // back from 설정 with a key now saved
    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }
    val openSettings = { focus.request(); nav.navigate(Route.Settings) }
    val deliver = { notes: List<String> ->
        nav.previousBackStackEntry?.savedStateHandle?.set(NoteHelperResult.KEY, NoteHelperResult.encode(notes))
        nav.popBackStack()
        Unit
    }

    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        ScreenTitleBar(AiTexts.TITLE, onBack = { nav.popBackStack() })
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).testTag("note-helper").verticalScroll(scroll).padding(horizontal = Dimens.gutter)) {
            SectionLabel(args.mode.label)
            Text("“${args.query}”", style = AppType.cardTitle)
            Spacer(Modifier.height(8.dp))
            when (val s = state) {
                NoteHelperUi.Preparing -> Unit
                is NoteHelperUi.NeedsKey -> NeedsKey(s, openSettings)
                is NoteHelperUi.NeedsConsent -> Text("${s.provider.label}로 물어볼게요.", style = AppType.bodyMuted)
                is NoteHelperUi.Declined -> {
                    Text(AiTexts.DECLINED, style = AppType.bodyMuted)
                    GhostButton(AiTexts.ASK_AGAIN, small = true, onClick = vm::start, modifier = Modifier.padding(top = 12.dp))
                }
                is NoteHelperUi.Asking -> Asking(s)
                is NoteHelperUi.Failed -> Failure(s, openSettings, onAskAgain = vm::start)
                is NoteHelperUi.Answered -> Answer(
                    s, args, picked, onToggle = vm::toggle, onAdd = { deliver(picked) }, onAskAgain = vm::start, onAskWith = vm::askWith,
                )
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    (state as? NoteHelperUi.NeedsConsent)?.let { s -> ConsentDialog(s.provider, onAnswer = vm::consent) }
}

/**
 * While the question is out: "출처를 찾아 답을 쓰고 있어요" with moving dots, the steps of the chosen service (the one
 * under way in ink with the same dots, finished ones ✓, later ones faint, a skipped one struck out), then the route and
 * the model.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Asking(s: NoteHelperUi.Asking) {
    val dots = rememberMovingDots()
    DotsText(AiTexts.ASKING, AppType.body, dots, Modifier.testTag("asking"))
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 6.dp).testTag("ask-stages"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        s.progress.stages.forEachIndexed { i, stage ->
            if (i > 0) Text("›", style = AppType.small.copy(color = Ink.textFaint))
            StageLabel(stage, s.progress.status(stage), dots)
        }
    }
    Text("${s.provider.route} · ${s.model}", style = AppType.monoSmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun StageLabel(stage: AskStage, status: StageStatus, dots: State<Int>?) {
    val modifier = Modifier.testTag("ask-stage-${stage.name}").semantics { stateDescription = AiTexts.stageState(status) }
    when (status) {
        StageStatus.CURRENT -> DotsText(stage.label, AppType.small.copy(color = Ink.text, fontWeight = FontWeight.SemiBold), dots, modifier)
        StageStatus.DONE -> Text("✓ ${stage.label}", style = AppType.small, modifier = modifier)
        StageStatus.LATER -> Text(stage.label, style = AppType.small.copy(color = Ink.textFaint), modifier = modifier)
        StageStatus.SKIPPED -> Text(stage.label, style = AppType.small.copy(color = Ink.textFaint, textDecoration = TextDecoration.LineThrough), modifier = modifier)
    }
}

/** One dot more every step, 0 → 1 → 2 → 3, then again. */
private const val DOT_STEP_MS = 480

/** Three dots with no-break spaces between them, so they never wrap apart. */
private const val DOTS = ".\u00A0.\u00A0."

/**
 * How many dots show now, from one infinite transition that recomposes only when the count changes; null when the user
 * turned screen motion off (설정 › 화면 › 화면 전환 끔), for a still "…". It starts at three dots, the frame a test clock
 * that runs no infinite animation keeps, so screenshots are stable.
 */
@Composable
private fun rememberMovingDots(): State<Int>? {
    if (Display.current.motion == Motion.OFF) return null
    val phase = rememberInfiniteTransition(label = "asking").animateFloat(
        initialValue = 3f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(DOT_STEP_MS * 4, easing = LinearEasing), RepeatMode.Restart),
        label = "dots",
    )
    return remember(phase) { derivedStateOf { phase.value.toInt() % 4 } }
}

@Composable
private fun DotsText(text: String, style: TextStyle, dots: State<Int>?, modifier: Modifier = Modifier) {
    val shown = dots?.value
    Text(remember(text, shown) { withDots(text, shown) }, style = style, modifier = modifier)
}

/**
 * [text] and three dots of which [shown] are visible; the others are drawn transparent, so the text keeps the width of
 * all three and nothing beside or below it moves. Null: the still "…".
 */
internal fun withDots(text: String, shown: Int?): AnnotatedString = buildAnnotatedString {
    append(text)
    if (shown == null) {
        append("…")
        return@buildAnnotatedString
    }
    val visible = if (shown <= 0) 0 else 2 * shown.coerceAtMost(3) - 1
    append(DOTS.substring(0, visible))
    withStyle(SpanStyle(color = Color.Transparent)) { append(DOTS.substring(visible)) }
}

@Composable
private fun NeedsKey(s: NoteHelperUi.NeedsKey, openSettings: () -> Unit) {
    EmptyNote("“${s.provider.label}”로 물으려면 ${s.missing.joinToString(" · ") { it.label }}가 필요해요. 설정에서 방식을 고르고 자기 키를 넣어 주세요.")
    HintText("앱에는 키가 들어 있지 않아요. 설정의 \"키 받는 방법\"에 받는 순서가 있어요.")
    TextLink(AiErrors.LINK_KEYS, Ink.text, openSettings)
}

@Composable
private fun Failure(s: NoteHelperUi.Failed, openSettings: () -> Unit, onAskAgain: () -> Unit) {
    val e = s.error
    Column(Modifier.fillMaxWidth().testTag("note-helper-error")) {
        Text("${s.provider.label} · ${s.model}", style = AppType.monoSmall)
        Text(e.message, style = AppType.body.copy(color = Ink.bad), modifier = Modifier.padding(top = 6.dp))
        e.hint?.let { Text(it, style = AppType.small, modifier = Modifier.padding(top = 4.dp)) }
        e.detail?.let { Text(it.take(300), style = AppType.faint, modifier = Modifier.padding(top = 4.dp)) }
        e.settingsLink?.let { TextLink(it, Ink.text, openSettings) }
        GhostButton(AiTexts.ASK_AGAIN, small = true, onClick = onAskAgain, modifier = Modifier.padding(top = 12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun Answer(
    s: NoteHelperUi.Answered,
    args: NoteHelperArgs,
    picked: List<String>,
    onToggle: (String) -> Unit,
    onAdd: () -> Unit,
    onAskAgain: () -> Unit,
    onAskWith: (String) -> Unit,
) {
    val answer = s.answer
    val scope = rememberCoroutineScope()
    val requesters = remember(answer) { answer.sources.associate { it.number to BringIntoViewRequester() } }
    var highlighted by remember(answer) { mutableStateOf<Int?>(null) }
    val showSource = { n: Int ->
        highlighted = n
        scope.launch { requesters[n]?.bringIntoView() }
        Unit
    }

    Text("${answer.provider.label} · ${answer.model}", style = AppType.monoSmall)
    HintText(AiTexts.DISCLAIMER)
    answer.notes.forEach { HintText(it) }
    Spacer(Modifier.height(8.dp))
    answer.paragraphs.forEach { p ->
        Text(paragraphText(p, showSource), style = AppType.body, modifier = Modifier.padding(vertical = 3.dp).testTag("answer-paragraph"))
        if (p.quotes.isNotEmpty()) QuoteBadges(p.quotes)
    }
    SearchQueryLine(answer, onAskWith)

    answer.searchSuggestionsHtml?.let { html ->
        SectionLabel(AiTexts.SUGGESTIONS)
        SearchSuggestions(html, Modifier.testTag("search-suggestions"))
    }

    if (args.mode == NoteMode.DESCRIBE) {
        SectionLabel(AiTexts.CANDIDATES)
        if (s.candidates.isEmpty()) {
            HintText(AiTexts.NO_CANDIDATES)
        } else {
            if (args.returnToForm) Text(AiTexts.CANDIDATES_HINT, style = AppType.small)
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 6.dp).testTag("note-candidates"),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                s.candidates.forEach { term ->
                    if (args.returnToForm) Chip(text = term, selected = term in picked, onClick = { onToggle(term) })
                    else Chip(text = term)
                }
            }
            if (args.returnToForm) {
                PrimaryButton(
                    if (picked.isEmpty()) AiTexts.ADD_TO_NOTES else "${AiTexts.ADD_TO_NOTES} (${picked.size})",
                    onClick = onAdd, enabled = picked.isNotEmpty(), small = true, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }

    SectionLabel(AiTexts.SOURCES, hint = "${answer.sources.size}개")
    Hairline()
    answer.sources.forEach { src ->
        SourceRow(src, highlighted = highlighted == src.number, modifier = Modifier.bringIntoViewRequester(requesters.getValue(src.number)))
    }
    GhostButton(AiTexts.ASK_AGAIN, small = true, onClick = onAskAgain, modifier = Modifier.padding(top = 14.dp))
}

/**
 * One line of the answer: sentences a source backs in ink, the others grey (never removed), "**bold**" shown bold, and
 * the [n] marks as small links to the source list.
 */
private fun paragraphText(p: AnswerParagraph, onMark: (Int) -> Unit): AnnotatedString = buildAnnotatedString {
    var bold = false
    p.runs.forEach { run ->
        val color = when {
            run.gap -> Ink.textMuted
            run.cited -> Ink.text
            else -> Ink.textFaint
        }
        run.text.split("**").forEachIndexed { i, piece ->
            if (i > 0) bold = !bold
            if (piece.isNotEmpty()) withStyle(SpanStyle(color = color, fontWeight = if (bold) FontWeight.SemiBold else null)) { append(piece) }
        }
        run.markers.forEach { n ->
            val style = SpanStyle(color = Ink.cafe, fontFamily = AppType.mono, fontSize = 10.5.sp, baselineShift = BaselineShift(0.3f))
            withLink(LinkAnnotation.Clickable("source-$n", TextLinkStyles(style)) { onMark(n) }) { append("[$n]") }
        }
    }
}

/**
 * "검색어: q1 · q2" under the answer. With Gemini 무료 + Tavily the app chose them, so they can be corrected in a panel
 * (not a dialog: a text field in a dialog kept the Robolectric flow tests from going idle) and asked again with.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchQueryLine(answer: GroundedAnswer, onAskWith: (String) -> Unit) {
    if (answer.queries.isEmpty()) return
    val editable = answer.provider == AiProvider.GEMINI_TAVILY
    var editing by remember(answer) { mutableStateOf(false) }
    var typed by remember(answer) { mutableStateOf(answer.queries.first()) }
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${AiTexts.QUERIES}: ${answer.queries.joinToString(" · ")}",
            style = AppType.monoSmall.copy(color = Ink.textFaint),
            modifier = Modifier.testTag("answer-queries"),
        )
        if (editable && !editing) TextLink(AiTexts.EDIT_QUERIES, Ink.text, { editing = true })
    }
    if (editable && editing) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 6.dp)
                .background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).padding(12.dp)
                .testTag("edit-queries"),
        ) {
            AppTextField(value = typed, onValueChange = { typed = it }, placeholder = AiTexts.QUERIES, singleLine = false)
            HintText(AiTexts.QUERIES_HINT)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(AiTexts.ASK_WITH_QUERIES, small = true, enabled = SearchQueries.fromTyped(typed).isNotEmpty(), onClick = { onAskWith(typed) })
                GhostButton(AiTexts.CLOSE, small = true, onClick = { editing = false })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuoteBadges(quotes: List<QuoteCheck>) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 4.dp)) {
        quotes.forEach { q ->
            FlowRow(Modifier.padding(vertical = 2.dp).testTag("quote-check"), horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text("“${q.quote}”", style = AppType.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (q.status == QuoteStatus.FOUND) Badge(AiTexts.FOUND, color = Ink.good)
                else Badge(AiTexts.NOT_FOUND, color = Ink.bad)
                q.source?.let { Text("[$it]", style = AppType.monoSmall) }
            }
        }
    }
}

/** A numbered source: title, domain and its kind; the whole row opens the page. */
@Composable
private fun SourceRow(src: AnswerSource, highlighted: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (highlighted) Ink.accentSoft else Color.Transparent)
                .heightIn(min = MinTouchTarget)
                .clickable(role = Role.Button, onClickLabel = "출처 열기") { openUrl(src.url) }
                .padding(vertical = 8.dp)
                // the source an [n] mark just pointed at
                .semantics { selected = highlighted }
                .testTag("source-${src.number}"),
            verticalAlignment = Alignment.Top,
        ) {
            Text("[${src.number}]", style = AppType.monoValue, modifier = Modifier.width(34.dp))
            Column(Modifier.weight(1f)) {
                Text(src.title, style = AppType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(src.domain, style = AppType.monoSmall, modifier = Modifier.weight(1f, fill = false))
                    when (src.kind) {
                        SourceKind.INSTITUTION -> { Spacer(Modifier.width(6.dp)); Badge(AiTexts.INSTITUTION, color = Ink.good) }
                        SourceKind.PERSONAL -> { Spacer(Modifier.width(6.dp)); Badge(AiTexts.PERSONAL) }
                        SourceKind.OTHER -> Unit
                    }
                }
            }
            Text("↗", style = AppType.small, modifier = Modifier.padding(start = 6.dp))
        }
        Hairline()
    }
}

/** Before the first question with an option: what is sent, where to, and on what terms. */
@Composable
private fun ConsentDialog(p: AiProvider, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        shape = RectangleShape, containerColor = Ink.bg,
        title = { Text(AiTexts.CONSENT_TITLE, style = AppType.title) },
        text = {
            Column(Modifier.testTag("ai-consent")) {
                AiTexts.consent(p).forEachIndexed { i, line ->
                    Text(line, style = if (i == 1) AppType.body else AppType.small, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
        },
        confirmButton = { PrimaryButton(AiTexts.CONSENT_SEND, small = true, onClick = { onAnswer(true) }) },
        dismissButton = { GhostButton("취소", small = true, onClick = { onAnswer(false) }) },
    )
}
