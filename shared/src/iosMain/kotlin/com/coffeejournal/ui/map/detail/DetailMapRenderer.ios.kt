@file:OptIn(ExperimentalForeignApi::class)

package com.coffeejournal.ui.map.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

actual fun platformDetailMapRenderer(): DetailMapRenderer = MapLibreDetailMapRenderer

internal actual val detailMapUserAgentOs: String = "iOS"

/**
 * MapLibre Native for iOS drawing the shared [MapLibreDetailMap]. The MapLibre framework comes from its official Swift
 * package, which the Xcode project links into the app (iosApp/project.yml, the version maplibre-compose was built
 * against); unlike Android there is no library to load first, so a map that cannot start shows up as a failed load.
 *
 * Whether there is a network comes from the system's path monitor (Network framework), started with the renderer:
 * the map tab asks [isSupported] before any 상세 지도 button shows, so the first path is known by the time the screen
 * opens. Until then the answer is "online" and the map is simply tried, as on Android without a context.
 */
object MapLibreDetailMapRenderer : DetailMapRenderer {
    override val isSupported: Boolean = true

    /** Whether the last path the system reported can reach the network; null before the first report. */
    private var satisfied: Boolean? = null

    // kept for the app's lifetime (a released monitor stops); updates arrive on the main queue, where isOnline is asked
    @Suppress("unused")
    private val monitor = nw_path_monitor_create().also { m ->
        nw_path_monitor_set_update_handler(m) { path -> satisfied = nw_path_get_status(path) == nw_path_status_satisfied }
        nw_path_monitor_set_queue(m, dispatch_get_main_queue())
        nw_path_monitor_start(m)
    }

    override fun isOnline(): Boolean = satisfied ?: true

    @Composable
    override fun MapView(
        style: String,
        controller: DetailMapController,
        pins: List<DetailPin>,
        selectedKey: String?,
        onPinTap: (String) -> Unit,
        onMapTap: () -> Unit,
        modifier: Modifier,
    ) {
        MapLibreDetailMap(style, controller, pins, selectedKey, onPinTap, onMapTap, modifier)
    }
}
