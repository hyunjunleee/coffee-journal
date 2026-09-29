package com.coffeejournal.ui.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
actual fun rememberImagePicker(maxItems: Int, onPicked: (List<ByteArray>) -> Unit): () -> Unit {
    val context = LocalContext.current
    // launched on Dispatchers.Main so the work after the file IO is back on the main thread in UI tests too, where the
    // composition's own scope does not dispatch
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)
    val deliver: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) scope.launch(Dispatchers.Main) {
            val read = withContext(Dispatchers.IO) {
                uris.map { uri -> runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            }
            // undecodable picks (e.g. HEIC on API 26-27) are refused rather than stored as a blank ".jpg"
            read.firstNotNullOfOrNull { PhotoBytes.problem(it) }?.let { showToast(context, it) }
            val usable = read.filter { PhotoBytes.problem(it) == null }.filterNotNull()
            if (usable.isNotEmpty()) callback.value(usable)
        }
    }
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    if (maxItems <= 1) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> deliver(listOfNotNull(uri)) }
        return { launchOrToast(context, PlatformMessages.PHOTO_PICKER) { launcher.launch(request) } }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems)) { uris -> deliver(uris) }
    return { launchOrToast(context, PlatformMessages.PHOTO_PICKER) { launcher.launch(request) } }
}

/** Camera output files: one fresh file per capture under cacheDir/captures, deleted once read. */
internal object CaptureFiles {
    fun dir(context: Context): File = File(context.cacheDir, "captures")

    fun newFile(context: Context): File =
        File(dir(context).apply { mkdirs() }, "capture-" + UUID.randomUUID().toString().replace("-", "") + ".jpg")
}

@Composable
actual fun rememberCameraCapture(onCaptured: (ByteArray) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onCaptured)
    // Only the path is saved state, so a result delivered to a recreated activity still finds its own file.
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = capturePath?.let(::File) ?: return@rememberLauncherForActivityResult
        capturePath = null
        scope.launch(Dispatchers.Main) {
            val bytes = withContext(Dispatchers.IO) {
                val read = if (ok) runCatching { file.takeIf { it.length() > 0 }?.readBytes() }.getOrNull() else null
                file.delete()
                read
            }
            if (!ok) return@launch
            // a camera app that answers OK without writing the file yields nothing here, never an older capture
            val problem = PhotoBytes.problem(bytes)
            if (problem != null || bytes == null) showToast(context, problem ?: PlatformMessages.PHOTO_READ_FAILED)
            else callback.value(bytes)
        }
    }
    val available = remember(context) { hasCameraApp(context) }
    if (!available) return null
    return {
        val file = CaptureFiles.newFile(context)
        val uri = runCatching { FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file) }.getOrNull()
        if (uri == null) {
            showToast(context, PlatformMessages.CAMERA)
        } else {
            capturePath = file.absolutePath
            if (!launchOrToast(context, PlatformMessages.CAMERA) { launcher.launch(uri) }) {
                capturePath = null
                file.delete()
            }
        }
    }
}

/** A camera exists and some app answers IMAGE_CAPTURE (the app manifest declares the matching <queries> entry). */
private fun hasCameraApp(context: Context): Boolean {
    val pm = context.packageManager
    if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return false
    return runCatching { Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(pm) != null }.getOrDefault(false)
}

internal var appContextForUrl: Context? = null

fun installUrlOpener(context: Context) { appContextForUrl = context.applicationContext }

actual fun openUrl(url: String) {
    val ctx = appContextForUrl ?: return
    val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
