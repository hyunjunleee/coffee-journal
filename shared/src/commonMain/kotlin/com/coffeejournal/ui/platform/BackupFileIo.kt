package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable

/** Opens the platform "save as" picker for a JSON document; [onResult] reports whether the file was written. */
@Composable
expect fun rememberJsonSaver(onResult: (Boolean) -> Unit): (suggestedName: String, json: String) -> Unit

/** Opens the platform document picker and reads the chosen file as text (refusing one too large to parse safely). */
@Composable
expect fun rememberJsonOpener(onOpened: (OpenedFile) -> Unit): () -> Unit

/** Shows the platform share sheet with the given text (large payloads are shared as a file). */
expect fun shareText(title: String, text: String)

/** What the document picker delivered. */
sealed interface OpenedFile {
    class Text(val text: String) : OpenedFile

    /** Refused before it was read completely: [bytes] is the file size (-1 when unknown), [limit] what fits. */
    class TooLarge(val bytes: Long, val limit: Long) : OpenedFile

    data object Unreadable : OpenedFile
}

/**
 * How large a backup file may be. Reading and parsing one needs several times its size in memory at once (the file
 * text, the parsed JSON with every photo's data URL, then the decoded photo bytes), so the limit follows the memory
 * the app may use; anything larger is refused with a message instead of running out of memory halfway.
 */
object BackupFileLimits {
    const val MB: Long = 1024L * 1024L
    const val MIN_LIMIT_BYTES: Long = 16 * MB
    const val MAX_LIMIT_BYTES: Long = 256 * MB

    /** About a fifth of [maxMemoryBytes], the heap (Android) or memory budget (iOS) the app can use. */
    fun maxFileBytes(maxMemoryBytes: Long): Long = (maxMemoryBytes / 5).coerceIn(MIN_LIMIT_BYTES, MAX_LIMIT_BYTES)
}
