package com.zame.nutrinest.ui.menu.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zame.nutrinest.data.local.entity.FoodTypes
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.ui.components.OptionDropdown
import com.zame.nutrinest.util.WeekUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MenuConfigScreen(
    onBack: () -> Unit,
    onGenerated: () -> Unit,
    viewModel: MenuConfigViewModel = viewModel(factory = MenuConfigViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()
    val recipes by viewModel.allRecipes.collectAsState()
    val folders by viewModel.allFolders.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLockDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onGenerated() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text("Plan the Week", style = MaterialTheme.typography.headlineSmall)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!state.isGenerating) viewModel.generate() },
                icon = { Icon(Icons.Rounded.AutoAwesome, null) },
                text = { Text(if (state.isGenerating) "Generating…" else "Generate") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Meals per day ───────────────────────────────────────
            item { SectionTitle("Meals per day") }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(3, 4, 5).forEachIndexed { index, count ->
                        SegmentedButton(
                            selected = state.mealsPerDay == count,
                            onClick = { viewModel.onMealsPerDayChange(count) },
                            shape = SegmentedButtonDefaults.itemShape(index, 3)
                        ) { Text("$count") }
                    }
                }
            }

            // ── Meal importance sliders ─────────────────────────────
            item { SectionTitle("Meal size distribution") }
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        val total = state.slotWeights.values.sum()
                        state.slotWeights.entries
                            .sortedBy { it.key.ordinal }
                            .forEach { (slot, weight) ->
                                val percent = (weight / total * 100).roundToInt()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        slot.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.width(130.dp)
                                    )
                                    Slider(
                                        value = weight,
                                        onValueChange = {
                                            viewModel.onSlotWeightChange(slot, it)
                                        },
                                        valueRange = 0.05f..1f,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "$percent%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(44.dp)
                                    )
                                }
                            }
                        Text(
                            "Shares are relative — they always add up to 100%.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Counters ────────────────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stepper(
                        label = "Cheat meals",
                        value = state.cheatMeals,
                        onChange = viewModel::onCheatMealsChange,
                        modifier = Modifier.weight(1f)
                    )
                    Stepper(
                        label = "Max recipe repeats",
                        value = state.maxRepeats,
                        onChange = viewModel::onMaxRepeatsChange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Avoid last week's recipes", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Skipped automatically if it would leave too few recipes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.excludeRecentlyUsed,
                        onCheckedChange = { viewModel.onExcludeRecentToggle() }
                    )
                }
            }

            // ── Exclusions ──────────────────────────────────────────
            item { SectionTitle("Exclude categories") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecipeCategories.ALL.forEach { category ->
                        FilterChip(
                            selected = category in state.excludedCategories,
                            onClick = { viewModel.toggleCategory(category) },
                            label = { Text(category) }
                        )
                    }
                }
            }
            item { SectionTitle("Exclude food types") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodTypes.ALL.forEach { type ->
                        FilterChip(
                            selected = type in state.excludedFoodTypes,
                            onClick = { viewModel.toggleFoodType(type) },
                            label = { Text(type) }
                        )
                    }
                }
            }
            if (folders.isNotEmpty()) {
                item { SectionTitle("Use only these folders") }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        folders.forEach { folder ->
                            FilterChip(
                                selected = folder in state.includedFolders,
                                onClick = { viewModel.toggleFolder(folder) },
                                label = { Text(folder) }
                            )
                        }
                    }
                }
            }
            if (recipes.isNotEmpty()) {
                item { SectionTitle("Exclude specific recipes") }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recipes.forEach { recipe ->
                            FilterChip(
                                selected = recipe.id in state.excludedRecipeIds,
                                onClick = { viewModel.toggleRecipeExclusion(recipe.id) },
                                label = { Text(recipe.name) }
                            )
                        }
                    }
                }
            }

            // ── Locks ───────────────────────────────────────────────
            item { SectionTitle("Locked meals") }
            item {
                Text(
                    "Fix a recipe to specific days and meals — the generator plans around them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.locks.size) { index ->
                val lock = state.locks[index]
                val recipeName = recipes.find { it.id == lock.recipeId }?.name ?: "?"
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                recipeName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${lock.slot.label} · " + lock.days.sorted()
                                    .joinToString(", ") { WeekUtils.DAY_NAMES[it].take(3) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.removeLock(index) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Remove lock",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showLockDialog = true },
                    enabled = recipes.isNotEmpty(),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add locked meal")
                }
            }
        }
    }

    if (showLockDialog) {
        AddLockDialog(
            slots = state.slotWeights.keys.sortedBy { it.ordinal },
            recipes = recipes,
            onDismiss = { showLockDialog = false },
            onConfirm = { days, slot, recipe ->
                viewModel.addLock(days, slot, recipe)
                showLockDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddLockDialog(
    slots: List<MealSlot>,
    recipes: List<RecipeEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Int>, MealSlot, RecipeEntity) -> Unit
) {
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var selectedSlot by remember { mutableStateOf(slots.first()) }
    var selectedRecipe by remember { mutableStateOf<RecipeEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock a meal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Days", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WeekUtils.DAY_NAMES.forEachIndexed { index, name ->
                        FilterChip(
                            selected = index in selectedDays,
                            onClick = {
                                selectedDays =
                                    if (index in selectedDays) selectedDays - index
                                    else selectedDays + index
                            },
                            label = { Text(name.take(3)) }
                        )
                    }
                }
                OptionDropdown(
                    label = "Meal",
                    options = slots.map { it.label },
                    selected = selectedSlot.label,
                    onSelected = { label ->
                        slots.find { it.label == label }?.let { selectedSlot = it }
                    }
                )
                OptionDropdown(
                    label = "Recipe",
                    options = recipes.map { it.name },
                    selected = selectedRecipe?.name,
                    onSelected = { name ->
                        selectedRecipe = recipes.find { it.name == name }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedDays.isNotEmpty() && selectedRecipe != null,
                onClick = {
                    selectedRecipe?.let { onConfirm(selectedDays, selectedSlot, it) }
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onChange(value - 1) }) { Text("−") }
                Text(
                    "$value",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onChange(value + 1) }) { Text("+") }
            }
        }
    }
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
