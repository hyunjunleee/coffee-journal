package com.coffeejournal.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coffeejournal.ui.theme.AppIcons
import com.coffeejournal.ui.theme.AppType
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.Hairline
import com.coffeejournal.ui.theme.Ink

data class TabSpec(val route: Route, val label: String, val icon: ImageVector, val tag: String)

val tabs = listOf(
    TabSpec(Route.Extract, "새로운 추출", AppIcons.cup, "tab-extract"),
    TabSpec(Route.Calendar, "커피 달력", AppIcons.calendar, "tab-calendar"),
    TabSpec(Route.Bean, "원두", AppIcons.bean, "tab-bean"),
    TabSpec(Route.Misc, "기타", AppIcons.misc, "tab-misc"),
)

/**
 * True when [destination] is this tab's root route itself. Route names are not compared as strings: MiscForm's
 * route "…Route.MiscForm/{type}…" starts with the 기타 tab's "…Route.Misc", but it is a full-screen page.
 */
fun TabSpec.isShowing(destination: NavDestination?): Boolean =
    destination?.hierarchy?.any { it.hasRoute(route::class) } == true

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTab = tabs.firstOrNull { it.isShowing(destination) }

    Scaffold(
        containerColor = Ink.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (currentTab != null) BottomBar(currentTab) { route -> nav.navigateTab(route) } },
    ) { padding ->
        // top: status bar / cutout; left and right: a landscape navigation bar or display cutout
        Box(Modifier.fillMaxSize().padding(padding).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))) {
            NavHost(navController = nav, startDestination = Route.Extract) {
                appGraph(nav)
            }
        }
    }
}

fun NavHostController.navigateTab(route: Route) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(current: TabSpec, onSelect: (Route) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ink.bg)) {
        Hairline(color = Ink.text, thickness = Dimens.rule)
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)))) {
            tabs.forEach { tab ->
                val selected = tab == current
                Column(
                    Modifier
                        .weight(1f)
                        .testTag(tab.tag)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.route) })
                        // read once as "기타" (not the icon label plus the bracketed text), with the selected state
                        .semantics { contentDescription = tab.label }
                        .padding(top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = if (selected) Ink.text else Ink.textFaint, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (selected) "[ ${tab.label} ]" else tab.label,
                        style = AppType.monoSmall.copy(color = if (selected) Ink.text else Ink.textFaint),
                    )
                }
            }
        }
    }
}
