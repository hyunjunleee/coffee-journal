package com.coffeejournal.ui.theme

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

/** Gap #8 fixes found in the 320 dp and font-scale renders. */
class LayoutRulesTest {
    @Test fun seg_equalSharesWhileEveryLabelFits() {
        assertEquals(listOf(listOf(0, 1, 2)), SegLayout.rows(listOf(80, 60, 70), 300))
        assertEquals(listOf(100, 100, 100), SegLayout.widths(listOf(80, 60, 70), 300))
    }

    @Test fun seg_aLongerLabelKeepsItsWidth_theOthersShareTheRest() {
        // "라이트 · 미디엄 라이트 · 미디엄 · 미디엄 다크 · 다크" in 379 px: the long one is not broken into "미디엄 라 / 이트"
        val oneLine = listOf(50, 95, 50, 80, 37)
        assertEquals(listOf(oneLine.indices.toList()), SegLayout.rows(oneLine, 379))
        val w = SegLayout.widths(oneLine, 379)
        assertEquals(379, w.sum())
        // like CSS flex: every label wider than an even fifth (75) keeps its width, the rest share what is left
        assertEquals(listOf(68, 95, 68, 80, 68), w)
    }

    @Test fun seg_optionsWrapOntoAnotherRowWhenTheyCannotAllFit() {
        // a large font: five labels of 100 px in 330 px
        val rows = SegLayout.rows(List(5) { 100 }, 330)
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4)), rows)
        assertEquals(listOf(110, 110, 110), SegLayout.widths(List(3) { 100 }, 330))
        assertEquals(listOf(165, 165), SegLayout.widths(List(2) { 100 }, 330))
        // one label wider than the whole row gets the row
        assertEquals(listOf(listOf(0), listOf(1)), SegLayout.rows(listOf(400, 50), 330))
        assertEquals(listOf(330), SegLayout.widths(listOf(400), 330))
    }

    @Test fun unfocusedField_showsTheStartOfItsText() {
        val typed = TextFieldValue("Yirgacheffe, Gedeb, Worka Chelbesa", TextRange(34))
        assertEquals(TextRange.Zero, typed.shownWhen(focused = false).selection)
        assertEquals(typed.text, typed.shownWhen(focused = false).text)
        assertEquals(typed, typed.shownWhen(focused = true))
    }
}
