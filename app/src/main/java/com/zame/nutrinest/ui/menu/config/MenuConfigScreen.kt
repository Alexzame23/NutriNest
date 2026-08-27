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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.MenuScope
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.ui.components.KeyedDropdown
import com.zame.nutrinest.ui.components.OptionDropdown
import com.zame.nutrinest.ui.components.SlowMarqueeText
import com.zame.nutrinest.ui.ingredients.rememberIsSpanish
import com.zame.nutrinest.ui.recipes.recipeCategoryLabel
import com.zame.nutrinest.util.TextNormalizer
import com.zame.nutrinest.util.WeekUtils
import kotlin.math.ceil
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MenuConfigScreen(
    onBack: () -> Unit,
    onGenerated: () -> Unit,
    viewModel: MenuConfigViewModel = viewModel(factory = MenuConfigViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()
    val configuration = LocalConfiguration.current
    val shortDays = remember(configuration) { WeekUtils.shortDayNames(configuration.locales[0]) }
    val recipes by viewModel.allRecipes.collectAsState()
    val folders by viewModel.allFolders.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val spanish = rememberIsSpanish()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLockDialog by remember { mutableStateOf(false) }
    var showOverrideDialog by remember { mutableStateOf(false) }
    val bookFolders = remember(recipes, folders) {
        buildList<String?> {
            RecipeBooks.BUILT_IN.filterTo(this) { it in folders }
            folders.filterTo(this) { it !in RecipeBooks.BUILT_IN }
            if (recipes.any { it.folder == null }) add(null)
        }
    }
    val availableBookFolders = bookFolders.toSet()
    val selectedBookFolders = state.includedFolders ?: availableBookFolders
    val enabledRecipes = recipes.filter {
        it.folder in selectedBookFolders && it.id !in state.excludedRecipeIds
    }

    LaunchedEffect(state.done) { if (state.done) onGenerated() }
    // The generator reports a code; the wording belongs to this layer.
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
                        stringResource(R.string.menu_config_title),
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
                onClick = {
                    if (!state.isGenerating && enabledRecipes.isNotEmpty()) viewModel.generate()
                },
                icon = { Icon(Icons.Rounded.AutoAwesome, null) },
                text = {
                    Text(
                        stringResource(
                            if (state.isGenerating) R.string.menu_config_generating
                            else R.string.menu_config_generate
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
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Meals per day ───────────────────────────────────────
            item { SectionTitle(stringResource(R.string.menu_config_meals_per_day)) }
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

            // ── Who eats what ───────────────────────────────────────
            if (profiles.size > 1) {
                item { SectionTitle(stringResource(R.string.menu_config_scope)) }
                items(MenuScope.entries.size) { index ->
                    val scope = MenuScope.entries[index]
                    ScopeCard(
                        scope = scope,
                        selected = state.scope == scope,
                        onClick = { viewModel.onScopeChange(scope) }
                    )
                }
            }

            // ── Meal importance sliders ─────────────────────────────
            item { SectionTitle(stringResource(R.string.menu_config_meal_sizes)) }
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
                                        stringResource(slot.labelRes),
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
                            stringResource(R.string.menu_config_shares_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Courses within a meal ───────────────────────────────
            if (state.courseCapableSlots.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.menu_config_courses)) }
                item {
                    Text(
                        stringResource(R.string.menu_config_courses_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(state.courseCapableSlots.size) { index ->
                    val slot = state.courseCapableSlots[index]
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                stringResource(slot.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            ToggleRow(
                                label = stringResource(R.string.menu_config_two_courses),
                                checked = slot in state.twoCourseSlots,
                                onToggle = { viewModel.toggleTwoCourses(slot) }
                            )
                            ToggleRow(
                                label = stringResource(R.string.menu_config_dessert),
                                checked = slot in state.dessertSlots,
                                onToggle = { viewModel.toggleDessert(slot) }
                            )
                        }
                    }
                }
            }

            // ── Per-meal exceptions ─────────────────────────────────
            if (state.scope == MenuScope.PER_MEAL && profiles.size > 1) {
                item { SectionTitle(stringResource(R.string.menu_config_exceptions)) }
                item {
                    Text(
                        stringResource(R.string.menu_config_exceptions_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val overrides = state.sortedOverrides
                items(overrides.size) { index ->
                    val override = overrides[index]
                    val who = profiles.find { it.id == override.profileId }?.name ?: "?"
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
                                    who,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(override.slot.labelRes) + " · " +
                                        shortDays[override.day],
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.removeOverride(override) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription =
                                        stringResource(R.string.menu_config_remove_exception),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { showOverrideDialog = true },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.menu_config_add_exception))
                    }
                }
            }

            // ── Counters ────────────────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stepper(
                        label = stringResource(R.string.menu_config_cheat_meals),
                        value = state.cheatMeals,
                        onChange = viewModel::onCheatMealsChange,
                        modifier = Modifier.weight(1f)
                    )
                    Stepper(
                        label = stringResource(R.string.menu_config_max_repeats),
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
                        Text(
                            stringResource(R.string.menu_config_avoid_recent),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(R.string.menu_config_avoid_recent_note),
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
            item { SectionTitle(stringResource(R.string.menu_config_exclude_categories)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecipeCategories.ALL.forEach { category ->
                        FilterChip(
                            selected = category in state.excludedCategories,
                            onClick = { viewModel.toggleCategory(category) },
                            label = { Text(stringResource(recipeCategoryLabel(category))) }
                        )
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.menu_config_exclude_ingredients)) }
            item {
                val spanish = rememberIsSpanish()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IngredientCategories.ALL.forEach { key ->
                        FilterChip(
                            selected = key in state.excludedIngredientCategories,
                            onClick = { viewModel.toggleIngredientCategory(key) },
                            label = { Text(IngredientCategories.shortName(key, spanish)) }
                        )
                    }
                }
            }
            if (recipes.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.menu_config_only_folders)) }
                item {
                    RecipeLibrarySelector(
                        recipes = recipes,
                        bookFolders = bookFolders,
                        selectedBookFolders = selectedBookFolders,
                        excludedRecipeIds = state.excludedRecipeIds,
                        enabledRecipeCount = enabledRecipes.size,
                        minimumForVariety = minimumRecipesForVariety(state, profiles.size),
                        onToggleBook = {
                            viewModel.toggleFolder(it, availableBookFolders)
                        },
                        onSelectAllBooks = viewModel::selectAllFolders,
                        onDeselectAllBooks = viewModel::deselectAllFolders,
                        onSetRecipeEnabled = viewModel::setRecipeEnabled,
                        onEnableRecipes = viewModel::enableRecipes,
                        onDisableRecipes = viewModel::disableRecipes
                    )
                }
            }

            // ── Locks ───────────────────────────────────────────────
            item { SectionTitle(stringResource(R.string.menu_config_locked_meals)) }
            item {
                Text(
                    stringResource(R.string.menu_config_locked_meals_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.locks.size) { index ->
                val lock = state.locks[index]
                val recipeName = recipes.find { it.id == lock.recipeId }
                    ?.displayName(spanish) ?: "?"
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
                            SlowMarqueeText(
                                text = recipeName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth()
                            )
                            val who = lock.profileId
                                ?.let { id -> profiles.find { it.id == id }?.name }
                                ?: stringResource(R.string.menu_scope_everyone)
                            Text(
                                stringResource(lock.slot.labelRes) + " · " +
                                    lock.days.sorted().joinToString(", ") { shortDays[it] } +
                                    " · " + who,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.removeLock(index) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.menu_config_remove_lock),
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
                    Text(stringResource(R.string.menu_config_add_lock))
                }
            }
        }
    }

    if (showLockDialog) {
        AddLockDialog(
            slots = state.slots,
            recipes = recipes,
            profiles = profiles,
            onDismiss = { showLockDialog = false },
            onConfirm = { days, slot, recipe, profileId ->
                viewModel.addLock(days, slot, recipe, profileId)
                showLockDialog = false
            }
        )
    }

    if (showOverrideDialog) {
        AddOverrideDialog(
            slots = state.slots,
            profiles = profiles,
            onDismiss = { showOverrideDialog = false },
            onConfirm = { days, slot, profileId ->
                viewModel.addOverride(days, slot, profileId)
                showOverrideDialog = false
            }
        )
    }
}

/**
 * Book-first recipe picker. Search and course filters only affect what is
 * shown; book and recipe checkboxes are the complete source of truth handed to
 * the generator.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeLibrarySelector(
    recipes: List<RecipeEntity>,
    bookFolders: List<String?>,
    selectedBookFolders: Set<String?>,
    excludedRecipeIds: Set<Long>,
    enabledRecipeCount: Int,
    minimumForVariety: Int,
    onToggleBook: (String?) -> Unit,
    onSelectAllBooks: () -> Unit,
    onDeselectAllBooks: () -> Unit,
    onSetRecipeEnabled: (Long, Boolean) -> Unit,
    onEnableRecipes: (Set<Long>) -> Unit,
    onDisableRecipes: (Set<Long>) -> Unit
) {
    val spanish = rememberIsSpanish()
    var query by remember { mutableStateOf("") }
    var categoryFilters by remember { mutableStateOf(emptySet<String>()) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var expandedBooks by remember { mutableStateOf(emptySet<String?>()) }

    val normalizedQuery = TextNormalizer.normalize(query)
    val visibleRecipes = recipes.filter { recipe ->
        (normalizedQuery.isEmpty() ||
            (TextNormalizer.normalize(recipe.name).contains(normalizedQuery) ||
                recipe.nameEs?.let { TextNormalizer.normalize(it).contains(normalizedQuery) } == true ||
                recipe.folder?.let { folder ->
                    RecipeBooks.searchableNames(folder).any {
                        TextNormalizer.normalize(it).contains(normalizedQuery)
                    }
                } == true)) &&
            (categoryFilters.isEmpty() || recipe.category in categoryFilters) &&
            (!favoritesOnly || recipe.isFavorite)
    }
    val enabledSummary = pluralStringResource(
        R.plurals.menu_config_enabled_count,
        enabledRecipeCount,
        enabledRecipeCount,
        recipes.size
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.menu_config_recipe_books_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (enabledRecipeCount < minimumForVariety)
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(enabledSummary, fontWeight = FontWeight.SemiBold)
                if (enabledRecipeCount < minimumForVariety) {
                    Text(
                        pluralStringResource(
                            R.plurals.menu_config_variety_warning,
                            enabledRecipeCount,
                            enabledRecipeCount,
                            minimumForVariety
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSelectAllBooks,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small
            ) { Text(stringResource(R.string.menu_config_select_all_books)) }
            OutlinedButton(
                onClick = onDeselectAllBooks,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small
            ) { Text(stringResource(R.string.menu_config_deselect_all_books)) }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.menu_config_recipe_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.action_clear))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text(stringResource(R.string.menu_config_favorites_only)) }
            )
            RecipeCategories.ALL.forEach { category ->
                FilterChip(
                    selected = category in categoryFilters,
                    onClick = {
                        categoryFilters = if (category in categoryFilters)
                            categoryFilters - category else categoryFilters + category
                    },
                    label = { Text(stringResource(recipeCategoryLabel(category))) }
                )
            }
        }

        if (visibleRecipes.isEmpty()) {
            Text(
                stringResource(R.string.menu_config_no_recipe_matches),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        bookFolders.forEach { folder ->
            val allInBook = recipes.filter { it.folder == folder }
            val visibleInBook = visibleRecipes.filter { it.folder == folder }
            if (visibleInBook.isEmpty()) return@forEach

            val bookSelected = folder in selectedBookFolders
            val bookEnabledCount = allInBook.count {
                bookSelected && it.id !in excludedRecipeIds
            }
            val expanded = folder in expandedBooks || query.isNotBlank()
            RecipeBookCard(
                title = folder?.let { RecipeBooks.displayName(it, spanish) }
                    ?: stringResource(R.string.menu_config_personal_recipes),
                allRecipes = allInBook,
                visibleRecipes = visibleInBook,
                selected = bookSelected,
                expanded = expanded,
                enabledCount = bookEnabledCount,
                excludedRecipeIds = excludedRecipeIds,
                onToggleBook = { onToggleBook(folder) },
                onToggleExpanded = {
                    expandedBooks = if (folder in expandedBooks)
                        expandedBooks - folder else expandedBooks + folder
                },
                onSetRecipeEnabled = onSetRecipeEnabled,
                onEnableAll = { onEnableRecipes(allInBook.map { it.id }.toSet()) },
                onDisableAll = { onDisableRecipes(allInBook.map { it.id }.toSet()) }
            )
        }
    }
}

@Composable
private fun RecipeBookCard(
    title: String,
    allRecipes: List<RecipeEntity>,
    visibleRecipes: List<RecipeEntity>,
    selected: Boolean,
    expanded: Boolean,
    enabledCount: Int,
    excludedRecipeIds: Set<Long>,
    onToggleBook: () -> Unit,
    onToggleExpanded: () -> Unit,
    onSetRecipeEnabled: (Long, Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit
) {
    val spanish = rememberIsSpanish()
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = selected, onCheckedChange = { onToggleBook() })
                Icon(
                    Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        pluralStringResource(
                            R.plurals.menu_config_book_enabled_count,
                            enabledCount,
                            enabledCount,
                            allRecipes.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            if (expanded) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onEnableAll, enabled = selected) {
                        Text(stringResource(R.string.menu_config_enable_all))
                    }
                    TextButton(onClick = onDisableAll, enabled = selected) {
                        Text(stringResource(R.string.menu_config_disable_all))
                    }
                }
                visibleRecipes.forEach { recipe ->
                    val enabled = selected && recipe.id !in excludedRecipeIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enabled,
                            onCheckedChange = { onSetRecipeEnabled(recipe.id, it) },
                            enabled = selected
                        )
                        Column(Modifier.weight(1f)) {
                            SlowMarqueeText(
                                text = recipe.displayName(spanish),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                stringResource(recipeCategoryLabel(recipe.category)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Conservative lower bound imposed by the generator's hard repeat cap. */
private fun minimumRecipesForVariety(state: MenuConfigUiState, profileCount: Int): Int {
    val dishesPerDay = state.slots.sumOf { slot -> state.slotCourses[slot]?.size ?: 1 }
    val audiences = if (state.scope == MenuScope.INDIVIDUAL) profileCount.coerceAtLeast(1) else 1
    val selections = 7 * dishesPerDay * audiences
    return maxOf(8, ceil(selections.toDouble() / state.maxRepeats.coerceAtLeast(1)).toInt())
}

/** One of the three ways a week can be split across the household. */
@Composable
private fun ScopeCard(scope: MenuScope, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(scope.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(scope.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

/** Day chips shared by the lock and exception dialogs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Text(stringResource(R.string.menu_config_days), style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Short forms: seven full day names never fit on one row.
        WeekUtils.shortDayNames().forEachIndexed { index, name ->
            FilterChip(
                selected = index in selected,
                onClick = { onToggle(index) },
                label = { Text(name) }
            )
        }
    }
}

@Composable
private fun AddOverrideDialog(
    slots: List<MealSlot>,
    profiles: List<ProfileEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Int>, MealSlot, Long) -> Unit
) {
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var selectedSlot by remember { mutableStateOf(slots.first()) }
    var selectedProfile by remember { mutableStateOf(profiles.firstOrNull()?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_config_exception_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DayChips(selectedDays) { index ->
                    selectedDays =
                        if (index in selectedDays) selectedDays - index else selectedDays + index
                }
                KeyedDropdown(
                    label = stringResource(R.string.menu_config_meal),
                    options = slots.map { it to stringResource(it.labelRes) },
                    selectedKey = selectedSlot,
                    onSelected = { selectedSlot = it }
                )
                KeyedDropdown(
                    label = stringResource(R.string.menu_config_who),
                    options = profiles.map { it.id to it.name },
                    selectedKey = selectedProfile,
                    onSelected = { selectedProfile = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedDays.isNotEmpty() && selectedProfile != null,
                onClick = { selectedProfile?.let { onConfirm(selectedDays, selectedSlot, it) } }
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun AddLockDialog(
    slots: List<MealSlot>,
    recipes: List<RecipeEntity>,
    profiles: List<ProfileEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Int>, MealSlot, RecipeEntity, Long?) -> Unit
) {
    val spanish = rememberIsSpanish()
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var selectedSlot by remember { mutableStateOf(slots.first()) }
    var selectedRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    // null = the whole household, which is what a lock has always meant.
    var lockedFor by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_config_lock_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DayChips(selectedDays) { index ->
                    selectedDays =
                        if (index in selectedDays) selectedDays - index else selectedDays + index
                }
                KeyedDropdown(
                    label = stringResource(R.string.menu_config_meal),
                    options = slots.map { it to stringResource(it.labelRes) },
                    selectedKey = selectedSlot,
                    onSelected = { selectedSlot = it }
                )
                OptionDropdown(
                    label = stringResource(R.string.menu_config_recipe),
                    options = recipes.map { it.displayName(spanish) },
                    selected = selectedRecipe?.displayName(spanish),
                    onSelected = { name ->
                        selectedRecipe = recipes.find { it.displayName(spanish) == name }
                    }
                )
                if (profiles.size > 1) {
                    KeyedDropdown(
                        label = stringResource(R.string.menu_config_who),
                        options = listOf(
                            null to stringResource(R.string.menu_scope_everyone)
                        ) + profiles.map { it.id to it.name },
                        selectedKey = lockedFor,
                        onSelected = { lockedFor = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedDays.isNotEmpty() && selectedRecipe != null,
                onClick = {
                    selectedRecipe?.let { onConfirm(selectedDays, selectedSlot, it, lockedFor) }
                }
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
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
