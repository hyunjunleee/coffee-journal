package com.coffeejournal.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt

/** Small mono uppercase-ish label above a group of fields (web .section-label). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SectionLabel(text: String, modifier: Modifier = Modifier, hint: String? = null) {
    // the hint moves under the label when both do not fit on one line (narrow screens, large fonts)
    FlowRow(modifier.padding(top = 18.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.Bottom) {
        Text(text, style = AppType.sectionLabel)
        if (hint != null) Text(hint, style = AppType.faint)
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppType.fieldLabel, modifier = modifier.padding(bottom = 4.dp))
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Ink.line, thickness: androidx.compose.ui.unit.Dp = Dimens.hairline) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

/** Square ink button (web button.primary). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, small: Boolean = false) {
    val bg = if (enabled) Ink.accent else Ink.line
    Box(
        modifier
            .heightIn(min = if (small) 34.dp else Dimens.touch)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 18.dp, vertical = if (small) 6.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.body.copy(color = Ink.bg, fontSize = if (small) 12.5.sp else 14.sp), textAlign = TextAlign.Center)
    }
}

/** Hairline-bordered button (web button.ghost). */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, small: Boolean = false, icon: ImageVector? = null, danger: Boolean = false) {
    val fg = if (!enabled) Ink.textFaint else if (danger) Ink.bad else Ink.text
    Row(
        modifier
            .heightIn(min = if (small) 32.dp else Dimens.touch)
            .border(BorderStroke(Dimens.hairline, if (danger) Ink.bad else Ink.line), RectangleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 5.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = AppType.body.copy(color = fg, fontSize = if (small) 12.5.sp else 14.sp))
    }
}

/**
 * Chip row used for sub tabs (web .cal-subtabs). When [scrollable], the row scrolls so the selected chip is on
 * screen: at once on first composition (a default chip may sit past the edge), animated after a tap or when the
 * selection changes from outside. [special] chips keep an accent outline (web .cal-subtab-special).
 */
@Composable
fun SubTabs(
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    dots: Map<String, Color> = emptyMap(),
    labels: Map<String, String> = emptyMap(),
    scrollable: Boolean = true,
    special: Set<String> = emptySet(),
) {
    val scroll = rememberScrollState()
    val chips = remember { mutableStateMapOf<String, IntRange>() }
    var viewport by remember { mutableIntStateOf(0) }
    val peek = with(LocalDensity.current) { 28.dp.roundToPx() }
    val shown = remember { SubTabFirstScroll() }
    val bounds = chips[selected]
    if (scrollable) LaunchedEffect(selected, bounds, viewport) {
        if (bounds == null || viewport == 0) return@LaunchedEffect
        val target = SubTabScroll.target(bounds.first, bounds.last, viewport, scroll.value, scroll.maxValue, peek)
        if (target != scroll.value) { if (shown.done) scroll.animateScrollTo(target) else scroll.scrollTo(target) }
        shown.done = true
    }
    val rowModifier = if (scrollable) modifier.onSizeChanged { viewport = it.width }.horizontalScroll(scroll) else modifier
    // TalkBack reads each item as a tab with its selected state (design §8)
    Row(rowModifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            val on = item == selected
            val accent = item in special
            val border = when {
                on -> BorderStroke(Dimens.hairline, Ink.accent)
                accent -> BorderStroke(1.5.dp, Ink.accent)
                else -> BorderStroke(Dimens.hairline, Ink.line)
            }
            Row(
                Modifier
                    .onPlaced { c -> val x = c.positionInParent().x.roundToInt(); chips[item] = x..(x + c.size.width) }
                    .border(border, RectangleShape)
                    .background(if (on) Ink.accent else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(item) })
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dots[item]?.let { c -> Box(Modifier.size(7.dp).background(c)); Spacer(Modifier.width(6.dp)) }
                Text(
                    labels[item] ?: item,
                    style = AppType.small.copy(
                        color = when { on -> Ink.bg; accent -> Ink.accent; else -> Ink.textMuted },
                        fontWeight = if (accent) FontWeight.SemiBold else AppType.small.fontWeight,
                    ),
                )
            }
        }
    }
}

private class SubTabFirstScroll { var done = false }

/** Where a sub-tab row scrolls to so the selected chip is fully visible. */
object SubTabScroll {
    /**
     * The scroll value that shows the chip spanning [start]..[end] (row content px) in a [viewport] px wide window,
     * leaving [peek] px of the neighbouring chip visible so the row still reads as scrollable. A chip already fully
     * visible keeps the current scroll.
     */
    fun target(start: Int, end: Int, viewport: Int, current: Int, max: Int, peek: Int): Int {
        val target = when {
            start < current -> start - peek
            end > current + viewport -> end - viewport + peek
            else -> current
        }
        return target.coerceIn(0, maxOf(0, max))
    }
}

/**
 * Square segmented control; tapping the active option again clears it when [allowClear]. Laid out like the web's
 * `.seg` (options `flex: 1` with `white-space: nowrap`, the row `flex-wrap: wrap`): the options share the width equally
 * while every label fits on one line, a longer label keeps its own width, and when they cannot all fit (a narrow
 * screen, a large font) the options continue on another row instead of breaking a word such as "미디엄 라이트".
 */
@Composable
fun Seg(
    options: List<String>,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = true,
    labels: Map<String, String> = emptyMap(),
) {
    // one choice out of several: radio buttons with a selected state for TalkBack (design §8)
    Layout(
        modifier = modifier.fillMaxWidth().border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape).selectableGroup(),
        content = {
            options.forEach { opt ->
                val on = opt == value
                Box(
                    Modifier
                        .heightIn(min = 40.dp)
                        .background(if (on) Ink.accent else Ink.surface)
                        // separators: every option draws its left and top edge; on the outer edges they fall on the border
                        .drawBehind {
                            val t = Dimens.hairline.toPx()
                            drawRect(Ink.line, size = androidx.compose.ui.geometry.Size(t, size.height))
                            drawRect(Ink.line, size = androidx.compose.ui.geometry.Size(size.width, t))
                        }
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onChange(if (on && allowClear) "" else opt) })
                        .padding(horizontal = 6.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(labels[opt] ?: opt, style = AppType.small.copy(color = if (on) Ink.bg else Ink.text), textAlign = TextAlign.Center)
                }
            }
        },
    ) { measurables, constraints ->
        val oneLine = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val total = if (constraints.hasBoundedWidth) constraints.maxWidth else oneLine.sum()
        val rows = SegLayout.rows(oneLine, total)
        val widths = IntArray(measurables.size)
        rows.forEach { row -> SegLayout.widths(row.map { oneLine[it] }, total).forEachIndexed { k, w -> widths[row[k]] = w } }
        val heights = rows.map { row -> row.maxOf { i -> measurables[i].minIntrinsicHeight(widths[i]) } }
        val rowOf = IntArray(measurables.size).also { r -> rows.forEachIndexed { ri, row -> row.forEach { r[it] = ri } } }
        val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i], heights[rowOf[i]])) }
        layout(total, heights.sum()) {
            var y = 0
            rows.forEachIndexed { ri, row ->
                var x = 0
                row.forEach { i -> placeables[i].place(x, y); x += widths[i] }
                y += heights[ri]
            }
        }
    }
}

/** The web `.seg` flex layout in pixels, for [Seg]. */
object SegLayout {
    /** Options in order, a new row whenever the next one-line width no longer fits in [total]. */
    fun rows(oneLine: List<Int>, total: Int): List<List<Int>> {
        val rows = mutableListOf<MutableList<Int>>()
        var used = 0
        oneLine.forEachIndexed { i, w ->
            if (rows.isEmpty() || (used + w > total && rows.last().isNotEmpty())) { rows += mutableListOf(i); used = w } else { rows.last() += i; used += w }
        }
        return rows
    }

    /**
     * Widths of one row filling [total] like `flex: 1` with a one-line minimum: equal shares, except that an option
     * wider than its share keeps its own width and the others share the rest. Always sums to [total].
     */
    fun widths(oneLine: List<Int>, total: Int): List<Int> {
        val n = oneLine.size
        if (n == 0) return emptyList()
        val frozen = BooleanArray(n)
        var remaining = total
        var open = n
        while (open > 0) {
            val share = remaining / open
            val wide = (0 until n).filter { !frozen[it] && oneLine[it] > share }
            if (wide.isEmpty()) break
            wide.forEach { frozen[it] = true; remaining -= oneLine[it]; open-- }
        }
        val result = IntArray(n) { if (frozen[it]) oneLine[it] else 0 }
        if (open > 0) {
            val share = remaining / open
            var extra = remaining - share * open
            for (i in 0 until n) if (!frozen[i]) { result[i] = share + if (extra > 0) 1 else 0; if (extra > 0) extra-- }
        } else {
            // every option is wider than an equal share: the row is exactly full or one option is wider than the row
            val sum = result.sum()
            if (sum > total) { val over = sum - total; result[result.indices.maxBy { result[it] }] -= over } else result[n - 1] += total - sum
        }
        return result.toList()
    }
}

/** White card with a hairline border and the web's offset "shadow" rule. */
@Composable
fun HairlineCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(Dimens.cardPadding), onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier
        .fillMaxWidth()
        .background(Ink.surface)
        .border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
    Column((if (onClick != null) base.clickable(onClick = onClick) else base).padding(padding), content = content)
}

/** Dashed empty-state box (web .empty). */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
                drawRect(color = Ink.line, style = stroke)
            }
            .padding(vertical = 34.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.bodyMuted, textAlign = TextAlign.Center)
    }
}

/** Faint helper text under a field (web .field-hint / .steps-hint). */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppType.faint, modifier = modifier.padding(top = 4.dp))
}

@Composable
fun CatDot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(modifier.size(size).background(color))
}

/** Small mono badge (web .cat-badge / .dripbag-badge). */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Ink.textMuted, filled: Boolean = false) {
    Text(
        text,
        style = AppType.monoSmall.copy(color = if (filled) Ink.bg else color),
        modifier = modifier
            .background(if (filled) color else Color.Transparent)
            .border(BorderStroke(Dimens.hairline, color), RectangleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(key, style = AppType.monoSmall, modifier = Modifier.width(84.dp))
        Text(value, style = AppType.small.copy(color = Ink.text), modifier = Modifier.weight(1f))
    }
}

/**
 * The text a field shows, kept by the field itself (cursor, selection and IME composition included) while its owner —
 * usually a ViewModel's StateFlow — receives every edit and echoes it back a little later. The owner's value only
 * replaces the field's text when it changes to something the field did not send: an async echo of an earlier
 * keystroke never moves the cursor back or breaks a Hangul syllable that is still being composed, while a real
 * change from outside (a restored draft, a reformatted price, a cleared input) still shows up.
 */
@Stable
class ImeSafeText(initial: String) {
    var value: TextFieldValue by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
        private set
    private var lastExternal: String = initial
    /** Texts reported to the owner whose echo has not come back yet (oldest first). */
    private val sent = ArrayDeque<String>()

    /** Call with the owner's current text on every composition, before reading [value]. */
    fun syncExternal(external: String) {
        if (external == lastExternal) return
        lastExternal = external
        if (external == value.text) { sent.clear(); return }
        val echo = sent.indexOf(external)
        if (echo >= 0) { repeat(echo + 1) { sent.removeFirst() }; return }
        sent.clear()
        value = TextFieldValue(external, TextRange(external.length))
    }

    /**
     * An edit from the text field. Returns the new text to report to the owner, or null when only the cursor moved
     * or [filter] rejected the edit. [filter] returns the text to accept (possibly rewritten) or null to reject;
     * it runs here, not in the owner, because the owner's reaction arrives asynchronously and cannot undo what the
     * field already shows.
     */
    fun onEdit(edited: TextFieldValue, filter: ((String) -> String?)? = null): String? {
        var accepted = edited
        if (filter != null && edited.text != value.text) {
            val text = filter(edited.text) ?: return null
            if (text != edited.text) {
                val cursor = (edited.selection.end + text.length - edited.text.length).coerceIn(0, text.length)
                accepted = TextFieldValue(text, TextRange(cursor))
            }
        }
        val changed = accepted.text != value.text
        value = accepted
        if (!changed) return null
        sent.addLast(accepted.text)
        while (sent.size > MAX_PENDING) sent.removeFirst()
        return accepted.text
    }

    /** Ends the IME composition, keeping the composed text as plain text. */
    fun commitComposition() {
        if (value.composition != null) value = value.copy(composition = null)
    }

    val isComposing: Boolean get() = value.composition != null

    private companion object { const val MAX_PENDING = 64 }
}

/**
 * What a field shows: its own value while it has focus; otherwise the same text with the cursor at the start. A
 * one-line field scrolls to its cursor even without focus, so a text too long for the box would show only its end
 * ("…, Worka Chelbesa") after typing or when a value arrives from outside.
 */
fun TextFieldValue.shownWhen(focused: Boolean): TextFieldValue =
    if (focused || (selection == TextRange.Zero && composition == null)) this else TextFieldValue(annotatedString, TextRange.Zero)

/** An [ImeSafeText] for a field whose text is owned by [value]. Use it with the TextFieldValue overload of a text field. */
@Composable
fun rememberImeSafeText(value: String): ImeSafeText {
    val sync = remember { ImeSafeText(value) }
    sync.syncExternal(value)
    return sync
}

/** Ready-made `inputFilter`s for [AppTextField] and the form fields. */
object InputFilters {
    /**
     * The web's `type=number` fields: a non-negative decimal as it is being typed ("", "10", "10.", "10.5"), a comma
     * taken as the point. Anything else — letters, a second point, a minus, a pasted "NaN" — is rejected (null), so
     * the field keeps its previous text.
     */
    fun decimal(typed: String): String? {
        val t = typed.trim().replace(',', '.')
        return if (DECIMAL_TYPING.matches(t)) t else null
    }

    private val DECIMAL_TYPING = Regex("\\d*\\.?\\d*")
}

/** Square outlined text field with the archive palette. Typing is IME-safe (see [ImeSafeText]). */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    minLines: Int = 1,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    /** Accepts, rewrites or rejects (null) each edit before it is shown; see [ImeSafeText.onEdit]. */
    inputFilter: ((String) -> String?)? = null,
) {
    val sync = rememberImeSafeText(value)
    AppTextFieldValue(
        value = sync.value, onValueChange = { edited -> sync.onEdit(edited, inputFilter)?.let(onValueChange) }, modifier = modifier, label = label,
        placeholder = placeholder, singleLine = singleLine, keyboardType = keyboardType, imeAction = imeAction,
        onImeAction = onImeAction, minLines = minLines, enabled = enabled, trailing = trailing,
    )
}

@Composable
private fun AppTextFieldValue(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier,
    label: String?,
    placeholder: String,
    singleLine: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: (() -> Unit)?,
    minLines: Int,
    enabled: Boolean,
    trailing: (@Composable () -> Unit)?,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) FieldLabel(label)
        OutlinedTextField(
            value = value.shownWhen(focused),
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            // a wrapping placeholder would make a one-line field taller than its neighbours
            placeholder = {
                Text(
                    placeholder, style = AppType.input.copy(color = Ink.textFaint),
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                )
            },
            singleLine = singleLine,
            minLines = minLines,
            enabled = enabled,
            textStyle = AppType.input,
            shape = RectangleShape,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
            trailingIcon = trailing,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink.accent,
                unfocusedBorderColor = Ink.line,
                focusedContainerColor = Ink.surface,
                unfocusedContainerColor = Ink.surface,
                cursorColor = Ink.accent,
                focusedTextColor = Ink.text,
                unfocusedTextColor = Ink.text,
            ),
        )
    }
}

/**
 * Chip list with an input row; Enter or the button adds, × removes (web .expected-notes-chips). The input is split on
 * commas and deduplicated (web addExpectedNoteFromInput). A syllable still being composed by the IME is committed
 * and added first; the input is cleared a frame later, so the keyboard does not put the syllable back into the
 * emptied field (the web skips Enter while `e.isComposing`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipInput(
    chips: List<String>,
    onChipsChange: (List<String>) -> Unit,
    input: String,
    onInputChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "노트 추가 후 Enter (예: 오렌지)",
    addLabel: String = "추가",
) {
    val sync = rememberImeSafeText(input)
    var clearPending by remember { mutableStateOf(false) }
    val clear = { sync.onEdit(TextFieldValue(""))?.let(onInputChange) }
    val add = {
        addChipsFromInput(sync, chips)?.let { added ->
            onChipsChange(added.chips)
            if (added.clearNow) clear() else clearPending = true
        }
    }
    LaunchedEffect(clearPending) {
        if (clearPending) {
            // let the committed text reach the IME before the field is emptied
            withFrameNanos { }
            clear()
            clearPending = false
        }
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextFieldValue(
                value = sync.value, onValueChange = { edited -> sync.onEdit(edited)?.let(onInputChange) }, modifier = Modifier.weight(1f),
                label = null, placeholder = placeholder, singleLine = true, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done,
                onImeAction = { add() }, minLines = 1, enabled = true, trailing = null,
            )
            Spacer(Modifier.width(8.dp))
            GhostButton(addLabel, small = true, onClick = { add() })
        }
        if (chips.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { chip ->
                    Chip(text = chip, onRemove = { onChipsChange(chips - chip) })
                }
            }
        }
    }
}

/** The chips after 추가 / Done, and whether the input can be emptied right away. */
internal class ChipAddition(val chips: List<String>, val clearNow: Boolean)

/**
 * 추가 / Done in [ChipInput]: the input (comma-split, deduplicated) is added to [chips]; null when it is blank. A
 * syllable the IME is still composing is committed first and stays in the field for this frame ([ChipAddition.clearNow]
 * false), so the keyboard is not handed an emptied field while it still holds the syllable.
 */
internal fun addChipsFromInput(input: ImeSafeText, chips: List<String>): ChipAddition? {
    val text = input.value.text
    if (text.isBlank()) return null
    val composing = input.isComposing
    if (composing) input.commitComposition()
    return ChipAddition(com.coffeejournal.domain.rules.NoteCanon.addChips(chips, text), clearNow = !composing)
}

/**
 * A small square chip. With [onClick] it is an on/off choice — a checkbox with its checked state for TalkBack — or,
 * with [toggle] false, a plain button (e.g. a name suggestion).
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    prefix: String = "",
    toggle: Boolean = true,
) {
    val base = modifier
        .border(BorderStroke(Dimens.hairline, if (selected) Ink.accent else Ink.line), RectangleShape)
        .background(if (selected) Ink.accent else Ink.surface)
    val interactive = when {
        onClick == null -> base
        toggle -> base.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
        else -> base.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        interactive.padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prefix + text, style = AppType.small.copy(color = if (selected) Ink.bg else Ink.text))
        if (onRemove != null) {
            Spacer(Modifier.width(6.dp))
            GlyphButton("×", label = "$text 삭제", onClick = onRemove, style = AppType.small.copy(color = if (selected) Ink.bg else Ink.textFaint))
        }
    }
}

/** Design §8: the smallest touch target. */
val MinTouchTarget = 48.dp

/**
 * A glyph-only action such as a chip's ×, a photo's ✕ or a row's remove mark. [modifier] shapes the visible glyph
 * box exactly as a plain `Box`/`Text` would (size, background, alignment, padding), so the look and the layout do not
 * change; the tap area is widened to at least [MinTouchTarget] around the glyph without taking layout space, and
 * TalkBack reads [label] ("오렌지 삭제", "사진 삭제") as a button instead of "multiplication sign".
 */
@Composable
fun GlyphButton(
    glyph: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = AppType.small.copy(color = Ink.textFaint),
    enabled: Boolean = true,
) {
    Layout(
        modifier = modifier,
        content = {
            // sizing probe: the glyph as it used to be laid out; measured only, never placed or announced
            Text(glyph, style = style, modifier = Modifier.clearAndSetSemantics { })
            Box(
                Modifier
                    .clickable(
                        interactionSource = null,
                        indication = ripple(bounded = false, radius = MinTouchTarget / 2),
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { Text(glyph, style = style) }
        },
    ) { measurables, constraints ->
        val glyphBox = measurables[0].measure(constraints)
        val min = MinTouchTarget.roundToPx()
        val w = maxOf(glyphBox.width, min)
        val h = maxOf(glyphBox.height, min)
        val target = measurables[1].measure(Constraints.fixed(w, h))
        layout(glyphBox.width, glyphBox.height) { target.place((glyphBox.width - w) / 2, (glyphBox.height - h) / 2) }
    }
}

/**
 * One line of text that shrinks (down to [minFontSize]) instead of wrapping or being cut when the space is narrow or
 * the font scale large; for short labels whose line break would read badly ("[ 새로운 추 / 출 ]").
 */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, minFontSize: TextUnit = 8.sp, textAlign: TextAlign? = null) {
    BasicText(
        text, modifier = modifier, style = if (textAlign != null) style.copy(textAlign = textAlign) else style,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}

/**
 * [this] width for text in a fixed column, grown with the font scale (at most [cap] times) so a mono value such as
 * "0:00" or "10.00" does not break in two at large font sizes.
 */
@Composable
fun Dp.fontScaled(cap: Float = 1.6f): Dp = this * LocalDensity.current.fontScale.coerceIn(1f, cap)

/** Top header shared by all tabs: title, tagline, a right-hand count and the small gear that opens 설정. */
@Composable
fun TopHeader(title: String, tagline: String, right: String?, modifier: Modifier = Modifier, onSettings: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = Dimens.gutter).padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            // one line each, a little smaller when needed (the web drops to 20px on phones): "coffee_journ / al" was worse
            Column(Modifier.weight(1f)) {
                FitText(title, style = AppType.headerTitle, minFontSize = 8.sp)
                FitText(tagline, style = AppType.tagline, minFontSize = 5.sp)
            }
            if (right != null) Text(right, style = AppType.count, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
            if (onSettings != null) {
                // a small gear, not a tab: 설정 is visited rarely (typefaces, text size, transitions, reminders, sources)
                Box(
                    Modifier.size(Dimens.touch).testTag("open-settings").clickable(role = Role.Button, onClickLabel = "설정 열기", onClick = onSettings)
                        .semantics { contentDescription = "설정" },
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Icon(AppIcons.settings, contentDescription = null, tint = Ink.textMuted, modifier = Modifier.padding(bottom = 1.dp).size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Hairline(color = Ink.text, thickness = Dimens.heavyRule)
    }
}

/**
 * While [busy] (a form's save is running) system back does nothing, so the screen is not left halfway through the
 * save; the screen closes itself when the save finishes. Title-bar back and 취소 should be held back the same way.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BlockBackWhile(busy: Boolean) {
    BackHandler(enabled = busy) { }
}

/** Sticky-looking screen title row used by sub screens (back arrow + title + optional action). */
@Composable
fun ScreenTitleBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().background(Ink.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(Modifier.size(Dimens.touch).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.chevronLeft, contentDescription = "뒤로", tint = Ink.text, modifier = Modifier.size(20.dp))
                }
            } else Spacer(Modifier.width(8.dp))
            Text(title, style = AppType.title, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        Hairline(color = Ink.text, thickness = Dimens.rule)
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Double.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
