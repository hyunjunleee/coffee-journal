package com.coffeejournal.ui.misc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.data.repo.MiscRepository
import com.coffeejournal.domain.model.MiscItem
import com.coffeejournal.domain.model.MiscStatus
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Ids
import com.coffeejournal.ui.theme.SavedFormState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

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
    /** A save is running or has succeeded (the screen is closing): 저장 stays disabled. */
    val saving: Boolean = false,
    val done: Boolean = false,
    /** Id a new item is stored under: fixed for this form, so a repeated save could only upsert the same row. */
    val draftId: String = "",
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving && !loading && !done
}

/**
 * The part of the form kept across process death (the camera app may push this app out of memory). Photos picked
 * but not saved yet are left out, like the record form; stored photo file names are kept.
 */
@Serializable
internal data class MiscFormDraft(
    val type: String,
    val isEdit: Boolean,
    val status: String,
    val name: String,
    val notes: String,
    val since: String,
    val storedPhotos: List<String?>,
    val draftId: String,
)

private fun MiscFormState.toDraft() = MiscFormDraft(
    type, isEdit, status, name, notes, since, slots.map { (it as? PhotoSlot.Existing)?.fileName }, draftId,
)

private fun MiscFormDraft.toState(loading: Boolean) = MiscFormState(
    type = type, isEdit = isEdit, status = status, name = name, notes = notes, since = since,
    slots = List(2) { i -> storedPhotos.getOrNull(i)?.let { PhotoSlot.Existing(it) } }, loading = loading, draftId = draftId,
)

class MiscFormViewModel(
    type: String,
    private val itemId: String?,
    private val repo: MiscRepository,
    private val photoStore: PhotoStore,
    savedState: SavedStateHandle? = null,
) : ViewModel() {
    private val kept = SavedFormState(savedState, "miscForm", MiscFormDraft.serializer())
    private val restored: MiscFormDraft? = kept.restore()
    private val _state = MutableStateFlow(
        restored?.toState(loading = itemId != null)
            ?: MiscFormState(type = type, isEdit = itemId != null, loading = itemId != null, draftId = Ids.newId()),
    )
    val state: StateFlow<MiscFormState> = _state.asStateFlow()
    private var original: MiscItem? = null

    init {
        kept.keep(viewModelScope, _state.map { it.toDraft() }.distinctUntilChanged())
        if (itemId != null) viewModelScope.launch {
            val item = repo.getById(itemId)
            original = item
            when {
                item == null -> _state.update { it.copy(loading = false, isEdit = false) }
                // the restored input wins; only the stored item behind it (id, scope, created date) was needed
                restored != null -> _state.update { it.copy(loading = false) }
                else -> _state.update {
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

    /**
     * Saves once: taps while the save runs, or after it succeeded while the screen closes, are ignored. The save also
     * finishes when the screen is left mid-way, so photo files and the item row never get out of step.
     */
    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                val created = mutableListOf<String>()
                try {
                    val names = s.slots.mapNotNull { slot ->
                        when (slot) {
                            null -> null
                            is PhotoSlot.Existing -> slot.fileName
                            is PhotoSlot.Fresh -> photoStore.save(slot.bytes).also { created += it }
                        }
                    }
                    // the row as it is now: its favourite mark may have changed since the form was loaded (gap #11)
                    val existing = original?.let { repo.getById(it.id) ?: it }
                    val now = Dates.nowMillis()
                    repo.upsert(
                        MiscItem(
                            id = existing?.id ?: s.draftId.ifBlank { Ids.newId(now) },
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
                    existing?.photos?.filter { it !in names }?.forEach { runCatching { photoStore.delete(it) } }
                    _state.update { it.copy(done = true) }
                } catch (e: Exception) {
                    created.forEach { runCatching { photoStore.delete(it) } }
                    _state.update { it.copy(saving = false) }
                }
            }
        }
    }
}
