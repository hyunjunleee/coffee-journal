package com.coffeejournal.android

import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.ui.theme.AppTextField
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.ChipInput
import com.coffeejournal.ui.theme.CoffeeJournalTheme
import com.coffeejournal.ui.theme.Seg
import com.coffeejournal.ui.theme.SubTabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Shared components: typing through a real InputConnection (IME composition, late ViewModel echoes — gap #3) and
 * the TalkBack semantics of tabs, segments and chips (gap #9).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ComponentsBehaviourTest : FlowTestBase() {

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { CoffeeJournalTheme { Column { content() } } }
        settle()
    }

    private var currentIc: InputConnection? = null

    /** Focuses the (only) text field and opens the InputConnection a keyboard would use. */
    private fun openKeyboard() {
        compose.onNode(hasSetTextAction()).performClick()
        settle()
        compose.runOnUiThread {
            val focused: View = compose.activity.window.decorView.findFocus()
            currentIc = focused.onCreateInputConnection(EditorInfo())
        }
        assertNotNull("text input session", currentIc)
    }

    private fun ime(action: InputConnection.() -> Unit) {
        compose.runOnUiThread { action(requireNotNull(currentIc)) }
        compose.waitForIdle()
    }

    private fun editable(): String =
        compose.onNode(hasSetTextAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun selection(): TextRange? = compose.onNode(hasSetTextAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.TextSelectionRange)

    // ───────────────────────── gap #3: typing ─────────────────────────

    /** The owner (a ViewModel StateFlow) echoes each keystroke late; the field keeps what was typed, cursor at the end. */
    @Test
    fun appTextField_lateEchoes_doNotEatKeystrokesOrMoveTheCursor() {
        val owner = mutableStateOf("")
        val reported = mutableListOf<String>()
        show { AppTextField(value = owner.value, onValueChange = { reported += it }, placeholder = "검색") }
        openKeyboard()
        ime { commitText("a", 1) }
        ime { commitText("b", 1) }
        compose.runOnUiThread { owner.value = reported[0] }   // the echo of "a" arrives after "b" was typed
        compose.waitForIdle()
        ime { setComposingText("가", 1) }
        compose.runOnUiThread { owner.value = reported[1] }   // "ab" arrives while 가 is being composed
        compose.waitForIdle()
        assertEquals("ab가", editable())
        assertEquals(TextRange(3), selection())
        ime { setComposingText("각", 1) }
        ime { finishComposingText() }
        compose.runOnUiThread { owner.value = reported.last() }
        compose.waitForIdle()
        assertEquals("ab각", editable())
        assertEquals(listOf("a", "ab", "ab가", "ab각"), reported)
        // a real change from outside (a cleared search) still replaces the text
        compose.runOnUiThread { owner.value = "" }
        compose.waitForIdle()
        assertEquals("", editable())
    }

    /** 추가 while a syllable is composed: the syllable becomes a chip, the input empties only on the next frame. */
    @Test
    fun chipInput_addWhileComposing_commitsTheSyllableFirstThenClears() {
        val chips = mutableStateListOf<String>()
        var input by mutableStateOf("")
        show {
            ChipInput(chips = chips.toList(), onChipsChange = { chips.clear(); chips.addAll(it) }, input = input, onInputChange = { input = it })
        }
        openKeyboard()
        ime { commitText("오렌지, ", 1) }
        ime { setComposingText("자두", 1) }
        compose.onNode(hasText("추가") and hasClickAction()).performClick()
        settle()
        // the composed syllable became a chip and the field is empty for the keyboard, with nothing left over
        assertEquals(listOf("오렌지", "자두"), chips.toList())
        assertEquals("", editable())
        assertEquals("", input)
        ime { setComposingText("복숭", 1) }
        assertEquals("the keyboard types into the emptied field", "복숭", editable())
        ime { setComposingText("", 1) }
        ime { finishComposingText() }
        // Done on the keyboard adds the same way; duplicates are dropped like the web's Set
        ime { commitText("자두,사과", 1) }
        ime { performEditorAction(EditorInfo.IME_ACTION_DONE) }
        settle()
        assertEquals(listOf("오렌지", "자두", "사과"), chips.toList())
        assertEquals("", editable())
    }

    /** A one-line field's long placeholder stays on one line with an ellipsis instead of growing the field. */
    @Test
    fun appTextField_singleLinePlaceholder_isOneEllipsizedLine() {
        val long = "예: 콜롬비아 라 플라타 게이샤 워시드, 에티오피아 예가체프 코체레 내추럴"
        show {
            AppTextField(value = "", onValueChange = {}, placeholder = long, modifier = Modifier.width(160.dp))
            AppTextField(value = "", onValueChange = {}, placeholder = "$long ", singleLine = false, modifier = Modifier.width(160.dp))
        }
        fun layout(text: String): TextLayoutResult {
            val node = compose.onNode(hasText(text) and !hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode()
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
            return results.single()
        }
        val single = layout(long)
        assertEquals(1, single.lineCount)
        assertTrue("ellipsized", single.isLineEllipsized(0))
        assertTrue("multi-line fields still wrap", layout("$long ").lineCount > 1)
    }

    // ───────────────────────── gap #9: semantics ─────────────────────────

    @Test
    fun subTabs_areTabsWithSelectedState() {
        var selected by mutableStateOf("스터디")
        show { SubTabs(items = listOf("스터디", "클래스", "로드맵"), selected = selected, onSelect = { selected = it }) }
        val study = compose.onNode(hasText("스터디") and hasClickAction())
        assertEquals(Role.Tab, study.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        study.assertIsSelected()
        compose.onNode(hasText("클래스") and hasClickAction()).assertIsNotSelected().performClick()
        settle()
        compose.onNode(hasText("클래스") and hasClickAction()).assertIsSelected()
        study.assertIsNotSelected()
    }

    @Test
    fun seg_optionsAreRadioButtonsWithSelectedState() {
        var value by mutableStateOf("병합")
        show { Seg(options = listOf("병합", "교체"), value = value, onChange = { value = it }, allowClear = false) }
        val merge = compose.onNode(hasText("병합") and hasClickAction())
        assertEquals(Role.RadioButton, merge.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        merge.assertIsSelected()
        compose.onNode(hasText("교체") and hasClickAction()).assertIsNotSelected().performClick()
        settle()
        compose.onNode(hasText("교체") and hasClickAction()).assertIsSelected()
        merge.assertIsNotSelected()
    }

    @Test
    fun chips_withAnActionAreCheckboxesOrButtons_plainChipsAreText() {
        var on by mutableStateOf(false)
        show {
            Chip("자스민", selected = on, onClick = { on = !on })
            Chip("게이샤 추천", onClick = {}, toggle = false)
            Chip("표시만")
        }
        val toggle = compose.onNode(hasText("자스민") and hasClickAction())
        assertEquals(Role.Checkbox, toggle.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        toggle.assertIsOff().performClick()
        settle()
        compose.onNode(hasText("자스민") and hasClickAction()).assertIsOn()
        assertEquals(ToggleableState.On, compose.onNode(hasText("자스민") and hasClickAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState))
        val suggestion = compose.onNode(hasText("게이샤 추천") and hasClickAction()).fetchSemanticsNode()
        assertEquals(Role.Button, suggestion.config.getOrNull(SemanticsProperties.Role))
        assertFalse(suggestion.config.contains(SemanticsProperties.ToggleableState))
        assertFalse("a chip without an action is not clickable", has(hasText("표시만") and hasClickAction()))
    }
}
