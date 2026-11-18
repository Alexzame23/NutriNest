package com.zame.nutrinest.ui.menu.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.PlannedMealWithRecipe
import com.zame.nutrinest.data.local.entity.PortionWithProfile
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.RecipeStepEntity
import com.zame.nutrinest.data.repository.IngredientRepository
import com.zame.nutrinest.data.repository.MealScopeTarget
import com.zame.nutrinest.data.repository.MenuRepository
import com.zame.nutrinest.data.repository.RecipeRepository
import com.zame.nutrinest.ui.menu.MenuIngredientCatalog
import com.zame.nutrinest.ui.menu.observeMenuIngredientCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MealDetailUiState(
    val meal: PlannedMealWithRecipe? = null,
    val steps: List<RecipeStepEntity> = emptyList(),
    val baseIngredients: List<IngredientWithQuantity> = emptyList(),
    val ingredientCatalog: MenuIngredientCatalog = MenuIngredientCatalog(),
    val portions: List<PortionWithProfile> = emptyList(),
    val profiles: List<ProfileEntity> = emptyList()
) {
    /** A free slot: a placeholder with nothing to cook. False while loading. */
    val isFree: Boolean
        get() = meal?.let { it.meal.isFree || it.recipe == null } ?: false

    /** null when the household shares this dish, otherwise whose it is. */
    val ownerProfileId: Long? get() = meal?.meal?.profileId

    /** Base recipe nutrition (factor 1.0). */
    val baseNutrition: NutritionValues
        get() = baseIngredients.fold(NutritionValues()) { acc, i -> acc + i.nutrition }

    /** Sum of everyone's portion factors → household multiplier. */
    val householdFactor: Double
        get() = portions.sumOf { it.portion.factor }

    val householdNutrition: NutritionValues
        get() = baseNutrition.let {
            NutritionValues(
                it.kcal * householdFactor,
                it.carbs * householdFactor,
                it.fats * householdFactor,
                it.protein * householdFactor
            )
        }

    fun nutritionFor(portion: PortionWithProfile): NutritionValues =
        baseNutrition.let {
            val f = portion.portion.factor
            NutritionValues(it.kcal * f, it.carbs * f, it.fats * f, it.protein * f)
        }

    /** True when today's logging has moved this person off the planned portion. */
    fun isAdjusted(portion: PortionWithProfile): Boolean =
        kotlin.math.abs(portion.portion.factor - portion.portion.baseFactor) > 0.01
}

@OptIn(ExperimentalCoroutinesApi::class)
class MealDetailViewModel(
    private val repository: MenuRepository,
    recipeRepository: RecipeRepository,
    ingredientRepository: IngredientRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mealId: Long = savedStateHandle["mealId"] ?: -1L

    val allRecipes = recipeRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState = repository.observeMeal(mealId)
        .flatMapLatest { mealWithRecipe ->
            if (mealWithRecipe == null) {
                flowOf(MealDetailUiState())
            } else {
                combine(
                    mealWithRecipe.recipe
                        ?.let { repository.observeSteps(it.id) }
                        ?: flowOf(emptyList()),
                    repository.observePortions(mealId),
                    repository.observeProfiles(),
                    mealWithRecipe.recipe
                        ?.let { repository.observeIngredientsOf(it.id) }
                        ?: flowOf(emptyList()),
                    ingredientRepository.observeMenuIngredientCatalog()
                ) { steps, portions, profiles, ingredients, catalog ->
                    MealDetailUiState(
                        meal = mealWithRecipe,
                        steps = steps,
                        baseIngredients = ingredients,
                        ingredientCatalog = catalog,
                        portions = portions.sortedBy { it.profile.name },
                        profiles = profiles
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealDetailUiState())

    /** The lock's reach is the row's own: household row, household lock. */
    fun toggleLock() {
        val meal = uiState.value.meal?.meal ?: return
        viewModelScope.launch { repository.setMealLocked(meal.id, !meal.isLocked) }
    }

    /**
     * Swaps this dish. [profileId] null changes it for everyone eating it;
     * otherwise only that person moves, and their portions are recalculated
     * from their own calorie target.
     */
    fun replaceMeal(recipeId: Long, profileId: Long?) {
        viewModelScope.launch {
            repository.replaceMeal(
                mealId = mealId,
                newRecipeId = recipeId,
                target = profileId
                    ?.let { MealScopeTarget.Person(it) }
                    ?: MealScopeTarget.Household
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                MealDetailViewModel(
                    app.container.menuRepository,
                    app.container.recipeRepository,
                    app.container.ingredientRepository,
                    createSavedStateHandle()
                )
            }
        }
    }
}
