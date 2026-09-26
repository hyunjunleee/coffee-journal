package com.coffeejournal.ui.map

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.coffeejournal.ui.nav.Feature
import com.coffeejournal.ui.nav.Route
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/** Korea / world maps shared by the roastery map, the café map and the location picker (design v2 §1). */
object MapFeature : Feature {
    override val module: Module = module {
        viewModelOf(::CafeMapViewModel)
        viewModel { (r: Route.MapPicker) -> MapPickerViewModel(r.target, r.name, r.scope, MapPickResult.decode(r.point), get()) }
    }

    override fun NavGraphBuilder.routes(nav: NavHostController) {
        composable<Route.MapPicker> { back ->
            val route = back.toRoute<Route.MapPicker>()
            MapPickerScreen(nav, koinViewModel<MapPickerViewModel> { parametersOf(route) })
        }
    }
}
