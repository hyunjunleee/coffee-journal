package com.coffeejournal.ui.map.detail

/**
 * iOS has no detail map yet (MapLibre Native for iOS would have to be linked into the Xcode project first): the
 * 상세 지도 buttons stay hidden and the SGIS map is used as it is. Opening the route anyway shows the fallback notice.
 */
actual fun platformDetailMapRenderer(): DetailMapRenderer = UnavailableDetailMapRenderer
