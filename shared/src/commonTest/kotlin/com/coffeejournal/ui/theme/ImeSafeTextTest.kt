package com.coffeejournal.ui.theme

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gap #3: a field's own text survives the owner's delayed echoes; real outside changes still replace it. */
class ImeSafeTextTest {
    private fun composing(text: String, from: Int) = TextFieldValue(text, TextRange(text.length), TextRange(from, text.length))

    @Test fun lateEchoesNeverResetTheCursorOrTheComposition() {
        val t = ImeSafeText("")
        assertEquals("ㅎ", t.onEdit(composing("ㅎ", 0)))
        assertEquals("하", t.onEdit(composing("하", 0)))
        assertEquals("한", t.onEdit(composing("한", 0)))
        // the owner's StateFlow catches up one keystroke at a time
        t.syncExternal("ㅎ")
        t.syncExternal("하")
        assertEquals(composing("한", 0), t.value, "still composing 한 with the cursor at the end")
        t.syncExternal("한")
        assertTrue(t.isComposing)
    }

    @Test fun conflatedEchoSkipsIntermediateTexts() {
        val t = ImeSafeText("커피")
        t.onEdit(TextFieldValue("커피 ", TextRange(3)))
        t.onEdit(TextFieldValue("커피 원", TextRange(4)))
        t.syncExternal("커피 원")
        t.syncExternal("커피 원두")    // a real change from outside after the echo
        assertEquals(TextFieldValue("커피 원두", TextRange(5)), t.value)
    }

    @Test fun outsideChangesReplaceTheText() {
        val t = ImeSafeText("")
        t.onEdit(TextFieldValue("18000", TextRange(5)))
        t.syncExternal("18,000")          // the owner reformats the price
        assertEquals(TextFieldValue("18,000", TextRange(6)), t.value)
        t.syncExternal("")                // a cleared draft
        assertEquals("", t.value.text)
    }

    @Test fun chipAddWhileComposing_commitsTheSyllableAndDefersTheClear() {
        val t = ImeSafeText("")
        t.onEdit(composing("오렌지, 자두", 5))
        val added = addChipsFromInput(t, listOf("자두"))!!
        assertEquals(listOf("자두", "오렌지"), added.chips)
        assertFalse(added.clearNow, "not cleared while the IME still holds 자두")
        assertFalse(t.isComposing)
        assertEquals("오렌지, 자두", t.value.text)
        // once committed, the next add clears right away
        t.onEdit(TextFieldValue("사과", TextRange(2)))
        assertTrue(addChipsFromInput(t, added.chips)!!.clearNow)
        t.onEdit(TextFieldValue("  ", TextRange(2)))
        assertNull(addChipsFromInput(t, added.chips))
    }

    @Test fun cursorMovesAreNotReported_andCommitKeepsTheText() {
        val t = ImeSafeText("abc")
        assertNull(t.onEdit(TextFieldValue("abc", TextRange(1))))
        t.onEdit(composing("abc가", 3))
        assertTrue(t.isComposing)
        t.commitComposition()
        assertFalse(t.isComposing)
        assertEquals("abc가", t.value.text)
    }
}

class ImeSafeTextFilterTest {
    private fun typed(text: String) = androidx.compose.ui.text.input.TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length))
    private val grams: (String) -> String? = { t -> t.replace(',', '.').takeIf { Regex("\\d*\\.?\\d*").matches(it) } }

    @Test
    fun rejectedEditIsNotShownAndNotReported() {
        val s = ImeSafeText("10.5")
        assertEquals(null, s.onEdit(typed("10.5g"), grams))
        assertEquals("10.5", s.value.text)
    }

    @Test
    fun rewrittenEditIsShownAndReportedRewritten() {
        val s = ImeSafeText("10")
        assertEquals("10.", s.onEdit(typed("10,"), grams))
        assertEquals("10.", s.value.text)
        assertEquals(3, s.value.selection.end)
        s.syncExternal("10.")
        assertEquals("10.", s.value.text)
    }

    @Test
    fun digitsOnlyFilterDropsLettersButKeepsDigits() {
        val s = ImeSafeText("12")
        assertEquals(null, s.onEdit(typed("12a")) { it.filter(Char::isDigit) }.takeIf { it != "12" })
        assertEquals("12", s.value.text)
        assertEquals("123", s.onEdit(typed("123")) { it.filter(Char::isDigit) })
    }
}
