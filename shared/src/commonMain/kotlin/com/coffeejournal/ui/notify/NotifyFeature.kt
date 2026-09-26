package com.coffeejournal.ui.notify

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.coffeejournal.ui.nav.Feature
import com.coffeejournal.ui.nav.LaunchRequests
import com.coffeejournal.ui.nav.Route
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Reminders (feature plan v2 §3) and the pieces the home-screen widget shares with them: the settings screen, the
 * daily check, the widget's data and the app-wide launch requests that notifications and the widget send.
 */
object NotifyFeature : Feature {
    override val module = module {
        single { LaunchRequests() }
        single { ReminderPrefs(get()) }
        single { ReminderCheck(get(), get(), get(), get(), get()) }
        single { HomeWidgetFeed(get(), get(), get(), get(), get()) }
        viewModelOf(::ReminderSettingsViewModel)
    }

    override fun NavGraphBuilder.routes(nav: NavHostController) {
        composable<Route.NotificationSettings> { ReminderSettingsScreen(nav) }
    }
}
