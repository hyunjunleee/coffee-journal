package com.coffeejournal.ui.map

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.coffeejournal.ui.map.detail.DetailMapRenderer
import com.coffeejournal.ui.map.detail.DetailMapScreen
import com.coffeejournal.ui.map.detail.DetailMapViewModel
import com.coffeejournal.ui.map.detail.platformDetailMapRenderer
import com.coffeejournal.ui.nav.Feature
import com.coffeejournal.ui.nav.Route
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/** Korea / world maps shared by the roastery map, the café map and the location picker, and the detail map (design v2 §1). */
object MapFeature : Feature {
    override val module: Module = module {
        viewModelOf(::CafeMapViewModel)
        viewModel { (r: Route.MapPicker) -> MapPickerViewModel(r.target, r.name, r.scope, MapPickResult.decode(r.point), get()) }
        viewModel { (r: Route.DetailMap) -> DetailMapViewModel(r, get(), get(), get()) }
        // the detail map's renderer (MapLibre Native on Android and iOS); the flow tests put a fake in its place
        single<DetailMapRenderer> { platformDetailMapRenderer() }
    }

    override fun NavGraphBuilder.routes(nav: NavHostController) {
        composable<Route.MapPicker> { back ->
            val route = back.toRoute<Route.MapPicker>()
            MapPickerScreen(nav, koinViewModel<MapPickerViewModel> { parametersOf(route) }, results = back.savedStateHandle)
        }
        composable<Route.DetailMap> { back ->
            val route = back.toRoute<Route.DetailMap>()
            DetailMapScreen(nav, koinViewModel<DetailMapViewModel> { parametersOf(route) })
        }
    }
}
