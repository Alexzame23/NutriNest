package com.zame.nutrinest.ui.recipes.create

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.repository.IngredientRepository
import com.zame.nutrinest.data.repository.RecipeRepository
import com.zame.nutrinest.util.ImageStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An ingredient added to the draft recipe with its quantity. */
data class DraftIngredient(
    val ingredient: IngredientEntity,
    val grams: Double
)

data class DraftStep(
    val text: String = "",
    val imagePath: String? = null
)

data class CreateRecipeUiState(
    val name: String = "",
    val category: String = RecipeCategories.ALL.first(),
    val folder: String = "",
    val coverImagePath: String? = null,
    val ingredients: List<DraftIngredient> = emptyList(),
    val steps: List<DraftStep> = listOf(DraftStep()),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false
) {
    val totalNutrition: NutritionValues
        get() = ingredients.fold(NutritionValues()) { acc, item ->
            acc + item.ingredient.nutrition.forGrams(item.grams)
        }
}

class CreateRecipeViewModel(
    app: Application,
    private val recipeRepository: RecipeRepository,
    ingredientRepository: IngredientRepository
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(CreateRecipeUiState())
    val uiState = _uiState.asStateFlow()

    /** Full ingredient database for the picker sheet. */
    val allIngredients = ingredientRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onNameChange(value: String) = update { copy(name = value) }
    fun onCategoryChange(value: String) = update { copy(category = value) }
    fun onFolderChange(value: String) = update { copy(folder = value) }

    fun onCoverImagePicked(uri: Uri) {
        viewModelScope.launch {
            val path = ImageStorage.copyToAppStorage(getApplication(), uri)
            if (path != null) update { copy(coverImagePath = path) }
        }
    }

    fun addIngredient(ingredient: IngredientEntity, grams: Double) = update {
        // Same ingredient twice merges into one entry with summed grams.
        val existing = ingredients.find { it.ingredient.id == ingredient.id }
        val newList = if (existing != null) {
            ingredients.map {
                if (it.ingredient.id == ingredient.id) it.copy(grams = it.grams + grams) else it
            }
        } else {
            ingredients + DraftIngredient(ingredient, grams)
        }
        copy(ingredients = newList)
    }

    fun removeIngredient(ingredientId: Long) = update {
        copy(ingredients = ingredients.filterNot { it.ingredient.id == ingredientId })
    }

    fun updateIngredientGrams(ingredientId: Long, grams: Double) = update {
        copy(ingredients = ingredients.map {
            if (it.ingredient.id == ingredientId) it.copy(grams = grams) else it
        })
    }

    fun onStepTextChange(index: Int, text: String) = update {
        copy(steps = steps.mapIndexed { i, s -> if (i == index) s.copy(text = text) else s })
    }

    fun onStepImagePicked(index: Int, uri: Uri) {
        viewModelScope.launch {
            val path = ImageStorage.copyToAppStorage(getApplication(), uri) ?: return@launch
            update {
                copy(steps = steps.mapIndexed { i, s ->
                    if (i == index) s.copy(imagePath = path) else s
                })
            }
        }
    }

    fun addStep() = update { copy(steps = steps + DraftStep()) }

    fun removeStep(index: Int) = update {
        copy(steps = steps.filterIndexed { i, _ -> i != index }.ifEmpty { listOf(DraftStep()) })
    }

    fun dismissError() = update { copy(errorMessage = null) }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            // ── Validation ──────────────────────────────────────────
            val name = state.name.trim()
            val nonEmptySteps = state.steps.filter { it.text.isNotBlank() }
            val error = when {
                name.isEmpty() -> "Please give your recipe a name."
                state.ingredients.isEmpty() -> "Add at least one ingredient before saving."
                nonEmptySteps.isEmpty() -> "Add at least one preparation step before saving."
                recipeRepository.existsByName(name) ->
                    "A recipe named \"$name\" already exists. Choose a different name."
                else -> null
            }
            if (error != null) {
                update { copy(errorMessage = error) }
                return@launch
            }

            update { copy(isSaving = true) }
            recipeRepository.create(
                recipe = RecipeEntity(
                    name = name,
                    category = state.category,
                    folder = state.folder.trim().ifEmpty { null },
                    coverImagePath = state.coverImagePath
                ),
                ingredients = state.ingredients.map { it.ingredient.id to it.grams },
                steps = nonEmptySteps.map { it.text.trim() to it.imagePath }
            )
            update { copy(isSaving = false, saved = true) }
        }
    }

    private inline fun update(block: CreateRecipeUiState.() -> CreateRecipeUiState) {
        _uiState.value = _uiState.value.block()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                CreateRecipeViewModel(
                    app,
                    app.container.recipeRepository,
                    app.container.ingredientRepository
                )
            }
        }
    }
}
