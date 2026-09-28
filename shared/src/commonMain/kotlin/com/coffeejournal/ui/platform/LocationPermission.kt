package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable

/**
 * Asks for the location permission for the picker's "현재 위치" and reports whether a position may be read
 * ([onResult]). Android asks for precise and approximate location together (either is enough; with approximate only
 * the position is a rough one); once refused for good, the answer comes back at once as false. iOS asks for "while
 * using the app" (the system prompt appears the first time only). Nothing is asked until the returned function is
 * called from a tap.
 */
@Composable
expect fun rememberLocationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit
