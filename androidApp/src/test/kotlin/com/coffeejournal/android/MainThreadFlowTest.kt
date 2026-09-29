package com.coffeejournal.android

import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.App
import com.coffeejournal.data.db.AppDatabase
import com.coffeejournal.data.db.SettingsDao
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.ui.settings.DisplayPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * In UI tests the composition's own coroutines do not dispatch: they go on on whichever thread resumes them. A value
 * that reaches one from a background thread then recomposes the app on that thread, where Android refuses the layout
 * request that follows ("Only the original thread that created a view hierarchy can touch its views") and two threads
 * composing at once break the slot table — once a random failure of MoreFlowTest.more11. The app takes such values on
 * the main thread; here the display settings (read on a background dispatcher) are held back and let go while the main
 * thread is busy, the moment a background recomposition would happen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MainThreadFlowTest : FlowTestBase() {

    @Test
    fun displaySettingsReadInTheBackground_recomposeTheAppOnTheMainThread() {
        val gate = CompletableDeferred<Unit>()
        val real = koinGet<AppDatabase>().settingsDao()
        val held = object : SettingsDao by real {
            override fun observe(key: String): Flow<String?> = flow { gate.await(); emit(null) }
        }
        loadKoinModules(module { single { DisplayPrefs(SettingsRepository(held)) } })
        compose.setContent { App() }
        settle()
        assertFalse("only the page colour until the settings are read", has(hasTestTag("tab-calendar")))

        gate.complete(Unit)
        // the settings arrive on a background thread while the main thread is not looking
        Thread.sleep(500)
        waitFor(hasTestTag("tab-calendar"), "the app once the settings are read")
    }
}
