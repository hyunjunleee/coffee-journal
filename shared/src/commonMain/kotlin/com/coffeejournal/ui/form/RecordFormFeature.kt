package com.coffeejournal.ui.form

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.coffeejournal.ui.nav.Feature
import com.coffeejournal.ui.nav.Route
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Record form, entry detail and my recipes: Koin module (view models) and full-screen routes. */
object RecordFormFeature : Feature {
    override val module: Module = module {
        // the last get() is the destination's SavedStateHandle, created by Koin from the view model's CreationExtras
        viewModel { (args: FormArgs) -> RecordFormViewModel(args, get(), get(), get(), get(), get(), get(), get()) }
        viewModel { (entryId: String) -> EntryDetailViewModel(entryId, get(), get(), get(), get()) }
        viewModelOf(::MyRecipesViewModel)
    }

    override fun NavGraphBuilder.routes(nav: NavHostController) {
        composable<Route.RecordForm> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.RecordForm>()
            RecordFormScreen(nav, route.mode, route.entryId, route.cuppingType)
        }
        composable<Route.EntryDetail> { backStackEntry ->
            EntryDetailScreen(nav, backStackEntry.toRoute<Route.EntryDetail>().entryId)
        }
        composable<Route.MyRecipes> { MyRecipesScreen(nav) }
    }
}
