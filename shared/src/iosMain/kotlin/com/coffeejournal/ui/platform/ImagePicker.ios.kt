@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import kotlin.coroutines.resume

/*
 * iOS photo picker (PHPickerViewController, images only), camera (UIImagePickerController) and URL opening.
 * Not compiled in this repository's Linux CI; verify on macOS when enabling the iOS target.
 */

/** Uniform type identifier every picked image conforms to (HEIC, JPEG, PNG ...). */
private const val IMAGE_TYPE = "public.image"

private class PhotoPickerDelegate(private val onPicked: (List<NSItemProvider>) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        val providers = didFinishPicking.mapNotNull { (it as? PHPickerResult)?.itemProvider }
        if (providers.isNotEmpty()) onPicked(providers)
    }
}

private suspend fun NSItemProvider.loadImageData(): NSData? {
    if (!hasItemConformingToTypeIdentifier(IMAGE_TYPE)) return null
    return suspendCancellableCoroutine { cont ->
        loadDataRepresentationForTypeIdentifier(IMAGE_TYPE) { data, _ -> cont.resume(data) }
    }
}

@Composable
actual fun rememberImagePicker(maxItems: Int, onPicked: (List<ByteArray>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)
    val delegate = remember {
        PhotoPickerDelegate { providers ->
            scope.launch {
                // only data UIKit can decode is delivered; IosPhotoStore re-encodes it as JPEG
                val images = providers.mapNotNull { it.loadImageData() }
                    .filter { UIImage.imageWithData(it) != null }
                    .map { it.toByteArray() }
                if (images.isNotEmpty()) callback.value(images)
            }
        }
    }
    return remember<() -> Unit>(delegate, maxItems) {
        {
            val config = PHPickerConfiguration()
            config.selectionLimit = maxItems.coerceAtLeast(1).toLong()
            config.filter = PHPickerFilter.imagesFilter
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = delegate
            presentModally(picker)
        }
    }
}

private class CameraDelegate(private val onCaptured: (ByteArray) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
        val jpeg = UIImageJPEGRepresentation(image, 0.9) ?: return
        val bytes = jpeg.toByteArray()
        if (bytes.isNotEmpty()) onCaptured(bytes)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
    }
}

@Composable
actual fun rememberCameraCapture(onCaptured: (ByteArray) -> Unit): (() -> Unit)? {
    val callback = rememberUpdatedState(onCaptured)
    val delegate = remember { CameraDelegate { bytes -> callback.value(bytes) } }
    val source = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
    val available = remember { UIImagePickerController.isSourceTypeAvailable(source) }
    if (!available) return null
    return remember<() -> Unit>(delegate) {
        {
            val picker = UIImagePickerController()
            picker.sourceType = source
            picker.delegate = delegate
            presentModally(picker)
        }
    }
}

actual fun openUrl(url: String) {
    val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    val nsUrl = NSURL.URLWithString(target) ?: return
    // openURL(_:) without options is refused ("Force returning false") since iOS 18
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
}
