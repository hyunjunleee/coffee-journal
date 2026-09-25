package com.coffeejournal.ui.misc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote
import com.coffeejournal.ui.theme.TopHeader

@Composable
fun MiscTabScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        TopHeader(title = "coffee_journal / 2026", tagline = "[ personal coffee archive ]", right = null)
        EmptyNote("준비 중", Modifier.padding(Dimens.gutter))
    }
}
