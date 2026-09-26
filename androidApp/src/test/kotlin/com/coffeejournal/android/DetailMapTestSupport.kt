package com.coffeejournal.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coffeejournal.ui.map.detail.DetailCamera
import com.coffeejournal.ui.map.detail.DetailMapController
import com.coffeejournal.ui.map.detail.DetailMapRenderer
import com.coffeejournal.ui.map.detail.DetailPin
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/**
 * Stands in for MapLibre in the JVM tests (its native renderer cannot run there): it shows the pins it is given as
 * buttons ("지도 핀 <label>") that tap like a pin on the map, remembers the style, pins and camera it got, and lets a test
 * move the camera ([pan]) or decide whether the network is up, whether loading finishes, or that the renderer fails.
 */
class FakeDetailMapRenderer(
    @Volatile var online: Boolean = true,
    private val loads: Boolean = true,
    private val failWith: String? = null,
    override val slowAfterMs: Long = 20_000L,
) : DetailMapRenderer {
    override val isSupported: Boolean = true

    @Volatile var controller: DetailMapController? = null
    @Volatile var style: String? = null
    @Volatile var pins: List<DetailPin> = emptyList()
    @Volatile var selected: String? = null
    /** The camera each map instance started with, in order (a retry starts another). */
    val starts = java.util.concurrent.CopyOnWriteArrayList<DetailCamera>()

    override fun isOnline(): Boolean = online

    /** A pan that comes to rest at [to], as MapLibre reports it. */
    fun pan(to: DetailCamera) {
        controller!!.reportCamera(to, moving = true)
        controller!!.reportCamera(to, moving = false)
    }

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
        this.controller = controller
        this.style = style
        this.pins = pins
        this.selected = selectedKey
        LaunchedEffect(controller) {
            starts += controller.camera
            when {
                failWith != null -> controller.reportFailure(failWith)
                loads -> controller.reportLoaded()
            }
        }
        // a requested move lands at once
        LaunchedEffect(controller.request?.id) { controller.request?.let { controller.reportCamera(it.camera, moving = false) } }
        Box(modifier.background(Color(0xFFDDDDDD))) {
            Column(Modifier.padding(top = 40.dp)) {
                pins.forEach { p ->
                    Text(
                        "● ${p.label}", color = if (p.key == selectedKey) Color.Red else Color.Black,
                        modifier = Modifier.clickable(role = Role.Button) { onPinTap(p.key) }.semantics { contentDescription = "지도 핀 ${p.label}" },
                    )
                }
            }
        }
    }
}

/** Puts [fake] in place of the platform renderer (MapFeature registers the real one). */
fun installFakeDetailMap(fake: FakeDetailMapRenderer = FakeDetailMapRenderer()): FakeDetailMapRenderer {
    loadKoinModules(module { single<DetailMapRenderer> { fake } })
    return fake
}
