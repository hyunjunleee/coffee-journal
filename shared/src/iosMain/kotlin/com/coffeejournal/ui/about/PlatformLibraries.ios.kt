package com.coffeejournal.ui.about

/**
 * The iOS app's library list is not generated yet: its runtime classpath differs from Android's (it also bundles
 * Skia through skiko, BSD-3-Clause). Generate it on macOS from the iOS framework's dependencies before shipping an
 * iOS build (see iosApp/README.md); until then the screen says the list is missing.
 */
actual val platformLibraries: List<Library> = emptyList()
