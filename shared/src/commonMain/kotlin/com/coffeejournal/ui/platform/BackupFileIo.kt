package com.coffeejournal.ui.platform

import androidx.compose.runtime.Composable

/** Opens the platform "save as" picker for a JSON document; [onResult] reports whether the file was written. */
@Composable
expect fun rememberJsonSaver(onResult: (Boolean) -> Unit): (suggestedName: String, json: String) -> Unit

/** Opens the platform document picker and reads the chosen file as text; null when it could not be read. */
@Composable
expect fun rememberJsonOpener(onLoaded: (String?) -> Unit): () -> Unit

/** Shows the platform share sheet with the given text (large payloads are shared as a file). */
expect fun shareText(title: String, text: String)
