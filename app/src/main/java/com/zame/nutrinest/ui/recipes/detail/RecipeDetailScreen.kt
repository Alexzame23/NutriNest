package com.zame.nutrinest.ui.recipes.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.ui.components.MacroDonutChart
import com.zame.nutrinest.ui.components.NutritionSummary
import com.zame.nutrinest.ui.components.SlowMarqueeText
import com.zame.nutrinest.ui.components.StatusChip
import com.zame.nutrinest.ui.components.formatGrams
import com.zame.nutrinest.ui.components.formatRecipeQuantity
import com.zame.nutrinest.ui.ingredients.rememberIsSpanish
import com.zame.nutrinest.ui.recipes.recipeCategoryColor
import com.zame.nutrinest.ui.recipes.recipeCategoryIcon
import com.zame.nutrinest.ui.recipes.recipeCategoryLabel
import com.zame.nutrinest.ui.recipes.toRecipeImageAlignment
import java.io.File
import kotlin.math.roundToInt

/**
 * A recipe on its own screen: what it makes, what goes into it, and how to
 * cook it.
 *
 * Tapping a recipe in the list used to do nothing at all — the navigation
 * callback was an empty TODO — so this is the destination that was missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    viewModel: RecipeDetailViewModel = viewModel(factory = RecipeDetailViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spanish = rememberIsSpanish()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    val recipe = state.recipe
    if (recipe == null) {
        // A just-deleted recipe also reads as null, and that is a pop rather
        // than an error, so it must not flash "no longer here" on the way out.
        if (!state.isLoading && !state.deleted) MissingRecipe(onBack)
        return
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.recipe_delete_title)) },
            text = {
                Text(stringResource(R.string.recipe_delete_body, recipe.displayName(spanish)))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.onDeleteConfirmed()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (state.deleteBlocked) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteBlocked,
            title = { Text(stringResource(R.string.recipe_delete_blocked_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.recipe_delete_blocked_body,
                        recipe.displayName(spanish)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDeleteBlocked) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }

    if (state.operationFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOperationError,
            title = { Text(stringResource(R.string.error_operation_title)) },
            text = { Text(stringResource(R.string.error_operation_failed)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissOperationError) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    SlowMarqueeText(
                        text = recipe.displayName(spanish),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onFavoriteToggled) {
                        Icon(
                            if (recipe.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = stringResource(
                                if (recipe.isFavorite) {
                                    R.string.recipes_favorite_remove
                                } else {
                                    R.string.recipes_favorite_add
                                }
                            ),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.action_delete)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("header") { Header(state) }

            item("totals") {
                LabelledCard(stringResource(R.string.recipe_totals)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MacroDonutChart(nutrition = state.totals)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(
                            R.string.recipe_total_weight,
                            formatGrams(state.totalGrams)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item("summary") { NutritionSummary(nutrition = state.totals) }

            item("ingredients") {
                LabelledCard(
                    pluralStringResource(
                        R.plurals.recipe_ingredients_count,
                        state.ingredients.size,
                        state.ingredients.size
                    )
                ) {
                    if (state.ingredients.isEmpty()) {
                        Text(
                            stringResource(R.string.recipe_no_ingredients),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.ingredients.forEachIndexed { index, item ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.ingredient.displayName(spanish),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        stringResource(
                                            R.string.kcal_value,
                                            item.nutrition.kcal.roundToInt()
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    formatRecipeQuantity(
                                        item.ingredient,
                                        item.grams,
                                        item.quantityUnit
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            item("steps_heading") { SectionTitle(stringResource(R.string.recipe_steps)) }

            if (state.steps.isEmpty()) {
                item("no_steps") {
                    Text(
                        stringResource(R.string.recipe_no_steps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                state.steps.forEachIndexed { index, step ->
                    item("step_${step.id}") {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.recipe_step, index + 1),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                step.imagePath?.let { path ->
                                    Spacer(Modifier.height(8.dp))
                                    AsyncImage(
                                        model = File(path),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    step.displayText(spanish),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: RecipeDetailUiState) {
    val recipe = state.recipe ?: return
    val spanish = rememberIsSpanish()
    val tint = recipeCategoryColor(recipe.category)

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(tint.copy(alpha = 0.16f), MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center
        ) {
            if (recipe.coverImagePath != null) {
                AsyncImage(
                    model = File(recipe.coverImagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = recipe.coverImageCrop.toRecipeImageAlignment(),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    recipeCategoryIcon(recipe.category),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(
                label = stringResource(recipeCategoryLabel(recipe.category)),
                color = tint,
                icon = recipeCategoryIcon(recipe.category)
            )
            recipe.folder?.let { folder ->
                StatusChip(
                    label = RecipeBooks.displayName(folder, spanish),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = Icons.Rounded.Folder
                )
            }
        }
    }
}

@Composable
private fun LabelledCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun MissingRecipe(onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.recipe_not_found),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}
