package com.coffeejournal.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.repo.StudyRepository
import com.coffeejournal.domain.model.ClassType
import com.coffeejournal.domain.model.CoffeeClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pure helpers for the classes list (web renderClasses / classHtml). */
internal object ClassLists {
    const val ALL = "all"
    val filters = listOf(ALL, ClassType.ONEDAY, ClassType.RECURRING)
    val filterLabels = mapOf(ALL to "전체", ClassType.ONEDAY to "원데이 클래스", ClassType.RECURRING to "주/월별 클래스")

    fun normalizedType(c: CoffeeClass): String = if (c.classType == ClassType.RECURRING) ClassType.RECURRING else ClassType.ONEDAY

    fun typeLabel(c: CoffeeClass): String = filterLabels.getValue(normalizedType(c))

    fun filter(list: List<CoffeeClass>, type: String): List<CoffeeClass> =
        if (type == ALL) list else list.filter { normalizedType(it) == type }

    /** Newest date first ((startDate || date) descending), ties broken by createdAt descending. */
    fun sorted(list: List<CoffeeClass>): List<CoffeeClass> =
        list.sortedWith(compareByDescending<CoffeeClass> { it.startDate.ifBlank { it.date } }.thenByDescending { it.createdAt })

    /** "2026년 9월 21일" for ISO input, otherwise the raw value. */
    fun dateKo(value: String): String {
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(value.trim()) ?: return value.trim()
        val (y, mo, d) = m.destructured
        return "${y.toInt()}년 ${mo.toInt()}월 ${d.toInt()}일"
    }

    fun dateLabel(c: CoffeeClass): String =
        if (normalizedType(c) == ClassType.RECURRING) listOf(c.startDate, c.endDate).map(::dateKo).filter { it.isNotBlank() }.joinToString(" ~ ")
        else dateKo(c.date)
}

internal data class ClassesUiState(
    val filter: String = ClassLists.ALL,
    val classes: List<CoffeeClass> = emptyList(),
    val totalCount: Int = 0,
)

class ClassesViewModel(private val repo: StudyRepository) : ViewModel() {
    private val filter = MutableStateFlow(ClassLists.ALL)

    internal val state: StateFlow<ClassesUiState> = combine(filter, repo.observeClasses()) { f, all ->
        ClassesUiState(f, ClassLists.sorted(ClassLists.filter(all, f)), all.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClassesUiState())

    fun setFilter(value: String) { filter.value = value }
    fun delete(id: String) { viewModelScope.launch { repo.deleteClass(id) } }
}
