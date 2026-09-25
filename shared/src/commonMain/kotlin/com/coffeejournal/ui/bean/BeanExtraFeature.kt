package com.coffeejournal.ui.bean

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.coffeejournal.ui.bean.b.BeanExtraDataViewModel
import com.coffeejournal.ui.bean.b.BlendFormScreen
import com.coffeejournal.ui.bean.b.BlendFormViewModel
import com.coffeejournal.ui.bean.b.BlendsViewModel
import com.coffeejournal.ui.bean.b.CountryDetailScreen
import com.coffeejournal.ui.bean.b.FlatItemFormScreen
import com.coffeejournal.ui.bean.b.FlatItemFormViewModel
import com.coffeejournal.ui.bean.b.MiscItemsViewModel
import com.coffeejournal.ui.bean.b.RoasteryDetailScreen
import com.coffeejournal.ui.nav.Route
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Second half of the 원두 tab (map + farms, roasteries, importers, blends): Koin module and routes. */
val beanExtraModule: Module = module {
    viewModelOf(::MiscItemsViewModel)
    viewModelOf(::BlendsViewModel)
    viewModelOf(::BeanExtraDataViewModel)
    viewModel { (type: String, itemId: String?) -> FlatItemFormViewModel(type, itemId, get()) }
    viewModel { (blendId: String?) -> BlendFormViewModel(blendId, get(), get()) }
}

fun NavGraphBuilder.beanExtraRoutes(nav: NavHostController) {
    composable<Route.FlatItemForm> { back ->
        val r = back.toRoute<Route.FlatItemForm>()
        FlatItemFormScreen(nav, r.type, r.itemId)
    }
    composable<Route.BlendForm> { back -> BlendFormScreen(nav, back.toRoute<Route.BlendForm>().blendId) }
    composable<Route.CountryDetail> { back -> CountryDetailScreen(nav, back.toRoute<Route.CountryDetail>().en) }
    composable<Route.RoasteryDetail> { back -> RoasteryDetailScreen(nav, back.toRoute<Route.RoasteryDetail>().name) }
}
