package com.coffeejournal.android

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.EntryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gap #3 in the record form: typing through the InputConnection a real keyboard uses (performTextInput skips IME
 * composition). A Hangul name is composed jamo by jamo, a number field drops what the web's type=number would not
 * take, and 총 추출시간 keeps the time the step log decides, as the web overwrites it on every input.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class RecordFormTypingTest : FlowTestBase() {

    private val namePlaceholder = "예: 콜롬비아 라 플라타 게이샤 워시드"
    private var ic: InputConnection? = null

    /** Taps the field and opens the InputConnection the keyboard would get; returns a matcher for that field. */
    private fun focus(field: SemanticsMatcher) {
        waitFor(field)
        val n = node(field)
        runCatching { n.performScrollTo() }
        n.performClick()
        settle(1)
        compose.runOnUiThread { ic = compose.activity.window.decorView.findFocus().onCreateInputConnection(EditorInfo()) }
        assertNotNull("text input session", ic)
    }

    private fun ime(action: InputConnection.() -> Unit) {
        compose.runOnUiThread { action(requireNotNull(ic)) }
        compose.waitForIdle()
    }

    private fun focusedEditable(): Pair<String, TextRange?> {
        val n = compose.onAllNodes(SemanticsMatcher("focused text field") {
            it.config.getOrNull(SemanticsProperties.Focused) == true && it.config.contains(SemanticsProperties.EditableText)
        }).fetchSemanticsNodes().single()
        return n.config[SemanticsProperties.EditableText].text to n.config.getOrNull(SemanticsProperties.TextSelectionRange)
    }

    @Test
    fun hangulComposition_numberFilter_andComputedTime() {
        launchApp()
        clickText("+ 새 기록 추가")
        waitForText("새 기록")

        // a Hangul name, one jamo at a time, with a space committed between the words
        focus(field(namePlaceholder))
        ime { setComposingText("ㅋ", 1) }
        ime { setComposingText("커", 1) }
        ime { setComposingText("커ㅍ", 1) }
        ime { setComposingText("커피", 1) }
        ime { finishComposingText() }
        ime { commitText(" ", 1) }
        ime { setComposingText("ㅇ", 1) }
        ime { setComposingText("우", 1) }
        ime { setComposingText("원", 1) }
        ime { setComposingText("원ㄷ", 1) }
        ime { setComposingText("원두", 1) }
        assertEquals("still composing: nothing lost, cursor at the end", "커피 원두" to TextRange(5), focusedEditable())
        ime { finishComposingText() }
        settle()
        assertEquals("커피 원두" to TextRange(5), focusedEditable())

        // 원두량 (web type=number): a unit or a letter is not taken, a comma becomes the point
        focus(field("20"))
        ime { commitText("1", 1) }
        ime { commitText("8", 1) }
        ime { commitText("g", 1) }
        assertEquals("the letter is not shown", "18", focusedEditable().first)
        ime { commitText(",", 1) }
        ime { commitText("5", 1) }
        assertEquals("18.5" to TextRange(4), focusedEditable())

        // 총 추출시간 follows the step log (the example steps end at 1:35 + 35 s)
        focus(field("2:10"))
        ime { commitText("9", 1) }
        assertEquals("the step log decides the time", "2:10", focusedEditable().first)

        clickText("저장")
        waitUntil("detail of the new record") { has(hasText("커피 원두")) && has(button("수정")) }
        val saved = runBlocking { koinGet<EntryRepository>().getAll() }.single()
        assertEquals("커피 원두", saved.name)
        assertEquals("18.5", saved.dose)
        assertEquals("2:10", saved.time)
    }
}
