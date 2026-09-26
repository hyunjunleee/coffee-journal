package com.coffeejournal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.coffeejournal.ui.nav.AppNav
import com.coffeejournal.ui.settings.DisplayPrefs
import com.coffeejournal.ui.theme.CoffeeJournalTheme
import com.coffeejournal.ui.theme.Display
import com.coffeejournal.ui.theme.Ink
import org.koin.compose.koinInject

@Composable
fun App() {
    val prefs = koinInject<DisplayPrefs>()
    val saved by remember(prefs) { prefs.observe() }.collectAsState(initial = null)
    // only the page colour until the saved display settings (설정 › 화면) are read: otherwise the first frame would show
    // the default typeface and size, then jump
    val display = saved
    if (display == null) {
        Box(Modifier.fillMaxSize().background(Ink.bg))
        return
    }
    // set before the theme and the screens read it, in this same composition (App itself never reads it)
    remember(display) { Display.current = display }
    CoffeeJournalTheme { AppNav() }
}
