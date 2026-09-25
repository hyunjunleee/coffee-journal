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

data class BackupUiState(
    val exporting: Boolean = false,
    val export: ExportResult? = null,
    val saveStatus: SaveStatus? = null,
    val pending: PendingRestore? = null,
    val importing: Boolean = false,
    val importResult: ImportResult? = null,
    val error: String? = null,
) {
    val busy: Boolean get() = exporting || importing
}

class BackupViewModel(private val service: BackupService, private val codec: BackupCodec) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

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
                _state.update { it.copy(exporting = false, error = "백업 중 오류가 발생했어요. (${e.message ?: e::class.simpleName})") }
            }
        }
    }

    fun onSaveResult(ok: Boolean) {
        _state.update { it.copy(saveStatus = if (ok) SaveStatus.SAVED else SaveStatus.FAILED) }
    }

    /** Parses a chosen file; a null text means the file could not be read. */
    fun onFileLoaded(text: String?) {
        if (text == null) {
            _state.update { it.copy(error = "파일을 읽을 수 없어요.") }
            return
        }
        _state.update { it.copy(error = null, importResult = null, export = null, saveStatus = null) }
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.Default) { codec.decode(text) }
                val label = BackupDates.localLabel(snapshot.exportedAt) ?: "날짜 미상"
                _state.update { it.copy(pending = PendingRestore(snapshot, label, snapshot.summaryLine())) }
            } catch (e: BackupFormatException) {
                _state.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "복원 중 오류가 발생했어요. (${e.message ?: e::class.simpleName})") }
            }
        }
    }

    fun cancelRestore() { _state.update { it.copy(pending = null) } }

    fun restore(mode: ImportMode) {
        val pending = _state.value.pending ?: return
        if (_state.value.busy) return
        _state.update { it.copy(pending = null, importing = true, error = null) }
        viewModelScope.launch {
            try {
                val result = service.import(pending.snapshot, mode)
                _state.update { it.copy(importing = false, importResult = result) }
            } catch (e: Exception) {
                _state.update { it.copy(importing = false, error = "복원 중 오류가 발생했어요. (${e.message ?: e::class.simpleName})") }
            }
        }
    }

    fun dismissError() { _state.update { it.copy(error = null) } }
}
