package com.coffeejournal.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.model.Scope
import com.coffeejournal.domain.rules.KoreaProjection
import com.coffeejournal.ui.map.KoreaFrames
import com.coffeejournal.ui.map.MapViewportMath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Touch gestures on the SGIS Korea maps (원두 › 로스터리), national and 시·도: a two-finger pinch zooms (the map says
 * "확대 …배" to TalkBack and "전체 보기" appears), a one-finger drag then pans the map and not the page, and at the
 * fitted zoom a one-finger swipe scrolls the page and leaves the map as it was.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MapGestureFlowTest : CoverageFlowBase() {
    private val mapLabel = "한국 로스터리 지도. 로스터리 3곳 표시"
    private val map: SemanticsMatcher = hasContentDescription(mapLabel, substring = true)
    /** 안동's chip: nationally and on the 경상북도 map it is the only pin, far from the others. */
    private val andong: SemanticsMatcher = hasContentDescription("안동 로스터리, ", substring = true) and hasClickAction()

    private fun seed() = runBlocking {
        koinGet<MiscRepository>().upsertAll(listOf(
            MiscItem(id = "g1", type = MiscType.SOURCE, name = "성수 로스터리", scope = Scope.DOMESTIC, location = "", lat = 37.5446, lng = 127.0557, createdAt = 1),
            MiscItem(id = "g2", type = MiscType.SOURCE, name = "부산 로스터리", scope = Scope.DOMESTIC, location = "", lat = 35.2270, lng = 129.0880, createdAt = 2),
            MiscItem(id = "g3", type = MiscType.SOURCE, name = "안동 로스터리", scope = Scope.DOMESTIC, location = "", lat = 36.5684, lng = 128.7294, createdAt = 3),
        ))
    }

    private fun openMap() {
        seed()
        launchApp()
        tab("tab-bean")
        tapText("로스터리")
        waitFor(map)
        bringIntoView(map)
        settle()
    }

    private fun mapNode() = node(map).fetchSemanticsNode()
    /** Where a node is in the root, not clipped to the scrolled viewport (boundsInRoot is). */
    private fun unclipped(m: SemanticsMatcher): Rect = node(m).fetchSemanticsNode().let { n ->
        Rect(n.positionInRoot, Size(n.size.width.toFloat(), n.size.height.toFloat()))
    }
    private fun mapBounds(): Rect = unclipped(map)
    private fun zoomState(): String? = mapNode().config.getOrNull(SemanticsProperties.StateDescription)
    private fun chipBounds(): Rect = unclipped(andong)

    /** Canvas px (inside the map node) where [lat]/[lng] is drawn at the fitted zoom of [frame]. */
    private fun canvasPoint(frame: Rect, lat: Double, lng: Double): Offset {
        val n = mapNode()
        val size = Size(n.size.width.toFloat(), n.size.height.toFloat())
        val p = KoreaProjection.toMap(lat, lng)
        return MapViewportMath.toCanvas(p.x.toFloat(), p.y.toFloat(), frame, MapViewportMath.fitScale(frame, size), Offset(size.width / 2f, size.height / 2f))
    }

    private fun zoomOf(state: String?): Float = Regex("확대 ([0-9.]+)배").find(state.orEmpty())?.groupValues?.get(1)?.toFloat() ?: 1f

    /**
     * The three gestures on the map showing [frame]: a pinch left of 안동 (clear of every chip), a drag, reset, and a swipe
     * over the sea on the left edge (no chips there either).
     */
    private fun pinchDragAndSwipe(frame: Rect) {
        assertEquals("기본 배율", zoomState())
        assertFalse(has(button("전체 보기")))
        // ── two-finger pinch: fingers 200 px apart, left of 안동's point (its chip is to the right), spread to 500 px
        val anchor = canvasPoint(frame, 36.5684, 128.7294)
        val c = anchor - Offset(160f, 0f)
        val chipBefore = chipBounds()
        val mapBefore = mapBounds()
        node(map).performTouchInput {
            pinch(c - Offset(100f, 0f), c - Offset(250f, 0f), c + Offset(100f, 0f), c + Offset(250f, 0f), durationMillis = 500)
        }
        settle()
        val zoomed = zoomOf(zoomState())
        // ×2.5 apart; the part before the touch slop only tells a pinch from a tap (as in Compose's transform detector)
        assertTrue("zoomed in: ${zoomState()}", zoomed >= 1.8f)
        waitFor(button("전체 보기"))
        assertEquals("the page did not move", mapBefore.top, mapBounds().top, 1f)
        // the point under the fingers stayed put, so 안동 moved away from it by the zoom factor
        val chipZoomed = chipBounds()
        assertTrue("chip moved right: $chipBefore → $chipZoomed", chipZoomed.left - chipBefore.left > 60f)

        // ── one-finger drag while zoomed: the map follows the finger, the page stays
        val from = c
        val to = c + Offset(-90f, 70f)
        node(map).performTouchInput { swipe(from, to, durationMillis = 400) }
        settle()
        val chipDragged = chipBounds()
        assertEquals("page still", mapBefore.top, mapBounds().top, 1f)
        // the map moves the way the finger went; the first touch slop of the drag only decides that it is a drag (as in
        // Compose's own transform detector), so it moves a little less than the finger
        val moved = chipDragged.topLeft - chipZoomed.topLeft
        val finger = to - from
        val cos = (moved.x * finger.x + moved.y * finger.y) / (moved.getDistance() * finger.getDistance())
        assertTrue("same direction as the finger: $moved vs $finger", cos > 0.995f)
        assertTrue("most of the finger's way: $moved vs $finger", moved.getDistance() in finger.getDistance() * 0.5f..finger.getDistance() * 1.05f)
        assertEquals("a drag does not zoom", zoomed, zoomOf(zoomState()), 0.11f)

        // ── back to the fitted zoom; a one-finger swipe there scrolls the page and leaves the map alone
        tapText("전체 보기")
        waitGone(button("전체 보기"))
        assertEquals("기본 배율", zoomState())
        val chipFitted = chipBounds()
        val mapFitted = mapBounds()
        val h = mapNode().size.height.toFloat()
        node(map).performTouchInput { swipe(Offset(12f, h * 0.85f), Offset(12f, h * 0.35f), durationMillis = 300) }
        settle()
        val scrolled = mapFitted.top - mapBounds().top
        assertTrue("the page scrolled up: $scrolled px", scrolled > 60f)
        assertEquals("still unzoomed", "기본 배율", zoomState())
        assertFalse(has(button("전체 보기")))
        // the chip moved with the page only
        assertEquals(chipFitted.left, chipBounds().left, 1f)
        assertEquals(chipFitted.top - scrolled, chipBounds().top, 1f)
    }

    @Test
    fun nationalMap_pinchZooms_dragPansWhenZoomed_swipeScrollsThePageWhenNot() {
        openMap()
        pinchDragAndSwipe(KoreaFrames.national)
    }

    @Test
    fun provinceMap_pinchZooms_dragPansWhenZoomed_swipeScrollsThePageWhenNot() {
        openMap()
        // 경주 (경상북도), well below 안동's chip
        val p = canvasPoint(KoreaFrames.national, 35.84, 129.21)
        node(map).performTouchInput { click(p) }
        settle()
        waitFor(button("← 전국 · 경상북도"))
        waitFor(andong)
        bringIntoView(map)
        settle()
        pinchDragAndSwipe(KoreaFrames.provinceFrame("47")!!)
    }
}
