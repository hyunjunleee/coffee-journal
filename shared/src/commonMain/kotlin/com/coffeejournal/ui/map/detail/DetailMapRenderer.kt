package com.coffeejournal.ui.map.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.koin.compose.koinInject

/** What the detail map is doing; only the first three show the map. */
sealed interface DetailMapStatus {
    data object Loading : DetailMapStatus
    data object Ready : DetailMapStatus
    /** Still not loaded after a while (a slow or failing connection); the map stays with a notice. */
    data object Slow : DetailMapStatus
    /** No network when the screen opened: the map is not shown. */
    data object Offline : DetailMapStatus
    /** The renderer could not start or the map failed to load; [reason] is for the notice (see [DetailMapRenderer]). */
    data class Failed(val reason: String?) : DetailMapStatus

    val showsMap: Boolean get() = this == Loading || this == Ready || this == Slow
}

/** A camera move the screen asks for (a pin chosen from the list); [id] tells repeated requests apart. */
data class DetailCameraRequest(val camera: DetailCamera, val id: Int)

/**
 * The screen's side of the map: where it should look ([request]), where it is ([camera], [settled] once it stops),
 * and whether it loaded ([status]). The renderer reports into it; the screen and its tests only read it.
 */
@Stable
class DetailMapController(val start: DetailCamera) {
    var camera by mutableStateOf(start)
        private set
    /** The camera when it last came to rest: the picker's crosshair point. */
    var settled by mutableStateOf(start)
        private set
    var request by mutableStateOf<DetailCameraRequest?>(null)
        private set
    var status by mutableStateOf<DetailMapStatus>(DetailMapStatus.Loading)
        private set
    /** Bumped by "다시 시도": the renderer is created afresh. */
    var attempt by mutableIntStateOf(0)
        private set

    fun moveTo(target: DetailCamera) {
        request = DetailCameraRequest(target, (request?.id ?: 0) + 1)
    }

    fun reportCamera(c: DetailCamera, moving: Boolean) {
        camera = c
        if (!moving) settled = c
    }

    fun reportLoaded() {
        if (status == DetailMapStatus.Loading || status == DetailMapStatus.Slow) status = DetailMapStatus.Ready
    }

    fun reportSlow() {
        if (status == DetailMapStatus.Loading) status = DetailMapStatus.Slow
    }

    fun reportFailure(reason: String?) {
        status = DetailMapStatus.Failed(reason)
    }

    fun reportOffline() {
        status = DetailMapStatus.Offline
    }

    /** "다시 시도": starts over from the last camera, or stays on the notice while there is still no network. */
    fun retry(online: Boolean) {
        request = null
        attempt++
        status = if (online) DetailMapStatus.Loading else DetailMapStatus.Offline
    }
}

/**
 * Draws the detail map. Android's is MapLibre Native (see `platformDetailMapRenderer`); it is looked up through Koin so
 * the flow tests, where no native renderer runs, put a fake in its place. A renderer draws [pins] over the
 * [style] (DetailMapStyle.json), starts at [DetailMapController.start], follows [DetailMapController.request]s and
 * reports camera moves, loading and failures to the controller; a tap on a pin goes to [onPinTap], elsewhere to
 * [onMapTap].
 */
interface DetailMapRenderer {
    /** False where there is no map renderer: the 상세 지도 buttons stay hidden and the SGIS map is all there is. */
    val isSupported: Boolean

    /** Whether the device has a network that reaches the internet now (checked when the screen opens). */
    fun isOnline(): Boolean

    /** How long loading may take before the screen says it is having trouble. */
    val slowAfterMs: Long get() = 20_000L

    @Composable
    fun MapView(
        style: String,
        controller: DetailMapController,
        pins: List<DetailPin>,
        selectedKey: String?,
        onPinTap: (String) -> Unit,
        onMapTap: () -> Unit,
        modifier: Modifier,
    )

    companion object {
        /** [DetailMapStatus.Failed.reason] when this platform has no renderer. */
        const val UNSUPPORTED = "unsupported"
        /** [DetailMapStatus.Failed.reason] when the native map library could not be loaded. */
        const val NATIVE_UNAVAILABLE = "native"
    }
}

/** No renderer on this platform (iOS for now): it reports [DetailMapRenderer.UNSUPPORTED] and the screen falls back. */
object UnavailableDetailMapRenderer : DetailMapRenderer {
    override val isSupported: Boolean = false

    override fun isOnline(): Boolean = true

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
        LaunchedEffect(controller) { controller.reportFailure(DetailMapRenderer.UNSUPPORTED) }
    }
}

/** The platform's renderer (Android: MapLibre Native; iOS: [UnavailableDetailMapRenderer] until it is wired up). */
expect fun platformDetailMapRenderer(): DetailMapRenderer

/** Whether the 상세 지도 buttons should show (a renderer exists on this platform). */
@Composable
fun rememberDetailMapSupported(): Boolean = koinInject<DetailMapRenderer>().isSupported
