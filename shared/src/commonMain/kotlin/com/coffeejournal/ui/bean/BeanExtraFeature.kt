package com.coffeejournal.ui.bean

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import org.koin.core.module.Module
import org.koin.dsl.module

/** Second half of the 원두 tab (map + farms, roasteries, importers, blends): Koin module and routes. */
val beanExtraModule: Module = module { }

fun NavGraphBuilder.beanExtraRoutes(nav: NavHostController) { }
