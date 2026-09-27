package com.coffeejournal.ui.ai

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.coffeejournal.ui.nav.Feature
import com.coffeejournal.ui.nav.Route
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * AI 노트 도우미 (docs/ai-note-helper-plan.md, 구현): the answer screen, its section in 설정 and the services behind
 * them. The platform module binds [AiHttp] and [SecretStore]; the app ships no key.
 */
object AiFeature : Feature {
    override val module = module {
        single { AiPrefs(get()) }
        single { AiSettingsFocus() }
        single { NoteHelperService(get(), get()) }
        viewModelOf(::AiSettingsViewModel)
        viewModel { (args: NoteHelperArgs) -> NoteHelperViewModel(args, get(), get()) }
    }

    override fun NavGraphBuilder.routes(nav: NavHostController) {
        composable<Route.NoteHelper> { entry -> NoteHelperScreen(nav, entry.toRoute<Route.NoteHelper>()) }
    }
}
