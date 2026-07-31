package com.zame.nutrinest.ui.ingredients.compare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.NutrientTags
import com.zame.nutrinest.data.repository.IngredientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One nutrient across the ingredients being compared.
 *
 * [values] is index-aligned with the ingredient list; null means that
 * ingredient has no figure for this nutrient, which is different from zero.
 */
data class ComparisonRow(
    val tag: String,
    val labelEn: String,
    val labelEs: String,
    val unit: String,
    val values: List<Double?>
) {
    fun label(spanish: Boolean): String = if (spanish) labelEs else labelEn

    /** Index of the highest value, so the column can be marked. Null if tied. */
    val bestIndex: Int?
        get() {
            val best = values.withIndex().filter { it.value != null }
                .maxByOrNull { it.value!! } ?: return null
            val ties = values.count { it != null && it == best.value }
            return if (ties > 1) null else best.index
        }
}

data class CompareUiState(
    val ingredients: List<IngredientEntity> = emptyList(),
    val macroRows: List<ComparisonRow> = emptyList(),
    val sharedRows: List<ComparisonRow> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Puts two or three ingredients side by side, per 100 g.
 *
 * Only nutrients every selected ingredient records are shown: with coverage
 * varying across the dataset, a column of blanks says more about the source
 * data than about the food.
 */
class CompareIngredientsViewModel(
    private val repository: IngredientRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val ids: List<Long> = savedStateHandle.get<String>("ids")
        .orEmpty()
        .split(',')
        .mapNotNull { it.trim().toLongOrNull() }

    /** Label and unit for a nutrient tag, in both languages. */
    private data class NutrientLabel(val nameEn: String, val nameEs: String, val unit: String)

    private val nutrientsById = MutableStateFlow<Map<Long, Map<String, Double>>>(emptyMap())
    private val labels = MutableStateFlow<Map<String, NutrientLabel>>(emptyMap())

    init {
        viewModelScope.launch {
            nutrientsById.value = ids.associateWith { id ->
                repository.nutrientsOf(id).associate { it.tag to it.value }
            }
        }
        viewModelScope.launch {
            // Labels and units come from the first ingredient's joined query;
            // nutrient definitions are shared, so any ingredient will do.
            ids.firstOrNull()?.let { first ->
                repository.observeNutrients(first).collect { nutrients ->
                    labels.value = nutrients.associate {
                        it.tag to NutrientLabel(it.nameEn, it.nameEs, it.unit)
                    }
                }
            }
        }
    }

    // `IN ()` is not valid SQLite, so an empty selection never reaches the DAO.
    private val selected =
        if (ids.isEmpty()) flowOf(emptyList()) else repository.observeByIds(ids)

    val uiState = combine(
        selected,
        nutrientsById,
        labels
    ) { ingredients, nutrients, nutrientLabels ->
        // Keep the order the user picked them in, not the database's.
        val ordered = ids.mapNotNull { id -> ingredients.firstOrNull { it.id == id } }

        CompareUiState(
            ingredients = ordered,
            macroRows = macroRows(ordered),
            sharedRows = sharedRows(ordered, nutrients, nutrientLabels),
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    /**
     * The four headline macros, taken straight off the ingredient rather than
     * the nutrient table so they are present even for a hand-typed food. The
     * screen supplies their labels from string resources, hence the blanks.
     */
    private fun macroRows(ingredients: List<IngredientEntity>): List<ComparisonRow> = listOf(
        ComparisonRow(NutrientTags.ENERGY, "", "", "kcal", ingredients.map { it.nutrition.kcal }),
        ComparisonRow(NutrientTags.CARBS, "", "", "g", ingredients.map { it.nutrition.carbs }),
        ComparisonRow(NutrientTags.FAT, "", "", "g", ingredients.map { it.nutrition.fats }),
        ComparisonRow(NutrientTags.PROTEIN, "", "", "g", ingredients.map { it.nutrition.protein })
    )

    private fun sharedRows(
        ingredients: List<IngredientEntity>,
        nutrients: Map<Long, Map<String, Double>>,
        nutrientLabels: Map<String, NutrientLabel>
    ): List<ComparisonRow> {
        if (ingredients.size < 2 || nutrients.size < ingredients.size) return emptyList()

        val tagSets = ingredients.map { nutrients[it.id]?.keys.orEmpty() }
        val shared = tagSets.reduce { acc, tags -> acc intersect tags }
            .filter { it !in MACRO_TAGS }
            // A nutrient every ingredient records as zero tells the reader nothing.
            .filter { tag -> ingredients.any { (nutrients[it.id]?.get(tag) ?: 0.0) > 0.0 } }

        return shared.mapNotNull { tag ->
            val label = nutrientLabels[tag] ?: return@mapNotNull null
            ComparisonRow(
                tag = tag,
                labelEn = label.nameEn,
                labelEs = label.nameEs,
                unit = label.unit,
                values = ingredients.map { nutrients[it.id]?.get(tag) }
            )
        }
    }

    companion object {
        private val MACRO_TAGS = setOf(
            NutrientTags.ENERGY, NutrientTags.CARBS, NutrientTags.FAT, NutrientTags.PROTEIN
        )

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as NutriNestApp
                CompareIngredientsViewModel(
                    app.container.ingredientRepository,
                    createSavedStateHandle()
                )
            }
        }
    }
}
