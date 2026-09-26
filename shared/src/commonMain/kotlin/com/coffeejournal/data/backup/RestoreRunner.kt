package com.coffeejournal.data.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a restore stands; the backup screen renders it, including after it was left and opened again. */
sealed interface RestoreState {
    data object Idle : RestoreState
    /** Photo files stored so far ([done] of [total]); the database changes follow in one transaction. */
    data class Running(val done: Int, val total: Int) : RestoreState
    data class Finished(val result: ImportResult) : RestoreState
    data class Failed(val detail: String) : RestoreState
}

/**
 * Runs [BackupService.import] in an application-wide [scope], so leaving the backup screen (system back, the title
 * bar arrow) clears the screen's ViewModel without cancelling the restore halfway. One restore at a time.
 */
class RestoreRunner(private val service: BackupService, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val state: StateFlow<RestoreState> = _state.asStateFlow()

    /** Starts restoring [snapshot]; false when a restore is already running. */
    fun start(snapshot: BackupSnapshot, mode: ImportMode): Boolean {
        val current = _state.value
        if (current is RestoreState.Running || !_state.compareAndSet(current, RestoreState.Running(0, snapshot.photoCount))) return false
        scope.launch {
            _state.value = try {
                RestoreState.Finished(service.import(snapshot, mode) { done, total -> _state.value = RestoreState.Running(done, total) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RestoreState.Failed(e.message ?: e::class.simpleName ?: "알 수 없는 오류")
            } catch (e: Error) {
                // the restore is all-or-nothing, so running out of memory while storing photos leaves the data as it was
                if (generateSequence<Throwable>(e) { it.cause }.take(8).none { it::class.simpleName == "OutOfMemoryError" }) throw e
                RestoreState.Failed(OUT_OF_MEMORY)
            }
        }
        return true
    }

    companion object {
        const val OUT_OF_MEMORY = "메모리가 부족해서 복원하지 못했어요."
    }

    /** The screen has shown the outcome; the next visit starts clean. A running restore is left alone. */
    fun acknowledge() {
        _state.update { if (it is RestoreState.Running) it else RestoreState.Idle }
    }
}
