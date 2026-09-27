package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.coffeejournal.ui.notify.IosNotifications
import org.koin.compose.koinInject

/*
 * iOS asks UNUserNotificationCenter for alerts, sounds and badges. The system prompt appears the first time only;
 * once answered, the answer comes back at once (a refusal is changed in Settings, which 알림 설정 opens). The result
 * arrives on the main thread with the cached authorization already updated, so ReminderPlatform.canNotify agrees.
 */
@Composable
actual fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val notifications = koinInject<IosNotifications>()
    val latest = rememberUpdatedState(onResult)
    return remember(notifications) { { notifications.requestAuthorization { granted -> latest.value(granted) } } }
}
