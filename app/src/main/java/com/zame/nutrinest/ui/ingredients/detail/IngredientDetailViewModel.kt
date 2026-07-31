package com.zame.nutrinest.ui.ingredients.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.FoodDatasetMetaEntity
import com.zame.nutrinest.data.local.entity.IngredientAliasEntity
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.IngredientPortionEntity
import com.zame.nutrinest.data.local.entity.IngredientQualityIssueEntity
import com.zame.nutrinest.data.local.entity.NutrientGroups
import com.zame.nutrinest.data.local.entity.NutrientTags
import com.zame.nutrinest.data.local.entity.NutrientValueWithDef
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.RecipeUsage
import com.zame.nutrinest.data.repository.FoodReferenceRepository
import com.zame.nutrinest.data.repository.IngredientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the figures on screen are measured against. */
data class NutritionBasis(
    /** Null means the standard 100 g. */
    val portion: IngredientPortionEntity? = null,
    val grams: Double = 100.0
)

/** One section of the micronutrient panel. */
data class NutrientSection(
    val group: String,
    val nutrients: List<NutrientValueWithDef>
)

data class IngredientDetailUiState(
    val ingredient: IngredientEntity? = null,
    val portions: List<IngredientPortionEntity> = emptyList(),
    val aliases: List<IngredientAliasEntity> = emptyList(),
    val issues: List<IngredientQualityIssueEntity> = emptyList(),
    val macroPanel: List<NutrientValueWithDef> = emptyList(),
    val micronutrients: List<NutrientSection> = emptyList(),
    val usedIn: List<RecipeUsage> = emptyList(),
    val basis: NutritionBasis = NutritionBasis(),
    val attribution: String? = null,
    val isLoading: Boolean = true,
    val deleted: Boolean = false,
    val deleteBlocked: Boolean = false
) {
    /** Macros scaled to whatever basis the user picked. */
    val scaledNutrition: NutritionValues
        get() = ingredient?.nutrition?.forGrams(basis.grams) ?: NutritionValues()

    val scaleFactor: Double get() = basis.grams / 100.0

    val hasMicronutrients: Boolean get() = micronutrients.isNotEmpty()
}

class IngredientDetailViewModel(
    private val repository: IngredientRepository,
    referenceRepository: FoodReferenceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val ingredientId: Long = savedStateHandle["ingredientId"] ?: -1L

    private val selectedPortionId = MutableStateFlow<Long?>(null)
    private val deletion = MutableStateFlow(DeletionState())

    private data class DeletionState(val deleted: Boolean = false, val blocked: Boolean = false)

    private val attribution = referenceRepository.observeDatasetMeta()
        .map { meta -> meta.firstOrNull { it.key == FoodDatasetMetaEntity.ATTRIBUTION }?.value }

    val uiState = combine(
        repository.observeDetails(ingredientId),
        repository.observeNutrients(ingredientId),
        repository.observeRecipesUsing(ingredientId),
        selectedPortionId,
        combine(deletion, attribution) { d, credit -> d to credit }
    ) { details, nutrients, usedIn, portionId, (deletionState, credit) ->
        val portions = details?.portions.orEmpty().sortedByDescending { it.isDefault }
        val portion = portions.firstOrNull { it.id == portionId }

        IngredientDetailUiState(
            ingredient = details?.ingredient,
            portions = portions,
            aliases = details?.aliases.orEmpty(),
            issues = details?.issues.orEmpty(),
            macroPanel = nutrients.filter { it.tag in NutrientTags.MACRO_PANEL }
                .sortedBy { NutrientTags.MACRO_PANEL.indexOf(it.tag) },
            micronutrients = sectionise(nutrients),
            usedIn = usedIn,
            basis = NutritionBasis(portion, portion?.grams ?: 100.0),
            attribution = credit,
            isLoading = false,
            deleted = deletionState.deleted,
            deleteBlocked = deletionState.blocked
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IngredientDetailUiState())

    fun onBasisSelected(portion: IngredientPortionEntity?) {
        selectedPortionId.value = portion?.id
    }

    fun onFavoriteToggled() {
        val current = uiState.value.ingredient ?: return
        viewModelScope.launch { repository.setFavorite(current.id, !current.isFavorite) }
    }

    /**
     * Deleting is refused while a recipe or shopping list still points here —
     * both foreign keys are RESTRICT, so the alternative is an exception the
     * user cannot act on.
     */
    fun onDeleteConfirmed() {
        viewModelScope.launch {
            if (repository.delete(ingredientId)) {
                deletion.value = DeletionState(deleted = true)
            } else {
                deletion.value = DeletionState(blocked = true)
            }
        }
    }

    fun dismissDeleteBlocked() {
        deletion.value = deletion.value.copy(blocked = false)
    }

    /**
     * Groups the nutrients for display, dropping the ones already shown in the
     * macro block and anything recorded as a flat zero (the dataset stores
     * explicit zeros, and a screenful of them buries the real figures).
     */
    private fun sectionise(nutrients: List<NutrientValueWithDef>): List<NutrientSection> {
        val interesting = nutrients.filter {
            it.tag !in NutrientTags.MACRO_PANEL &&
                it.nutrientGroup != NutrientGroups.ENERGY &&
                it.value > 0.0
        }
        val byGroup = interesting.groupBy { it.nutrientGroup }
        return NutrientGroups.DISPLAY_ORDER.mapNotNull { group ->
            byGroup[group]
                ?.sortedWith(compareByDescending<NutrientValueWithDef> { it.isPrimary }
                    .thenBy { it.sortOrder })
                ?.takeIf { it.isNotEmpty() }
                ?.let { NutrientSection(group, it) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as NutriNestApp
                IngredientDetailViewModel(
                    app.container.ingredientRepository,
                    app.container.foodReferenceRepository,
                    createSavedStateHandle()
                )
            }
        }
    }
}
