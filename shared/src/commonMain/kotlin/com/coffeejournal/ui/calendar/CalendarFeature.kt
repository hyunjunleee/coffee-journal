package com.coffeejournal.ui.calendar

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.coffeejournal.ui.calendar.forms.BookFormScreen
import com.coffeejournal.ui.calendar.forms.BookFormViewModel
import com.coffeejournal.ui.calendar.forms.ClassFormScreen
import com.coffeejournal.ui.calendar.forms.ClassFormViewModel
import com.coffeejournal.ui.calendar.forms.VideoFormScreen
import com.coffeejournal.ui.calendar.forms.VideoFormViewModel
import com.coffeejournal.ui.nav.Feature
import com.coffeejournal.ui.nav.Route
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** 커피 달력 tab: calendar, study (books/videos), classes and the roadmap, plus the three study forms. */
object CalendarFeature : Feature {
    override val module = module {
        viewModelOf(::CalendarViewModel)
        viewModelOf(::StudyViewModel)
        viewModelOf(::ClassesViewModel)
        // the last get() is the destination's SavedStateHandle (typed input survives process death)
        viewModel { (id: String?) -> BookFormViewModel(id, get(), get()) }
        viewModel { (id: String?) -> VideoFormViewModel(id, get(), get()) }
        viewModel { (id: String?) -> ClassFormViewModel(id, get(), get()) }
    }

    override fun NavGraphBuilder.routes(nav: NavHostController) {
        composable<Route.BookForm> { entry -> BookFormScreen(nav, entry.toRoute<Route.BookForm>().bookId) }
        composable<Route.VideoForm> { entry -> VideoFormScreen(nav, entry.toRoute<Route.VideoForm>().videoId) }
        composable<Route.ClassForm> { entry -> ClassFormScreen(nav, entry.toRoute<Route.ClassForm>().classId) }
    }
}
