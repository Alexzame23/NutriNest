package com.zame.nutrinest.ui.ingredients.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.IngredientPortionEntity
import com.zame.nutrinest.data.local.entity.NutrientValueWithDef
import com.zame.nutrinest.data.local.entity.PrepState
import com.zame.nutrinest.data.local.entity.RawSafety
import com.zame.nutrinest.ui.components.MacroDonutChart
import com.zame.nutrinest.ui.components.NutrientRow
import com.zame.nutrinest.ui.components.StatusChip
import com.zame.nutrinest.ui.components.formatNutrientValue
import com.zame.nutrinest.ui.ingredients.categoryColor
import com.zame.nutrinest.ui.ingredients.categoryIcon
import com.zame.nutrinest.ui.ingredients.dataQualityLabel
import com.zame.nutrinest.ui.ingredients.nutrientGroupLabel
import com.zame.nutrinest.ui.ingredients.prepStateLabel
import com.zame.nutrinest.ui.ingredients.qualityIssueLabel
import com.zame.nutrinest.ui.ingredients.rawSafetyDescription
import com.zame.nutrinest.ui.ingredients.rawSafetyLabel
import com.zame.nutrinest.ui.ingredients.rememberIsSpanish
import com.zame.nutrinest.ui.theme.Amber
import com.zame.nutrinest.ui.theme.Clay
import com.zame.nutrinest.ui.theme.Sage
import java.io.File

/**
 * Everything the app knows about one ingredient: where it sits, what it is made
 * of, how it is measured, and what it is used in.
 *
 * All figures are stored per 100 g; the basis selector rescales the whole page
 * to a serving or a household measure without touching the stored values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onCompare: (Long) -> Unit,
    onRetail: ((Long) -> Unit)?,
    viewModel: IngredientDetailViewModel = viewModel(factory = IngredientDetailViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spanish = rememberIsSpanish()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    val ingredient = state.ingredient
    if (ingredient == null) {
        if (!state.isLoading) {
            MissingIngredient(onBack)
        }
        return
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.ingredient_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ingredient_delete_body,
                        ingredient.displayName(spanish)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.onDeleteConfirmed()
                }) { Text(stringResource(R.string.ingredient_action_archive)) }
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
            title = { Text(stringResource(R.string.ingredient_delete_blocked_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ingredient_delete_blocked_body,
                        ingredient.displayName(spanish)
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
                    Text(
                        ingredient.displayName(spanish),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1
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
                            if (ingredient.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = stringResource(
                                if (ingredient.isFavorite) {
                                    R.string.ingredients_favorite_remove
                                } else {
                                    R.string.ingredients_favorite_add
                                }
                            ),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { onCompare(ingredient.id) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.CompareArrows,
                            contentDescription = stringResource(R.string.action_compare)
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.ingredient_action_archive)
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
                onClick = { onEdit(ingredient.id) },
                icon = { Icon(Icons.Rounded.Edit, null) },
                text = { Text(stringResource(R.string.action_edit)) },
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
            item("header") { Header(state, spanish) }
            item("retail") {
                if (onRetail != null) TextButton(onClick = { onRetail(ingredient.id) }) {
                    Text(stringResource(R.string.retail_title))
                }
            }

            if (state.portions.isNotEmpty() || ingredient.servingSizeG != null) {
                item("basis") {
                    BasisSelector(
                        portions = state.portions,
                        selected = state.basis.portion,
                        spanish = spanish,
                        onSelected = viewModel::onBasisSelected
                    )
                }
            }

            item("macros") {
                MacroSection(state = state, spanish = spanish)
            }

            item("raw_safety") { RawSafetySection(ingredient.rawSafety, ingredient.prepState) }

            if (state.hasMicronutrients) {
                item("micros_heading") { SectionTitle(stringResource(R.string.ingredient_micros)) }
                state.micronutrients.forEach { section ->
                    item("micro_${section.group}") {
                        MicronutrientCard(
                            title = stringResource(nutrientGroupLabel(section.group)),
                            nutrients = section.nutrients,
                            scale = state.scaleFactor,
                            spanish = spanish
                        )
                    }
                }
                item("nrv_note") {
                    Text(
                        stringResource(R.string.ingredient_nrv_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item("no_micros") {
                    Text(
                        stringResource(R.string.ingredient_no_micros),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.portions.isNotEmpty()) {
                item("portions") {
                    PortionsCard(state.portions, spanish)
                }
            }

            if (!ingredient.notes.isNullOrBlank()) {
                item("notes") {
                    LabelledCard(stringResource(R.string.ingredient_notes)) {
                        Text(
                            ingredient.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (state.aliases.isNotEmpty()) {
                item("aliases") {
                    LabelledCard(stringResource(R.string.ingredient_synonyms)) {
                        Text(
                            state.aliases.map { it.alias }.distinct().joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item("usage") { UsageCard(state) }

            if (state.issues.isNotEmpty() || ingredient.hasIncompleteData) {
                item("quality") { QualityCard(state) }
            }

            state.attribution?.takeIf { ingredient.sourceKey != null }?.let { credit ->
                item("attribution") {
                    Column {
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
            }
        }
    }
}

@Composable
private fun Header(state: IngredientDetailUiState, spanish: Boolean) {
    val ingredient = state.ingredient ?: return
    val tint = categoryColor(ingredient.categoryKey)

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(tint.copy(alpha = 0.16f), MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center
        ) {
            if (ingredient.imagePath != null) {
                AsyncImage(
                    model = File(ingredient.imagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    categoryIcon(ingredient.categoryKey),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // The other language's name, so a bilingual kitchen recognises it either way.
        val secondaryName = if (spanish) ingredient.name else ingredient.nameEs
        if (!secondaryName.isNullOrBlank() && secondaryName != ingredient.displayName(spanish)) {
            Text(
                secondaryName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(
                label = IngredientCategories.shortName(ingredient.categoryKey, spanish),
                color = tint,
                icon = categoryIcon(ingredient.categoryKey)
            )
            if (ingredient.prepState != PrepState.UNSPECIFIED) {
                StatusChip(
                    label = stringResource(prepStateLabel(ingredient.prepState)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BasisSelector(
    portions: List<IngredientPortionEntity>,
    selected: IngredientPortionEntity?,
    spanish: Boolean,
    onSelected: (IngredientPortionEntity?) -> Unit
) {
    Column {
        Text(
            stringResource(R.string.ingredient_basis_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item("100g") {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelected(null) },
                    label = { Text(stringResource(R.string.ingredient_basis_100g)) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            items(portions, key = { it.id }) { portion ->
                FilterChip(
                    selected = selected?.id == portion.id,
                    onClick = { onSelected(portion) },
                    label = {
                        Text(
                            stringResource(
                                R.string.ingredient_basis_serving,
                                formatNutrientValue(portion.grams)
                            )
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun MacroSection(state: IngredientDetailUiState, spanish: Boolean) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ingredient_macros),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    state.basis.portion?.displayLabel(spanish)
                        ?: stringResource(R.string.ingredient_basis_100g),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MacroDonutChart(nutrition = state.scaledNutrition)
            }
            Spacer(Modifier.height(16.dp))
            state.macroPanel.forEach { nutrient ->
                NutrientRow(
                    label = nutrient.displayName(spanish),
                    value = nutrient.value * state.scaleFactor,
                    unit = nutrient.unit,
                    emphasised = nutrient.isPrimary
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RawSafetySection(rawSafety: String, prepState: String) {
    val (tint, icon) = when (rawSafety) {
        RawSafety.SAFE -> Sage to Icons.Rounded.CheckCircle
        RawSafety.CAUTION -> Amber to Icons.Rounded.Warning
        RawSafety.UNSAFE -> Clay to Icons.Rounded.LocalFireDepartment
        else -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Rounded.Warning
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ingredient_raw_safety),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(rawSafetyLabel(rawSafety)),
                    style = MaterialTheme.typography.titleMedium,
                    color = tint
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(rawSafetyDescription(rawSafety)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.raw_safety_guidance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MicronutrientCard(
    title: String,
    nutrients: List<NutrientValueWithDef>,
    scale: Double,
    spanish: Boolean
) {
    LabelledCard(title) {
        nutrients.forEachIndexed { index, nutrient ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            NutrientRow(
                label = nutrient.displayName(spanish),
                value = nutrient.value * scale,
                unit = nutrient.unit,
                nrvPercent = nutrient.nrvPercent?.times(scale),
                emphasised = nutrient.isPrimary
            )
        }
    }
}

@Composable
private fun PortionsCard(portions: List<IngredientPortionEntity>, spanish: Boolean) {
    LabelledCard(stringResource(R.string.ingredient_portions)) {
        portions.forEachIndexed { index, portion ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    portion.displayLabel(spanish),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(
                        R.string.value_with_grams,
                        formatNutrientValue(portion.grams)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun UsageCard(state: IngredientDetailUiState) {
    val spanish = rememberIsSpanish()
    LabelledCard(stringResource(R.string.ingredient_used_in)) {
        if (state.usedIn.isEmpty()) {
            Text(
                stringResource(R.string.ingredient_used_in_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.usedIn.forEachIndexed { index, usage ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        usage.displayName(spanish),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(
                            R.string.value_with_grams,
                            formatNutrientValue(usage.grams)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityCard(state: IngredientDetailUiState) {
    val ingredient = state.ingredient ?: return
    LabelledCard(stringResource(R.string.ingredient_data_quality)) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(stringResource(dataQualityLabel(ingredient.quality))) },
            leadingIcon = { Icon(Icons.Rounded.Warning, null, Modifier.size(16.dp)) },
            colors = AssistChipDefaults.assistChipColors(
                disabledLabelColor = Amber,
                disabledLeadingIconContentColor = Amber
            )
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.ingredient_completeness, ingredient.completenessPct),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.issues.forEach { issue ->
            qualityIssueLabel(issue.code)?.let { labelRes ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun MissingIngredient(onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.ingredient_not_found),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}
