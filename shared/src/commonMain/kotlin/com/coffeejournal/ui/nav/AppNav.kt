package com.coffeejournal.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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

data class TabSpec(val route: Route, val label: String, val icon: ImageVector, val routeName: String)

val tabs = listOf(
    TabSpec(Route.Extract, "새로운 추출", AppIcons.cup, Route.Extract::class.qualifiedName!!),
    TabSpec(Route.Calendar, "커피 달력", AppIcons.calendar, Route.Calendar::class.qualifiedName!!),
    TabSpec(Route.Bean, "원두", AppIcons.bean, Route.Bean::class.qualifiedName!!),
    TabSpec(Route.Misc, "기타", AppIcons.misc, Route.Misc::class.qualifiedName!!),
)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val onTab = tabs.any { t -> currentRoute?.startsWith(t.routeName) == true }

    Scaffold(
        containerColor = Ink.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (onTab) BottomBar(currentRoute) { route -> nav.navigateTab(route) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).windowInsetsPadding(WindowInsets.statusBars)) {
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
private fun BottomBar(currentRoute: String?, onSelect: (Route) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ink.bg)) {
        Hairline(color = Ink.text, thickness = Dimens.rule)
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
            tabs.forEach { tab ->
                val selected = currentRoute?.startsWith(tab.routeName) == true
                Column(
                    Modifier.weight(1f).clickable { onSelect(tab.route) }.padding(top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = if (selected) Ink.text else Ink.textFaint, modifier = Modifier.size(19.dp))
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
