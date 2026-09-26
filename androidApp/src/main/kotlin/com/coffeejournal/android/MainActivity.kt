package com.coffeejournal.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.coffeejournal.App
import com.coffeejournal.android.widget.CoffeeWidgets
import com.coffeejournal.ui.nav.LaunchRequests
import com.coffeejournal.ui.notify.LaunchIntents
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is light-only (ivory background), so the bar icons stay dark even when the phone is in dark mode.
        enableEdgeToEdge(statusBarStyle = LightBars, navigationBarStyle = LightBars)
        super.onCreate(savedInstanceState)
        // a notification or the widget named a screen; a restored activity, or one reopened from recents, went there already
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) openRequestedScreen(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequestedScreen(intent)
    }

    override fun onStop() {
        super.onStop()
        // the app went to the background: the widget shows what was just recorded and today's D-day
        if (!isChangingConfigurations) CoffeeWidgets.refreshSoon(applicationContext)
    }

    private fun openRequestedScreen(intent: Intent?) {
        val target = LaunchIntents.targetOf(intent) ?: return
        GlobalContext.getOrNull()?.getOrNull<LaunchRequests>()?.request(target)
        // taken once: recreating the activity later must not open the screen again
        intent?.removeExtra(LaunchIntents.EXTRA_OPEN)
    }

    private companion object {
        /** Transparent bars with dark icons; the dark scrim only applies where dark icons are unsupported. */
        val LightBars = SystemBarStyle.light(Color.TRANSPARENT, Color.argb(0x80, 0x1b, 0x1b, 0x1b))
    }
}
