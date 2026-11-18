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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.RecipeImageCrop
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.domain.nutrition.RecipeMeasure
import com.zame.nutrinest.domain.nutrition.RecipeQuantity
import com.zame.nutrinest.ui.components.ImagePickerBox
import com.zame.nutrinest.ui.components.KeyedDropdown
import com.zame.nutrinest.ui.components.MacroDonutChart
import com.zame.nutrinest.ui.components.NutritionSummary
import com.zame.nutrinest.ui.components.formatSavedRecipeQuantity
import com.zame.nutrinest.ui.ingredients.prepStateLabel
import com.zame.nutrinest.ui.ingredients.rememberIsSpanish
import com.zame.nutrinest.ui.recipes.EditRecipeQuantityDialog
import com.zame.nutrinest.ui.recipes.RecipeQuantityEditor
import com.zame.nutrinest.ui.recipes.RecipeQuantityFields
import com.zame.nutrinest.ui.recipes.label
import com.zame.nutrinest.ui.recipes.recipeCategoryLabel
import com.zame.nutrinest.ui.recipes.toRecipeImageAlignment
import com.zame.nutrinest.util.TextNormalizer
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRecipeScreen(
    onBack: () -> Unit,
    onCreateIngredient: () -> Unit,
    viewModel: CreateRecipeViewModel = viewModel(factory = CreateRecipeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val catalog by viewModel.ingredientCatalog.collectAsStateWithLifecycle()
    val (allIngredients, measures) = catalog
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val spanish = rememberIsSpanish()
    val snackbarHostState = remember { SnackbarHostState() }
    var showIngredientPicker by remember { mutableStateOf(false) }
    var editingIngredient by remember { mutableStateOf<DraftIngredient?>(null) }

    // Navigate back once saved.
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    // Surface validation errors as snackbars.
    val errorMessage = state.error?.let { stringResource(it.messageRes) }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
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
                    Text(
                        stringResource(R.string.create_recipe_title),
                        style = MaterialTheme.typography.headlineSmall
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::save,
                icon = { Icon(Icons.Rounded.Check, null) },
                text = {
                    Text(
                        stringResource(
                            if (state.isSaving) R.string.create_recipe_saving
                            else R.string.action_save
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Cover image + basics ────────────────────────────────
            item {
                ImagePickerBox(
                    imagePath = state.coverImagePath,
                    onImagePicked = viewModel::onCoverImagePicked,
                    placeholderText = stringResource(R.string.create_recipe_cover_photo),
                    imageAlignment = state.coverImageCrop.toRecipeImageAlignment()
                )
                if (state.coverImagePath != null) {
                    Spacer(Modifier.height(8.dp))
                    CoverImageCropPicker(
                        selected = state.coverImageCrop,
                        onSelected = viewModel::onCoverImageCropChange
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.create_recipe_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Keyed, because the stored category is the English wording
                    // while the option text is translated.
                    KeyedDropdown(
                        label = stringResource(R.string.create_recipe_category),
                        options = RecipeCategories.ALL.map { key ->
                            key to stringResource(recipeCategoryLabel(key))
                        },
                        selectedKey = state.category,
                        onSelected = viewModel::onCategoryChange,
                        modifier = Modifier.weight(1f)
                    )
                    FolderPicker(
                        selectedFolder = state.folder,
                        folders = folders,
                        onFolderSelected = viewModel::onFolderChange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Ingredients ────────────────────────────────────────
            item { SectionTitle(stringResource(R.string.ingredients_title)) }
            items(state.ingredients, key = { it.ingredient.id }) { draft ->
                DraftIngredientRow(
                    draft = draft,
                    spanish = spanish,
                    options = measures[draft.ingredient.id].orEmpty(),
                    onEdit = { editingIngredient = draft },
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
                    Text(stringResource(R.string.create_recipe_add_ingredient))
                }
            }

            // ── Live nutrition ─────────────────────────────────────
            if (state.ingredients.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.create_recipe_nutrition)) }
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MacroDonutChart(nutrition = state.totalNutrition)
                    }
                }
                item { NutritionSummary(nutrition = state.totalNutrition) }
            }

            // ── Preparation steps ──────────────────────────────────
            item { SectionTitle(stringResource(R.string.meal_preparation)) }
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
                    Text(stringResource(R.string.create_recipe_add_step))
                }
            }
        }
    }

    if (showIngredientPicker) {
        IngredientPickerSheet(
            ingredients = allIngredients,
            measures = measures,
            onDismiss = { showIngredientPicker = false },
            onPicked = { ingredient, quantity ->
                viewModel.addIngredient(ingredient, quantity)
                showIngredientPicker = false
            },
            onCreateIngredient = {
                showIngredientPicker = false
                onCreateIngredient()
            }
        )
    }

    editingIngredient?.let { draft ->
        EditRecipeQuantityDialog(
            ingredientName = draft.ingredient.displayName(spanish),
            saved = draft.quantity,
            options = measures[draft.ingredient.id] ?: listOf(RecipeMeasure.GRAMS),
            spanish = spanish,
            onDismiss = { editingIngredient = null },
            onSave = { quantity ->
                viewModel.updateIngredientQuantity(draft.ingredient.id, quantity)
                editingIngredient = null
            }
        )
    }
}

private const val NEW_FOLDER_OPTION = "__new_folder__"

@Composable
private fun FolderPicker(
    selectedFolder: String,
    folders: List<String>,
    onFolderSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spanish = rememberIsSpanish()
    var enteringNewFolder by remember {
        mutableStateOf(selectedFolder.isNotBlank() && selectedFolder !in folders)
    }
    val options = buildList {
        add("" to stringResource(R.string.create_recipe_no_folder))
        folders.distinct().forEach { add(it to RecipeBooks.displayName(it, spanish)) }
        add(NEW_FOLDER_OPTION to stringResource(R.string.create_recipe_new_folder))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyedDropdown(
            label = stringResource(R.string.create_recipe_folder),
            options = options,
            selectedKey = when {
                enteringNewFolder -> NEW_FOLDER_OPTION
                selectedFolder.isBlank() -> ""
                else -> selectedFolder
            },
            onSelected = { folder ->
                if (folder == NEW_FOLDER_OPTION) {
                    enteringNewFolder = true
                    onFolderSelected("")
                } else {
                    enteringNewFolder = false
                    onFolderSelected(folder)
                }
            }
        )
        if (enteringNewFolder) {
            OutlinedTextField(
                value = selectedFolder,
                onValueChange = onFolderSelected,
                label = { Text(stringResource(R.string.create_recipe_new_folder_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
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

@Composable
private fun DraftIngredientRow(
    draft: DraftIngredient,
    spanish: Boolean,
    options: List<RecipeMeasure>,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
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
                    draft.ingredient.displayName(spanish),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                val kcal = draft.ingredient.nutrition.forGrams(draft.quantity.grams).kcal.roundToInt()
                Text(
                    stringResource(R.string.kcal_value, kcal) + " · " +
                        formatSavedRecipeQuantity(draft.quantity, options, spanish),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, stringResource(R.string.recipe_edit_quantity))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.action_remove))
            }
        }
    }
}

@Composable
private fun CoverImageCropPicker(selected: String, onSelected: (String) -> Unit) {
    Text(
        stringResource(R.string.create_recipe_image_framing),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val anchors = listOf(
        RecipeImageCrop.TOP_START to "↖",
        RecipeImageCrop.TOP_CENTER to "↑",
        RecipeImageCrop.TOP_END to "↗",
        RecipeImageCrop.CENTER_START to "←",
        RecipeImageCrop.CENTER to "•",
        RecipeImageCrop.CENTER_END to "→",
        RecipeImageCrop.BOTTOM_START to "↙",
        RecipeImageCrop.BOTTOM_CENTER to "↓",
        RecipeImageCrop.BOTTOM_END to "↘"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        anchors.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (anchor, glyph) ->
                    FilterChip(
                        selected = anchor == selected,
                        onClick = { onSelected(anchor) },
                        label = { Text(glyph) }
                    )
                }
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
                    stringResource(R.string.meal_step, index + 1),
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
                        contentDescription = stringResource(R.string.create_recipe_step_photo, index + 1),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.create_recipe_remove_step, index + 1),
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
                placeholder = { Text(stringResource(R.string.create_recipe_step_hint)) },
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
    measures: Map<Long, List<RecipeMeasure>>,
    onDismiss: () -> Unit,
    onPicked: (IngredientEntity, RecipeQuantity) -> Unit,
    onCreateIngredient: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<IngredientEntity?>(null) }
    val spanish = rememberIsSpanish()
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var customOnly by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            val selected = pending
            if (selected == null) {
                // ── Search & pick ──────────────────────────────────
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text(stringResource(R.string.ingredients_search_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onCreateIngredient) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.create_recipe_new_ingredient))
                }
                LazyRow(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedCategories.isNotEmpty() || favoritesOnly || customOnly) {
                        item {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    selectedCategories = emptySet()
                                    favoritesOnly = false
                                    customOnly = false
                                },
                                label = { Text(stringResource(R.string.ingredients_filter_clear)) },
                                shape = MaterialTheme.shapes.small
                            )
                        }
                    }
                    item {
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = { favoritesOnly = !favoritesOnly },
                            label = { Text(stringResource(R.string.ingredients_view_favorites)) },
                            shape = MaterialTheme.shapes.small,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = customOnly,
                            onClick = { customOnly = !customOnly },
                            label = { Text(stringResource(R.string.ingredients_custom_only)) },
                            shape = MaterialTheme.shapes.small,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    items(IngredientCategories.ALL) { category ->
                        val selectedCategory = category in selectedCategories
                        FilterChip(
                            selected = selectedCategory,
                            onClick = {
                                selectedCategories = if (selectedCategory) {
                                    selectedCategories - category
                                } else {
                                    selectedCategories + category
                                }
                            },
                            label = { Text(IngredientCategories.shortName(category, spanish)) },
                            shape = MaterialTheme.shapes.small,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
                val needle = TextNormalizer.normalize(query)
                val filtered = ingredients.filter { ingredient ->
                    (selectedCategories.isEmpty() || ingredient.categoryKey in selectedCategories) &&
                        (!favoritesOnly || ingredient.isFavorite) &&
                        (!customOnly || ingredient.isCustom) &&
                        (needle.isEmpty() ||
                            ingredient.nameNorm.contains(needle) ||
                            ingredient.nameEsNorm?.contains(needle) == true ||
                            TextNormalizer.normalize(ingredient.displayName(true)).contains(needle) ||
                            TextNormalizer.normalize(ingredient.displayName(false)).contains(needle))
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.ingredients_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { ingredient ->
                        Card(
                            onClick = { pending = ingredient },
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
                                        ingredient.displayName(spanish),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (ingredient.catalogRank in 1..2) {
                                        Text(
                                            stringResource(if (ingredient.catalogRank == 1) R.string.catalog_secondary else R.string.catalog_review_required),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    val preferred = RecipeMeasure.default(measures[ingredient.id].orEmpty())
                                    val subtitle = listOf(
                                        IngredientCategories.shortName(ingredient.categoryKey, spanish),
                                        stringResource(prepStateLabel(ingredient.prepState)).takeUnless {
                                            ingredient.prepState == "UNSPECIFIED"
                                        },
                                        preferred.label(spanish),
                                        stringResource(R.string.kcal_per_100g, ingredient.nutrition.kcal.roundToInt())
                                    ).filterNotNull().joinToString(" · ")
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (ingredient.isCustom) {
                                    Text(
                                        stringResource(R.string.ingredients_custom_badge),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val options = measures[selected.id] ?: listOf(RecipeMeasure.GRAMS)
                val editor = remember(selected.id) { RecipeQuantityEditor(options) }
                Text(
                    selected.displayName(spanish),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                RecipeQuantityFields(editor, options, spanish)
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
                    ) { Text(stringResource(R.string.action_back)) }
                    androidx.compose.material3.Button(
                        onClick = { editor.quantity?.let { onPicked(selected, it) } },
                        enabled = editor.quantity != null,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_add)) }
                }
            }
        }
    }
}
