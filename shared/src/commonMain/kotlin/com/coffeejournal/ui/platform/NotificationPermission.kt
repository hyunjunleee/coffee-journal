package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable

/**
 * Asks the platform for permission to show notifications and reports whether they can be shown ([onResult]).
 * Android 13+ shows the POST_NOTIFICATIONS prompt when the permission is missing (once refused for good, the answer
 * comes back at once as false); older Android answers whether the app's notifications are on. Call the returned
 * function from a tap.
 */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit
