package com.coffeejournal.ui.extract

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.domain.model.PackageType
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Ids
import com.coffeejournal.domain.rules.NoteCanon
import com.coffeejournal.domain.rules.Packages
import com.coffeejournal.domain.rules.PantryRules
import com.coffeejournal.domain.rules.Prices
import com.coffeejournal.ui.theme.SavedFormState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

data class PantryUiState(
    val loaded: Boolean = false,
    val sortMode: String = PantryListing.SORT_REGISTERED,
    val unopened: List<PantryItem> = emptyList(),
    val opened: List<PantryItem> = emptyList(),
)

class PantryViewModel(private val pantry: PantryRepository) : ViewModel() {
    private val sortMode = MutableStateFlow(PantryListing.SORT_REGISTERED)

    val state: StateFlow<PantryUiState> = combine(pantry.observeAll(), sortMode) { items, sort ->
        PantryUiState(loaded = true, sortMode = sort, unopened = PantryListing.unopened(items, sort), opened = PantryListing.opened(items))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryUiState())

    fun setSortMode(mode: String) { sortMode.value = mode }

    fun open(item: PantryItem) { viewModelScope.launch { pantry.upsert(PantryListing.markOpened(item, Dates.nowMillis())) } }

    fun cancelOpen(item: PantryItem) { viewModelScope.launch { pantry.upsert(PantryListing.markUnopened(item)) } }

    fun delete(item: PantryItem) { viewModelScope.launch { pantry.delete(item.id) } }
}

/** Editable copy of a pantry item (web #bean-pantry-form). Serializable so the typed input survives process death. */
@Serializable
data class PantryForm(
    val loaded: Boolean = false,
    val isEdit: Boolean = false,
    /** Id a new bag is stored under: fixed for this form, so a repeated save upserts the same row. */
    val draftId: String = "",
    val name: String = "",
    val roastery: String = "",
    val packageType: String = PackageType.STANDARD,
    val weight: String = "",
    /** As typed; thousands separators are added when the field loses focus (web blur), digits only are stored. */
    val price: String = "",
    val roastLevel: String = "",
    val roastDate: String = "",
    val purchaseDate: String = "",
    val peakStart: String = "",
    val peakEnd: String = "",
    val expectedNotes: List<String> = emptyList(),
    val noteInput: String = "",
    val notes: String = "",
    @Transient val error: String? = null,
    /** A save is running or has succeeded: 저장 is disabled and further saves are ignored. */
    @Transient val saving: Boolean = false,
    @Transient val saved: Boolean = false,
) {
    val unitPriceText: String get() = PantryRules.unitPriceText(weight, price)
}

class PantryEditorViewModel(
    private val itemId: String?,
    private val pantry: PantryRepository,
    savedState: SavedStateHandle? = null,
) : ViewModel() {
    private val kept = SavedFormState(savedState, "pantryForm", PantryForm.serializer())
    private val restored: PantryForm? = kept.restore()
    private val _form = MutableStateFlow(restored ?: PantryForm(loaded = itemId == null, isEdit = itemId != null, draftId = Ids.newId()))
    val form: StateFlow<PantryForm> = _form.asStateFlow()
    private var existing: PantryItem? = null

    /** The form as it was opened (blank, or the stored bag), kept next to the input: leaving asks only when they differ. */
    private val keptOpened = SavedFormState(savedState, "pantryForm.opened", PantryForm.serializer())
    private var opened: PantryForm? = keptOpened.restore() ?: _form.value.takeIf { itemId == null }?.also(keptOpened::put)

    init {
        kept.keep(viewModelScope, _form)
        if (itemId != null) viewModelScope.launch {
            val item = pantry.getById(itemId)
            existing = item
            if (restored != null) return@launch
            _form.value = if (item == null) _form.value.copy(loaded = true, isEdit = false) else _form.value.copy(
                loaded = true,
                isEdit = true,
                name = item.name,
                roastery = item.roastery,
                packageType = Packages.pantryPackageType(item),
                weight = item.weight,
                price = Prices.formatInput(item.price),
                roastLevel = legacyRoastLevel(item.roastLevel),
                roastDate = item.roastDate,
                purchaseDate = item.purchaseDate,
                peakStart = item.peakStart,
                peakEnd = item.peakEnd,
                expectedNotes = NoteCanon.parseChips(item.expectedNotes),
                notes = item.notes,
            )
            _form.value.also { opened = it; keptOpened.put(it) }
        }
    }

    /** Whether the input differs from the form as it was opened. */
    fun hasChanges(): Boolean = opened?.let { !kept.sameInput(it, _form.value) } ?: false

    fun update(transform: PantryForm.() -> PantryForm) = _form.update { it.transform().copy(error = null) }

    /** Typing keeps the text as it is; reformatting on every key moved the cursor and swapped digits. */
    fun setPrice(raw: String) = update { copy(price = raw) }

    /** Web pantry-price blur: "18000" → "18,000". */
    fun formatPrice() = _form.update { it.copy(price = Prices.formatInput(it.price)) }

    /**
     * One save per form: taps while it runs, or after it succeeded (the screen is closing), are ignored. The form
     * owns only what it shows; the bag's status, opening time, creation time and source record belong to the row as
     * it is now — it may have been opened, moved to 드립백 / 소량 or linked to a record since the form was loaded —
     * so the row is read again right before it is written (gap #11).
     */
    fun save() {
        val f = _form.value
        if (!f.loaded || f.saving || f.saved) return
        val notes = if (f.noteInput.isNotBlank()) NoteCanon.addChips(f.expectedNotes, f.noteInput) else f.expectedNotes
        val name = f.name.trim()
        if (name.isEmpty()) { _form.update { it.copy(error = "원두 이름을 입력해주세요.") }; return }
        _form.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                // an edit restored after process death may be saved before the row finished loading
                val id = existing?.id ?: itemId?.takeIf { f.isEdit } ?: f.draftId.ifBlank { Ids.newId() }
                val current = pantry.getById(id) ?: existing
                pantry.upsert(formItem(f, name, notes, id, current))
                _form.update { it.copy(expectedNotes = notes, noteInput = "", saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _form.update { it.copy(saving = false, error = "저장하지 못했어요: ${e.message ?: "알 수 없는 오류"}") }
            }
        }
    }

    /** The row to write: the form's fields over [current] (the stored row, null for a new bag). */
    private fun formItem(f: PantryForm, name: String, notes: List<String>, id: String, current: PantryItem?): PantryItem {
        val now = Dates.nowMillis()
        return PantryItem(
            id = id,
            name = name,
            roastery = f.roastery.trim(),
            packageType = f.packageType,
            weight = f.weight.trim(),
            price = Prices.normalize(f.price),
            roastLevel = f.roastLevel,
            roastDate = f.roastDate,
            purchaseDate = f.purchaseDate,
            peakStart = f.peakStart,
            peakEnd = f.peakEnd,
            expectedNotes = NoteCanon.joinChips(notes),
            notes = f.notes.trim(),
            status = current?.status ?: PantryItem.STATUS_UNOPENED,
            openedAt = current?.openedAt,
            createdAt = current?.createdAt ?: now,
            sourceEntryId = current?.sourceEntryId ?: "",
        )
    }

    private fun legacyRoastLevel(value: String): String = when (value) {
        "light" -> "라이트"; "medium" -> "미디엄"; "dark" -> "다크"; else -> value
    }
}
