package com.coffeejournal.ui.misc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.photo.PhotoStore
import com.coffeejournal.data.repo.MiscRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MiscUiState(
    val type: String = MiscListLogic.ALL,
    val sort: String = MiscListLogic.SORT_FIXED,
    val sections: List<MiscListLogic.Section> = emptyList(),
    val total: Int = 0,
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && sections.isEmpty()
}

class MiscViewModel(private val repo: MiscRepository, private val photoStore: PhotoStore) : ViewModel() {
    private val type = MutableStateFlow(MiscListLogic.ALL)
    private val sort = MutableStateFlow(MiscListLogic.SORT_FIXED)

    val state: StateFlow<MiscUiState> = combine(repo.observeAll(), type, sort) { items, t, s ->
        MiscUiState(
            type = t,
            sort = s,
            sections = MiscListLogic.sections(items, t, s),
            total = MiscListLogic.equipmentOnly(items).size,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MiscUiState())

    fun selectType(value: String) { type.value = value }
    fun selectSort(value: String) { sort.value = value }

    fun delete(id: String) { viewModelScope.launch { repo.delete(id) } }

    fun photoPath(fileName: String): String = photoStore.pathFor(fileName)
}
