package com.coffeejournal

import androidx.compose.ui.window.ComposeUIViewController
import com.coffeejournal.di.initKoin
import platform.UIKit.UIViewController

private var koinStarted = false

/** Entry point used by the SwiftUI host (iosApp/iosApp/ContentView.swift). */
@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController {
    if (!koinStarted) { initKoin(); koinStarted = true }
    return ComposeUIViewController { App() }
}
