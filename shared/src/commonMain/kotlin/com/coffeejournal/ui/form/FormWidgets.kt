package com.coffeejournal.ui.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.rules.Altitude
import com.coffeejournal.domain.rules.EnglishCase
import com.coffeejournal.ui.platform.openUrl
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GlyphButton
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.ImeSafeText
import com.coffeejournal.ui.theme.Ink
import com.coffeejournal.ui.theme.fontScaled
import com.coffeejournal.ui.theme.rememberImeSafeText
import com.coffeejournal.ui.theme.shownWhen
import kotlinx.coroutines.delay

/**
 * Square outlined field like [com.coffeejournal.ui.theme.AppTextField], plus focus tracking, a focus requester,
 * an error line and a hint. (Candidate for promotion into the theme.) Typing is IME-safe like AppTextField: the field
 * keeps its own text, cursor and composition while the owner's echo catches up, and [inputFilter] accepts, rewrites or
 * rejects each edit before it is shown (see [ImeSafeText.onEdit]).
 */
@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    /** A placeholder that stands for a value the field already has (the brew timer's estimated grams, a blend bean's inherited roastery) is darker. */
    placeholderColor: Color = Ink.textFaint,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    error: String? = null,
    hint: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    inputFilter: ((String) -> String?)? = null,
    /** English words start with a capital: the keyboard shifts at each word, and leaving the field fixes the rest. */
    capitalizeWords: Boolean = keyboardType == KeyboardType.Text && singleLine,
) {
    val sync = rememberImeSafeText(value)
    // onFocusChanged fires once on attach with "not focused"; only report a blur after a real focus.
    var hadFocus by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) FieldLabel(label)
        var fieldModifier: Modifier = Modifier.fillMaxWidth()
        if (focusRequester != null) fieldModifier = fieldModifier.focusRequester(focusRequester)
        fieldModifier = fieldModifier.onFocusChanged { st ->
            focused = st.isFocused
            if (st.isFocused) wasFocused = true
            else if (wasFocused) {
                wasFocused = false
                if (capitalizeWords) EnglishCase.words(value).takeIf { it != value }?.let(onValueChange)
            }
            if (onFocusChanged == null) return@onFocusChanged
            if (st.isFocused) { hadFocus = true; onFocusChanged(true) } else if (hadFocus) { hadFocus = false; onFocusChanged(false) }
        }
        OutlinedTextField(
            value = sync.value.shownWhen(focused),
            onValueChange = { edited -> sync.onEdit(edited, inputFilter)?.let(onValueChange) },
            modifier = fieldModifier,
            // a wrapping placeholder would make a one-line field taller than its neighbours in a two-column row
            placeholder = {
                Text(
                    placeholder, style = AppType.input.copy(color = placeholderColor),
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                )
            },
            singleLine = singleLine,
            minLines = minLines,
            isError = error != null,
            textStyle = AppType.input,
            shape = RectangleShape,
            keyboardOptions = KeyboardOptions(
                capitalization = if (capitalizeWords) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
            ),
            trailingIcon = trailing,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink.accent, unfocusedBorderColor = Ink.line, errorBorderColor = Ink.bad,
                focusedContainerColor = Ink.surface, unfocusedContainerColor = Ink.surface, errorContainerColor = Ink.surface,
                cursorColor = Ink.accent, focusedTextColor = Ink.text, unfocusedTextColor = Ink.text, errorTextColor = Ink.text,
            ),
        )
        if (error != null) ErrorText(error)
        if (hint != null) HintText(hint)
    }
}

@Composable
internal fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppType.faint.copy(color = Ink.bad), modifier = modifier.padding(top = 4.dp))
}

/** Free text with a suggestion list under the field while it has focus (web `<input list=datalist>`). */
@Composable
internal fun AutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    /** See [FormTextField]: darker for a placeholder that stands for a value (a blend's bean 1 roastery). */
    placeholderColor: Color = Ink.textFaint,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    error: String? = null,
    hint: String? = null,
    maxSuggestions: Int = 6,
    inputFilter: ((String) -> String?)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(focused) {
        if (focused) showList = true else { delay(150); showList = false }
    }
    val query = value.trim().lowercase()
    val matches = remember(options, query) {
        val filtered = if (query.isEmpty()) options else options.filter { it.lowercase().contains(query) && it.trim().lowercase() != query }
        filtered.distinct().take(maxSuggestions)
    }
    Column(modifier) {
        FormTextField(
            value = value, onValueChange = onValueChange, label = label, placeholder = placeholder, placeholderColor = placeholderColor, keyboardType = keyboardType,
            focusRequester = focusRequester, error = error, hint = hint, inputFilter = inputFilter,
            onFocusChanged = { f -> focused = f; onFocusChanged?.invoke(f) },
        )
        if (showList && matches.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)) {
                matches.forEachIndexed { i, opt ->
                    Text(
                        opt, style = AppType.small.copy(color = Ink.text),
                        modifier = Modifier.fillMaxWidth().clickable { onValueChange(opt); focusManager.clearFocus() }.padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                    if (i < matches.lastIndex) Box(Modifier.fillMaxWidth().height(Dimens.hairline).background(Ink.line))
                }
            }
        }
    }
}

/**
 * Free text with a list of [presets] to pick from (국가 · 지역 · 세부 지역): the list opens on focus or with ▾, narrows
 * as one types (Korean, English or another spelling), and shows each choice's English name faintly. Picking puts its
 * value in the field; anything typed is kept as it is.
 */
@Composable
internal fun PresetField(
    value: String,
    onValueChange: (String) -> Unit,
    presets: List<Preset>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    hint: String? = null,
    /** The list's test tag ("presets-지역"): the rows under it are the choices. */
    listTag: String = "presets-${label ?: placeholder}",
) {
    var focused by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(focused) {
        if (focused) showList = true else { delay(150); showList = false }
    }
    // a value that is one of the choices shows them all, so another can be picked without clearing the field
    val matches = remember(presets, value) {
        if (presets.any { it.names(value) }) presets else presets.filter { it.matches(value.trim()) }
    }
    Column(modifier) {
        FormTextField(
            value = value, onValueChange = onValueChange, label = label, placeholder = placeholder, hint = hint,
            onFocusChanged = { f -> focused = f },
            trailing = if (presets.isEmpty()) null else {
                {
                    Box(
                        Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button) { showList = !showList }
                            .semantics { contentDescription = "${label ?: placeholder} 목록 ${if (showList) "닫기" else "열기"}" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.chevronDown, contentDescription = null, tint = Ink.textMuted, modifier = Modifier.size(18.dp)) }
                }
            },
        )
        if (showList && matches.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 264.dp).background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
                    .verticalScroll(rememberScrollState()).testTag(listTag),
            ) {
                matches.forEachIndexed { i, p ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onValueChange(p.value); showList = false; focusManager.clearFocus() }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(p.value, style = AppType.small.copy(color = Ink.text), modifier = Modifier.weight(1f, fill = false))
                        if (p.note.isNotBlank() && p.note != p.value) {
                            Spacer(Modifier.width(8.dp))
                            Text(p.note, style = AppType.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (i < matches.lastIndex) Box(Modifier.fillMaxWidth().height(Dimens.hairline).background(Ink.line))
                }
            }
        }
    }
}

/** 재배 고도: the number (or a range, "1800-2000") on the number keyboard, with the unit "m" shown after it and saved with it. */
@Composable
internal fun AltitudeField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, placeholder: String = "") {
    FormTextField(
        value, onValueChange, modifier, label = label, placeholder = placeholder, keyboardType = KeyboardType.Number,
        inputFilter = Altitude::typing,
        trailing = { Text("m", style = AppType.input.copy(color = Ink.textMuted), modifier = Modifier.padding(end = 4.dp)) },
    )
}

/** Small bordered input for dense rows (steps, blend components); IME-safe with an optional [inputFilter] like [FormTextField]. */
@Composable
internal fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
    textAlign: TextAlign = TextAlign.Start,
    inputFilter: ((String) -> String?)? = null,
) {
    val sync = rememberImeSafeText(value)
    var focused by remember { mutableStateOf(false) }
    // the box stays 36 dp tall; the surrounding layout reserves a 48 dp touch target
    var m = modifier.minimumInteractiveComponentSize().heightIn(min = 36.dp).background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
    if (focusRequester != null) m = m.focusRequester(focusRequester)
    m = m.onFocusChanged {
        // leaving the field starts each English word with a capital, as FormTextField does
        if (focused && !it.isFocused && keyboardType == KeyboardType.Text) EnglishCase.words(value).takeIf { t -> t != value }?.let(onValueChange)
        focused = it.isFocused
    }
    BasicTextField(
        value = sync.value.shownWhen(focused),
        onValueChange = { edited -> sync.onEdit(edited, inputFilter)?.let(onValueChange) },
        modifier = m,
        singleLine = true,
        textStyle = AppType.inputSmall.copy(color = Ink.text, textAlign = textAlign),
        cursorBrush = SolidColor(Ink.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            keyboardType = keyboardType, imeAction = ImeAction.Next,
        ),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart) {
                if (sync.value.text.isEmpty()) Text(placeholder, style = AppType.inputSmall.copy(color = Ink.textFaint), maxLines = 1)
                inner()
            }
        },
    )
}

/** Two fields side by side (web .grid). */
@Composable
internal fun TwoUp(left: @Composable (Modifier) -> Unit, right: (@Composable (Modifier) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        left(Modifier.weight(1f))
        if (right != null) right(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun FieldBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(bottom = 10.dp), content = content)
}

/** Label · slider · readout (web .attr-row). A null [value] shows the slider at [min] with a dash readout. */
@Composable
internal fun SliderRow(
    label: String,
    value: Double?,
    min: Double,
    max: Double,
    step: Double,
    readout: String,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    /** What TalkBack calls the slider (default [label]), e.g. "프레그런스 강도" where two rows share a label. */
    description: String? = null,
) {
    val stepsCount = (((max - min) / step) - 1).toInt().coerceAtLeast(0)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // the label and readout columns grow with the font, so "Sweetness" and "10.00" stay whole
        Text(label, style = if (secondary) AppType.faint else AppType.small, modifier = Modifier.width(112.dp.fontScaled()))
        Slider(
            value = (value ?: min).toFloat(),
            onValueChange = { v -> onChange(snap(v.toDouble(), min, step)) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = stepsCount,
            modifier = Modifier.weight(1f).semantics { contentDescription = description ?: label },
            colors = SliderDefaults.colors(
                thumbColor = Ink.accent, activeTrackColor = Ink.accent, inactiveTrackColor = Ink.line,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
        Text(readout, style = AppType.monoValue, softWrap = false, modifier = Modifier.widthIn(min = 44.dp), textAlign = TextAlign.End)
    }
}

private fun snap(v: Double, min: Double, step: Double): Double {
    val n = kotlin.math.round((v - min) / step)
    val snapped = min + n * step
    return kotlin.math.round(snapped * 100) / 100.0
}

/** Header row that toggles its body (web `<details>`). */
@Composable
internal fun Collapsible(title: String, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, body: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = if (open) "접기" else "펼치기", role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (open) "펼쳐짐" else "접힘" }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = AppType.body, modifier = Modifier.weight(1f))
            Icon(if (open) AppIcons.chevronDown else AppIcons.chevronRight, contentDescription = null, tint = Ink.textMuted, modifier = Modifier.size(16.dp))
        }
        if (open) Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), content = body)
    }
}

/** Card of the recipe launcher panels (web .champ-card). */
@Composable
internal fun LauncherCard(
    title: String,
    right: String,
    spec: String,
    desc: String?,
    applyLabel: String,
    onApply: () -> Unit,
    highlight: Boolean = false,
    onDelete: (() -> Unit)? = null,
    sourceUrl: String? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .background(if (highlight) Ink.surfaceRaised else Ink.surface)
            .border(BorderStroke(Dimens.hairline, if (highlight) Ink.accent else Ink.line), RectangleShape)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(title, style = AppType.cardTitle, modifier = Modifier.weight(1f))
            if (right.isNotBlank()) Text(right, style = AppType.monoSmall)
        }
        Text(spec, style = AppType.monoValue, modifier = Modifier.padding(top = 4.dp))
        if (!desc.isNullOrBlank()) Text(desc, style = AppType.bodyMuted, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextLink(applyLabel, Ink.text, onApply)
            if (onDelete != null) {
                // kept well apart so the delete dialog is not opened by a slightly missed "apply" tap
                Spacer(Modifier.width(20.dp))
                TextLink("삭제", Ink.bad, onDelete)
            }
            if (sourceUrl != null) {
                Spacer(Modifier.width(20.dp))
                TextLink("출처: ${sourceHost(sourceUrl)} ↗", Ink.textMuted, { openUrl(sourceUrl) })
            }
        }
    }
}

/** "kurasu.kyoto" for a recipe's source link. */
internal fun sourceHost(url: String): String = url.substringAfter("://").substringBefore('/').removePrefix("www.")

/** Minimum touch target of the small text / glyph controls below (design §8: 48 dp). */
internal val MinTouch = 48.dp

/** Plain text action (web text link): the text looks the same, the tappable area around it is at least 48 dp. */
@Composable
internal fun TextLink(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.sizeIn(minWidth = MinTouch, minHeight = MinTouch).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = AppType.small.copy(color = color))
    }
}

/**
 * Small "✕" remove control used by rows and photos: the glyph sits in the middle of a 48 dp box as before, the tap
 * target is 48 dp and TalkBack reads [label] ("사진 삭제", "단계 삭제") as a button.
 */
@Composable
internal fun RemoveButton(onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    GlyphButton(
        "✕", label = label, onClick = onClick,
        modifier = modifier.size(MinTouch).wrapContentSize(Alignment.Center),
        style = AppType.small.copy(color = Ink.textFaint),
    )
}
