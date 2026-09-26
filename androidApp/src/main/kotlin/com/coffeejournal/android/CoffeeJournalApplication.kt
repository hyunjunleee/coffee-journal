package com.coffeejournal.android

import android.app.Application
import com.coffeejournal.android.widget.CoffeeWidgets
import com.coffeejournal.android.widget.GlanceHomeWidgets
import com.coffeejournal.di.AppScope
import com.coffeejournal.di.initKoin
import com.coffeejournal.ui.notify.HomeWidgetSync
import com.coffeejournal.ui.notify.HomeWidgets
import com.coffeejournal.ui.notify.ReminderSchedule
import com.coffeejournal.ui.platform.installUrlOpener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

class CoffeeJournalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installUrlOpener(this)
        initKoin {
            androidContext(this@CoffeeJournalApplication)
            modules(module { single<HomeWidgets> { GlanceHomeWidgets(androidContext()) } })
        }
        val koin = GlobalContext.get()
        val scope = koin.get<AppScope>()
        // the daily reminder check follows the saved settings (also after an update or a phone transfer)
        scope.launch { quietly { ReminderSchedule.sync(koin.get(), koin.get()) } }
        scope.launch { quietly { CoffeeWidgets.syncMidnightRefresh(this@CoffeeJournalApplication) } }
        // redraw the home-screen widget after each save while the app runs
        scope.launch { quietly { HomeWidgetSync.run(koin.get(), koin.get()) } }
    }

    /** Background upkeep must never take the app down; the next start tries again. */
    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // nothing to show: a missing schedule or a stale widget is fixed at the next start or save
        }
    }
}
