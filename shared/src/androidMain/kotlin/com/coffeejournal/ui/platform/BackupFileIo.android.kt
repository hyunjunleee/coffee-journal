package com.coffeejournal.ui.platform

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * The payload of a pending "save as". It is written to cacheDir/backup-save before the picker opens and only its path
 * is kept as saved state, so the result can still be written after the activity was recreated or the process killed
 * while DocumentsUI was in front (a backup with photos is far too large for the saved-state bundle).
 */
internal object PendingBackupFiles {
    fun dir(context: Context): File = File(context.cacheDir, "backup-save")

    /** Replaces any earlier staged payload; null when [json] is empty or could not be written completely. */
    fun stage(context: Context, json: String): File? {
        if (json.isEmpty()) return null
        return runCatching {
            val folder = dir(context).apply { deleteRecursively(); mkdirs() }
            val bytes = json.toByteArray(Charsets.UTF_8)
            File(folder, "pending-${System.currentTimeMillis()}.json").also { it.writeBytes(bytes) }.takeIf { it.length() == bytes.size.toLong() }
        }.getOrNull()
    }

    /**
     * Copies [staged] into the document the picker created at [uri]. Reports success only when every byte arrived;
     * otherwise the created document is deleted, so no empty or partial backup file is left behind.
     */
    fun writeOrDiscard(context: Context, uri: Uri, staged: File?): Boolean {
        val expected = staged?.takeIf { it.exists() }?.length() ?: 0L
        val ok = expected > 0 && runCatching {
            val resolver = context.contentResolver
            val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull() ?: resolver.openOutputStream(uri)
            out?.use { stream -> staged!!.inputStream().use { it.copyTo(stream) } } == expected
        }.getOrDefault(false)
        staged?.delete()
        if (!ok) deleteDocument(context, uri)
        return ok
    }

    private fun deleteDocument(context: Context, uri: Uri) {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { File(it).delete() }
            return
        }
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }
}

@Composable
actual fun rememberJsonSaver(onResult: (Boolean) -> Unit): (suggestedName: String, json: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onResult)
    var stagedPath by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val staged = stagedPath?.let(::File)
        stagedPath = null
        if (uri == null) {
            staged?.delete()
            callback.value(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(NonCancellable + Dispatchers.IO) { PendingBackupFiles.writeOrDiscard(context, uri, staged) }
            callback.value(ok)
        }
    }
    return { suggestedName, json ->
        scope.launch {
            val staged = withContext(Dispatchers.IO) { PendingBackupFiles.stage(context, json) }
            if (staged == null) {
                callback.value(false)
            } else {
                stagedPath = staged.absolutePath
                if (!launchOrToast(context, PlatformMessages.SAVE_PICKER) { launcher.launch(suggestedName) }) {
                    stagedPath = null
                    staged.delete()
                    callback.value(false)
                }
            }
        }
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
    return { launchOrToast(context, PlatformMessages.OPEN_PICKER) { launcher.launch(arrayOf("application/json", "*/*")) } }
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
        launchOrToast(ctx, PlatformMessages.SHARE) { ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return
    }
    Thread {
        val chooser = runCatching {
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
            Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.getOrNull()
        Handler(Looper.getMainLooper()).post {
            if (chooser == null) showToast(ctx, PlatformMessages.SHARE)
            else launchOrToast(ctx, PlatformMessages.SHARE) { ctx.startActivity(chooser) }
        }
    }.start()
}
