package com.coffeejournal.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.backup.BackupCodec
import com.coffeejournal.data.backup.BackupDates
import com.coffeejournal.data.backup.BackupFormatException
import com.coffeejournal.data.backup.BackupService
import com.coffeejournal.data.backup.BackupSnapshot
import com.coffeejournal.data.backup.ExportResult
import com.coffeejournal.data.backup.ImportMode
import com.coffeejournal.data.backup.ImportResult
import com.coffeejournal.data.backup.RestoreRunner
import com.coffeejournal.data.backup.RestoreState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A parsed backup waiting for the user's confirmation. */
data class PendingRestore(val snapshot: BackupSnapshot, val exportedAtLabel: String, val summary: String)

enum class SaveStatus { SAVED, FAILED }

/**
 * An error panel: [restore] picks the title ("복원 중 …" / "백업 중 오류가 발생했어요") and [detail] is the message shown
 * under it (web: the title, then `e.message || String(e)`). [untouched] says a failed restore changed nothing.
 */
data class BackupError(val restore: Boolean, val detail: String, val untouched: Boolean = false)

data class BackupUiState(
    val exporting: Boolean = false,
    val export: ExportResult? = null,
    val saveStatus: SaveStatus? = null,
    val pending: PendingRestore? = null,
    val importing: Boolean = false,
    /** Photo files stored so far while restoring (done to total). */
    val importProgress: Pair<Int, Int>? = null,
    val importResult: ImportResult? = null,
    val error: BackupError? = null,
) {
    val busy: Boolean get() = exporting || importing
}

class BackupViewModel(
    private val service: BackupService,
    private val codec: BackupCodec,
    private val runner: RestoreRunner,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        // The restore itself runs in the app scope; this screen (a fresh one after leaving and coming back) only watches it.
        viewModelScope.launch {
            runner.state.collect { st ->
                when (st) {
                    RestoreState.Idle -> Unit
                    is RestoreState.Running -> _state.update { it.copy(importing = true, importProgress = st.done to st.total) }
                    is RestoreState.Finished -> {
                        _state.update { it.copy(importing = false, importProgress = null, importResult = st.result) }
                        runner.acknowledge()
                    }
                    is RestoreState.Failed -> {
                        _state.update { it.copy(importing = false, importProgress = null, error = BackupError(restore = true, detail = st.detail, untouched = true)) }
                        runner.acknowledge()
                    }
                }
            }
        }
    }

    /** Builds the backup; [onReady] receives it so the screen can open the platform "save as" picker. */
    fun export(onReady: (ExportResult) -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(exporting = true, export = null, saveStatus = null, importResult = null, error = null) }
        viewModelScope.launch {
            try {
                val result = service.export()
                _state.update { it.copy(exporting = false, export = result) }
                onReady(result)
            } catch (e: Exception) {
                _state.update { it.copy(exporting = false, error = BackupError(restore = false, detail = e.message ?: e::class.simpleName ?: "")) }
            }
        }
    }

    fun onSaveResult(ok: Boolean) {
        _state.update { it.copy(saveStatus = if (ok) SaveStatus.SAVED else SaveStatus.FAILED) }
    }

    /** Parses a chosen file completely before anything is written; a null text means the file could not be read. */
    fun onFileLoaded(text: String?) {
        if (text == null) {
            _state.update { it.copy(error = BackupError(restore = true, detail = "파일을 읽을 수 없어요.")) }
            return
        }
        _state.update { it.copy(error = null, importResult = null, export = null, saveStatus = null) }
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.Default) { codec.decode(text) }
                val label = BackupDates.localLabel(snapshot.exportedAt) ?: "날짜 미상"
                _state.update { it.copy(pending = PendingRestore(snapshot, label, snapshot.summaryLine())) }
            } catch (e: BackupFormatException) {
                _state.update { it.copy(error = BackupError(restore = true, detail = e.message ?: "")) }
            } catch (e: Exception) {
                _state.update { it.copy(error = BackupError(restore = true, detail = e.message ?: e::class.simpleName ?: "")) }
            }
        }
    }

    fun cancelRestore() { _state.update { it.copy(pending = null) } }

    /** Hands the restore to the app-scoped [RestoreRunner]; leaving this screen does not stop it. */
    fun restore(mode: ImportMode) {
        val pending = _state.value.pending ?: return
        if (_state.value.busy) return
        _state.update { it.copy(pending = null, importing = true, importProgress = null, importResult = null, error = null) }
        runner.start(pending.snapshot, mode)
    }

    fun dismissError() { _state.update { it.copy(error = null) } }
}
