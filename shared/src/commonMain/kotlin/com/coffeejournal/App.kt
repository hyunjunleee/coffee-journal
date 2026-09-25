package com.coffeejournal

import androidx.compose.runtime.Composable
import com.coffeejournal.ui.nav.AppNav
import com.coffeejournal.ui.theme.CoffeeJournalTheme

@Composable
fun App() {
    CoffeeJournalTheme { AppNav() }
}
