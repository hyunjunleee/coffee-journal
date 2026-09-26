package com.coffeejournal.android

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.data.backup.BackupService
import com.coffeejournal.ui.settings.DisplayPrefs
import com.coffeejournal.ui.settings.SettingsTexts
import com.coffeejournal.ui.theme.AppHeader
import com.coffeejournal.ui.theme.BodyFont
import com.coffeejournal.ui.theme.Display
import com.coffeejournal.ui.theme.DisplaySettings
import com.coffeejournal.ui.theme.Motion
import com.coffeejournal.ui.theme.NumberFont
import com.coffeejournal.ui.theme.TextSize
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 설정 (not on the web): the small gear in every tab's header opens it (there is no settings tab); its display choices
 * (typeface, header and number typeface, text size, screen transition) apply to the whole app at once and are kept on
 * this phone only; the reminders and the sources moved in from the bottom of the 기타 tab.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SettingsFlowTest : CoverageFlowBase() {
    private val prefs get() = koinGet<DisplayPrefs>()
    private val gear = hasTestTag("open-settings")
    private val header = AppHeader.TITLE

    private fun openSettings() {
        tap(gear)
        waitForText(SettingsTexts.PREVIEW_TITLE)
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.runOnIdle {
            compose.onAllNodes(hasText(text), useUnmergedTree = true)[0].fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        }
        return results.first()
    }

    private fun fontOf(text: String): FontFamily? = layoutOf(text).layoutInput.style.fontFamily

    private fun fontScaleOf(text: String): Float = layoutOf(text).layoutInput.density.fontScale

    /** The drawn size of [text] in px: its sp size through the density it was laid out with. */
    private fun fontPx(text: String): Float = layoutOf(text).layoutInput.let { with(it.density) { it.style.fontSize.toPx() } }

    @Test
    fun theGear_opensSettingsFromEveryTab_andTheMiscTabNoLongerEndsInLinks() {
        launchApp()
        for (tab in listOf("tab-extract", "tab-calendar", "tab-bean", "tab-misc")) {
            tab(tab)
            waitFor(gear)
            val g = node(gear).fetchSemanticsNode()
            assertEquals("labelled for TalkBack", listOf("설정"), g.config[SemanticsProperties.ContentDescription])
            assertTrue("a full touch target", g.size.width >= 44 * g.layoutInfo.density.density - 1)
            openSettings()
            // the sections: display, reminders (moved in), sources (moved in)
            for (label in listOf("화면", "알림", "정보")) assertTrue(label, has(hasText(label)))
            assertTrue(has(hasText("알림 받기") and isToggleable()))
            assertTrue(has(button(SettingsTexts.SOURCES)))
            back()
            waitGone(hasText(SettingsTexts.PREVIEW_TITLE))
        }
        // the bottom bar keeps its four tabs; 설정 is not one of them
        assertEquals(4, count(hasTestTag("tab-extract") or hasTestTag("tab-calendar") or hasTestTag("tab-bean") or hasTestTag("tab-misc")))
        assertFalse(has(hasContentDescription("설정") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)))
        assertFalse(has(button("알림 설정 →")))
        assertFalse(has(button("출처 · 오픈소스 라이선스 →")))
    }

    @Test
    fun aTypeface_appliesEverywhereAtOnce_andIsKeptOnThisPhoneOnly() {
        launchApp()
        openSettings()
        assertEquals(FontFamily.Default, fontOf(SettingsTexts.PREVIEW_BODY))
        assertEquals(FontFamily.Monospace, fontOf(SettingsTexts.PREVIEW_NUMBERS))

        tap(button("명조"))
        waitUntil("serif body") { fontOf(SettingsTexts.PREVIEW_BODY) == FontFamily.Serif }
        assertEquals("headers and numbers stay monospace", FontFamily.Monospace, fontOf(SettingsTexts.PREVIEW_NUMBERS))
        tap(button("본문과 같게"))
        waitUntil("numbers in the body typeface") { fontOf(SettingsTexts.PREVIEW_NUMBERS) == FontFamily.Serif }

        // the rest of the app follows: the home tab header and the tab labels
        back()
        waitFor(hasText(header))
        assertEquals(FontFamily.Serif, fontOf(header))
        assertEquals(FontFamily.Serif, fontOf("[ 새로운 추출 ]"))

        // saved under this phone's own keys, which a backup leaves out
        val expected = DisplaySettings(bodyFont = BodyFont.SERIF, numberFont = NumberFont.BODY)
        waitUntil("saved") { runBlocking { prefs.load() } == expected }
        assertFalse(runBlocking { koinGet<BackupService>().export().json }.contains("display."))

        // back to the defaults
        openSettings()
        tap(button("고딕"))
        tap(button("고정폭"))
        waitUntil("monospace numbers again") { fontOf(SettingsTexts.PREVIEW_NUMBERS) == FontFamily.Monospace }
        assertEquals(FontFamily.Default, fontOf(SettingsTexts.PREVIEW_BODY))
        waitUntil("defaults saved") { runBlocking { prefs.load() } == DisplaySettings() }
    }

    @Test
    fun theTextSize_multipliesThePhonesFontSize() {
        launchApp()
        openSettings()
        val base = fontPx(SettingsTexts.PREVIEW_BODY)
        assertEquals(1f, fontScaleOf(SettingsTexts.PREVIEW_BODY))
        tap(button("더 크게"))
        // laid out as on a phone set to 1.3: small text grows a little more than 1.3 times (Android's non-linear curve)
        waitUntil("font scale 1.3") { fontScaleOf(SettingsTexts.PREVIEW_BODY) == 1.3f }
        assertTrue(fontPx(SettingsTexts.PREVIEW_BODY) > base * 1.29f)
        tap(button("작게"))
        waitUntil("font scale 0.9") { fontScaleOf(SettingsTexts.PREVIEW_BODY) == 0.9f }
        assertEquals(base * 0.9f, fontPx(SettingsTexts.PREVIEW_BODY), 0.5f)
        // the home tab follows
        back()
        waitFor(hasText(header))
        assertEquals(0.9f, fontScaleOf(header))
        tap(gear)
        tap(button("기본"))
        waitUntil("back to 1") { fontScaleOf(SettingsTexts.PREVIEW_BODY) == 1f }
        assertEquals(base, fontPx(SettingsTexts.PREVIEW_BODY), 0.01f)
        waitUntil("saved") { runBlocking { prefs.load() }.textSize == TextSize.NORMAL }
    }

    @Test
    fun theTransitionSpeed_setsHowLongScreensFadeIntoEachOther() {
        launchApp()
        // the new default: 0.4 s (Navigation's own 0.7 s read as a slow blur)
        assertEquals(Motion.NORMAL, Display.current.motion)
        assertEquals(400, Motion.NORMAL.millis)

        openSettings()
        tap(button("느리게"))
        waitUntil("slow") { Display.current.motion == Motion.SLOW }
        back()
        waitGone(hasText(SettingsTexts.PREVIEW_TITLE))

        // 0.7 s: 0.4 s into opening 설정 both screens are still there, fading
        compose.mainClock.autoAdvance = false
        try {
            node(gear).performClick()
            compose.mainClock.advanceTimeBy(400)
            assertTrue(has(hasText(SettingsTexts.PREVIEW_TITLE)))
            assertTrue(has(hasText(header)))
            compose.mainClock.advanceTimeBy(500)
            assertFalse("the home tab is gone once the fade ends", has(hasText(header)))
        } finally {
            compose.mainClock.autoAdvance = true
        }

        // off: the next screen replaces the last at once
        tap(button("끔"))
        waitUntil("off") { Display.current.motion == Motion.OFF }
        compose.mainClock.autoAdvance = false
        try {
            node(hasContentDescription("뒤로")).performClick()
            compose.mainClock.advanceTimeBy(50)
            assertTrue(has(hasText(header)))
            assertFalse(has(hasText(SettingsTexts.PREVIEW_TITLE)))
        } finally {
            compose.mainClock.autoAdvance = true
        }
        waitUntil("saved") { runBlocking { prefs.load() }.motion == Motion.OFF }
    }
}
