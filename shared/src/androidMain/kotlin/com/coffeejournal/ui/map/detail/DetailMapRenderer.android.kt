package com.coffeejournal.ui.map.detail

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.coffeejournal.ui.platform.appContextForUrl
import org.maplibre.android.MapLibre

actual fun platformDetailMapRenderer(): DetailMapRenderer = MapLibreDetailMapRenderer

internal actual val detailMapUserAgentOs: String = "Android"

/**
 * MapLibre Native for Android drawing the shared [MapLibreDetailMap].
 *
 * The native library is loaded before any map is created: where it cannot load (an unsupported CPU, or the JVM
 * tests) the controller hears [DetailMapRenderer.NATIVE_UNAVAILABLE] and the screen falls back instead of crashing.
 */
object MapLibreDetailMapRenderer : DetailMapRenderer {
    override val isSupported: Boolean = true

    override fun isOnline(): Boolean {
        val ctx = appContextForUrl ?: return true
        return runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    /** Loads libmaplibre.so and starts MapLibre once; the failure, if any, is kept. */
    private var started: Result<Unit>? = null

    private fun start(context: Context): Result<Unit> = started ?: runCatching {
        System.loadLibrary("maplibre")
        MapLibre.getInstance(context.applicationContext)
        Unit
    }.also { started = it }

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
        val context = LocalContext.current
        val ready = remember { start(context) }
        if (ready.isFailure) {
            LaunchedEffect(controller) { controller.reportFailure(DetailMapRenderer.NATIVE_UNAVAILABLE) }
            return
        }
        MapLibreDetailMap(style, controller, pins, selectedKey, onPinTap, onMapTap, modifier)
    }
}
