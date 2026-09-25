package com.coffeejournal.ui.extract

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

/** Editable copy of a pantry item (web #bean-pantry-form). */
data class PantryForm(
    val loaded: Boolean = false,
    val isEdit: Boolean = false,
    val name: String = "",
    val roastery: String = "",
    val packageType: String = PackageType.STANDARD,
    val weight: String = "",
    /** Displayed with thousands separators; digits only are stored. */
    val price: String = "",
    val roastLevel: String = "",
    val roastDate: String = "",
    val purchaseDate: String = "",
    val peakStart: String = "",
    val peakEnd: String = "",
    val expectedNotes: List<String> = emptyList(),
    val noteInput: String = "",
    val notes: String = "",
    val error: String? = null,
    val saved: Boolean = false,
) {
    val unitPriceText: String get() = PantryRules.unitPriceText(weight, price)
}

class PantryEditorViewModel(private val itemId: String?, private val pantry: PantryRepository) : ViewModel() {
    private val _form = MutableStateFlow(PantryForm())
    val form: StateFlow<PantryForm> = _form.asStateFlow()
    private var existing: PantryItem? = null

    init {
        viewModelScope.launch {
            val item = itemId?.let { pantry.getById(it) }
            existing = item
            _form.value = if (item == null) PantryForm(loaded = true) else PantryForm(
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
        }
    }

    fun update(transform: PantryForm.() -> PantryForm) = _form.update { it.transform().copy(error = null) }

    fun setPrice(raw: String) = update { copy(price = Prices.formatInput(raw)) }

    fun save() {
        val f = _form.value
        val notes = if (f.noteInput.isNotBlank()) NoteCanon.addChips(f.expectedNotes, f.noteInput) else f.expectedNotes
        val name = f.name.trim()
        if (name.isEmpty()) { _form.update { it.copy(error = "원두 이름을 입력해주세요.") }; return }
        val prev = existing
        val now = Dates.nowMillis()
        val item = PantryItem(
            id = prev?.id ?: Ids.newId(now),
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
            status = prev?.status ?: PantryItem.STATUS_UNOPENED,
            openedAt = prev?.openedAt,
            createdAt = prev?.createdAt ?: now,
            sourceEntryId = prev?.sourceEntryId ?: "",
        )
        viewModelScope.launch {
            pantry.upsert(item)
            _form.update { it.copy(expectedNotes = notes, noteInput = "", saved = true) }
        }
    }

    private fun legacyRoastLevel(value: String): String = when (value) {
        "light" -> "라이트"; "medium" -> "미디엄"; "dark" -> "다크"; else -> value
    }
}
