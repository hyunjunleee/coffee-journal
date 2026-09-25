package com.coffeejournal.ui.platform

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File

private class PendingText { var value: String? = null }

@Composable
actual fun rememberJsonSaver(onResult: (Boolean) -> Unit): (suggestedName: String, json: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onResult)
    val pending = remember { PendingText() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pending.value
        pending.value = null
        if (uri == null || json == null) {
            callback.value(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull() ?: resolver.openOutputStream(uri)
                    stream?.use { it.write(json.toByteArray(Charsets.UTF_8)); it.flush() } != null
                }.getOrDefault(false)
            }
            callback.value(ok)
        }
    }
    return { suggestedName, json ->
        pending.value = json
        launcher.launch(suggestedName)
    }
}

@Composable
actual fun rememberJsonOpener(onLoaded: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onLoaded)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            }
            callback.value(text)
        }
    }
    return { launcher.launch(arrayOf("application/json", "*/*")) }
}

/** Above this many characters the text goes through a cache file to stay under the Binder transaction limit. */
private const val INLINE_SHARE_LIMIT = 200_000

actual fun shareText(title: String, text: String) {
    val ctx = runCatching { GlobalContext.getOrNull()?.get<Context>() }.getOrNull()?.applicationContext ?: return
    if (text.length <= INLINE_SHARE_LIMIT) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return
    }
    Thread {
        runCatching {
            val dir = File(ctx.cacheDir, "backups").apply { mkdirs() }
            val safeName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "backup.json" }
            val file = File(dir, if (safeName.endsWith(".json")) safeName else "$safeName.json")
            file.writeText(text, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }.start()
}
