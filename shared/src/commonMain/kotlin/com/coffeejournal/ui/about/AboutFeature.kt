package com.coffeejournal.ui.about

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.coffeejournal.ui.nav.Feature
import com.coffeejournal.ui.nav.Route

/** 출처 · 라이선스 page, opened from the bottom of the 기타 tab. */
object AboutFeature : Feature {
    override fun NavGraphBuilder.routes(nav: NavHostController) {
        composable<Route.About> { AboutScreen(nav) }
    }
}
