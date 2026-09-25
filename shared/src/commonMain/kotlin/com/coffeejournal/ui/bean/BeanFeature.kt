package com.coffeejournal.ui.bean

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.coffeejournal.ui.nav.Feature
import org.koin.core.module.Module
import org.koin.dsl.module

/** 원두 tab shell + notes/process/roast/variety/specialty views; delegates the rest to BeanExtraFeature. */
object BeanFeature : Feature {
    override val module: Module = module { includes(beanExtraModule) }
    override fun NavGraphBuilder.routes(nav: NavHostController) { beanExtraRoutes(nav) }
}
