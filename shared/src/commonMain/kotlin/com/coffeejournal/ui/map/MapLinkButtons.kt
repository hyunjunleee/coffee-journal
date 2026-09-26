package com.coffeejournal.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeejournal.domain.model.GeoPoint
import com.coffeejournal.domain.rules.MapLinks
import com.coffeejournal.ui.platform.openMapUri
import com.coffeejournal.ui.theme.GhostButton

/**
 * "네이버 지도에서 열기" / "카카오맵에서 열기" (and "Google 지도에서 열기" first for an overseas place): each opens the
 * map app, or its web map when the app is missing. The app itself sends nothing; the map app gets the link.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapLinkButtons(name: String, location: String, point: GeoPoint?, overseas: Boolean, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MapLinks.forPlace(name, location, point, overseas).forEach { link ->
            GhostButton("${link.label} ↗", small = true, onClick = { openMapUri(link.uri, link.fallback) })
        }
    }
}
