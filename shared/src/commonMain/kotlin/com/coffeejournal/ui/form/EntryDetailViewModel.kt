package com.coffeejournal.ui.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.data.repo.BeanMetaRepository
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.MyRecipeRepository
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.domain.rules.BeanNames
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Ids
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailEvent {
    data object Deleted : DetailEvent
    data class RecipeSaved(val name: String) : DetailEvent
}

class EntryDetailViewModel(
    private val entryId: String,
    private val entries: EntryRepository,
    private val beanMeta: BeanMetaRepository,
    private val myRecipes: MyRecipeRepository,
    private val photos: PhotoStore,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val entry: Entry? = null,
        /** Other records of the same bean (web siblings), used to complete missing bag info. */
        val siblings: List<Entry> = emptyList(),
        val isBest: Boolean = false,
    )

    private var deleting = false

    val state: StateFlow<UiState> = combine(entries.observeAll(), beanMeta.observeBest()) { all, best ->
        val en = all.firstOrNull { it.id == entryId }
        if (en == null) UiState(loading = deleting, entry = null)
        else {
            val key = BeanNames.coreBeanName(en.name)
            UiState(
                loading = false,
                entry = en,
                siblings = if (key.isBlank()) emptyList() else all.filter { it.id != en.id && BeanNames.coreBeanName(it.name) == key },
                isBest = best[key] == en.id,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private val _events = MutableSharedFlow<DetailEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<DetailEvent> = _events

    fun photoPath(fileName: String): String = "file://" + photos.pathFor(fileName)

    fun delete() {
        deleting = true
        viewModelScope.launch {
            entries.delete(entryId)
            _events.emit(DetailEvent.Deleted)
        }
    }

    fun toggleBest() {
        val s = state.value
        val en = s.entry ?: return
        val key = BeanNames.coreBeanName(en.name)
        if (key.isBlank()) return
        viewModelScope.launch { beanMeta.setBest(key, if (s.isBest) null else en.id) }
    }

    /** Web saveAsMyRecipe: the record's brew parameters and steps become a reusable recipe. */
    fun saveAsMyRecipe(name: String) {
        val en = state.value.entry ?: return
        if (en.steps.isEmpty()) return
        val finalName = name.trim().ifBlank { EntryDisplay.defaultRecipeName(en) }
        viewModelScope.launch {
            val now = Dates.nowMillis()
            myRecipes.upsert(
                MyRecipe(
                    id = Ids.newId(now), name = finalName, fromEntryId = en.id, beanName = en.name, rating = 0,
                    dose = en.dose, water = en.water, temp = en.temp, dripper = en.dripper, filter = en.filter, grind = en.grind, time = en.time,
                    steps = en.steps, createdAt = now,
                )
            )
            _events.emit(DetailEvent.RecipeSaved(finalName))
        }
    }
}
