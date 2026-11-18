package com.zame.nutrinest.ui.recipes.detail

import android.app.Application
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
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.local.entity.RecipeIngredientInput
import com.zame.nutrinest.data.local.entity.RecipeStepEntity
import com.zame.nutrinest.data.repository.IngredientRepository
import com.zame.nutrinest.data.repository.RecipeRepository
import com.zame.nutrinest.domain.nutrition.RecipeMeasure
import com.zame.nutrinest.domain.nutrition.RecipeQuantity
import com.zame.nutrinest.util.ImageStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipeDetailUiState(
    val recipe: RecipeEntity? = null,
    val steps: List<RecipeStepEntity> = emptyList(),
    val ingredients: List<IngredientWithQuantity> = emptyList(),
    val measures: Map<Long, List<RecipeMeasure>> = emptyMap(),
    val deleted: Boolean = false,
    /** A weekly menu still plans this recipe, so the delete was refused. */
    val deleteBlocked: Boolean = false,
    val operationFailed: Boolean = false,
    val isLoading: Boolean = true
) {
    /** Everything the recipe makes, summed from its ingredients. */
    val totals: NutritionValues
        get() = ingredients.fold(NutritionValues()) { acc, item -> acc + item.nutrition }

    val totalGrams: Double get() = ingredients.sumOf { it.grams }
}

class RecipeDetailViewModel(
    private val app: Application,
    private val repository: RecipeRepository,
    ingredientRepository: IngredientRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recipeId: Long = savedStateHandle["recipeId"] ?: -1L

    private val deletion = MutableStateFlow(Deletion())

    private data class Deletion(
        val done: Boolean = false,
        val blocked: Boolean = false,
        val failed: Boolean = false
    )

    val uiState: StateFlow<RecipeDetailUiState> = combine(
        repository.observeRecipeWithSteps(recipeId),
        repository.observeIngredientsOf(recipeId),
        ingredientRepository.observeRecipeMeasures(),
        deletion
    ) { withSteps, ingredients, measures, deleteState ->
        RecipeDetailUiState(
            recipe = withSteps?.recipe,
            // @Relation does not order its rows, so the numbering is applied here.
            steps = withSteps?.steps?.sortedBy { it.position }.orEmpty(),
            ingredients = ingredients,
            measures = measures,
            deleted = deleteState.done,
            deleteBlocked = deleteState.blocked,
            operationFailed = deleteState.failed,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    fun onFavoriteToggled() {
        val recipe = uiState.value.recipe ?: return
        viewModelScope.launch {
            runCatching { repository.setFavorite(recipe.id, !recipe.isFavorite) }
                .onFailure { deletion.value = deletion.value.copy(failed = true) }
        }
    }

    fun updateIngredientQuantity(previous: IngredientWithQuantity, quantity: RecipeQuantity) {
        if (uiState.value.recipe?.isBuiltIn != false || previous.quantity == quantity) return
        val ingredientId = previous.ingredient.id
        val options = uiState.value.measures[ingredientId] ?: listOf(RecipeMeasure.GRAMS)
        if (options.none { it.supports(quantity) }) {
            deletion.value = deletion.value.copy(failed = true)
            return
        }
        viewModelScope.launch {
            val saved = runCatching {
                repository.updateIngredientQuantity(recipeId, previous.quantity, RecipeIngredientInput(ingredientId, quantity))
            }.getOrDefault(false)
            if (!saved) deletion.value = deletion.value.copy(failed = true)
        }
    }

    fun onDeleteConfirmed() {
        val paths = buildList {
            uiState.value.recipe?.coverImagePath?.let(::add)
            uiState.value.steps.mapNotNullTo(this) { it.imagePath }
        }
        viewModelScope.launch {
            runCatching { repository.delete(recipeId) }.fold(
                onSuccess = { deleted ->
                    if (deleted) {
                        paths.forEach { ImageStorage.deleteManaged(app, it) }
                        deletion.value = Deletion(done = true)
                    } else {
                        deletion.value = Deletion(blocked = true)
                    }
                },
                onFailure = { deletion.value = Deletion(failed = true) }
            )
        }
    }

    fun dismissOperationError() {
        deletion.value = deletion.value.copy(failed = false)
    }

    fun dismissDeleteBlocked() {
        deletion.value = Deletion()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as NutriNestApp
                RecipeDetailViewModel(
                    app, app.container.recipeRepository, app.container.ingredientRepository, createSavedStateHandle()
                )
            }
        }
    }
}
