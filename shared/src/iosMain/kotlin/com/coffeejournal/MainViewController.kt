package com.coffeejournal

import androidx.compose.ui.window.ComposeUIViewController
import com.coffeejournal.di.AppScope
import com.coffeejournal.di.initKoin
import com.coffeejournal.ui.notify.IosReminderPlatform
import com.coffeejournal.ui.notify.ReminderCheck
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

private var koinStarted = false

/** Entry point used by the SwiftUI host (iosApp/iosApp/ContentView.swift). */
@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController {
    if (!koinStarted) {
        initKoin()
        koinStarted = true
        val koin = KoinPlatform.getKoin()
        // reminders are scheduled ahead (no daily check runs in the background): now, after every change to what they
        // read and back in the foreground
        koin.get<IosReminderPlatform>().start(koin.get<AppScope>(), koin.get<ReminderCheck>().changes())
    }
    return ComposeUIViewController { App() }
}
