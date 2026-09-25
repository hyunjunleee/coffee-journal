package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable

/** Opens the platform photo picker; delivers the raw bytes of the chosen images (at most [maxItems]). */
@Composable
expect fun rememberImagePicker(maxItems: Int = 1, onPicked: (List<ByteArray>) -> Unit): () -> Unit

/** Opens the platform camera; delivers a JPEG when a photo was taken. */
@Composable
expect fun rememberCameraCapture(onCaptured: (ByteArray) -> Unit): (() -> Unit)?

/** Opens an external URL (video links). */
expect fun openUrl(url: String)
