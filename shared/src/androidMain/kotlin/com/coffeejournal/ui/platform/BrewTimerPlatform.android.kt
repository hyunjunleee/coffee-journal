package com.coffeejournal.ui.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
actual fun rememberStepBuzz(): () -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(context) { { StepBuzz.vibrate(context) } }
}

internal object StepBuzz {
    private const val DURATION_MS = 180L

    fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** One short pulse; nothing on a device without a vibrator (or when the system refuses). */
    fun vibrate(context: Context) {
        val v = vibrator(context)?.takeIf { it.hasVibrator() } ?: return
        runCatching { v.vibrate(VibrationEffect.createOneShot(DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
}
