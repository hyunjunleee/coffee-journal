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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.FieldLabel
import com.coffeejournal.ui.theme.GlyphButton
import com.coffeejournal.ui.theme.HintText
import com.coffeejournal.ui.theme.Ink
import kotlinx.coroutines.delay

/**
 * Square outlined field like [com.coffeejournal.ui.theme.AppTextField], plus focus tracking, a focus requester,
 * an error line and a hint. (Candidate for promotion into the theme.)
 */
@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    error: String? = null,
    hint: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    // onFocusChanged fires once on attach with "not focused"; only report a blur after a real focus.
    var hadFocus by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) FieldLabel(label)
        var fieldModifier: Modifier = Modifier.fillMaxWidth()
        if (focusRequester != null) fieldModifier = fieldModifier.focusRequester(focusRequester)
        if (onFocusChanged != null) fieldModifier = fieldModifier.onFocusChanged { st ->
            if (st.isFocused) { hadFocus = true; onFocusChanged(true) } else if (hadFocus) { hadFocus = false; onFocusChanged(false) }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier,
            // a wrapping placeholder would make a one-line field taller than its neighbours in a two-column row
            placeholder = {
                Text(
                    placeholder, style = AppType.body.copy(color = Ink.textFaint),
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                )
            },
            singleLine = singleLine,
            minLines = minLines,
            isError = error != null,
            textStyle = AppType.body,
            shape = RectangleShape,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
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
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    error: String? = null,
    hint: String? = null,
    maxSuggestions: Int = 6,
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
            value = value, onValueChange = onValueChange, label = label, placeholder = placeholder, keyboardType = keyboardType,
            focusRequester = focusRequester, error = error, hint = hint,
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

/** Small bordered input for dense rows (steps, blend components). */
@Composable
internal fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
    textAlign: TextAlign = TextAlign.Start,
) {
    // the box stays 36 dp tall; the surrounding layout reserves a 48 dp touch target
    var m = modifier.minimumInteractiveComponentSize().heightIn(min = 36.dp).background(Ink.surface).border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)
    if (focusRequester != null) m = m.focusRequester(focusRequester)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = m,
        singleLine = true,
        textStyle = AppType.small.copy(color = Ink.text, textAlign = textAlign),
        cursorBrush = SolidColor(Ink.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = AppType.small.copy(color = Ink.textFaint), maxLines = 1)
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
) {
    val stepsCount = (((max - min) / step) - 1).toInt().coerceAtLeast(0)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (secondary) AppType.faint else AppType.small, modifier = Modifier.width(112.dp))
        Slider(
            value = (value ?: min).toFloat(),
            onValueChange = { v -> onChange(snap(v.toDouble(), min, step)) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = stepsCount,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Ink.accent, activeTrackColor = Ink.accent, inactiveTrackColor = Ink.line,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
        Text(readout, style = AppType.monoValue, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
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
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
        }
    }
}

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
