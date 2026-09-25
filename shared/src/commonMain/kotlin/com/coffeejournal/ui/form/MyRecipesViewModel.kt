package com.coffeejournal.ui.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffeejournal.data.repo.MyRecipeRepository
import com.coffeejournal.domain.model.MyRecipe
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.domain.rules.Ids
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Fields of the "+ 새 레시피 만들기" form (web #new-my-recipe-form). */
data class MyRecipeDraft(
    val name: String = "",
    val dripper: String = "",
    val filter: String = "",
    val grind: String = "",
    val dose: String = "",
    val water: String = "",
    val temp: String = "",
    val time: String = "",
)

class MyRecipesViewModel(private val repo: MyRecipeRepository) : ViewModel() {
    val recipes: StateFlow<List<MyRecipe>> = repo.observeAll()
        .map { list -> list.sortedByDescending { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: String) { viewModelScope.launch { repo.delete(id) } }

    /** Returns false when the name is missing (the only required field). */
    fun create(draft: MyRecipeDraft): Boolean {
        val name = draft.name.trim()
        if (name.isEmpty()) return false
        viewModelScope.launch {
            val now = Dates.nowMillis()
            repo.upsert(
                MyRecipe(
                    id = Ids.newId(now), name = name, fromEntryId = null, beanName = "", rating = 0,
                    dose = draft.dose.trim(), water = draft.water.trim(), temp = draft.temp.trim(),
                    dripper = draft.dripper.trim(), filter = draft.filter.trim(), grind = draft.grind.trim(), time = draft.time.trim(),
                    steps = emptyList(), createdAt = now,
                )
            )
        }
        return true
    }
}
