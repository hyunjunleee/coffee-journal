package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/*
 * Reminders are not delivered on iOS yet (IosReminderPlatform), so there is nothing to ask for: the answer is "no",
 * and 알림 설정 keeps its switch off with the hint. Wiring them means asking
 * UNUserNotificationCenter.requestAuthorizationWithOptions here.
 */
@Composable
actual fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onResult)
    return remember { { latest.value(false) } }
}
