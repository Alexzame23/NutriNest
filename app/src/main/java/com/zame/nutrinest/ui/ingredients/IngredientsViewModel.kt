package com.zame.nutrinest.ui.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.CategoryWithCount
import com.zame.nutrinest.data.local.entity.FoodDatasetMetaEntity
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.repository.FoodReferenceRepository
import com.zame.nutrinest.data.repository.IngredientRepository
import com.zame.nutrinest.util.TextNormalizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/** How the browser lays the ingredients out. */
enum class IngredientView { CATEGORIES, ALL, FAVORITES, RECENT }

enum class IngredientSort { NAME_ASC, NAME_DESC, CALORIES_DESC, CALORIES_ASC, PROTEIN_DESC }

/** One expandable section of the grouped view. */
data class CategoryGroup(
    val key: String,
    val name: String,
    val colorHex: String,
    val ingredients: List<IngredientEntity>
)

/** A titled run of ingredients in the "Recent" view. */
data class LabelledSection(
    val titleRes: Int,
    val ingredients: List<IngredientEntity>
)

data class IngredientsUiState(
    val query: String = "",
    val view: IngredientView = IngredientView.CATEGORIES,
    val sort: IngredientSort = IngredientSort.NAME_ASC,
    val selectedCategories: Set<String> = emptySet(),
    val customOnly: Boolean = false,
    val categories: List<CategoryWithCount> = emptyList(),
    /** Populated for [IngredientView.CATEGORIES]. */
    val groups: List<CategoryGroup> = emptyList(),
    /** Populated for the flat views. */
    val items: List<IngredientEntity> = emptyList(),
    /** Populated for [IngredientView.RECENT]. */
    val sections: List<LabelledSection> = emptyList(),
    val collapsedCategories: Set<String> = emptySet(),
    val compareMode: Boolean = false,
    val compareSelection: List<Long> = emptyList(),
    val attribution: String? = null,
    val isLoading: Boolean = true
) {
    val hasFilters: Boolean get() = selectedCategories.isNotEmpty() || customOnly
    val isEmpty: Boolean
        get() = groups.all { it.ingredients.isEmpty() } && items.isEmpty() &&
            sections.all { it.ingredients.isEmpty() }

    /** Search auto-expands, so a match is never hidden inside a collapsed group. */
    fun isExpanded(key: String): Boolean =
        query.isNotBlank() || key !in collapsedCategories

    val canCompare: Boolean get() = compareSelection.size in 2..MAX_COMPARE

    companion object {
        const val MAX_COMPARE = 3
    }
}

private data class Filters(
    val query: String = "",
    val view: IngredientView = IngredientView.CATEGORIES,
    val sort: IngredientSort = IngredientSort.NAME_ASC,
    val selectedCategories: Set<String> = emptySet(),
    val customOnly: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientsViewModel(
    private val repository: IngredientRepository,
    referenceRepository: FoodReferenceRepository,
    private val locale: Locale = Locale.getDefault()
) : ViewModel() {

    private val spanish = isSpanishLocale(locale)

    /** PRIMARY strength ignores case and accents, matching how search behaves. */
    private val collator = Collator.getInstance(locale)
        .apply { strength = Collator.PRIMARY }

    private val filters = MutableStateFlow(Filters())
    private val collapsed = MutableStateFlow<Set<String>>(emptySet())
    private val compare = MutableStateFlow(CompareState())

    private data class CompareState(
        val active: Boolean = false,
        val selection: List<Long> = emptyList()
    )

    private val ingredients = filters.flatMapLatest { f ->
        when (f.view) {
            // "Recent" is ordered by the database, then narrowed in memory so
            // the search box still works inside it.
            IngredientView.RECENT -> repository.observeRecentlyUsed(RECENT_LIMIT)
            else -> repository.observeFiltered(
                query = f.query,
                categoryKeys = f.selectedCategories,
                favoritesOnly = f.view == IngredientView.FAVORITES,
                customOnly = f.customOnly
            )
        }
    }

    private val recentlyAdded = repository.observeRecentlyAdded(RECENT_LIMIT)

    private val attribution = referenceRepository.observeDatasetMeta()
        .map { meta -> meta.firstOrNull { it.key == FoodDatasetMetaEntity.ATTRIBUTION }?.value }

    val uiState: StateFlow<IngredientsUiState> = combine(
        filters,
        ingredients,
        referenceRepository.observeCategoriesWithCounts(),
        collapsed,
        combine(compare, recentlyAdded, attribution) { c, added, credit -> Triple(c, added, credit) }
    ) { f, all, categories, collapsedKeys, (compareState, added, credit) ->
        val visible = if (f.view == IngredientView.RECENT) narrowInMemory(all, f) else all
        val sorted = sort(visible, f.sort)

        IngredientsUiState(
            query = f.query,
            view = f.view,
            sort = f.sort,
            selectedCategories = f.selectedCategories,
            customOnly = f.customOnly,
            categories = categories,
            groups = if (f.view == IngredientView.CATEGORIES) group(sorted, categories) else emptyList(),
            items = if (f.view == IngredientView.CATEGORIES || f.view == IngredientView.RECENT) {
                emptyList()
            } else {
                sorted
            },
            sections = if (f.view == IngredientView.RECENT) {
                listOf(
                    LabelledSection(
                        R.string.ingredients_view_recent,
                        sorted
                    ),
                    LabelledSection(
                        R.string.ingredients_recently_added,
                        sort(narrowInMemory(added, f), f.sort)
                    )
                ).filter { it.ingredients.isNotEmpty() }
            } else {
                emptyList()
            },
            collapsedCategories = collapsedKeys,
            compareMode = compareState.active,
            compareSelection = compareState.selection,
            attribution = credit,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IngredientsUiState())

    // ── Intents ─────────────────────────────────────────────────────────

    fun onQueryChange(value: String) = filters.update { copy(query = value) }

    fun onViewChange(view: IngredientView) = filters.update { copy(view = view) }

    fun onSortChange(sort: IngredientSort) = filters.update { copy(sort = sort) }

    fun onCategoryToggled(key: String) = filters.update {
        copy(
            selectedCategories = if (key in selectedCategories) {
                selectedCategories - key
            } else {
                selectedCategories + key
            }
        )
    }

    fun onCustomOnlyToggled() = filters.update { copy(customOnly = !customOnly) }

    fun clearFilters() = filters.update {
        copy(selectedCategories = emptySet(), customOnly = false)
    }

    fun onGroupToggled(key: String) {
        collapsed.value = if (key in collapsed.value) collapsed.value - key else collapsed.value + key
    }

    fun onFavoriteToggled(ingredient: IngredientEntity) {
        viewModelScope.launch {
            repository.setFavorite(ingredient.id, !ingredient.isFavorite)
        }
    }

    fun onCompareModeToggled() {
        compare.value = if (compare.value.active) CompareState() else CompareState(active = true)
    }

    fun onCompareToggled(id: Long) {
        val current = compare.value
        val selection = when {
            id in current.selection -> current.selection - id
            current.selection.size >= IngredientsUiState.MAX_COMPARE -> current.selection
            else -> current.selection + id
        }
        compare.value = current.copy(selection = selection)
    }

    fun clearCompare() {
        compare.value = CompareState()
    }

    // ── Shaping ─────────────────────────────────────────────────────────

    /**
     * Applies the search box and category chips to a list the database has
     * already ordered for us (the "Recent" views), so those views behave like
     * the others without giving up their ordering.
     */
    private fun narrowInMemory(items: List<IngredientEntity>, f: Filters): List<IngredientEntity> {
        val needle = TextNormalizer.normalize(f.query)
        return items.filter { item ->
            (f.selectedCategories.isEmpty() || item.categoryKey in f.selectedCategories) &&
                (!f.customOnly || item.isCustom) &&
                (
                    needle.isEmpty() ||
                        item.nameNorm.contains(needle) ||
                        item.nameEsNorm?.contains(needle) == true
                    )
        }
    }

    private fun sort(items: List<IngredientEntity>, sort: IngredientSort): List<IngredientEntity> {
        val byName = compareBy(collator) { it: IngredientEntity -> it.displayName(spanish) }
        return when (sort) {
            IngredientSort.NAME_ASC -> items.sortedWith(byName)
            IngredientSort.NAME_DESC -> items.sortedWith(byName.reversed())
            IngredientSort.CALORIES_DESC ->
                items.sortedWith(compareByDescending<IngredientEntity> { it.nutrition.kcal }.then(byName))
            IngredientSort.CALORIES_ASC ->
                items.sortedWith(compareBy<IngredientEntity> { it.nutrition.kcal }.then(byName))
            IngredientSort.PROTEIN_DESC ->
                items.sortedWith(compareByDescending<IngredientEntity> { it.nutrition.protein }.then(byName))
        }
    }

    /** Groups into the dataset's category order, dropping categories with no hits. */
    private fun group(
        items: List<IngredientEntity>,
        categories: List<CategoryWithCount>
    ): List<CategoryGroup> {
        val byCategory = items.groupBy { it.categoryKey }
        return categories.mapNotNull { category ->
            val members = byCategory[category.key].orEmpty()
            if (members.isEmpty()) {
                null
            } else {
                CategoryGroup(
                    key = category.key,
                    name = if (spanish) category.nameEs else category.nameEn,
                    colorHex = category.colorHex,
                    ingredients = members
                )
            }
        }
    }

    private inline fun MutableStateFlow<Filters>.update(block: Filters.() -> Filters) {
        value = value.block()
    }

    companion object {
        private const val RECENT_LIMIT = 30

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as NutriNestApp
                IngredientsViewModel(
                    app.container.ingredientRepository,
                    app.container.foodReferenceRepository
                )
            }
        }
    }
}
