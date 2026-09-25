package com.coffeejournal.ui.bean

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.coffeejournal.ui.bean.a.NoteDetailScreen
import com.coffeejournal.ui.bean.a.ProcessDetailScreen
import com.coffeejournal.ui.bean.a.ProcessMiscViewModel
import com.coffeejournal.ui.bean.a.VarietyDetailScreen
import com.coffeejournal.ui.nav.Feature
import com.coffeejournal.ui.nav.Route
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** 원두 tab shell + notes/process/roast/variety/specialty views; delegates the rest to BeanExtraFeature. */
object BeanFeature : Feature {
    override val module: Module = module {
        includes(beanExtraModule)
        viewModelOf(::BeanViewModel)
        viewModelOf(::ProcessMiscViewModel)
    }

    override fun NavGraphBuilder.routes(nav: NavHostController) {
        beanExtraRoutes(nav)
        composable<Route.NoteDetail> { entry ->
            val route = entry.toRoute<Route.NoteDetail>()
            NoteDetailScreen(nav, kind = route.kind, noteKey = route.noteKey)
        }
        composable<Route.VarietyDetail> { entry ->
            val route = entry.toRoute<Route.VarietyDetail>()
            VarietyDetailScreen(nav, varietyKey = route.varietyKey)
        }
        composable<Route.ProcessDetail> { entry ->
            val route = entry.toRoute<Route.ProcessDetail>()
            ProcessDetailScreen(nav, name = route.name, seg = route.seg)
        }
    }
}
