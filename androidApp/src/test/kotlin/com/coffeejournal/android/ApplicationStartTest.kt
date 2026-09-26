package com.coffeejournal.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.android.widget.GlanceHomeWidgets
import com.coffeejournal.ui.nav.LaunchRequests
import com.coffeejournal.ui.notify.HomeWidgets
import com.coffeejournal.ui.notify.AndroidReminderPlatform
import com.coffeejournal.ui.notify.ReminderPlatform
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real application start: Koin with the production modules plus the widget hook, and the background upkeep
 * (reminder schedule sync, midnight widget refresh, the widget observer) never takes the app down, even where
 * WorkManager is not initialised (as here, on the test JVM).
 */
@RunWith(AndroidJUnit4::class)
// the same sandbox as ProductionStackBackupTest: the real app opens the bundled SQLite, whose native library loads
// into one class loader only
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = CoffeeJournalApplication::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ApplicationStartTest {
    @After
    fun tearDown() = stopKoin()

    @Test
    fun startsKoin_withTheWidgetHookAndTheReminderPlatform() {
        val koin = GlobalContext.get()
        assertTrue(koin.get<HomeWidgets>() is GlanceHomeWidgets)
        assertTrue(koin.get<ReminderPlatform>() is AndroidReminderPlatform)
        // the launch requests MainActivity feeds are there before any screen
        koin.get<LaunchRequests>()
        // the upkeep coroutines have had time to fail quietly if they were going to
        Thread.sleep(500)
    }
}
