package com.coffeejournal.ui.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.coffeejournal.ui.bean.BeanTabScreen
import com.coffeejournal.ui.calendar.CalendarTabScreen
import com.coffeejournal.ui.extract.ExtractTabScreen
import com.coffeejournal.ui.misc.MiscTabScreen

/** Tab roots plus every feature's full-screen routes. */
fun NavGraphBuilder.appGraph(nav: NavHostController) {
    composable<Route.Extract> { ExtractTabScreen(nav) }
    composable<Route.Calendar> { CalendarTabScreen(nav) }
    composable<Route.Bean> { BeanTabScreen(nav) }
    composable<Route.Misc> { MiscTabScreen(nav) }
    Features.all.forEach { feature -> with(feature) { routes(nav) } }
}
