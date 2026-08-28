package com.zame.nutrinest.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.repository.RecipeRepository
import com.zame.nutrinest.util.TextNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/** How the browser lays the recipes out. Mirrors [com.zame.nutrinest.ui.ingredients.IngredientView]. */
enum class RecipeView { CATEGORIES, ALL, FAVORITES, RECENT }

enum class RecipeSort { NAME_ASC, NAME_DESC, CALORIES_DESC, CALORIES_ASC, NEWEST, OLDEST }

/** One expandable section of the grouped view. */
data class RecipeGroup(
    val categoryKey: String,
    val recipes: List<RecipeEntity>
)

data class RecipesUiState(
    val query: String = "",
    val view: RecipeView = RecipeView.CATEGORIES,
    val sort: RecipeSort = RecipeSort.NAME_ASC,
    val selectedCategories: Set<String> = emptySet(),
    val selectedFolders: Set<String> = emptySet(),
    val folders: List<String> = emptyList(),
    /** Populated for [RecipeView.CATEGORIES]. */
    val groups: List<RecipeGroup> = emptyList(),
    /** Populated for the flat views. */
    val items: List<RecipeEntity> = emptyList(),
    val collapsedCategories: Set<String> = emptySet(),
    /** Counts per category across the whole library, for the filter chips. */
    val categoryCounts: Map<String, Int> = emptyMap(),
    /** Totals per recipe id; a recipe with no ingredients is simply absent. */
    val nutrition: Map<Long, NutritionValues> = emptyMap(),
    /** False only while the very first query is in flight. */
    val hasAnyRecipes: Boolean = false,
    val isLoading: Boolean = true
) {
    val hasFilters: Boolean get() = selectedCategories.isNotEmpty() || selectedFolders.isNotEmpty()

    val isEmpty: Boolean get() = groups.all { it.recipes.isEmpty() } && items.isEmpty()

    fun nutritionOf(recipeId: Long): NutritionValues = nutrition[recipeId] ?: NutritionValues()

    /** Search auto-expands, so a match is never hidden inside a collapsed group. */
    fun isExpanded(key: String): Boolean =
        query.isNotBlank() || key !in collapsedCategories
}

private data class Filters(
    val query: String = "",
    val view: RecipeView = RecipeView.CATEGORIES,
    val sort: RecipeSort = RecipeSort.NAME_ASC,
    val selectedCategories: Set<String> = emptySet(),
    val selectedFolders: Set<String> = emptySet()
)

/**
 * Drives the Recipes tab.
 *
 * A user's own recipe library is small — tens, not thousands — so searching,
 * filtering and sorting all happen in memory over the single `observeAll()`
 * flow. The ingredient browser pushes the same work into SQL because it has a
 * thousand imported foods to get through.
 */
class RecipesViewModel(
    private val repository: RecipeRepository,
    private val locale: () -> Locale = { Locale.getDefault() }
) : ViewModel() {

    /**
     * PRIMARY strength ignores case and accents, matching how search behaves.
     * Built on use, not captured: this ViewModel outlives the activity restart
     * that the in-app language switch performs.
     */
    private fun collator(): Collator =
        Collator.getInstance(locale()).apply { strength = Collator.PRIMARY }

    private val filters = MutableStateFlow(Filters())
    private val collapsed = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<RecipesUiState> = combine(
        repository.observeAll(),
        repository.observeFolders(),
        repository.observeNutritionTotals(),
        filters,
        collapsed
    ) { recipes, folders, nutrition, f, collapsedKeys ->
        val visible = sort(narrow(recipes, f), f.sort, nutrition)
        val grouped = f.view == RecipeView.CATEGORIES

        RecipesUiState(
            query = f.query,
            view = f.view,
            sort = f.sort,
            selectedCategories = f.selectedCategories,
            selectedFolders = f.selectedFolders,
            folders = folders,
            groups = if (grouped) group(visible) else emptyList(),
            items = if (grouped) emptyList() else visible,
            collapsedCategories = collapsedKeys,
            categoryCounts = recipes.groupingBy { it.category }.eachCount(),
            nutrition = nutrition,
            hasAnyRecipes = recipes.isNotEmpty(),
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesUiState())

    // ── Intents ─────────────────────────────────────────────────────────

    fun onQueryChange(value: String) = filters.update { copy(query = value) }

    fun onViewChange(view: RecipeView) = filters.update { copy(view = view) }

    fun onSortChange(sort: RecipeSort) = filters.update { copy(sort = sort) }

    fun onCategoryToggled(key: String) = filters.update {
        copy(selectedCategories = selectedCategories.toggle(key))
    }

    fun onFolderToggled(folder: String) = filters.update {
        copy(selectedFolders = selectedFolders.toggle(folder))
    }

    fun clearFilters() = filters.update {
        copy(selectedCategories = emptySet(), selectedFolders = emptySet())
    }

    fun onGroupToggled(key: String) {
        collapsed.value = collapsed.value.toggle(key)
    }

    fun onFavoriteToggled(recipe: RecipeEntity) {
        viewModelScope.launch {
            repository.setFavorite(recipe.id, !recipe.isFavorite)
        }
    }

    // ── Shaping ─────────────────────────────────────────────────────────

    private fun narrow(recipes: List<RecipeEntity>, f: Filters): List<RecipeEntity> {
        val needle = TextNormalizer.normalize(f.query)
        val recent = if (f.view == RecipeView.RECENT) {
            recipes.sortedByDescending { it.createdAt }.take(RECENT_LIMIT).map { it.id }.toSet()
        } else {
            null
        }
        return recipes.filter { recipe ->
            (f.selectedCategories.isEmpty() || recipe.category in f.selectedCategories) &&
                (f.selectedFolders.isEmpty() || recipe.folder in f.selectedFolders) &&
                (f.view != RecipeView.FAVORITES || recipe.isFavorite) &&
                (recent == null || recipe.id in recent) &&
                (needle.isEmpty() || recipe.matches(needle))
        }
    }

    /** Matches the name and the folder, so "desserts" finds a folder of them. */
    private fun RecipeEntity.matches(needle: String): Boolean =
        TextNormalizer.normalize(name).contains(needle) ||
            nameEs?.let { TextNormalizer.normalize(it).contains(needle) } == true ||
            folder?.let { value ->
                RecipeBooks.searchableNames(value).any {
                    TextNormalizer.normalize(it).contains(needle)
                }
            } == true

    private fun sort(
        recipes: List<RecipeEntity>,
        sort: RecipeSort,
        nutrition: Map<Long, NutritionValues>
    ): List<RecipeEntity> {
        val spanish = locale().language.equals("es", ignoreCase = true)
        val byName = compareBy(collator()) { recipe: RecipeEntity -> recipe.displayName(spanish) }
        return when (sort) {
            RecipeSort.NAME_ASC -> recipes.sortedWith(byName)
            RecipeSort.NAME_DESC -> recipes.sortedWith(byName.reversed())
            RecipeSort.CALORIES_DESC -> recipes.sortedWith(
                compareByDescending<RecipeEntity> { nutrition[it.id]?.kcal ?: 0.0 }.then(byName)
            )
            RecipeSort.CALORIES_ASC -> recipes.sortedWith(
                compareBy<RecipeEntity> { nutrition[it.id]?.kcal ?: 0.0 }.then(byName)
            )
            RecipeSort.NEWEST -> recipes.sortedWith(
                compareByDescending<RecipeEntity> { it.createdAt }.then(byName)
            )
            RecipeSort.OLDEST -> recipes.sortedWith(
                compareBy<RecipeEntity> { it.createdAt }.then(byName)
            )
        }
    }

    /**
     * Groups into the course order, dropping empty courses. Anything with a
     * category this build does not know about lands in a trailing group rather
     * than disappearing from the list.
     */
    private fun group(recipes: List<RecipeEntity>): List<RecipeGroup> {
        val byCategory = recipes.groupBy { it.category }
        val known = RecipeCategories.ALL.mapNotNull { key ->
            byCategory[key]?.takeIf { it.isNotEmpty() }?.let { RecipeGroup(key, it) }
        }
        val unknown = byCategory
            .filterKeys { it !in RecipeCategories.ALL }
            .map { (key, members) -> RecipeGroup(key, members) }
            .sortedBy { it.categoryKey }
        return known + unknown
    }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value

    private inline fun MutableStateFlow<Filters>.update(block: Filters.() -> Filters) {
        value = value.block()
    }

    companion object {
        private const val RECENT_LIMIT = 15

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as NutriNestApp
                RecipesViewModel(app.container.recipeRepository)
            }
        }
    }
}
