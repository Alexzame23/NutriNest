package com.zame.nutrinest.ui.recipes.create

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.local.entity.RecipeImageCrop
import com.zame.nutrinest.data.local.entity.RecipeIngredientInput
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
    val grams: Double,
    val quantityUnit: String = "GRAM"
)

data class DraftStep(
    val text: String = "",
    val imagePath: String? = null
)

/** What is wrong with the form, as a code the UI layer translates. */
enum class RecipeFormError(@param:StringRes val messageRes: Int) {
    NAME_REQUIRED(R.string.recipe_error_name),
    NO_INGREDIENTS(R.string.recipe_error_no_ingredients),
    NO_STEPS(R.string.recipe_error_no_steps),
    DUPLICATE_NAME(R.string.recipe_error_duplicate_name),
    INVALID_QUANTITY(R.string.recipe_error_invalid_quantity),
    SAVE_FAILED(R.string.error_save_failed)
}

data class CreateRecipeUiState(
    val name: String = "",
    val category: String = RecipeCategories.ALL.first(),
    val folder: String = "",
    val coverImagePath: String? = null,
    val coverImageCrop: String = RecipeImageCrop.CENTER,
    val ingredients: List<DraftIngredient> = emptyList(),
    val steps: List<DraftStep> = listOf(DraftStep()),
    val isSaving: Boolean = false,
    val error: RecipeFormError? = null,
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
    private val ingredientRepository: IngredientRepository
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(CreateRecipeUiState())
    val uiState = _uiState.asStateFlow()
    private val draftImages = mutableSetOf<String>()

    /** Full ingredient database for the picker sheet. */
    val allIngredients = ingredientRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Existing recipe folders used by the creation dropdown. */
    val folders = recipeRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onNameChange(value: String) = update { copy(name = value) }
    fun onCategoryChange(value: String) = update { copy(category = value) }
    fun onFolderChange(value: String) = update { copy(folder = value) }

    fun onCoverImagePicked(uri: Uri) {
        viewModelScope.launch {
            val path = ImageStorage.copyToAppStorage(getApplication(), uri)
            if (path != null) {
                val previous = _uiState.value.coverImagePath
                previous?.takeIf { it in draftImages }?.let {
                    ImageStorage.deleteManaged(getApplication(), it)
                    draftImages.remove(it)
                }
                draftImages += path
                update {
                    copy(
                        coverImagePath = path,
                        coverImageCrop = RecipeImageCrop.CENTER
                    )
                }
            }
        }
    }

    fun onCoverImageCropChange(value: String) = update {
        copy(coverImageCrop = value)
    }

    fun addIngredient(
        ingredient: IngredientEntity,
        grams: Double,
        quantityUnit: String = "GRAM"
    ) = update {
        // Same ingredient twice merges into one entry with summed grams.
        val existing = ingredients.find { it.ingredient.id == ingredient.id }
        val newList = if (existing != null) {
            ingredients.map {
                if (it.ingredient.id == ingredient.id) {
                    it.copy(grams = it.grams + grams, quantityUnit = quantityUnit)
                } else it
            }
        } else {
            ingredients + DraftIngredient(ingredient, grams, quantityUnit)
        }
        copy(ingredients = newList)
    }

    fun removeIngredient(ingredientId: Long) = update {
        copy(ingredients = ingredients.filterNot { it.ingredient.id == ingredientId })
    }

    fun updateIngredientQuantity(ingredientId: Long, grams: Double, quantityUnit: String) = update {
        copy(ingredients = ingredients.map {
            if (it.ingredient.id == ingredientId) it.copy(grams = grams, quantityUnit = quantityUnit) else it
        })
    }

    fun onStepTextChange(index: Int, text: String) = update {
        copy(steps = steps.mapIndexed { i, s -> if (i == index) s.copy(text = text) else s })
    }

    fun onStepImagePicked(index: Int, uri: Uri) {
        viewModelScope.launch {
            val path = ImageStorage.copyToAppStorage(getApplication(), uri) ?: return@launch
            val previous = _uiState.value.steps.getOrNull(index)?.imagePath
            previous?.takeIf { it in draftImages }?.let {
                ImageStorage.deleteManaged(getApplication(), it)
                draftImages.remove(it)
            }
            draftImages += path
            update {
                copy(steps = steps.mapIndexed { i, s ->
                    if (i == index) s.copy(imagePath = path) else s
                })
            }
        }
    }

    fun addStep() = update { copy(steps = steps + DraftStep()) }

    fun removeStep(index: Int) {
        val image = _uiState.value.steps.getOrNull(index)?.imagePath
        image?.takeIf { it in draftImages }?.let {
            viewModelScope.launch { ImageStorage.deleteManaged(getApplication(), it) }
            draftImages.remove(it)
        }
        update {
            copy(steps = steps.filterIndexed { i, _ -> i != index }.ifEmpty { listOf(DraftStep()) })
        }
    }

    fun dismissError() = update { copy(error = null) }

    fun save() {
        if (_uiState.value.isSaving) return
        val state = _uiState.value
        viewModelScope.launch {
            // ── Validation ──────────────────────────────────────────
            val name = state.name.trim()
            val nonEmptySteps = state.steps.filter { it.text.isNotBlank() }
            val error = when {
                name.isEmpty() -> RecipeFormError.NAME_REQUIRED
                state.ingredients.isEmpty() -> RecipeFormError.NO_INGREDIENTS
                state.ingredients.any {
                    !it.grams.isFinite() || it.grams <= 0.0 || it.grams > MAX_INGREDIENT_GRAMS
                } -> RecipeFormError.INVALID_QUANTITY
                nonEmptySteps.isEmpty() -> RecipeFormError.NO_STEPS
                recipeRepository.existsByName(name) -> RecipeFormError.DUPLICATE_NAME
                else -> null
            }
            if (error != null) {
                update { copy(error = error) }
                return@launch
            }

            update { copy(isSaving = true) }
            runCatching {
                recipeRepository.create(
                    recipe = RecipeEntity(
                        name = name,
                        category = state.category,
                        folder = state.folder.trim().ifEmpty { null },
                        coverImagePath = state.coverImagePath,
                        coverImageCrop = state.coverImageCrop
                    ),
                    ingredients = state.ingredients.map {
                        RecipeIngredientInput(it.ingredient.id, it.grams, it.quantityUnit)
                    },
                    steps = nonEmptySteps.map { it.text.trim() to it.imagePath }
                )
            }.onSuccess {
                // Usage counts are useful metadata, but a failure updating one
                // must not report that the already-committed recipe failed.
                state.ingredients.forEach {
                    runCatching { ingredientRepository.markUsed(it.ingredient.id) }
                }
                val retained = buildSet {
                    state.coverImagePath?.let(::add)
                    nonEmptySteps.mapNotNullTo(this) { it.imagePath }
                }
                draftImages.filterNot { it in retained }.forEach {
                    ImageStorage.deleteManagedNow(getApplication(), it)
                }
                draftImages.clear()
                update { copy(isSaving = false, saved = true) }
            }.onFailure {
                update { copy(isSaving = false, error = RecipeFormError.SAVE_FAILED) }
            }
        }
    }

    private inline fun update(block: CreateRecipeUiState.() -> CreateRecipeUiState) {
        _uiState.value = _uiState.value.block()
    }

    companion object {
        private const val MAX_INGREDIENT_GRAMS = 100_000.0
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

    override fun onCleared() {
        draftImages.forEach { ImageStorage.deleteManagedNow(getApplication(), it) }
        draftImages.clear()
        super.onCleared()
    }
}
