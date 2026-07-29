package com.zame.nutrinest.ui.ingredients

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
import com.zame.nutrinest.data.repository.IngredientRepository
import com.zame.nutrinest.util.ImageStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreateIngredientUiState(
    val name: String = "",
    val foodType: String? = null,
    val kcal: String = "",
    val carbs: String = "",
    val fats: String = "",
    val protein: String = "",
    val imagePath: String? = null,
    val fieldErrors: Set<Field> = emptySet(),
    val errorMessage: String? = null,
    val saved: Boolean = false
) {
    enum class Field { NAME, FOOD_TYPE, KCAL, CARBS, FATS, PROTEIN }
}

class CreateIngredientViewModel(
    app: Application,
    private val repository: IngredientRepository
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(CreateIngredientUiState())
    val uiState = _uiState.asStateFlow()

    fun onNameChange(v: String) = update { copy(name = v, fieldErrors = fieldErrors - CreateIngredientUiState.Field.NAME) }
    fun onFoodTypeChange(v: String) = update { copy(foodType = v, fieldErrors = fieldErrors - CreateIngredientUiState.Field.FOOD_TYPE) }
    fun onKcalChange(v: String) = update { copy(kcal = v, fieldErrors = fieldErrors - CreateIngredientUiState.Field.KCAL) }
    fun onCarbsChange(v: String) = update { copy(carbs = v, fieldErrors = fieldErrors - CreateIngredientUiState.Field.CARBS) }
    fun onFatsChange(v: String) = update { copy(fats = v, fieldErrors = fieldErrors - CreateIngredientUiState.Field.FATS) }
    fun onProteinChange(v: String) = update { copy(protein = v, fieldErrors = fieldErrors - CreateIngredientUiState.Field.PROTEIN) }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            val path = ImageStorage.copyToAppStorage(getApplication(), uri)
            if (path != null) update { copy(imagePath = path) }
        }
    }

    fun dismissError() = update { copy(errorMessage = null) }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val errors = buildSet {
                if (state.name.isBlank()) add(CreateIngredientUiState.Field.NAME)
                if (state.foodType == null) add(CreateIngredientUiState.Field.FOOD_TYPE)
                if (state.kcal.toNonNegativeOrNull() == null) add(CreateIngredientUiState.Field.KCAL)
                if (state.carbs.toNonNegativeOrNull() == null) add(CreateIngredientUiState.Field.CARBS)
                if (state.fats.toNonNegativeOrNull() == null) add(CreateIngredientUiState.Field.FATS)
                if (state.protein.toNonNegativeOrNull() == null) add(CreateIngredientUiState.Field.PROTEIN)
            }
            if (errors.isNotEmpty()) {
                update {
                    copy(
                        fieldErrors = errors,
                        errorMessage = "Please fill in all required fields with valid values."
                    )
                }
                return@launch
            }
            if (repository.existsByName(state.name)) {
                update {
                    copy(
                        fieldErrors = setOf(CreateIngredientUiState.Field.NAME),
                        errorMessage = "An ingredient named \"${state.name.trim()}\" already exists."
                    )
                }
                return@launch
            }

            repository.create(
                IngredientEntity(
                    name = state.name.trim(),
                    foodType = state.foodType!!,
                    nutrition = NutritionValues(
                        kcal = state.kcal.toDouble(),
                        carbs = state.carbs.toDouble(),
                        fats = state.fats.toDouble(),
                        protein = state.protein.toDouble()
                    ),
                    imagePath = state.imagePath,
                    isCustom = true
                )
            )
            update { copy(saved = true) }
        }
    }

    private fun String.toNonNegativeOrNull(): Double? =
        toDoubleOrNull()?.takeIf { it >= 0 }

    private inline fun update(block: CreateIngredientUiState.() -> CreateIngredientUiState) {
        _uiState.value = _uiState.value.block()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                CreateIngredientViewModel(app, app.container.ingredientRepository)
            }
        }
    }
}
