package com.coffeejournal.ui.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import org.koin.core.module.Module
import org.koin.dsl.module

/** A feature package contributes a Koin module (view models) and full-screen routes. */
interface Feature {
    val module: Module get() = module { }
    fun NavGraphBuilder.routes(nav: NavHostController) {}
}
