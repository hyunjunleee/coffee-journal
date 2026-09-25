package com.coffeejournal.ui.platform

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberImagePicker(maxItems: Int, onPicked: (List<ByteArray>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)
    val readAll: suspend (List<Uri>) -> List<ByteArray> = { uris ->
        withContext(Dispatchers.IO) {
            uris.mapNotNull { uri -> runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
        }
    }
    if (maxItems <= 1) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch { callback.value(readAll(listOf(uri))) }
        }
        return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems)) { uris ->
        if (uris.isNotEmpty()) scope.launch { callback.value(readAll(uris)) }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
actual fun rememberCameraCapture(onCaptured: (ByteArray) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onCaptured)
    val file = File(context.cacheDir, "capture.jpg")
    val authority = context.packageName + ".fileprovider"
    val uri = runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull() ?: return null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
            if (bytes != null) callback.value(bytes)
        }
    }
    val hasCamera = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
    if (!hasCamera) return null
    return { launcher.launch(uri) }
}

private var appContextForUrl: android.content.Context? = null

fun installUrlOpener(context: android.content.Context) { appContextForUrl = context.applicationContext }

actual fun openUrl(url: String) {
    val ctx = appContextForUrl ?: return
    val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
