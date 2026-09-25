package com.coffeejournal.ui.form

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Keeps a form's typed input in the destination's [SavedStateHandle] as JSON, so it survives process death (for
 * example while the camera app is in front). [handle] is null in plain unit tests; then nothing is kept.
 */
class SavedFormState<T>(private val handle: SavedStateHandle?, private val key: String, private val serializer: KSerializer<T>) {

    /** The input kept before the process died, or null (nothing kept, or written by an older app version). */
    fun restore(): T? = handle?.get<String>(key)?.let { text -> runCatching { json.decodeFromString(serializer, text) }.getOrNull() }

    /** Writes every later value of [state] (the first one is the starting value) until [scope] ends. */
    fun keep(scope: CoroutineScope, state: Flow<T>) {
        val h = handle ?: return
        state.drop(1).onEach { h[key] = json.encodeToString(serializer, it) }.launchIn(scope)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
