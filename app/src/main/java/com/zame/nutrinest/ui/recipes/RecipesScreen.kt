package com.zame.nutrinest.ui.recipes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.ui.components.CategoryDot
import com.zame.nutrinest.ui.components.MacroBarChart
import java.io.File
import kotlin.math.roundToInt

/**
 * The Recipes list.
 *
 * Laid out like the Ingredients browser next door — same search field, view
 * modes, filter chips, sort menu, expandable course sections and compact rows —
 * so moving between the two tabs does not mean learning a second list.
 *
 * Rendered inside the Recipes & Ingredients tab host, which owns the Scaffold,
 * the top bar and the add button; hence content rather than a screen of its own.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecipesTabContent(
    onOpenRecipe: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipesViewModel = viewModel(factory = RecipesViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        ViewModeRow(selected = state.view, onSelected = viewModel::onViewChange)

        FilterRow(
            state = state,
            onCategoryToggled = viewModel::onCategoryToggled,
            onFolderToggled = viewModel::onFolderToggled,
            onClearFilters = viewModel::clearFilters,
            onSortChange = viewModel::onSortChange
        )

        if (state.isEmpty && !state.isLoading) {
            EmptyState(state)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.groups.forEach { group ->
                    stickyHeader(key = "header_${group.categoryKey}") {
                        CategoryHeader(
                            title = stringResource(recipeCategoryLabel(group.categoryKey)),
                            count = group.recipes.size,
                            color = recipeCategoryColor(group.categoryKey),
                            expanded = state.isExpanded(group.categoryKey),
                            onClick = { viewModel.onGroupToggled(group.categoryKey) }
                        )
                    }
                    if (state.isExpanded(group.categoryKey)) {
                        items(group.recipes, key = { it.id }) { recipe ->
                            RecipeRow(
                                recipe = recipe,
                                nutrition = state.nutritionOf(recipe.id),
                                onClick = { onOpenRecipe(recipe.id) },
                                onFavoriteClick = { viewModel.onFavoriteToggled(recipe) }
                            )
                        }
                    }
                }

                items(state.items, key = { it.id }) { recipe ->
                    RecipeRow(
                        recipe = recipe,
                        nutrition = state.nutritionOf(recipe.id),
                        onClick = { onOpenRecipe(recipe.id) },
                        onFavoriteClick = { viewModel.onFavoriteToggled(recipe) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.action_clear))
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun ViewModeRow(selected: RecipeView, onSelected: (RecipeView) -> Unit) {
    val options = listOf(
        RecipeView.CATEGORIES to R.string.recipes_view_categories,
        RecipeView.ALL to R.string.recipes_view_all,
        RecipeView.FAVORITES to R.string.recipes_view_favorites,
        RecipeView.RECENT to R.string.recipes_view_recent
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { (view, labelRes) ->
            FilterChip(
                selected = selected == view,
                onClick = { onSelected(view) },
                label = { Text(stringResource(labelRes)) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun FilterRow(
    state: RecipesUiState,
    onCategoryToggled: (String) -> Unit,
    onFolderToggled: (String) -> Unit,
    onClearFilters: () -> Unit,
    onSortChange: (RecipeSort) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.hasFilters) {
                item(key = "clear") {
                    FilterChip(
                        selected = false,
                        onClick = onClearFilters,
                        label = { Text(stringResource(R.string.ingredients_filter_clear)) },
                        leadingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) },
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
            items(state.folders, key = { "folder_$it" }) { folder ->
                FilterChip(
                    selected = folder in state.selectedFolders,
                    onClick = { onFolderToggled(folder) },
                    label = { Text(folder) },
                    leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(16.dp)) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            // Only courses that actually have recipes, so the row stays short.
            val categories = RecipeCategories.ALL.filter { (state.categoryCounts[it] ?: 0) > 0 }
            items(categories, key = { "category_$it" }) { category ->
                val tint = recipeCategoryColor(category)
                FilterChip(
                    selected = category in state.selectedCategories,
                    onClick = { onCategoryToggled(category) },
                    label = { Text(stringResource(recipeCategoryLabel(category))) },
                    leadingIcon = { CategoryDot(tint, size = 10.dp) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tint.copy(alpha = 0.20f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        SortMenu(selected = state.sort, onSelected = onSortChange)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun SortMenu(selected: RecipeSort, onSelected: (RecipeSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        RecipeSort.NAME_ASC to R.string.ingredients_sort_name_asc,
        RecipeSort.NAME_DESC to R.string.ingredients_sort_name_desc,
        RecipeSort.CALORIES_DESC to R.string.ingredients_sort_calories_desc,
        RecipeSort.CALORIES_ASC to R.string.ingredients_sort_calories_asc,
        RecipeSort.NEWEST to R.string.recipes_sort_newest,
        RecipeSort.OLDEST to R.string.recipes_sort_oldest
    )
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Rounded.Sort,
                contentDescription = stringResource(R.string.ingredients_sort_label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (sort, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    trailingIcon = {
                        if (sort == selected) Icon(Icons.Rounded.Check, contentDescription = null)
                    },
                    onClick = {
                        onSelected(sort)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    title: String,
    count: Int,
    color: Color,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryDot(color, size = 12.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                pluralStringResource(R.plurals.recipes_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecipeRow(
    recipe: RecipeEntity,
    nutrition: NutritionValues,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (recipe.coverImagePath != null) {
                AsyncImage(
                    model = File(recipe.coverImagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.width(12.dp))
            } else {
                CategoryDot(recipeCategoryColor(recipe.category), size = 12.dp)
                Spacer(Modifier.width(12.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        stringResource(recipeCategoryLabel(recipe.category)),
                        recipe.folder,
                        stringResource(R.string.kcal_value, nutrition.kcal.roundToInt())
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                MacroBarChart(nutrition, height = 5.dp)
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onFavoriteClick) {
                Icon(
                    if (recipe.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = stringResource(
                        if (recipe.isFavorite) {
                            R.string.recipes_favorite_remove
                        } else {
                            R.string.recipes_favorite_add
                        }
                    ),
                    tint = if (recipe.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(state: RecipesUiState) {
    // A library with nothing in it needs "add one"; a library with no matches
    // needs "loosen the filters". Same layout, different advice.
    val bodyRes = when {
        !state.hasAnyRecipes -> R.string.recipes_empty_body
        state.view == RecipeView.FAVORITES -> R.string.recipes_empty_favorites
        state.view == RecipeView.RECENT -> R.string.recipes_empty_recent
        else -> R.string.recipes_empty_filtered
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Icon(
                if (state.hasAnyRecipes) Icons.Rounded.Search else Icons.Rounded.RestaurantMenu,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(
                    if (state.hasAnyRecipes) {
                        R.string.recipes_empty_filtered_title
                    } else {
                        R.string.recipes_empty_title
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
