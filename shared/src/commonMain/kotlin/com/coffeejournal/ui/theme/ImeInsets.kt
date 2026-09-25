package com.coffeejournal.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

/**
 * Bottom padding for the part of the on-screen keyboard that covers this node; nothing while the keyboard is closed.
 * Put it on a scroll container (before `verticalScroll`, or on a `LazyColumn`): the viewport then ends at the
 * keyboard, so the focused field is scrolled above it. The app draws edge-to-edge, so the window is not resized for
 * the keyboard and every screen has to do this itself. Unlike `imePadding()` it leaves out the space that is already
 * below the node (the bottom tab bar of the tab screens), so tab and full-screen layouts both end right at the keyboard.
 */
fun Modifier.imeOverlapPadding(): Modifier = composed {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var spaceBelow by remember { mutableIntStateOf(0) }
    this
        .onGloballyPositioned { c ->
            val bottom = c.positionInRoot().y + c.size.height
            spaceBelow = (c.findRootCoordinates().size.height - bottom).roundToInt().coerceAtLeast(0)
        }
        .padding(bottom = with(density) { ImeInsets.overlap(imeBottom, spaceBelow).toDp() })
}

object ImeInsets {
    /** Pixels of a keyboard [imeBottom] high that a node ending [spaceBelow] pixels above the window bottom must give up. */
    fun overlap(imeBottom: Int, spaceBelow: Int): Int = (imeBottom - spaceBelow).coerceAtLeast(0)
}
