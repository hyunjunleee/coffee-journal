package com.coffeejournal.android

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import com.coffeejournal.data.repo.BlendRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MiscItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Helpers shared by the coverage flow tests (critic gap #7): record form, 원두 tab, calendar and 기타 flows that the
 * first flow suite never reached. Everything drives the real [com.coffeejournal.App] through [FlowTestBase].
 */
abstract class CoverageFlowBase : FlowTestBase() {
    protected val namePlaceholder = "예: 콜롬비아 라 플라타 게이샤 워시드"
    protected val searchPlaceholder = "예: 벤사, 게이샤, 리브레"

    protected fun entries(): List<Entry> = runBlocking { koinGet<EntryRepository>().getAll() }
    protected fun entry(id: String): Entry? = runBlocking { koinGet<EntryRepository>().getById(id) }
    protected fun misc(): List<MiscItem> = runBlocking { koinGet<MiscRepository>().getAll() }
    protected fun blends() = runBlocking { koinGet<BlendRepository>().getAll() }
    protected fun roadmap() = runBlocking { koinGet<RoadmapRepository>().getAll() }

    protected fun onDetailOf(name: String): Boolean = has(hasText(name)) && has(button("수정")) && has(button("삭제"))

    protected fun openNewForm() {
        clickText("+ 새 기록 추가")
        waitForText("새 기록")
    }

    /** Home search → the [index]-th "기록 보기" → the record's detail screen. */
    protected fun openViaSearch(query: String, index: Int = 0) {
        typeInto(searchPlaceholder, query)
        waitForText("전체 기록에서", substring = true)
        clickNode(button("기록 보기"), index)
        waitUntil("detail opened from search") { has(button("수정")) && has(button("삭제")) }
    }

    /** Saves the record form and waits for the detail screen of [name]. */
    protected fun saveForm(name: String, label: String = "저장") {
        clickText(label)
        waitUntil("detail of '$name'") { onDetailOf(name) && !has(hasText("새 기록")) && !has(hasText("기록 수정")) }
    }

    /** Top edge (px, root coordinates, not clipped to the viewport) of the [index]-th node matching [matcher]. */
    protected fun top(matcher: SemanticsMatcher, index: Int = 0): Float = node(matcher, index).fetchSemanticsNode().positionInRoot.y

    /** The nodes appear top to bottom in the given order. */
    protected fun assertTopToBottom(what: String, vararg matchers: SemanticsMatcher) {
        matchers.forEach { waitFor(it) }
        val tops = matchers.map { top(it) }
        assertEquals("$what: ${matchers.map { it.description }} at $tops", tops.sorted(), tops)
        assertTrue("$what: distinct rows", tops.toSet().size == tops.size)
    }

    protected fun toggleState(matcher: SemanticsMatcher, index: Int = 0): ToggleableState? =
        node(matcher, index).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)

    protected fun isSelected(matcher: SemanticsMatcher, index: Int = 0): Boolean =
        node(matcher, index).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true

    /** A clickable node whose (merged) texts contain [part] somewhere. */
    protected fun clickableWith(part: String): SemanticsMatcher = hasText(part, substring = true) and hasClickAction()

    protected fun rootHeight(): Float = compose.onAllNodes(isRoot()).fetchSemanticsNodes().first().boundsInRoot.height

    protected fun rootWidth(): Float = compose.onAllNodes(isRoot()).fetchSemanticsNodes().first().boundsInRoot.width

    protected val density: Float get() = context.resources.displayMetrics.density

    /**
     * Scrolls every scroll container around the node (innermost first) until the node is inside its viewport.
     * performScrollTo only moves the closest container, so a tab row inside a horizontal scroller that itself sits in a
     * scrolled-away vertical page stays off screen and the tap lands nowhere.
     */
    protected fun bringIntoView(matcher: SemanticsMatcher, index: Int = 0) {
        val bottomMargin = 80 * density
        repeat(3) {
            var moved = false
            var p = node(matcher, index).fetchSemanticsNode().parent
            while (p != null) {
                val scroll = p.config.getOrNull(SemanticsActions.ScrollBy)?.action
                if (scroll != null) {
                    val cur = node(matcher, index).fetchSemanticsNode()
                    val top = cur.positionInRoot.y
                    val bottom = top + cur.size.height
                    val left = cur.positionInRoot.x
                    val right = left + cur.size.width
                    val vp = p.boundsInRoot
                    val vpBottom = maxOf(vp.top + cur.size.height, vp.bottom - bottomMargin)
                    val dy = when { top < vp.top -> top - vp.top; bottom > vpBottom -> minOf(bottom - vpBottom, top - vp.top); else -> 0f }
                    val dx = when { left < vp.left -> left - vp.left; right > vp.right -> minOf(right - vp.right, left - vp.left); else -> 0f }
                    if (dx != 0f || dy != 0f) {
                        compose.runOnUiThread { scroll(dx, dy) }
                        compose.waitForIdle()
                        moved = true
                    }
                }
                p = p.parent
            }
            if (!moved) return
        }
    }

    /** Taps a node after bringing it into view through all its scroll containers. */
    protected fun tap(matcher: SemanticsMatcher, index: Int = 0) {
        waitFor(matcher)
        bringIntoView(matcher, index)
        node(matcher, index).performClick()
        settle()
    }

    protected fun tapText(text: String, index: Int = 0) = tap(button(text), index)
}
