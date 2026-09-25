package com.coffeejournal.android

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
    }

    @Test
    fun tabs_render() {
        compose.setContent { App() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/01-home.png")
        compose.onNodeWithText("커피 달력").performClick(); compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/02-calendar.png")
        compose.onNodeWithText("원두").performClick(); compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/03-bean.png")
        compose.onNodeWithText("기타").performClick(); compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/04-misc.png")
    }
}
