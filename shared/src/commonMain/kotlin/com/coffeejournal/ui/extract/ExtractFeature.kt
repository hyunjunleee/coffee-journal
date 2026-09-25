package com.coffeejournal.ui.extract

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.coffeejournal.ui.nav.Feature
import com.coffeejournal.ui.nav.Route
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Home tab (새로운 추출) plus the bean pantry screens. */
object ExtractFeature : Feature {
    override val module = module {
        viewModel { ExtractViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { PantryViewModel(get()) }
        viewModel { (itemId: String) -> PantryEditorViewModel(itemId.ifBlank { null }, get()) }
    }

    override fun NavGraphBuilder.routes(nav: NavHostController) {
        composable<Route.Pantry> { PantryScreen(nav) }
        composable<Route.PantryEditor> { back -> PantryEditorScreen(nav, back.toRoute<Route.PantryEditor>().itemId) }
    }
}
