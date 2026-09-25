package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS pickers are wired in phase 2 (PHPickerViewController / UIImagePickerController via UIKitViewController).
 * These placeholders keep the shared UI compiling; they report "no picker" so the UI hides the buttons.
 */
@Composable
actual fun rememberImagePicker(maxItems: Int, onPicked: (List<ByteArray>) -> Unit): () -> Unit = { }

@Composable
actual fun rememberCameraCapture(onCaptured: (ByteArray) -> Unit): (() -> Unit)? = null

actual fun openUrl(url: String) {
    val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    NSURL.URLWithString(target)?.let { UIApplication.sharedApplication.openURL(it) }
}
