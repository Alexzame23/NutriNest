package com.zame.nutrinest.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.data.local.entity.RecipeEntity
import java.io.File

/**
 * The Recipes list.
 *
 * Rendered inside the Recipes & Ingredients tab host, which owns the Scaffold,
 * the top bar and the add button — hence content rather than a screen of its
 * own. The filtering, cards and empty state are unchanged.
 */
@Composable
fun RecipesTabContent(
    onOpenRecipe: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipesViewModel = viewModel(factory = RecipesViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier.fillMaxSize()) {
        // ── Folder chips ────────────────────────────────────────
        if (state.folders.isNotEmpty()) {
            FilterChipRow(
                icon = { Icon(Icons.Rounded.Folder, null, Modifier.size(16.dp)) },
                options = state.folders,
                selected = state.selectedFolder,
                onSelected = viewModel::onFolderSelected
            )
        }
        // ── Category chips ──────────────────────────────────────
        FilterChipRow(
            icon = null,
            options = RecipeCategories.ALL,
            selected = state.selectedCategory,
            onSelected = viewModel::onCategorySelected
        )

        if (state.isEmpty) {
            EmptyState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(state.recipes, key = { it.id }) { recipe ->
                    RecipeCard(recipe = recipe, onClick = { onOpenRecipe(recipe.id) })
                }
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    icon: (@Composable () -> Unit)?,
    options: List<String>,
    selected: String?,
    onSelected: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text(option) },
                leadingIcon = if (selected == option || icon == null) null else icon,
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
private fun RecipeCard(recipe: RecipeEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (recipe.coverImagePath != null) {
                AsyncImage(
                    model = File(recipe.coverImagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(92.dp)
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(92.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.RestaurantMenu,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(recipe.category, recipe.folder).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.RestaurantMenu,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.recipes_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.recipes_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
