package com.zame.nutrinest.ui.recipes.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.ui.components.ImagePickerBox
import com.zame.nutrinest.ui.components.MacroDonutChart
import com.zame.nutrinest.ui.components.NutritionSummary
import com.zame.nutrinest.ui.components.OptionDropdown
import com.zame.nutrinest.ui.components.formatGrams
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRecipeScreen(
    onBack: () -> Unit,
    onCreateIngredient: () -> Unit,
    viewModel: CreateRecipeViewModel = viewModel(factory = CreateRecipeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    val allIngredients by viewModel.allIngredients.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showIngredientPicker by remember { mutableStateOf(false) }

    // Navigate back once saved.
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    // Surface validation errors as snackbars.
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
                title = { Text("New Recipe", style = MaterialTheme.typography.headlineSmall) },
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
                onClick = viewModel::save,
                icon = { Icon(Icons.Rounded.Check, null) },
                text = { Text(if (state.isSaving) "Saving…" else "Save") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Cover image + basics ────────────────────────────────
            item {
                ImagePickerBox(
                    imagePath = state.coverImagePath,
                    onImagePicked = viewModel::onCoverImagePicked,
                    placeholderText = "Add cover photo"
                )
            }
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Recipe name *") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OptionDropdown(
                        label = "Category *",
                        options = RecipeCategories.ALL,
                        selected = state.category,
                        onSelected = viewModel::onCategoryChange,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.folder,
                        onValueChange = viewModel::onFolderChange,
                        label = { Text("Folder") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Ingredients ────────────────────────────────────────
            item { SectionTitle("Ingredients") }
            items(state.ingredients, key = { it.ingredient.id }) { draft ->
                DraftIngredientRow(
                    draft = draft,
                    onGramsChange = { viewModel.updateIngredientGrams(draft.ingredient.id, it) },
                    onRemove = { viewModel.removeIngredient(draft.ingredient.id) }
                )
            }
            item {
                OutlinedButton(
                    onClick = { showIngredientPicker = true },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add ingredient")
                }
            }

            // ── Live nutrition ─────────────────────────────────────
            if (state.ingredients.isNotEmpty()) {
                item { SectionTitle("Nutrition") }
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MacroDonutChart(nutrition = state.totalNutrition)
                    }
                }
                item { NutritionSummary(nutrition = state.totalNutrition) }
            }

            // ── Preparation steps ──────────────────────────────────
            item { SectionTitle("Preparation") }
            items(state.steps.size) { index ->
                StepEditor(
                    index = index,
                    step = state.steps[index],
                    onTextChange = { viewModel.onStepTextChange(index, it) },
                    onImagePicked = { viewModel.onStepImagePicked(index, it) },
                    onRemove = { viewModel.removeStep(index) }
                )
            }
            item {
                OutlinedButton(
                    onClick = viewModel::addStep,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add step")
                }
            }
        }
    }

    if (showIngredientPicker) {
        IngredientPickerSheet(
            ingredients = allIngredients,
            onDismiss = { showIngredientPicker = false },
            onPicked = { ingredient, grams ->
                viewModel.addIngredient(ingredient, grams)
                showIngredientPicker = false
            },
            onCreateIngredient = {
                showIngredientPicker = false
                onCreateIngredient()
            }
        )
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

@Composable
private fun DraftIngredientRow(
    draft: DraftIngredient,
    onGramsChange: (Double) -> Unit,
    onRemove: () -> Unit
) {
    var gramsText by remember(draft.ingredient.id) {
        mutableStateOf(
            if (draft.grams == draft.grams.roundToInt().toDouble())
                draft.grams.roundToInt().toString()
            else draft.grams.toString()
        )
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    draft.ingredient.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${draft.ingredient.nutrition.forGrams(draft.grams).kcal.roundToInt()} kcal · " +
                        formatGrams(draft.grams),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = gramsText,
                onValueChange = { text ->
                    gramsText = text
                    text.toDoubleOrNull()?.let { if (it > 0) onGramsChange(it) }
                },
                label = { Text("g") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.width(90.dp)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Remove ${draft.ingredient.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepEditor(
    index: Int,
    step: DraftStep,
    onTextChange: (String) -> Unit,
    onImagePicked: (android.net.Uri) -> Unit,
    onRemove: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onImagePicked) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Step ${index + 1}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Icon(
                        Icons.Rounded.AddAPhoto,
                        contentDescription = "Add photo to step ${index + 1}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Remove step ${index + 1}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (step.imagePath != null) {
                AsyncImage(
                    model = File(step.imagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }
            OutlinedTextField(
                value = step.text,
                onValueChange = onTextChange,
                placeholder = { Text("Describe this step…") },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientPickerSheet(
    ingredients: List<IngredientEntity>,
    onDismiss: () -> Unit,
    onPicked: (IngredientEntity, Double) -> Unit,
    onCreateIngredient: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<IngredientEntity?>(null) }
    var gramsText by remember { mutableStateOf("100") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            val selected = pending
            if (selected == null) {
                // ── Search & pick ──────────────────────────────────
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Search ingredients") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onCreateIngredient) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create new ingredient")
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val filtered = ingredients.filter {
                        it.name.contains(query.trim(), ignoreCase = true)
                    }
                    items(filtered, key = { it.id }) { ingredient ->
                        Card(
                            onClick = { pending = ingredient; gramsText = "100" },
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        ingredient.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${ingredient.foodType} · " +
                                            "${ingredient.nutrition.kcal.roundToInt()} kcal / 100 g",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (ingredient.isCustom) {
                                    Text(
                                        "Custom",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ── Quantity entry ─────────────────────────────────
                Text(
                    selected.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { gramsText = it },
                    label = { Text("Quantity (grams)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { pending = null },
                        modifier = Modifier.weight(1f)
                    ) { Text("Back") }
                    androidx.compose.material3.Button(
                        onClick = {
                            gramsText.toDoubleOrNull()?.takeIf { it > 0 }?.let { grams ->
                                onPicked(selected, grams)
                            }
                        },
                        enabled = (gramsText.toDoubleOrNull() ?: 0.0) > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("Add") }
                }
            }
        }
    }
}
