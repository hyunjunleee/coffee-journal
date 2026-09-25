package com.coffeejournal.ui.misc

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.coffeejournal.ui.nav.Feature
import com.coffeejournal.ui.nav.Route
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** 기타(장비) tab: equipment list by type plus the add/edit form. */
object MiscFeature : Feature {
    override val module = module {
        viewModelOf(::MiscViewModel)
        viewModel { (type: String, itemId: String?) -> MiscFormViewModel(type, itemId, get(), get()) }
    }

    override fun NavGraphBuilder.routes(nav: NavHostController) {
        composable<Route.MiscForm> { entry ->
            val route = entry.toRoute<Route.MiscForm>()
            MiscFormScreen(nav, type = route.type, itemId = route.itemId)
        }
    }
}
