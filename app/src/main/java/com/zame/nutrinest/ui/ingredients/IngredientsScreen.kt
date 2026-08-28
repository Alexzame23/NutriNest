package com.zame.nutrinest.ui.ingredients

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.ui.components.CategoryDot
import com.zame.nutrinest.ui.components.MacroBarChart
import com.zame.nutrinest.ui.theme.Amber
import java.io.File
import kotlin.math.roundToInt

/**
 * Browse, search, filter and organise every ingredient the app knows about.
 *
 * Lives inside the Recipes & Ingredients tab host, which owns the Scaffold, so
 * this is content rather than a screen of its own.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IngredientsTabContent(
    onOpenIngredient: (Long) -> Unit,
    onCompare: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IngredientsViewModel = viewModel(factory = IngredientsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spanish = rememberIsSpanish()

    // The ViewModel outlives the language switch, so it has to be told.
    LaunchedEffect(spanish) { viewModel.onLocaleChanged() }

    Column(modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        ViewModeRow(
            selected = state.view,
            onSelected = viewModel::onViewChange
        )

        FilterRow(
            state = state,
            spanish = spanish,
            onCategoryToggled = viewModel::onCategoryToggled,
            onCustomOnlyToggled = viewModel::onCustomOnlyToggled,
            onClearFilters = viewModel::clearFilters,
            onSortChange = viewModel::onSortChange,
            onCompareModeToggled = viewModel::onCompareModeToggled
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
                    stickyHeader(key = "header_${group.key}") {
                        CategoryHeader(
                            name = group.name,
                            count = group.ingredients.size,
                            color = categoryColor(group.key, group.colorHex),
                            expanded = state.isExpanded(group.key),
                            onClick = { viewModel.onGroupToggled(group.key) }
                        )
                    }
                    if (state.isExpanded(group.key)) {
                        items(group.ingredients, key = { it.id }) { ingredient ->
                            IngredientRow(
                                ingredient = ingredient,
                                spanish = spanish,
                                compareMode = state.compareMode,
                                selectedForCompare = ingredient.id in state.compareSelection,
                                onClick = {
                                    if (state.compareMode) {
                                        viewModel.onCompareToggled(ingredient.id)
                                    } else {
                                        onOpenIngredient(ingredient.id)
                                    }
                                },
                                onFavoriteClick = { viewModel.onFavoriteToggled(ingredient) }
                            )
                        }
                    }
                }

                state.sections.forEach { section ->
                    stickyHeader(key = "section_${section.titleRes}") {
                        SectionHeader(stringResource(section.titleRes))
                    }
                    items(section.ingredients, key = { "${section.titleRes}_${it.id}" }) { ingredient ->
                        IngredientRow(
                            ingredient = ingredient,
                            spanish = spanish,
                            compareMode = state.compareMode,
                            selectedForCompare = ingredient.id in state.compareSelection,
                            onClick = {
                                if (state.compareMode) {
                                    viewModel.onCompareToggled(ingredient.id)
                                } else {
                                    onOpenIngredient(ingredient.id)
                                }
                            },
                            onFavoriteClick = { viewModel.onFavoriteToggled(ingredient) }
                        )
                    }
                }

                items(state.items, key = { it.id }) { ingredient ->
                    IngredientRow(
                        ingredient = ingredient,
                        spanish = spanish,
                        compareMode = state.compareMode,
                        selectedForCompare = ingredient.id in state.compareSelection,
                        onClick = {
                            if (state.compareMode) {
                                viewModel.onCompareToggled(ingredient.id)
                            } else {
                                onOpenIngredient(ingredient.id)
                            }
                        },
                        onFavoriteClick = { viewModel.onFavoriteToggled(ingredient) }
                    )
                }

                state.attribution?.let { credit ->
                    item(key = "attribution") { AttributionNote(credit) }
                }
            }
        }

        AnimatedVisibility(visible = state.compareMode) {
            CompareBar(
                selectedCount = state.compareSelection.size,
                canCompare = state.canCompare,
                onCompare = { onCompare(state.compareSelection) },
                onCancel = viewModel::onCompareModeToggled
            )
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
        placeholder = { Text(stringResource(R.string.ingredients_search_hint)) },
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
private fun ViewModeRow(
    selected: IngredientView,
    onSelected: (IngredientView) -> Unit
) {
    val options = listOf(
        IngredientView.CATEGORIES to R.string.ingredients_view_categories,
        IngredientView.ALL to R.string.ingredients_view_all,
        IngredientView.FAVORITES to R.string.ingredients_view_favorites,
        IngredientView.RECENT to R.string.ingredients_view_recent
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
    state: IngredientsUiState,
    spanish: Boolean,
    onCategoryToggled: (String) -> Unit,
    onCustomOnlyToggled: () -> Unit,
    onClearFilters: () -> Unit,
    onSortChange: (IngredientSort) -> Unit,
    onCompareModeToggled: () -> Unit
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
                        leadingIcon = {
                            Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                        },
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
            item(key = "custom") {
                FilterChip(
                    selected = state.customOnly,
                    onClick = onCustomOnlyToggled,
                    label = { Text(stringResource(R.string.ingredients_custom_only)) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            items(state.categories, key = { it.key }) { category ->
                val selected = category.key in state.selectedCategories
                val tint = categoryColor(category.key, category.colorHex)
                FilterChip(
                    selected = selected,
                    onClick = { onCategoryToggled(category.key) },
                    label = {
                        Text(if (spanish) category.nameEs.shortened() else category.nameEn.shortened())
                    },
                    leadingIcon = { CategoryDot(tint, size = 10.dp) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tint.copy(alpha = 0.20f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        IconButton(onClick = onCompareModeToggled) {
            Icon(
                Icons.AutoMirrored.Rounded.CompareArrows,
                contentDescription = stringResource(R.string.ingredients_compare_start),
                tint = if (state.compareMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        SortMenu(selected = state.sort, onSelected = onSortChange)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun SortMenu(selected: IngredientSort, onSelected: (IngredientSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        IngredientSort.NAME_ASC to R.string.ingredients_sort_name_asc,
        IngredientSort.NAME_DESC to R.string.ingredients_sort_name_desc,
        IngredientSort.CALORIES_DESC to R.string.ingredients_sort_calories_desc,
        IngredientSort.CALORIES_ASC to R.string.ingredients_sort_calories_asc,
        IngredientSort.PROTEIN_DESC to R.string.ingredients_sort_protein_desc
    )
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Rounded.Sort,
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
    name: String,
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
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                pluralStringResource(R.plurals.ingredients_count, count, count),
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
private fun SectionHeader(title: String) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }
}

@Composable
private fun IngredientRow(
    ingredient: IngredientEntity,
    spanish: Boolean,
    compareMode: Boolean,
    selectedForCompare: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (selectedForCompare) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (ingredient.imagePath != null) {
                AsyncImage(
                    model = File(ingredient.imagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
            } else {
                CategoryDot(categoryColor(ingredient.categoryKey), size = 12.dp)
                Spacer(Modifier.width(12.dp))
            }

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ingredient.displayName(spanish),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (ingredient.hasIncompleteData) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = stringResource(R.string.ingredients_incomplete_data),
                            tint = Amber,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (ingredient.isCustom) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.ingredients_custom_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.kcal_per_100g, ingredient.nutrition.kcal.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                MacroBarChart(ingredient.nutrition, height = 5.dp)
            }

            Spacer(Modifier.width(8.dp))

            if (compareMode) {
                Icon(
                    if (selectedForCompare) Icons.Rounded.Check
                    else Icons.AutoMirrored.Rounded.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        if (ingredient.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(
                            if (ingredient.isFavorite) {
                                R.string.ingredients_favorite_remove
                            } else {
                                R.string.ingredients_favorite_add
                            }
                        ),
                        tint = if (ingredient.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareBar(
    selectedCount: Int,
    canCompare: Boolean,
    onCompare: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider()
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (selectedCount) {
                        0 -> stringResource(R.string.ingredients_compare_hint)
                        IngredientsUiState.MAX_COMPARE ->
                            stringResource(R.string.ingredients_compare_limit)
                        else ->
                            stringResource(R.string.ingredients_compare_selected, selectedCount)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onCompare, enabled = canCompare) {
                    Text(stringResource(R.string.action_compare))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(state: IngredientsUiState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.ingredients_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    when {
                        state.view == IngredientView.FAVORITES ->
                            R.string.ingredients_empty_favorites
                        state.view == IngredientView.RECENT ->
                            R.string.ingredients_empty_recent
                        else -> R.string.ingredients_empty_body
                    }
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The dataset's licence asks for BEDCA to be credited wherever its figures are
 * shown, so the browser carries the notice at the end of the list.
 */
@Composable
private fun AttributionNote(credit: String) {
    Column(Modifier.padding(top = 24.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.ingredients_attribution_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            credit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The dataset's official category names run long ("Pescados, moluscos,
 * reptiles, crustáceos y derivados"); chips only have room for the head of it.
 */
private fun String.shortened(): String = substringBefore(',').substringBefore(" y ").trim()
