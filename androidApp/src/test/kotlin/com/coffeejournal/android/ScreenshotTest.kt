package com.coffeejournal.android

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.App
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real app on the JVM (Robolectric) and stores PNGs under androidApp/screenshots.
 * Run: ./gradlew :androidApp:recordRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun setUp() {
        startTestKoin(ApplicationProvider.getApplicationContext())
        SampleData.seed()
    }

    /** Room flows run on background dispatchers; give them real time to emit before capturing. */
    private fun settle() {
        repeat(6) { Thread.sleep(250); compose.waitForIdle() }
    }

    /**
     * Switches tabs through the click action itself: a simulated touch leaves the tapped tab's press/hover layer on
     * the captured frame under Robolectric, which a person's tap on a device does not.
     */
    private fun openTab(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    @Test
    fun tabs_render() {
        compose.setContent { App() }
        settle()
        compose.onRoot().captureRoboImage("screenshots/01-home.png")
        openTab("tab-calendar")
        compose.onRoot().captureRoboImage("screenshots/02-calendar.png")
        openTab("tab-bean")
        compose.onRoot().captureRoboImage("screenshots/03-bean.png")
        openTab("tab-misc")
        compose.onRoot().captureRoboImage("screenshots/04-misc.png")
    }
}
