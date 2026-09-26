package com.coffeejournal.android

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.ui.nav.LaunchRequests
import com.coffeejournal.ui.nav.LaunchTarget
import com.coffeejournal.ui.notify.LaunchIntents
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The pantry screen's title ("원두 보관함 · 2봉"); the home tab's button alone says "원두 보관함". */
private const val PANTRY_TITLE = "원두 보관함 · "

/** Notification and widget taps: the app-wide launch request is taken once by the nav host and opens its screen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class LaunchTargetFlowTest : CoverageFlowBase() {
    private fun request(target: LaunchTarget) {
        compose.runOnUiThread { koinGet<LaunchRequests>().request(target) }
        settle()
    }

    @Test
    fun eachTarget_opensItsScreen_fromAnywhere() {
        SampleData.seed()
        launchApp()
        // a reminder about a bag → the pantry, with home under it
        request(LaunchTarget.PANTRY)
        waitForText(PANTRY_TITLE, substring = true)
        assertNull("taken once", koinGet<LaunchRequests>().pending.value)
        back()
        waitForText("+ 새 기록 추가")

        // the widget's "+ 새 기록" from another tab → the new-record form
        tab("tab-calendar")
        request(LaunchTarget.NEW_RECORD)
        waitForText("새 기록")
        back()
        waitForText("+ 새 기록 추가")

        // the D-day milestone from a detail screen on another tab → the home tab itself
        tab("tab-misc")
        request(LaunchTarget.HOME)
        waitForText("+ 새 기록 추가")
        waitForText("Coffee D-", substring = true)
    }
}

/** The same through MainActivity's intents: a cold start from the widget, and a notification while the app runs. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class LaunchIntentTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = startTestKoin(context)

    @After
    fun tearDown() = stopKoin()

    private fun waitForText(text: String, substring: Boolean = false) {
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            compose.waitForIdle()
            if (compose.onAllNodes(hasText(text, substring)).fetchSemanticsNodes().isNotEmpty()) return
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for '$text'")
            Thread.sleep(60)
        }
    }

    @Test
    fun widgetNewRecord_coldStartsIntoTheRecordForm() {
        val intent = LaunchIntents.open(context, LaunchTarget.NEW_RECORD)
        assertEquals("the launcher activity", MainActivity::class.java.name, intent.component?.className)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            waitForText("새 기록")
            waitForText("레시피로 시작", substring = true)
            scenario.onActivity { activity ->
                // taken once: a recreated activity does not open another form
                assertNull(LaunchIntents.targetOf(activity.intent))
                assertNull(GlobalContext.get().get<LaunchRequests>().pending.value)
            }
        }
    }

    @Test
    fun notificationTap_whileTheAppRuns_opensThePantry() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, Intent(context, MainActivity::class.java)).setup()
        waitForText("+ 새 기록 추가")
        controller.newIntent(LaunchIntents.open(context, LaunchTarget.PANTRY))
        waitForText(PANTRY_TITLE, substring = true)
        controller.pause().stop().destroy()
    }

    @Test
    fun reopenedFromRecents_doesNotRepeatTheOldTarget() {
        val intent = LaunchIntents.open(context, LaunchTarget.PANTRY).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        ActivityScenario.launch<MainActivity>(intent).use {
            waitForText("+ 새 기록 추가")
            assertNull(GlobalContext.get().get<LaunchRequests>().pending.value)
            assertEquals(0, compose.onAllNodes(hasText(PANTRY_TITLE, substring = true)).fetchSemanticsNodes().size)
        }
    }
}
