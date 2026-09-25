package com.coffeejournal.ui.misc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Ids
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One of the two photo slots: a file already in the store, or bytes picked in this session. */
sealed interface PhotoSlot {
    data class Existing(val fileName: String) : PhotoSlot
    class Fresh(val bytes: ByteArray) : PhotoSlot
}

data class MiscFormState(
    val type: String,
    val isEdit: Boolean,
    val status: String = MiscStatus.OWNED,
    val name: String = "",
    val notes: String = "",
    val since: String = "",
    val slots: List<PhotoSlot?> = listOf(null, null),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving && !loading
}

class MiscFormViewModel(
    type: String,
    private val itemId: String?,
    private val repo: MiscRepository,
    private val photoStore: PhotoStore,
) : ViewModel() {
    private val _state = MutableStateFlow(MiscFormState(type = type, isEdit = itemId != null, loading = itemId != null))
    val state: StateFlow<MiscFormState> = _state.asStateFlow()
    private var original: MiscItem? = null

    init {
        if (itemId != null) viewModelScope.launch {
            val item = repo.getById(itemId)
            original = item
            if (item == null) {
                _state.update { it.copy(loading = false, isEdit = false) }
            } else {
                _state.update {
                    it.copy(
                        type = item.type,
                        status = item.status.ifBlank { MiscStatus.OWNED },
                        name = item.name,
                        notes = item.notes,
                        since = item.since,
                        slots = List(2) { i -> item.photos.getOrNull(i)?.let { name -> PhotoSlot.Existing(name) } },
                        loading = false,
                    )
                }
            }
        }
    }

    fun setStatus(value: String) { if (value.isNotBlank()) _state.update { it.copy(status = value) } }
    fun setName(value: String) { _state.update { it.copy(name = value) } }
    fun setNotes(value: String) { _state.update { it.copy(notes = value) } }
    fun setSince(value: String) { _state.update { it.copy(since = value) } }

    fun setPhoto(index: Int, bytes: ByteArray) {
        _state.update { s -> s.copy(slots = s.slots.mapIndexed { i, slot -> if (i == index) PhotoSlot.Fresh(bytes) else slot }) }
    }

    fun removePhoto(index: Int) {
        _state.update { s -> s.copy(slots = s.slots.mapIndexed { i, slot -> if (i == index) null else slot }) }
    }

    fun photoPath(fileName: String): String = photoStore.pathFor(fileName)

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val names = s.slots.mapNotNull { slot ->
                    when (slot) {
                        null -> null
                        is PhotoSlot.Existing -> slot.fileName
                        is PhotoSlot.Fresh -> photoStore.save(slot.bytes)
                    }
                }
                val existing = original
                val now = Dates.nowMillis()
                repo.upsert(
                    MiscItem(
                        id = existing?.id ?: Ids.newId(now),
                        type = s.type,
                        name = s.name.trim(),
                        notes = s.notes.trim(),
                        since = s.since,
                        status = s.status.ifBlank { MiscStatus.OWNED },
                        scope = existing?.scope ?: "",
                        location = existing?.location ?: "",
                        favorite = existing?.favorite ?: false,
                        photos = names,
                        createdAt = existing?.createdAt ?: now,
                    ),
                )
                existing?.photos?.filter { it !in names }?.forEach { photoStore.delete(it) }
                _state.update { it.copy(saving = false, done = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}
