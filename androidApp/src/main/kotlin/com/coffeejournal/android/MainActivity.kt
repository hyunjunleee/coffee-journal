package com.coffeejournal.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.coffeejournal.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is light-only (ivory background), so the bar icons stay dark even when the phone is in dark mode.
        enableEdgeToEdge(statusBarStyle = LightBars, navigationBarStyle = LightBars)
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    private companion object {
        /** Transparent bars with dark icons; the dark scrim only applies where dark icons are unsupported. */
        val LightBars = SystemBarStyle.light(Color.TRANSPARENT, Color.argb(0x80, 0x1b, 0x1b, 0x1b))
    }
}
