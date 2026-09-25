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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Small mono uppercase-ish label above a group of fields (web .section-label). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, hint: String? = null) {
    Row(modifier.padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = AppType.sectionLabel)
        if (hint != null) { Spacer(Modifier.width(6.dp)); Text(hint, style = AppType.faint) }
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
            .clickable(enabled = enabled, onClick = onClick)
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
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 5.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = AppType.body.copy(color = fg, fontSize = if (small) 12.5.sp else 14.sp))
    }
}

/** Underlined chip row used for sub tabs (web .cal-subtabs). */
@Composable
fun SubTabs(
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    dots: Map<String, Color> = emptyMap(),
    labels: Map<String, String> = emptyMap(),
    scrollable: Boolean = true,
) {
    val rowModifier = if (scrollable) modifier.horizontalScroll(rememberScrollState()) else modifier
    Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            val on = item == selected
            Row(
                Modifier
                    .border(BorderStroke(Dimens.hairline, if (on) Ink.accent else Ink.line), RectangleShape)
                    .background(if (on) Ink.accent else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dots[item]?.let { c -> Box(Modifier.size(7.dp).background(c)); Spacer(Modifier.width(6.dp)) }
                Text(labels[item] ?: item, style = AppType.small.copy(color = if (on) Ink.bg else Ink.textMuted))
            }
        }
    }
}

/** Square segmented control; tapping the active option again clears it when [allowClear]. */
@Composable
fun Seg(
    options: List<String>,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = true,
    labels: Map<String, String> = emptyMap(),
) {
    Row(modifier.fillMaxWidth().border(BorderStroke(Dimens.hairline, Ink.line), RectangleShape)) {
        options.forEachIndexed { i, opt ->
            val on = opt == value
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .background(if (on) Ink.accent else Ink.surface)
                    .clickable { onChange(if (on && allowClear) "" else opt) }
                    .padding(horizontal = 6.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(labels[opt] ?: opt, style = AppType.small.copy(color = if (on) Ink.bg else Ink.text), textAlign = TextAlign.Center)
            }
            if (i < options.lastIndex) Box(Modifier.width(Dimens.hairline).heightIn(min = 40.dp).background(Ink.line))
        }
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

/** Square outlined text field with the archive palette. */
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
) {
    Column(modifier) {
        if (label != null) FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = AppType.body.copy(color = Ink.textFaint)) },
            singleLine = singleLine,
            minLines = minLines,
            enabled = enabled,
            textStyle = AppType.body,
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

/** Chip list with an input row; Enter or the button adds, × removes (web .expected-notes-chips). */
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
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextField(
                value = input, onValueChange = onInputChange, modifier = Modifier.weight(1f), placeholder = placeholder,
                imeAction = ImeAction.Done,
                onImeAction = { if (input.isNotBlank()) { onChipsChange(com.coffeejournal.domain.rules.NoteCanon.addChips(chips, input)); onInputChange("") } },
            )
            Spacer(Modifier.width(8.dp))
            GhostButton(addLabel, small = true, onClick = {
                if (input.isNotBlank()) { onChipsChange(com.coffeejournal.domain.rules.NoteCanon.addChips(chips, input)); onInputChange("") }
            })
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

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, prefix: String = "") {
    val base = modifier
        .border(BorderStroke(Dimens.hairline, if (selected) Ink.accent else Ink.line), RectangleShape)
        .background(if (selected) Ink.accent else Ink.surface)
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prefix + text, style = AppType.small.copy(color = if (selected) Ink.bg else Ink.text))
        if (onRemove != null) {
            Spacer(Modifier.width(6.dp))
            Text("×", style = AppType.small.copy(color = if (selected) Ink.bg else Ink.textFaint), modifier = Modifier.clickable(onClick = onRemove))
        }
    }
}

/** Top header shared by all tabs: title, tagline and a right-hand count. */
@Composable
fun TopHeader(title: String, tagline: String, right: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = Dimens.gutter).padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(title, style = AppType.headerTitle)
                Text(tagline, style = AppType.tagline)
            }
            if (right != null) Text(right, style = AppType.count)
        }
        Spacer(Modifier.height(12.dp))
        Hairline(color = Ink.text, thickness = Dimens.heavyRule)
    }
}

/** Sticky-looking screen title row used by sub screens (back arrow + title + optional action). */
@Composable
fun ScreenTitleBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().background(Ink.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(Modifier.size(Dimens.touch).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
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
