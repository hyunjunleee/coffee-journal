package com.coffeejournal.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.koinInject

/** A screen the app is asked to open from outside: a reminder notification or the home-screen widget. */
enum class LaunchTarget(val id: String) {
    /** 새로운 추출 tab root (the D-day pill). */
    HOME("home"),

    /** 원두 보관함 (a bag's peak, a bean running low). */
    PANTRY("pantry"),

    /** The new-record chooser (the widget's "+ 새 기록"), as the home tab's "+ 새 기록 추가" opens it. */
    NEW_RECORD("new-record");

    companion object {
        fun of(id: String?): LaunchTarget? = entries.firstOrNull { it.id == id }
    }
}

/**
 * App-wide pending launch request (Koin single), like BeanViewRequests: the activity puts the target of the intent it
 * was started with here, and the nav host takes it once and navigates ([LaunchRequestHandler]).
 */
class LaunchRequests {
    private val _pending = MutableStateFlow<LaunchTarget?>(null)
    val pending: StateFlow<LaunchTarget?> = _pending.asStateFlow()

    fun request(target: LaunchTarget) {
        _pending.value = target
    }

    fun clear() {
        _pending.value = null
    }
}

/** Placed next to the NavHost: opens each requested target once the graph is set. */
@Composable
fun LaunchRequestHandler(nav: NavHostController) {
    val requests = koinInject<LaunchRequests>()
    val pending by requests.pending.collectAsState()
    LaunchedEffect(pending) {
        val target = pending ?: return@LaunchedEffect
        requests.clear()
        nav.openLaunchTarget(target)
    }
}

/**
 * Home tab root first (a screen left open on the home tab is closed, like the calendar's jump to 블렌드), then the
 * target on top of it, so back always returns to home.
 */
fun NavHostController.openLaunchTarget(target: LaunchTarget) {
    navigateTab(Route.Extract)
    popBackStack<Route.Extract>(inclusive = false)
    when (target) {
        LaunchTarget.HOME -> Unit
        LaunchTarget.PANTRY -> navigate(Route.Pantry) { launchSingleTop = true }
        LaunchTarget.NEW_RECORD -> navigate(Route.NewRecord) { launchSingleTop = true }
    }
}
