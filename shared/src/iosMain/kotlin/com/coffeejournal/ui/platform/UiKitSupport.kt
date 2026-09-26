@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.coffeejournal.ui.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.posix.memcpy

/*
 * UIKit plumbing shared by the iOS actuals (pickers, document picker, share sheet).
 * Not compiled in this repository's Linux CI; verify on macOS when enabling the iOS target.
 */

/** The view controller currently on top of the key window: the one a picker or share sheet is presented from. */
@Suppress("DEPRECATION")
internal fun topViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    val sceneWindows = app.connectedScenes
        .mapNotNull { it as? UIWindowScene }
        .flatMap { scene -> scene.windows.mapNotNull { it as? UIWindow } }
    val window = app.keyWindow ?: sceneWindows.firstOrNull()
    var top = window?.rootViewController
    while (true) {
        val next = top?.presentedViewController ?: break
        top = next
    }
    return top
}

/** Presents [controller] modally; false when there is no window to present from. */
internal fun presentModally(controller: UIViewController): Boolean {
    val presenter = topViewController() ?: return false
    presenter.presentViewController(controller, animated = true, completion = null)
    return true
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }

internal fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return out
}

/**
 * Writes [text] as UTF-8 to tmp/backups/[name] and returns its file URL; null when the text is empty or the file
 * could not be written, so an empty backup is never handed to the document picker.
 */
internal fun writeTempTextFile(name: String, text: String): NSURL? {
    val bytes = text.encodeToByteArray()
    if (bytes.isEmpty()) return null
    val dir = NSTemporaryDirectory().trimEnd('/') + "/backups"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "backup.json" }
    val path = "$dir/$safeName"
    if (!bytes.toNSData().writeToFile(path, atomically = true)) return null
    return NSURL.fileURLWithPath(path)
}

internal fun deleteTempFile(url: NSURL) {
    url.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
}
