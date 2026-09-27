@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDataReadingMappedIfSafe
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

/*
 * iOS backup file IO: UIDocumentPickerViewController for "save as" / open, UIActivityViewController for sharing.
 */

/** Export picker delegate; UIKit holds delegates weakly, so the composable keeps this object alive. */
private class ExportPickerDelegate(private val onDone: (Boolean) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    /** The temp copy handed to the picker; removed once the picker is done with it. */
    var pendingFile: NSURL? = null

    fun finish(ok: Boolean) {
        pendingFile?.let(::deleteTempFile)
        pendingFile = null
        onDone(ok)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        finish(didPickDocumentsAtURLs.isNotEmpty())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(false)
    }
}

private class OpenPickerDelegate(private val onPicked: (NSURL) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onPicked)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}

@Composable
actual fun rememberJsonSaver(onResult: (Boolean) -> Unit): (suggestedName: String, json: String) -> Unit {
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onResult)
    val delegate = remember { ExportPickerDelegate { ok -> callback.value(ok) } }
    return remember<(String, String) -> Unit>(delegate) {
        { suggestedName: String, json: String ->
            scope.launch {
                // the picker exports a copy of a finished temp file, so an empty or half-written backup is never saved
                val file = withContext(Dispatchers.Default) { writeTempTextFile(suggestedName, json) }
                if (file == null) {
                    callback.value(false)
                } else {
                    delegate.pendingFile = file
                    val picker = UIDocumentPickerViewController(forExportingURLs = listOf(file), asCopy = true)
                    picker.delegate = delegate
                    if (!presentModally(picker)) delegate.finish(false)
                }
            }
        }
    }
}

@Composable
actual fun rememberJsonOpener(onOpened: (OpenedFile) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onOpened)
    val delegate = remember {
        OpenPickerDelegate { url ->
            scope.launch {
                val opened = withContext(Dispatchers.Default) { readText(url) }
                callback.value(opened)
            }
        }
    }
    return remember<() -> Unit>(delegate) {
        {
            // asCopy: the picker copies the file into the app sandbox, so no security-scoped bookmark is kept
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText), asCopy = true)
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            presentModally(picker)
        }
    }
}

private fun readText(url: NSURL): OpenedFile {
    val scoped = url.startAccessingSecurityScopedResource()
    try {
        val limit = BackupFileLimits.maxFileBytes((NSProcessInfo.processInfo.physicalMemory / 4u).toLong())
        // mapped, so checking the size does not read the whole file into memory first
        val data = NSData.create(contentsOfURL = url, options = NSDataReadingMappedIfSafe, error = null) ?: return OpenedFile.Unreadable
        val size = data.length.toLong()
        if (size > limit) return OpenedFile.TooLarge(size, limit)
        return OpenedFile.Text(data.toByteArray().decodeToString())
    } finally {
        if (scoped) url.stopAccessingSecurityScopedResource()
    }
}

/** Above this many characters the text is shared as a .json file rather than inline text. */
private const val INLINE_SHARE_LIMIT = 200_000

actual fun shareText(title: String, text: String) {
    val presenter = topViewController() ?: return
    val item: Any = if (text.length <= INLINE_SHARE_LIMIT) text else writeTempTextFile(title, text) ?: text
    val sheet = UIActivityViewController(activityItems = listOf(item), applicationActivities = null)
    // iPad shows the sheet as a popover, which needs an anchor
    sheet.popoverPresentationController?.sourceView = presenter.view
    presenter.presentViewController(sheet, animated = true, completion = null)
}
