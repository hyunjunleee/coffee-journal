package com.coffeejournal.ui.theme

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

/** Where screen state is derived from the database lists: never the main thread (gap #10). */
val DerivationDispatcher: CoroutineDispatcher get() = Dispatchers.Default

/**
 * Derives screen state from [this] off the main thread: [transform] (and everything upstream, such as a `combine` of
 * repository flows) runs on [DerivationDispatcher], and only the newest input counts — a derivation still running
 * when newer input arrives is cancelled at its next suspension point and its result dropped. A `stateIn` on
 * `viewModelScope` after it then does nothing on the main thread but publish finished states.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T, R> Flow<T>.deriveOffMain(transform: suspend (T) -> R): Flow<R> = mapLatest(transform).flowOn(DerivationDispatcher)
