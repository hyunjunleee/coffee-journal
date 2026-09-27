package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import platform.UIKit.UIApplication
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

@Composable
actual fun rememberStepBuzz(): () -> Unit = remember {
    val generator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    val buzz: () -> Unit = { generator.impactOccurred() }
    buzz
}
