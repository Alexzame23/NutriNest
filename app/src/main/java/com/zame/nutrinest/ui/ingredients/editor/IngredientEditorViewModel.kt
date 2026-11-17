package com.zame.nutrinest.ui.ingredients.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.DataQuality
import com.zame.nutrinest.data.local.entity.IngredientAliasEntity
import com.zame.nutrinest.data.local.entity.IngredientCategoryEntity
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.IngredientNutrientEntity
import com.zame.nutrinest.data.local.entity.IngredientPortionEntity
import com.zame.nutrinest.data.local.entity.NutrientDefEntity
import com.zame.nutrinest.data.local.entity.NutrientTags
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.PrepState
import com.zame.nutrinest.data.local.entity.RawSafety
import com.zame.nutrinest.data.repository.FoodReferenceRepository
import com.zame.nutrinest.data.repository.IngredientRepository
import com.zame.nutrinest.domain.nutrition.IngredientValidator
import com.zame.nutrinest.util.ImageStorage
import com.zame.nutrinest.util.LocalizedNumbers
import com.zame.nutrinest.util.TextNormalizer
import com.zame.nutrinest.util.toLocalizedDoubleOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A household measure being edited, held as text until it is saved. */
data class PortionDraft(
    val label: String = "",
    val grams: String = "",
    val labelEs: String = "",
    val portionType: String = "PORTION",
    val isDefault: Boolean = false
)

data class IngredientEditorUiState(
    val isEditing: Boolean = false,
    val name: String = "",
    val nameEs: String = "",
    val categoryKey: String? = null,
    val kcal: String = "",
    val carbs: String = "",
    val fats: String = "",
    val protein: String = "",
    /** Nutrient tag → raw text, so a half-typed number never becomes a value. */
    val micronutrients: Map<String, String> = emptyMap(),
    val notes: String = "",
    val servingSizeG: String = "",
    val rawSafety: String = RawSafety.UNKNOWN,
    val isLiquid: Boolean = false,
    val densityGml: String = "",
    val imagePath: String? = null,
    val portions: List<PortionDraft> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val categories: List<IngredientCategoryEntity> = emptyList(),
    val nutrientDefs: List<NutrientDefEntity> = emptyList(),
    val validation: IngredientValidator.Result = IngredientValidator.Result(emptyList()),
    val duplicateName: Boolean = false,
    val similar: List<IngredientEntity> = emptyList(),
    /** Set when the user pressed save and only warnings stand in the way. */
    val confirmWarnings: Boolean = false,
    val saved: Boolean = false,
    val savedIngredientId: Long? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false
) {
    val fieldsInError: Set<IngredientValidator.Field> get() = validation.fieldsInError()

    /** Energy the macros imply, offered as a one-tap fix for a mismatch. */
    val suggestedKcal: Int?
        get() {
            val c = carbs.toLocalizedDoubleOrNull() ?: return null
            val f = fats.toLocalizedDoubleOrNull() ?: return null
            val p = protein.toLocalizedDoubleOrNull() ?: return null
            val fibre = micronutrients[NutrientTags.FIBRE]?.toLocalizedDoubleOrNull() ?: 0.0
            val alcohol = micronutrients[NutrientTags.ALCOHOL]?.toLocalizedDoubleOrNull() ?: 0.0
            return IngredientValidator.atwaterKcal(p, c, f, fibre, alcohol).toInt()
        }
}

/**
 * Backs both creating and editing an ingredient — the same form either way,
 * following the pattern `ProfileEditorViewModel` already uses.
 *
 * Validation runs on every keystroke so the sheet can show what is wrong while
 * it is still being typed; errors block saving, warnings ask for confirmation.
 */
class IngredientEditorViewModel(
    app: Application,
    private val repository: IngredientRepository,
    private val referenceRepository: FoodReferenceRepository,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {

    private val ingredientId: Long = savedStateHandle["ingredientId"] ?: -1L

    private val _uiState = MutableStateFlow(IngredientEditorUiState())
    val uiState = _uiState.asStateFlow()

    /** Kept so saving can preserve the fields the form does not expose. */
    private var original: IngredientEntity? = null
    private var originalAliases: Map<String, IngredientAliasEntity> = emptyMap()
    private var duplicateCheckJob: Job? = null

    init {
        viewModelScope.launch {
            val defs = referenceRepository.nutrientDefs()
            update { copy(nutrientDefs = defs, isLoading = false) }
            loadExisting(defs)
        }
        // Categories are observed rather than read once: on a first launch the
        // import may still be populating them when the editor opens.
        viewModelScope.launch {
            referenceRepository.observeCategories().collect { list ->
                update { copy(categories = list) }
            }
        }
    }

    private suspend fun loadExisting(defs: List<NutrientDefEntity>) {
        if (ingredientId <= 0L) return
        val existing = repository.findById(ingredientId) ?: return
        original = existing

        val nutrients = repository.nutrientsOf(ingredientId).associate { it.tag to it.value }
        val knownTags = defs.map { it.tag }.toSet()
        // A single snapshot of the relations — the form owns the values from
        // here, so observing would fight whatever the user is typing.
        val details = repository.observeDetails(ingredientId).first()
        originalAliases = details?.aliases.orEmpty().associateBy { it.alias }

        update {
            copy(
                isEditing = true,
                name = existing.name,
                nameEs = existing.nameEs.orEmpty(),
                categoryKey = existing.categoryKey,
                kcal = existing.nutrition.kcal.toEditableText(),
                carbs = existing.nutrition.carbs.toEditableText(),
                fats = existing.nutrition.fats.toEditableText(),
                protein = existing.nutrition.protein.toEditableText(),
                micronutrients = nutrients
                    .filterKeys { it in knownTags }
                    .mapValues { it.value.toEditableText() },
                notes = existing.notes.orEmpty(),
                servingSizeG = existing.servingSizeG?.toEditableText().orEmpty(),
                rawSafety = existing.rawSafety,
                isLiquid = existing.isLiquid,
                densityGml = existing.densityGml?.toEditableText().orEmpty(),
                imagePath = existing.imagePath,
                portions = details?.portions.orEmpty().map {
                    PortionDraft(
                        label = it.labelEn,
                        grams = it.grams.toEditableText(),
                        labelEs = it.labelEs,
                        portionType = it.portionType,
                        isDefault = it.isDefault
                    )
                },
                synonyms = details?.aliases.orEmpty().map { it.alias }.distinct()
            )
        }
        revalidate()
    }

    // ── Field changes ───────────────────────────────────────────────────

    fun onNameChange(value: String) {
        update { copy(name = value) }
        revalidate()
        checkForDuplicates(value)
    }

    fun onNameEsChange(value: String) = updateAndValidate { copy(nameEs = value) }
    fun onCategoryChange(value: String) = updateAndValidate { copy(categoryKey = value) }
    fun onKcalChange(value: String) = updateAndValidate { copy(kcal = value) }
    fun onCarbsChange(value: String) = updateAndValidate { copy(carbs = value) }
    fun onFatsChange(value: String) = updateAndValidate { copy(fats = value) }
    fun onProteinChange(value: String) = updateAndValidate { copy(protein = value) }
    fun onNotesChange(value: String) = update { copy(notes = value) }
    fun onServingSizeChange(value: String) = updateAndValidate { copy(servingSizeG = value) }
    fun onRawSafetyChange(value: String) = update { copy(rawSafety = value) }
    fun onDensityChange(value: String) = updateAndValidate { copy(densityGml = value) }

    fun onIsLiquidChange(value: Boolean) = updateAndValidate {
        copy(isLiquid = value, densityGml = if (value) densityGml else "")
    }

    fun onMicronutrientChange(tag: String, value: String) = updateAndValidate {
        copy(
            micronutrients = if (value.isBlank()) {
                micronutrients - tag
            } else {
                micronutrients + (tag to value)
            }
        )
    }

    fun onUseSuggestedKcal() {
        val suggested = _uiState.value.suggestedKcal ?: return
        updateAndValidate { copy(kcal = suggested.toString()) }
    }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            val path = ImageStorage.copyToAppStorage(getApplication(), uri)
            if (path != null) {
                val previousDraft = _uiState.value.imagePath
                if (previousDraft != original?.imagePath) {
                    ImageStorage.deleteManaged(getApplication(), previousDraft)
                }
                update { copy(imagePath = path) }
            }
        }
    }

    // ── Portions and synonyms ───────────────────────────────────────────

    fun onAddPortion() = updateAndValidate { copy(portions = portions + PortionDraft()) }

    fun onPortionChange(index: Int, draft: PortionDraft) = updateAndValidate {
        copy(portions = portions.mapIndexed { i, existing -> if (i == index) draft else existing })
    }

    fun onRemovePortion(index: Int) = updateAndValidate {
        copy(portions = portions.filterIndexed { i, _ -> i != index })
    }

    fun onAddSynonym() = update { copy(synonyms = synonyms + "") }

    fun onSynonymChange(index: Int, value: String) = update {
        copy(synonyms = synonyms.mapIndexed { i, existing -> if (i == index) value else existing })
    }

    fun onRemoveSynonym(index: Int) = update {
        copy(synonyms = synonyms.filterIndexed { i, _ -> i != index })
    }

    // ── Saving ──────────────────────────────────────────────────────────

    fun onSave() {
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        val state = _uiState.value
        val result = validate(state)
        update { copy(validation = result) }

        if (!result.canSave || state.duplicateName) return
        if (result.needsConfirmation && !state.confirmWarnings) {
            update { copy(confirmWarnings = true) }
            return
        }
        persist(state)
    }

    fun onConfirmWarnings() {
        update { copy(confirmWarnings = false) }
        persist(_uiState.value)
    }

    fun onDismissWarnings() = update { copy(confirmWarnings = false) }
    fun dismissSaveError() = update { copy(saveFailed = false) }

    private fun persist(state: IngredientEditorUiState) {
        viewModelScope.launch {
            if (_uiState.value.isSaving) return@launch
            update { copy(isSaving = true, saveFailed = false) }
            val now = System.currentTimeMillis()
            val name = state.name.trim()
            val nameEs = state.nameEs.trim().takeIf { it.isNotEmpty() }
            val previous = original
            if (repository.isDuplicateName(name, previous?.id ?: 0L)) {
                update { copy(isSaving = false, duplicateName = true) }
                return@launch
            }

            val ingredient = IngredientEntity(
                id = previous?.id ?: 0L,
                name = name,
                nameEs = nameEs,
                nameNorm = TextNormalizer.normalize(name),
                nameEsNorm = nameEs?.let(TextNormalizer::normalize),
                baseName = previous?.baseName,
                categoryKey = state.categoryKey!!,
                aisleKey = previous?.aisleKey,
                nutrition = NutritionValues(
                    kcal = state.kcal.toLocalizedDoubleOrNull() ?: 0.0,
                    carbs = state.carbs.toLocalizedDoubleOrNull() ?: 0.0,
                    fats = state.fats.toLocalizedDoubleOrNull() ?: 0.0,
                    protein = state.protein.toLocalizedDoubleOrNull() ?: 0.0
                ),
                prepState = previous?.prepState ?: PrepState.UNSPECIFIED,
                rawSafety = state.rawSafety,
                notes = state.notes.trim().takeIf { it.isNotEmpty() },
                servingSizeG = state.servingSizeG.toLocalizedDoubleOrNull(),
                isLiquid = state.isLiquid,
                densityGml = state.densityGml.toLocalizedDoubleOrNull(),
                completenessPct = completeness(state),
                quality = previous?.quality ?: DataQuality.OK,
                sourceKey = previous?.sourceKey,
                imagePath = state.imagePath,
                // Editing an imported food makes it the user's own, so a later
                // dataset refresh leaves their version alone.
                isCustom = true,
                isFavorite = previous?.isFavorite ?: false,
                usageCount = previous?.usageCount ?: 0,
                lastUsedAt = previous?.lastUsedAt,
                createdAt = previous?.createdAt ?: now,
                updatedAt = now,
                remoteId = previous?.remoteId
            )

            runCatching { repository.save(
                ingredient = ingredient,
                nutrients = state.micronutrients.mapNotNull { (tag, text) ->
                    text.toLocalizedDoubleOrNull()?.let {
                        IngredientNutrientEntity(ingredient.id, tag, it)
                    }
                },
                portions = state.portions.mapNotNull { draft ->
                    val grams = draft.grams.toLocalizedDoubleOrNull() ?: return@mapNotNull null
                    val label = draft.label.trim().ifEmpty { return@mapNotNull null }
                    IngredientPortionEntity(
                        ingredientId = ingredient.id,
                        labelEn = label,
                        labelEs = draft.labelEs.ifBlank { label },
                        grams = grams,
                        portionType = draft.portionType,
                        isDefault = draft.isDefault
                    )
                },
                aliases = state.synonyms.mapNotNull { synonym ->
                    val trimmed = synonym.trim().ifEmpty { return@mapNotNull null }
                    IngredientAliasEntity(
                        ingredientId = ingredient.id,
                        alias = trimmed,
                        aliasNorm = TextNormalizer.normalize(trimmed),
                        lang = originalAliases[trimmed]?.lang
                            ?: if (nameEs == trimmed) "es" else "en"
                    )
                }
            ) }.onSuccess { savedId ->
                // The previous snapshot and its image still belong to historical recipes.
                update { copy(isSaving = false, saved = true, savedIngredientId = savedId) }
            }.onFailure {
                update { copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    // ── Validation ──────────────────────────────────────────────────────

    private fun validate(state: IngredientEditorUiState): IngredientValidator.Result =
        IngredientValidator.validate(
            IngredientValidator.Draft(
                name = state.name,
                categoryKey = state.categoryKey,
                kcal = state.kcal.toLocalizedDoubleOrNull(),
                carbs = state.carbs.toLocalizedDoubleOrNull(),
                fats = state.fats.toLocalizedDoubleOrNull(),
                protein = state.protein.toLocalizedDoubleOrNull(),
                extraNutrients = state.micronutrients.mapValues { (_, text) ->
                    text.toLocalizedDoubleOrNull() ?: Double.NaN
                },
                servingSizeG = state.servingSizeG.parseOptionalNumber(),
                portionGrams = state.portions
                    .filter { it.label.isNotBlank() || it.grams.isNotBlank() }
                    .map { draft ->
                        draft.grams.toLocalizedDoubleOrNull()
                            ?.takeIf { draft.label.isNotBlank() }
                    },
                densityGml = state.densityGml.parseOptionalNumber()
            )
        )

    private fun revalidate() = update { copy(validation = validate(this)) }

    /**
     * Warns when a name matches, or nearly matches, one already in the
     * database — the imported set is large enough that "Olive oil" is easy to
     * add twice without noticing.
     */
    private fun checkForDuplicates(name: String) {
        duplicateCheckJob?.cancel()
        duplicateCheckJob = viewModelScope.launch {
            delay(250)
            val excluding = original?.id ?: 0L
            val exact = name.isNotBlank() && repository.isDuplicateName(name, excluding)
            val similar = if (exact) emptyList() else repository.findSimilar(name, excluding)
            if (_uiState.value.name == name) {
                update { copy(duplicateName = exact, similar = similar) }
            }
        }
    }

    /** Rough share of the nutrient panel the user filled in, for the warnings. */
    private fun completeness(state: IngredientEditorUiState): Int {
        val possible = state.nutrientDefs.count { it.isPrimary }.coerceAtLeast(1)
        val filled = state.nutrientDefs.count {
            it.isPrimary && state.micronutrients[it.tag]?.toLocalizedDoubleOrNull() != null
        }
        // The four required macros always count as present.
        return (((filled + 4).toDouble() / (possible + 4)) * 100).toInt().coerceIn(0, 100)
    }

    private inline fun updateAndValidate(block: IngredientEditorUiState.() -> IngredientEditorUiState) {
        update(block)
        revalidate()
    }

    private inline fun update(block: IngredientEditorUiState.() -> IngredientEditorUiState) {
        _uiState.value = _uiState.value.block()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as NutriNestApp
                IngredientEditorViewModel(
                    app,
                    app.container.ingredientRepository,
                    app.container.foodReferenceRepository,
                    createSavedStateHandle()
                )
            }
        }
    }

    override fun onCleared() {
        val draft = _uiState.value.imagePath
        if (!_uiState.value.saved && draft != original?.imagePath) {
            ImageStorage.deleteManagedNow(getApplication(), draft)
        }
        super.onCleared()
    }
}

/** Renders a stored value for a text field without a trailing ".0". */
private fun Double.toEditableText(): String =
    LocalizedNumbers.format(this)

private fun String.parseOptionalNumber(): Double? =
    if (isBlank()) null else toLocalizedDoubleOrNull() ?: Double.NaN
