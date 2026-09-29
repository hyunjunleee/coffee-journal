package com.coffeejournal.android

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import com.coffeejournal.App
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Shared driver for the end-to-end flow tests: the real [App] on Robolectric with an in-memory Room database.
 * Room flows emit on background dispatchers, so every wait polls with real sleeps plus [waitForIdle].
 */
abstract class FlowTestBase {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    protected val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun startFlowKoin() {
        File(context.cacheDir, "photos").deleteRecursively()
        startTestKoin(context)
    }

    @After
    fun stopFlowKoin() {
        stopKoin()
    }

    private val offMainWrites = CopyOnWriteArrayList<String>()
    private var writeWatch: ObserverHandle? = null

    /**
     * Compose state is written on the main thread only. In UI tests the composition's own coroutines do not dispatch,
     * so a value handed to one from a background thread is written there, and the recomposition it starts can run on
     * that thread too (Android then refuses its layout request, and two threads composing at once break the slot
     * table): every flow test fails on such a write, with where it came from.
     */
    @Before
    fun watchStateWrites() {
        val main = Looper.getMainLooper().thread
        writeWatch = Snapshot.registerGlobalWriteObserver {
            val t = Thread.currentThread()
            if (t !== main && offMainWrites.size < 5) {
                val stack = t.stackTrace.drop(2)
                val shown = stack.filter { it.className.startsWith("com.coffeejournal") }.ifEmpty { stack }.take(10)
                offMainWrites += "${t.name}\n    at " + shown.joinToString("\n    at ")
            }
        }
    }

    @After
    fun noStateWrittenOffMain() {
        writeWatch?.dispose()
        if (offMainWrites.isNotEmpty()) throw AssertionError("Compose state written off the main thread:\n" + offMainWrites.joinToString("\n"))
    }

    protected inline fun <reified T : Any> koinGet(): T = GlobalContext.get().get()

    fun launchApp() {
        compose.setContent { App() }
        settle()
    }

    fun settle(rounds: Int = 3) {
        repeat(rounds) { Thread.sleep(80); compose.waitForIdle() }
    }

    fun waitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            compose.waitForIdle()
            if (runCatching(condition).getOrDefault(false)) return
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for: $what\n--- UI ---\n${dump()}")
            Thread.sleep(60)
        }
    }

    fun has(matcher: SemanticsMatcher, unmerged: Boolean = false): Boolean =
        compose.onAllNodes(matcher, unmerged).fetchSemanticsNodes().isNotEmpty()

    fun count(matcher: SemanticsMatcher): Int = compose.onAllNodes(matcher).fetchSemanticsNodes().size

    fun waitFor(matcher: SemanticsMatcher, what: String = matcher.description, timeoutMs: Long = 10_000) =
        waitUntil(what, timeoutMs) { has(matcher) }

    fun waitForText(text: String, substring: Boolean = false) = waitFor(hasText(text, substring), "text '$text'")

    fun waitGone(matcher: SemanticsMatcher, what: String = matcher.description) = waitUntil("gone: $what") { !has(matcher) }

    fun node(matcher: SemanticsMatcher, index: Int = 0): SemanticsNodeInteraction = compose.onAllNodes(matcher)[index]

    fun clickNode(matcher: SemanticsMatcher, index: Int = 0) {
        waitFor(matcher)
        val n = node(matcher, index)
        runCatching { n.performScrollTo() }
        keepClearOfBottomOverlay(n)
        n.performClick()
        settle()
    }

    /**
     * performScrollTo stops as soon as the node touches the viewport edge, where the floating "+" button of the
     * home / 기타 tabs sits. A user scrolls a little further; so do we, so taps do not land on the FAB.
     */
    private fun keepClearOfBottomOverlay(n: SemanticsNodeInteraction) {
        val sn = n.fetchSemanticsNode()
        val rootHeight = sn.layoutInfo.coordinates.findRootCoordinates().size.height
        val margin = 180f * context.resources.displayMetrics.density
        val overshoot = sn.boundsInRoot.bottom - (rootHeight - margin)
        if (overshoot <= 0f) return
        var p = sn.parent
        while (p != null && !p.config.contains(SemanticsActions.ScrollBy)) p = p.parent
        val scroll = p?.config?.getOrNull(SemanticsActions.ScrollBy)?.action ?: return
        compose.runOnUiThread { scroll(0f, overshoot) }
        settle(1)
    }

    fun button(text: String): SemanticsMatcher = hasText(text) and hasClickAction()

    fun clickText(text: String, index: Int = 0) = clickNode(button(text), index)

    fun dialogButton(text: String): SemanticsMatcher = hasText(text) and hasClickAction() and hasAnyAncestor(isDialog())

    fun field(placeholderOrValue: String): SemanticsMatcher = hasSetTextAction() and hasText(placeholderOrValue)

    fun typeInto(placeholder: String, text: String, index: Int = 0) {
        waitFor(field(placeholder), "field '$placeholder'")
        val n = node(field(placeholder), index)
        runCatching { n.performScrollTo() }
        n.performTextInput(text)
        settle(1)
    }

    fun replaceIn(currentValue: String, newValue: String, index: Int = 0) {
        waitFor(field(currentValue), "field with '$currentValue'")
        val n = node(field(currentValue), index)
        runCatching { n.performScrollTo() }
        n.performTextReplacement(newValue)
        settle(1)
    }

    /** The title-bar back arrow of every full-screen route. */
    fun back() = clickNode(hasContentDescription("뒤로") and hasClickAction())

    fun tab(tag: String) = clickNode(hasTestTag(tag))

    /**
     * Scrolls the vertical lazy list that contains [anchor] until [target] is composed. The target can depend on data
     * that is still on its way (a Room flow re-emitting after a write), so a miss is retried until [timeoutMs] instead
     * of failing on the first frame that does not show it yet.
     */
    fun scrollListTo(anchor: SemanticsMatcher, target: SemanticsMatcher, timeoutMs: Long = 10_000) {
        val list = hasScrollToIndexAction() and hasAnyDescendant(anchor)
        waitFor(list, "list containing ${anchor.description}")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val miss = runCatching { node(list).performScrollToNode(target) }.exceptionOrNull() ?: break
            if (System.currentTimeMillis() > deadline) throw miss
            settle(1)
        }
        settle(1)
    }

    fun dump(): String {
        val roots = compose.onAllNodes(isRoot())
        val n = roots.fetchSemanticsNodes(atLeastOneRootRequired = false).size
        return (0 until n).joinToString("\n") { i -> runCatching { roots[i].printToString(maxDepth = Int.MAX_VALUE) }.getOrElse { "<$it>" } }
    }
}
