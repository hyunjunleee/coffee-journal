package com.coffeejournal.ui.bean

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.coffeejournal.ui.theme.Dimens
import com.coffeejournal.ui.theme.EmptyNote

/** 원두 탭 하위 뷰 — 구현 예정. */
@Composable
fun BeanBlendView(nav: NavHostController, data: BeanData) {
    EmptyNote("준비 중", Modifier.padding(Dimens.gutter))
}
