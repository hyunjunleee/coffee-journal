package com.coffeejournal.ui.form

import kotlin.test.Test
import kotlin.test.assertEquals

/** platform-4: a scroll area gives up exactly the part of the keyboard that covers it. */
class ImeInsetsTest {
    @Test fun overlapLeavesOutWhatIsAlreadyBelowTheNode() {
        assertEquals(0, ImeInsets.overlap(imeBottom = 0, spaceBelow = 0), "keyboard closed")
        assertEquals(900, ImeInsets.overlap(imeBottom = 900, spaceBelow = 0), "full-screen form: the whole keyboard")
        assertEquals(740, ImeInsets.overlap(imeBottom = 900, spaceBelow = 160), "tab screen: minus the bottom tab bar")
        assertEquals(0, ImeInsets.overlap(imeBottom = 120, spaceBelow = 160), "keyboard lower than the tab bar")
    }
}
