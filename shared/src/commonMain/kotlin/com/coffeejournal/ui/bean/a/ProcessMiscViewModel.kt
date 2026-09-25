package com.coffeejournal.ui.bean.a

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscType
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Ids
import kotlinx.coroutines.launch

/** Add / edit / delete of the "내가 마셔본 가공 방식" list (web makeFlatListTab('process')). */
class ProcessMiscViewModel(private val misc: MiscRepository) : ViewModel() {
    fun save(editingId: String?, existing: List<MiscItem>, name: String, notes: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = editingId?.let { id -> existing.firstOrNull { it.id == id } }
            val item = current?.copy(name = trimmed, notes = notes.trim())
                ?: MiscItem(id = Ids.newId(), type = MiscType.PROCESS, name = trimmed, notes = notes.trim(), createdAt = Dates.nowMillis())
            misc.upsert(item)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { misc.delete(id) }
    }
}
