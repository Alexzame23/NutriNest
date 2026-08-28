package com.zame.nutrinest.ui.menu.config

import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.util.TextNormalizer

/** All filters available in the locked-meal recipe picker. */
internal data class LockedMealRecipeSearchFilters(
    val query: String = "",
    /** null = all books; an empty set = deliberately no books. */
    val folders: Set<String?>? = null,
    val categories: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false
)

/** Applies every active picker filter and keeps favorites at the top. */
internal fun filterLockedMealRecipes(
    recipes: List<RecipeEntity>,
    filters: LockedMealRecipeSearchFilters,
    spanish: Boolean
): List<RecipeEntity> {
    val normalizedQuery = TextNormalizer.normalize(filters.query)
    return recipes.asSequence()
        .filter { recipe -> filters.folders == null || recipe.folder in filters.folders }
        .filter { recipe -> filters.categories.isEmpty() || recipe.category in filters.categories }
        .filter { recipe -> !filters.favoritesOnly || recipe.isFavorite }
        .filter { recipe ->
            normalizedQuery.isEmpty() ||
                TextNormalizer.normalize(recipe.name).contains(normalizedQuery) ||
                recipe.nameEs?.let { TextNormalizer.normalize(it).contains(normalizedQuery) } == true ||
                recipe.folder?.let { folder ->
                    RecipeBooks.searchableNames(folder).any {
                        TextNormalizer.normalize(it).contains(normalizedQuery)
                    }
                } == true
        }
        .sortedWith(
            compareByDescending<RecipeEntity> { it.isFavorite }
                .thenBy { it.displayName(spanish) }
        )
        .toList()
}
