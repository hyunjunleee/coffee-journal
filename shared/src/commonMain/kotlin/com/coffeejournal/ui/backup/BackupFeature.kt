package com.coffeejournal.ui.backup

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.coffeejournal.data.backup.BackupCodec
import com.coffeejournal.data.backup.BackupService
import com.coffeejournal.data.backup.RestoreRunner
import com.coffeejournal.di.AppScope
import com.coffeejournal.ui.nav.Feature
import com.coffeejournal.ui.nav.Route
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Backup / restore: web-compatible JSON export and import plus the platform file pickers. */
object BackupFeature : Feature {
    override val module = module {
        single { BackupCodec() }
        single { BackupService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        single { RestoreRunner(get(), get<AppScope>()) }
        viewModelOf(::BackupViewModel)
    }

    override fun NavGraphBuilder.routes(nav: NavHostController) {
        composable<Route.Backup> { BackupScreen(nav) }
    }
}
