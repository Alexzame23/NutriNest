package com.zame.nutrinest.ui.menu.config

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.MealCourse
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.MenuScope
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.repository.MenuRepository
import com.zame.nutrinest.data.repository.RecipeRepository
import com.zame.nutrinest.domain.planner.FailureReason
import com.zame.nutrinest.domain.planner.GenerationConfig
import com.zame.nutrinest.domain.planner.GenerationResult
import com.zame.nutrinest.domain.planner.MealLock
import com.zame.nutrinest.domain.planner.MealOverride
import com.zame.nutrinest.util.WeekUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MenuConfigUiState(
    val mealsPerDay: Int = 3,
    val slotWeights: Map<MealSlot, Float> = MealSlot.defaultWeights(MealSlot.forMealsPerDay(3)),
    val scope: MenuScope = MenuScope.SHARED,
    /** Slots served as a first course plus a main. */
    val twoCourseSlots: Set<MealSlot> = emptySet(),
    /** Slots that finish with a dessert. */
    val dessertSlots: Set<MealSlot> = emptySet(),
    /** Only meaningful for [MenuScope.PER_MEAL]. */
    val overrides: Set<MealOverride> = emptySet(),
    val cheatMeals: Int = 0,
    val maxRepeats: Int = 2,
    val excludedCategories: Set<String> = emptySet(),
    val excludedIngredientCategories: Set<String> = emptySet(),
    val excludedRecipeIds: Set<Long> = emptySet(),
    /** null means all books; empty means the user deliberately selected none. */
    val includedFolders: Set<String?>? = null,
    val excludeRecentlyUsed: Boolean = true,
    val locks: List<MealLock> = emptyList(),
    val isGenerating: Boolean = false,
    val error: FailureReason? = null,
    val done: Boolean = false
) {
    val slots: List<MealSlot> get() = slotWeights.keys.sortedBy { it.ordinal }

    /** Stable order for the exception list, which is otherwise a set. */
    val sortedOverrides: List<MealOverride>
        get() = overrides.sortedWith(
            compareBy<MealOverride>({ it.day }, { it.slot.ordinal }, { it.profileId })
        )

    /** Slots that can take a first course or a dessert at all. */
    val courseCapableSlots: List<MealSlot> get() = slots.filter { it.allowsCourses }

    /** The structure handed to the generator; single-course slots are implicit. */
    val slotCourses: Map<MealSlot, List<MealCourse>>
        get() = courseCapableSlots
            .associateWith {
                MealCourse.structure(it in twoCourseSlots, it in dessertSlots)
            }
            .filterValues { it != listOf(MealCourse.SINGLE) }
}

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

    /** The household. With one profile the scope options don't apply. */
    val profiles = menuRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onMealsPerDayChange(count: Int) = update {
        val slots = MealSlot.forMealsPerDay(count)
        copy(
            mealsPerDay = count,
            slotWeights = MealSlot.defaultWeights(slots),
            // Anything pinned to a slot that no longer exists is dropped.
            locks = locks.filter { it.slot in slots },
            overrides = overrides.filterTo(mutableSetOf()) { it.slot in slots },
            twoCourseSlots = twoCourseSlots.filterTo(mutableSetOf()) { it in slots },
            dessertSlots = dessertSlots.filterTo(mutableSetOf()) { it in slots }
        )
    }

    fun onSlotWeightChange(slot: MealSlot, weight: Float) = update {
        copy(slotWeights = slotWeights + (slot to weight.coerceIn(0.05f, 1f)))
    }

    fun onScopeChange(scope: MenuScope) = update {
        // Overrides only mean anything in the per-meal scope.
        copy(scope = scope, overrides = if (scope == MenuScope.PER_MEAL) overrides else emptySet())
    }

    fun toggleTwoCourses(slot: MealSlot) = update {
        copy(twoCourseSlots = twoCourseSlots.toggle(slot))
    }

    fun toggleDessert(slot: MealSlot) = update {
        copy(dessertSlots = dessertSlots.toggle(slot))
    }

    fun addOverride(days: Set<Int>, slot: MealSlot, profileId: Long) = update {
        copy(overrides = overrides + days.map { MealOverride(it, slot, profileId) })
    }

    fun removeOverride(override: MealOverride) = update {
        copy(overrides = overrides - override)
    }

    fun onCheatMealsChange(count: Int) = update { copy(cheatMeals = count.coerceIn(0, 7)) }
    fun onMaxRepeatsChange(count: Int) = update { copy(maxRepeats = count.coerceIn(1, 7)) }
    fun onExcludeRecentToggle() = update { copy(excludeRecentlyUsed = !excludeRecentlyUsed) }

    fun toggleCategory(category: String) = update {
        copy(excludedCategories = excludedCategories.toggle(category))
    }

    fun toggleIngredientCategory(categoryKey: String) = update {
        copy(excludedIngredientCategories = excludedIngredientCategories.toggle(categoryKey))
    }

    fun toggleRecipeExclusion(recipeId: Long) = update {
        copy(
            excludedRecipeIds =
                if (recipeId in excludedRecipeIds) excludedRecipeIds - recipeId
                else excludedRecipeIds + recipeId
        )
    }

    fun toggleFolder(folder: String?, availableFolders: Set<String?>) = update {
        val selected = includedFolders ?: availableFolders
        copy(includedFolders = selected.toggle(folder))
    }

    fun selectAllFolders() = update { copy(includedFolders = null) }

    fun deselectAllFolders() = update { copy(includedFolders = emptySet()) }

    /** Enables or disables a recipe without changing the selected books. */
    fun setRecipeEnabled(recipeId: Long, enabled: Boolean) = update {
        copy(
            excludedRecipeIds = if (enabled) excludedRecipeIds - recipeId
            else excludedRecipeIds + recipeId
        )
    }

    fun enableRecipes(recipeIds: Set<Long>) = update {
        copy(excludedRecipeIds = excludedRecipeIds - recipeIds)
    }

    fun disableRecipes(recipeIds: Set<Long>) = update {
        copy(excludedRecipeIds = excludedRecipeIds + recipeIds)
    }

    /** [profileId] null pins the meal for everyone; otherwise for that person. */
    fun addLock(days: Set<Int>, slot: MealSlot, recipe: RecipeEntity, profileId: Long?) = update {
        copy(locks = locks + MealLock(days, slot, recipe.id, profileId))
    }

    fun removeLock(index: Int) = update {
        copy(locks = locks.filterIndexed { i, _ -> i != index })
    }

    fun dismissError() = update { copy(error = null) }

    fun generate() {
        if (_state.value.isGenerating) return
        val s = _state.value
        update { copy(isGenerating = true) }
        viewModelScope.launch {
            val result = runCatching {
                menuRepository.generate(
                    weekStart = weekStart,
                    config = GenerationConfig(
                        slotWeights = s.slotWeights,
                        scope = s.scope,
                        slotCourses = s.slotCourses,
                        overrides = s.overrides,
                        cheatMeals = s.cheatMeals,
                        maxRecipeRepeats = s.maxRepeats,
                        excludedCategories = s.excludedCategories,
                        excludedIngredientCategories = s.excludedIngredientCategories,
                        excludedRecipeIds = s.excludedRecipeIds,
                        includedFolders = s.includedFolders,
                        excludeRecentlyUsed = s.excludeRecentlyUsed,
                        locks = s.locks
                    )
                )
            }.getOrElse { GenerationResult.Failure(FailureReason.SAVE_FAILED) }
            when (result) {
                is GenerationResult.Success -> update { copy(isGenerating = false, done = true) }
                is GenerationResult.Failure -> update {
                    copy(isGenerating = false, error = result.reason)
                }
            }
        }
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
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
