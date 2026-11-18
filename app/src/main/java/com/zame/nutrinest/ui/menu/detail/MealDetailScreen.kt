package com.zame.nutrinest.ui.menu.detail

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.MealCourse
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.ui.components.KeyedDropdown
import com.zame.nutrinest.ui.components.MacroDonutChart
import com.zame.nutrinest.ui.components.NutritionSummary
import com.zame.nutrinest.ui.components.OptionDropdown
import com.zame.nutrinest.ui.components.SlowMarqueeText
import com.zame.nutrinest.ui.menu.formatMealIngredientQuantity
import com.zame.nutrinest.ui.ingredients.rememberIsSpanish
import com.zame.nutrinest.ui.recipes.toRecipeImageAlignment
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    onBack: () -> Unit,
    viewModel: MealDetailViewModel = viewModel(factory = MealDetailViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spanish = rememberIsSpanish()
    val recipes by viewModel.allRecipes.collectAsStateWithLifecycle()
    val meal = state.meal
    var showSwapDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    SlowMarqueeText(
                        text = meal?.recipe?.displayName(spanish)
                            ?: stringResource(
                                if (state.isFree) R.string.meal_free_placeholder
                                else R.string.meal_detail_title
                            ),
                        style = MaterialTheme.typography.headlineSmall,
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
                    if (meal != null) {
                        IconButton(onClick = { showSwapDialog = true }) {
                            Icon(
                                Icons.Rounded.SwapHoriz,
                                contentDescription = stringResource(R.string.meal_swap)
                            )
                        }
                        if (!state.isFree) {
                            IconButton(onClick = viewModel::toggleLock) {
                                Icon(
                                    if (meal.meal.isLocked) Icons.Rounded.Lock
                                    else Icons.Rounded.LockOpen,
                                    contentDescription = stringResource(R.string.meal_lock_toggle),
                                    tint = if (meal.meal.isLocked)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (meal == null) return@Scaffold

        if (state.isFree) {
            FreeMealBody(state, Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    buildList {
                        add(stringResource(meal.meal.slot.labelRes))
                        if (meal.meal.course != MealCourse.SINGLE) {
                            add(stringResource(meal.meal.course.labelRes))
                        }
                        add(
                            state.ownerProfileId
                                ?.let { id ->
                                    stringResource(
                                        R.string.meal_only_for,
                                        state.profiles.find { it.id == id }?.name.orEmpty()
                                    )
                                }
                                ?: stringResource(R.string.meal_whole_household)
                        )
                        if (meal.meal.isCheatMeal) add(stringResource(R.string.meal_cheat))
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            meal.recipe?.coverImagePath?.let { path ->
                item {
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = meal.recipe.coverImageCrop.toRecipeImageAlignment(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }

            // ── Household nutrition ─────────────────────────────────
            item { SectionTitle(stringResource(R.string.meal_nutrition_household)) }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MacroDonutChart(nutrition = state.householdNutrition)
                }
            }
            item { NutritionSummary(nutrition = state.householdNutrition) }

            // ── Per person ──────────────────────────────────────────
            item { SectionTitle(stringResource(R.string.meal_per_person)) }
            items(state.portions.size) { i ->
                val portion = state.portions[i]
                val nutrition = state.nutritionFor(portion)
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                portion.profile.name,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${nutrition.kcal.roundToInt()} kcal",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            stringResource(R.string.meal_portion_summary).format(
                                portion.portion.factor,
                                nutrition.protein.roundToInt(),
                                nutrition.carbs.roundToInt(),
                                nutrition.fats.roundToInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Says why the grams below don't match the plan.
                        if (state.isAdjusted(portion)) {
                            Text(
                                stringResource(
                                    R.string.meal_portion_adjusted,
                                    portion.portion.baseFactor
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        state.baseIngredients.forEach { ingredient ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    state.ingredientCatalog.nameOf(ingredient.ingredient, spanish),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    formatMealIngredientQuantity(
                                        ingredient,
                                        options = state.ingredientCatalog.measures[ingredient.ingredient.id].orEmpty(),
                                        spanish = spanish,
                                        factor = portion.portion.factor
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // ── Household ingredient totals ─────────────────────────
            item { SectionTitle(stringResource(R.string.meal_ingredients_total)) }
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.baseIngredients.forEach { ingredient ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    state.ingredientCatalog.nameOf(ingredient.ingredient, spanish),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    formatMealIngredientQuantity(
                                        ingredient,
                                        options = state.ingredientCatalog.measures[ingredient.ingredient.id].orEmpty(),
                                        spanish = spanish,
                                        factor = state.householdFactor
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // ── Preparation ─────────────────────────────────────────
            item { SectionTitle(stringResource(R.string.meal_preparation)) }
            items(state.steps.size) { i ->
                val step = state.steps[i]
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.meal_step, i + 1),
                            style = MaterialTheme.typography.titleLarge,
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
                                    .height(140.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            step.displayText(spanish),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }

    if (showSwapDialog && meal != null) {
        SwapMealDialog(
            state = state,
            recipes = recipes,
            onDismiss = { showSwapDialog = false },
            onConfirm = { recipeId, profileId ->
                viewModel.replaceMeal(recipeId, profileId)
                showSwapDialog = false
            }
        )
    }
}

/**
 * A free slot. There is deliberately nothing to show: no recipe, no
 * ingredients, no calories counted — the budget for this slot stays with the
 * person to spend as they like, and the rest of their day is unaffected.
 */
@Composable
private fun FreeMealBody(state: MealDetailUiState, modifier: Modifier = Modifier) {
    val meal = state.meal ?: return
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            buildList {
                add(stringResource(meal.meal.slot.labelRes))
                add(
                    state.ownerProfileId
                        ?.let { id ->
                            stringResource(
                                R.string.meal_only_for,
                                state.profiles.find { it.id == id }?.name.orEmpty()
                            )
                        }
                        ?: stringResource(R.string.meal_whole_household)
                )
            }.joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.meal_free_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.meal_free_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Swap this dish for another. With more than one person eating it, the choice
 * of who the swap is for is the point — one person's change must not move
 * anybody else's plate.
 */
@Composable
private fun SwapMealDialog(
    state: MealDetailUiState,
    recipes: List<RecipeEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long?) -> Unit
) {
    val spanish = rememberIsSpanish()
    var selectedRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    // Pre-selected to the row's own scope, which is the expected default.
    var swapFor by remember { mutableStateOf(state.ownerProfileId) }
    val canChooseScope = state.ownerProfileId == null && state.profiles.size > 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meal_swap_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionDropdown(
                    label = stringResource(R.string.menu_config_recipe),
                    options = recipes.map { it.displayName(spanish) },
                    selected = selectedRecipe?.displayName(spanish),
                    onSelected = { name ->
                        selectedRecipe = recipes.find { it.displayName(spanish) == name }
                    }
                )
                if (canChooseScope) {
                    KeyedDropdown(
                        label = stringResource(R.string.menu_config_who),
                        options = listOf(
                            null to stringResource(R.string.menu_scope_everyone)
                        ) + state.profiles.map { it.id to it.name },
                        selectedKey = swapFor,
                        onSelected = { swapFor = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedRecipe != null,
                onClick = { selectedRecipe?.let { onConfirm(it.id, swapFor) } }
            ) { Text(stringResource(R.string.meal_swap_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp)
    )
}
