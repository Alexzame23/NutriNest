package com.zame.nutrinest.ui.menu.config

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.repository.MenuRepository
import com.zame.nutrinest.data.repository.RecipeRepository
import com.zame.nutrinest.domain.planner.GenerationConfig
import com.zame.nutrinest.domain.planner.GenerationResult
import com.zame.nutrinest.domain.planner.MealLock
import com.zame.nutrinest.util.WeekUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MenuConfigUiState(
    val mealsPerDay: Int = 3,
    val slotWeights: Map<MealSlot, Float> = MealSlot.defaultWeights(MealSlot.forMealsPerDay(3)),
    val cheatMeals: Int = 0,
    val maxRepeats: Int = 2,
    val excludedCategories: Set<String> = emptySet(),
    val excludedFoodTypes: Set<String> = emptySet(),
    val excludedRecipeIds: Set<Long> = emptySet(),
    val includedFolders: Set<String> = emptySet(),
    val excludeRecentlyUsed: Boolean = true,
    val locks: List<MealLock> = emptyList(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false
)

class MenuConfigViewModel(
    private val menuRepository: MenuRepository,
    recipeRepository: RecipeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val weekStart: Long = savedStateHandle["weekStart"] ?: WeekUtils.weekStartOf()

    private val _state = MutableStateFlow(MenuConfigUiState())
    val state = _state.asStateFlow()

    val allRecipes = recipeRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allFolders = recipeRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onMealsPerDayChange(count: Int) = update {
        val slots = MealSlot.forMealsPerDay(count)
        copy(
            mealsPerDay = count,
            slotWeights = MealSlot.defaultWeights(slots),
            // Locks referencing slots that no longer exist are dropped.
            locks = locks.filter { it.slot in slots }
        )
    }

    fun onSlotWeightChange(slot: MealSlot, weight: Float) = update {
        copy(slotWeights = slotWeights + (slot to weight.coerceIn(0.05f, 1f)))
    }

    fun onCheatMealsChange(count: Int) = update { copy(cheatMeals = count.coerceIn(0, 7)) }
    fun onMaxRepeatsChange(count: Int) = update { copy(maxRepeats = count.coerceIn(1, 7)) }
    fun onExcludeRecentToggle() = update { copy(excludeRecentlyUsed = !excludeRecentlyUsed) }

    fun toggleCategory(category: String) = update {
        copy(excludedCategories = excludedCategories.toggle(category))
    }

    fun toggleFoodType(foodType: String) = update {
        copy(excludedFoodTypes = excludedFoodTypes.toggle(foodType))
    }

    fun toggleRecipeExclusion(recipeId: Long) = update {
        copy(
            excludedRecipeIds =
                if (recipeId in excludedRecipeIds) excludedRecipeIds - recipeId
                else excludedRecipeIds + recipeId
        )
    }

    fun toggleFolder(folder: String) = update {
        copy(includedFolders = includedFolders.toggle(folder))
    }

    fun addLock(days: Set<Int>, slot: MealSlot, recipe: RecipeEntity) = update {
        copy(locks = locks + MealLock(days, slot, recipe.id))
    }

    fun removeLock(index: Int) = update {
        copy(locks = locks.filterIndexed { i, _ -> i != index })
    }

    fun dismissError() = update { copy(errorMessage = null) }

    fun generate() {
        val s = _state.value
        update { copy(isGenerating = true) }
        viewModelScope.launch {
            val result = menuRepository.generate(
                weekStart = weekStart,
                config = GenerationConfig(
                    slotWeights = s.slotWeights,
                    cheatMeals = s.cheatMeals,
                    maxRecipeRepeats = s.maxRepeats,
                    excludedCategories = s.excludedCategories,
                    excludedFoodTypes = s.excludedFoodTypes,
                    excludedRecipeIds = s.excludedRecipeIds,
                    includedFolders = s.includedFolders,
                    excludeRecentlyUsed = s.excludeRecentlyUsed,
                    locks = s.locks
                )
            )
            when (result) {
                is GenerationResult.Success -> update { copy(isGenerating = false, done = true) }
                is GenerationResult.Failure -> update {
                    copy(isGenerating = false, errorMessage = result.reason)
                }
            }
        }
    }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value

    private inline fun update(block: MenuConfigUiState.() -> MenuConfigUiState) {
        _state.value = _state.value.block()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                MenuConfigViewModel(
                    app.container.menuRepository,
                    app.container.recipeRepository,
                    createSavedStateHandle()
                )
            }
        }
    }
}
