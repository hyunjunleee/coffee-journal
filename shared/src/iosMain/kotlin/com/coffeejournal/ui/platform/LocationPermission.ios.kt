package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.coffeejournal.ui.map.search.IosCurrentLocation
import org.koin.compose.koinInject

/*
 * iOS asks CLLocationManager for "while using the app" (Info.plist NSLocationWhenInUseUsageDescription says why). The
 * system prompt appears the first time only; afterwards the answer comes back at once (a refusal is changed in
 * Settings). The result arrives on the main thread.
 */
@Composable
actual fun rememberLocationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val location = koinInject<IosCurrentLocation>()
    val latest = rememberUpdatedState(onResult)
    return remember(location) { { location.requestAuthorization { granted -> latest.value(granted) } } }
}
