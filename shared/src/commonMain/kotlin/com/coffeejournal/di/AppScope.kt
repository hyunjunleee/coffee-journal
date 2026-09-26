package com.coffeejournal.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine scope that lives as long as the app process (a Koin single). Work that must not stop halfway when a
 * screen is left, such as restoring a backup, is launched here instead of in a viewModelScope.
 */
class AppScope : CoroutineScope {
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
}
