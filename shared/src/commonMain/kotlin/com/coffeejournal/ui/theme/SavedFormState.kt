package com.coffeejournal.ui.theme

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

    /** Writes [value] once, e.g. the form as it was opened, which the leave question compares the input with. */
    fun put(value: T) {
        handle?.set(key, json.encodeToString(serializer, value))
    }

    /** Whether [a] and [b] hold the same input: what is kept counts; `@Transient` flags (errors, saving) do not. */
    fun sameInput(a: T, b: T): Boolean = a == b || json.encodeToString(serializer, a) == json.encodeToString(serializer, b)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
