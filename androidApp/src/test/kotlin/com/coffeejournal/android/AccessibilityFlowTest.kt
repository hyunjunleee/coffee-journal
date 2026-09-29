package com.coffeejournal.android

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.RoadmapRepository
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.RoadmapItem
import com.coffeejournal.domain.reference.RoadmapDefaults
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.form.sections.FlavorWheelTexts
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.number
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gap #9: what TalkBack gets from the roadmap check boxes, the calendar squares, the canvas views (world map, roastery
 * map, flavor wheels) and the blend name suggestions.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class AccessibilityFlowTest : FlowTestBase() {

    private fun role(m: SemanticsMatcher): Role? = node(m).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)
    private fun described(text: String, substring: Boolean = false) = hasContentDescription(text, substring = substring)

    @Test
    fun roadmapCheckbox_isAToggleWithItsItemAsLabel() {
        runBlocking {
            val repo = koinGet<RoadmapRepository>()
            repo.ensureSeeded()
            repo.upsert(repo.getAll().single().copy(items = listOf(RoadmapItem("i1", "커핑 5회"), RoadmapItem("i2", "핸드드립 30잔", done = true))))
        }
        launchApp()
        tab("tab-calendar")
        val phaseHeader = hasText(RoadmapDefaults.STARTER_TITLE) and hasClickAction()
        scrollListTo(hasText("토"), phaseHeader)
        clickNode(phaseHeader)
        val first = described("커핑 5회") and isToggleable()
        scrollListTo(hasText("토"), first)
        waitFor(first)
        assertEquals(Role.Checkbox, role(first))
        node(first).assertIsOff()
        node(described("핸드드립 30잔") and isToggleable()).assertIsOn()
        val bounds = node(first).fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("48 dp tap target", bounds.width / density >= 47.5f && bounds.height / density >= 47.5f)

        clickNode(first)
        waitUntil("stored as done") { runBlocking { koinGet<RoadmapRepository>().getAll().single().items.first().done } }
        waitUntil("shown as checked") { runCatching { node(first).assertIsOn() }.isSuccess }
    }

    @Test
    fun calendarSquares_readDateAndCount_andOnlyDaysWithRecordsAreButtons() {
        // two records on a day of the month the calendar opens on (the real current month), nothing on another day
        val today = Dates.today()
        val busy = if (today.day > 2) Dates.plusDays(today, -1) else Dates.plusDays(today, 1)
        val empty = if (today.day > 2) Dates.plusDays(today, -2) else Dates.plusDays(today, 2)
        runBlocking {
            val entries = koinGet<EntryRepository>()
            entries.upsert(Entry(id = "x1", createdAt = Dates.toMillis(busy, 8, 0), name = "케냐 AA"))
            entries.upsert(Entry(id = "x2", createdAt = Dates.toMillis(busy, 15, 0), name = "케냐 AA"))
        }
        launchApp()
        tab("tab-calendar")
        val m = today.month.number
        val busyCell = described("${m}월 ${busy.day}일, 기록 2개")
        waitFor(busyCell)
        node(busyCell).assertHasClickAction()
        assertEquals(Role.Button, role(busyCell))
        compose.onNode(described("${m}월 ${empty.day}일")).assertHasNoClickAction()
        waitFor(described("${m}월 ${today.day}일, 오늘"), "today's square says so")

        clickNode(busyCell)
        waitForText("케냐 AA", substring = true) // the day panel lists both records
    }

    @Test
    fun canvasViews_haveLabelsAndReachableAlternatives() {
        SampleData.seed()
        launchApp()
        tab("tab-bean")
        // world map: a label naming the tasted countries, and the country list below it as buttons with a state
        val map = described("커피 지도. 마셔본 나라", substring = true)
        waitFor(map)
        val label = node(map).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(label, label.contains("에티오피아") && label.contains("콜롬비아") && label.contains("'경험해본 산지' 목록"))
        val country = hasText("🇪🇹 에티오피아", substring = true) and hasClickAction()
        waitFor(country)
        node(country).performScrollTo()
        assertEquals(Role.Button, role(country))
        assertEquals("접힘", node(country).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        clickNode(country)
        assertEquals("펼쳐짐", node(country).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))

        // roastery map: a label with the counts; each pin is a button with its selected state
        clickNode(hasText("로스터리") and hasClickAction())
        waitFor(described("한국 로스터리 지도. 로스터리 1곳 표시", substring = true))
        val pin = hasContentDescription("커피 리브레, ", substring = true) and hasClickAction()
        waitFor(pin)
        assertEquals(Role.Button, role(pin))
        clickNode(pin)
        node(pin).assertIsSelected()

        // bean notes: the flavor wheel ring is labelled with its families
        clickNode(hasText("커피 노트") and hasClickAction())
        waitFor(described("플레이버 휠 9개 계열", substring = true))
    }

    @Test
    fun recordFormFlavorWheel_ringIsLabelled_descriptorsAreCheckboxes() {
        launchApp()
        clickText("+ 새 기록 추가")
        waitForText("새 기록")
        val header = hasText("🎨 SCA Coffee Taster's Flavor Wheel") and hasClickAction()
        clickNode(header)
        assertEquals("펼쳐짐", node(header).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        waitFor(described("플레이버 휠", substring = true))
        val jasmine = hasText("Jasmine") and hasClickAction()
        waitFor(jasmine)
        assertEquals(Role.Checkbox, role(jasmine))
        node(jasmine).performScrollTo().assertIsOff()
        clickNode(jasmine)
        node(jasmine).assertIsOn()
        // the app's unofficial notes are check boxes too, and say so
        val apricot = described(FlavorWheelTexts.unofficial("Apricot")) and isToggleable()
        waitFor(apricot)
        assertEquals(Role.Checkbox, role(apricot))
        node(apricot).performScrollTo().assertIsOff()
    }

    @Test
    fun blendNameSuggestions_areButtons() {
        SampleData.seed()
        launchApp()
        tab("tab-bean")
        clickNode(hasText("블렌드") and hasClickAction())
        clickText("+ 블렌드 기록 추가")
        waitForText("블렌드 기록 추가")
        clickNode(hasSetTextAction() and hasText("원두 이름 (최근 마신 것부터 추천)"))
        val suggestion = hasText("에티오피아 예가체프 워카 첼베사") and hasClickAction()
        waitFor(suggestion)
        assertEquals(Role.Button, role(suggestion))
        assertFalse("not an on/off choice", node(suggestion).fetchSemanticsNode().config.contains(SemanticsProperties.ToggleableState))
    }
}
