package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable

/**
 * Keeps the display on while [enabled] and this composable is shown (the brew timer while it runs).
 * Android: the window's keep-screen-on flag through the view; iOS: `UIApplication.idleTimerDisabled`.
 */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

/**
 * A short vibration marking a recipe step change on the brew timer.
 * Android: the system vibrator (VIBRATE permission); iOS: an impact haptic.
 */
@Composable
expect fun rememberStepBuzz(): () -> Unit
