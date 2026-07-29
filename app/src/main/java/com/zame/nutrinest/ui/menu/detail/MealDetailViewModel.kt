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
import com.zame.nutrinest.data.local.entity.RecipeStepEntity
import com.zame.nutrinest.data.repository.MenuRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class MealDetailUiState(
    val meal: PlannedMealWithRecipe? = null,
    val steps: List<RecipeStepEntity> = emptyList(),
    val baseIngredients: List<IngredientWithQuantity> = emptyList(),
    val portions: List<PortionWithProfile> = emptyList()
) {
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
}

@OptIn(ExperimentalCoroutinesApi::class)
class MealDetailViewModel(
    repository: MenuRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mealId: Long = savedStateHandle["mealId"] ?: -1L

    val uiState = repository.observeMeal(mealId)
        .flatMapLatest { mealWithRecipe ->
            if (mealWithRecipe == null) {
                flowOf(MealDetailUiState())
            } else {
                combine(
                    repository.observeSteps(mealWithRecipe.recipe.id),
                    repository.observePortions(mealId),
                    flow { emit(repository.getIngredientsOf(mealWithRecipe.recipe.id)) }
                ) { steps, portions, ingredients ->
                    MealDetailUiState(
                        meal = mealWithRecipe,
                        steps = steps,
                        baseIngredients = ingredients,
                        portions = portions.sortedBy { it.profile.name }
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealDetailUiState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                MealDetailViewModel(app.container.menuRepository, createSavedStateHandle())
            }
        }
    }
}
