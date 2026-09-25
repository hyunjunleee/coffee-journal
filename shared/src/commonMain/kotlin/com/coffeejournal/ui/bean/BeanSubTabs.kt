package com.coffeejournal.ui.bean

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Ink
import kotlin.math.roundToInt

/**
 * The 원두 tab's sub views as the app's chip row (the look of theme SubTabs), scrolled so the selected chip is on
 * screen: on first composition (the default 커피 지도 is the 8th chip), after a tap and when another screen asks for
 * a view through [BeanViewRequests]. ✦ Competition Lots keeps the web's accent outline (.cal-subtab-special).
 */
@Composable
internal fun BeanSubTabs(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val chips = remember { mutableStateMapOf<String, IntRange>() }
    var viewport by remember { mutableIntStateOf(0) }
    val peek = with(LocalDensity.current) { 28.dp.roundToPx() }
    val shown = remember { FirstScroll() }
    val bounds = chips[selected]
    LaunchedEffect(selected, bounds, viewport) {
        if (bounds == null || viewport == 0) return@LaunchedEffect
        val target = SubTabScroll.target(bounds.first, bounds.last, viewport, scroll.value, scroll.maxValue, peek)
        // the first position is taken at once (no slide on opening the tab); later changes animate
        if (target != scroll.value) { if (shown.done) scroll.animateScrollTo(target) else scroll.scrollTo(target) }
        shown.done = true
    }
    Row(modifier.onSizeChanged { viewport = it.width }.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BeanViews.all.forEach { view ->
            BeanSubTab(
                label = BeanViews.labels[view] ?: view,
                on = view == selected,
                special = view == BeanViews.SPECIALTY,
                onClick = { onSelect(view) },
                modifier = Modifier.onPlaced { c -> val x = c.positionInParent().x.roundToInt(); chips[view] = x..(x + c.size.width) },
            )
        }
    }
}

@Composable
private fun BeanSubTab(label: String, on: Boolean, special: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val border = when {
        on -> BorderStroke(Dimens.hairline, Ink.accent)
        special -> BorderStroke(1.5.dp, Ink.accent)
        else -> BorderStroke(Dimens.hairline, Ink.line)
    }
    val color = when {
        on -> Ink.bg
        special -> Ink.accent
        else -> Ink.textMuted
    }
    Row(
        modifier
            .border(border, RectangleShape)
            .background(if (on) Ink.accent else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = on }
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppType.small.copy(color = color, fontWeight = if (special) FontWeight.SemiBold else AppType.small.fontWeight))
    }
}

private class FirstScroll { var done = false }

/** Where the sub-tab row scrolls to so the selected chip is fully visible. */
internal object SubTabScroll {
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
