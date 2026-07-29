package com.zame.nutrinest.ui.ingredients.editor

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.NutrientDefEntity
import com.zame.nutrinest.data.local.entity.NutrientGroups
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.RawSafety
import com.zame.nutrinest.domain.nutrition.IngredientValidator
import com.zame.nutrinest.ui.components.ImagePickerBox
import com.zame.nutrinest.ui.components.KeyedDropdown
import com.zame.nutrinest.ui.components.MacroDonutChart
import com.zame.nutrinest.ui.ingredients.nutrientGroupLabel
import com.zame.nutrinest.ui.ingredients.rawSafetyLabel
import com.zame.nutrinest.ui.ingredients.rememberIsSpanish
import com.zame.nutrinest.ui.theme.Amber
import com.zame.nutrinest.ui.theme.Clay

/**
 * Create or edit an ingredient.
 *
 * Required fields come first, everything optional sits below in sections that
 * stay collapsed until wanted — the dataset defines 81 nutrients and showing
 * them all at once would bury the four figures that actually matter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientEditorScreen(
    onBack: () -> Unit,
    viewModel: IngredientEditorViewModel = viewModel(factory = IngredientEditorViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    val spanish = rememberIsSpanish()

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    if (state.confirmWarnings) {
        WarningsDialog(
            warnings = state.validation.warnings,
            onConfirm = viewModel::onConfirmWarnings,
            onDismiss = viewModel::onDismissWarnings
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.editor_title_edit
                            else R.string.editor_title_new
                        ),
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
                onClick = viewModel::onSave,
                icon = { Icon(Icons.Rounded.Check, null) },
                text = { Text(stringResource(R.string.action_save)) },
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
            item("photo") {
                ImagePickerBox(
                    imagePath = state.imagePath,
                    onImagePicked = viewModel::onImagePicked,
                    placeholderText = stringResource(R.string.editor_photo_hint)
                )
            }

            item("name") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.editor_name)) },
                    isError = IngredientValidator.Field.NAME in state.fieldsInError ||
                        state.duplicateName,
                    supportingText = {
                        when {
                            state.duplicateName -> Text(
                                stringResource(R.string.validation_duplicate_name, state.name.trim()),
                                color = MaterialTheme.colorScheme.error
                            )
                            state.similar.isNotEmpty() -> Text(
                                stringResource(
                                    R.string.validation_similar_name,
                                    state.similar.joinToString(", ") { it.displayName(spanish) }
                                ),
                                color = Amber
                            )
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item("name_es") {
                OutlinedTextField(
                    value = state.nameEs,
                    onValueChange = viewModel::onNameEsChange,
                    label = { Text(stringResource(R.string.editor_name_es)) },
                    supportingText = { Text(stringResource(R.string.editor_name_es_help)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item("category") {
                KeyedDropdown(
                    label = stringResource(R.string.editor_category),
                    options = state.categories.map { it.key to it.displayName(spanish) },
                    selectedKey = state.categoryKey,
                    onSelected = viewModel::onCategoryChange,
                    isError = IngredientValidator.Field.CATEGORY in state.fieldsInError
                )
            }

            item("nutrition_heading") {
                SectionTitle(stringResource(R.string.editor_nutrition_heading))
            }

            item("kcal") {
                NumberField(
                    label = stringResource(R.string.editor_kcal),
                    value = state.kcal,
                    isError = IngredientValidator.Field.KCAL in state.fieldsInError,
                    onChange = viewModel::onKcalChange
                )
            }

            item("macros") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        label = stringResource(R.string.editor_carbs),
                        value = state.carbs,
                        isError = IngredientValidator.Field.CARBS in state.fieldsInError,
                        onChange = viewModel::onCarbsChange,
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = stringResource(R.string.editor_fats),
                        value = state.fats,
                        isError = IngredientValidator.Field.FATS in state.fieldsInError,
                        onChange = viewModel::onFatsChange,
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = stringResource(R.string.editor_protein),
                        value = state.protein,
                        isError = IngredientValidator.Field.PROTEIN in state.fieldsInError,
                        onChange = viewModel::onProteinChange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (state.validation.issues.isNotEmpty()) {
                item("validation") {
                    ValidationCard(
                        issues = state.validation.issues,
                        suggestedKcal = state.suggestedKcal,
                        onUseSuggestedKcal = viewModel::onUseSuggestedKcal
                    )
                }
            }

            // Live macro balance: a typo like 400 g of carbs is obvious here.
            item("preview") {
                val preview = NutritionValues(
                    kcal = state.kcal.toDoubleOrNull() ?: 0.0,
                    carbs = state.carbs.toDoubleOrNull() ?: 0.0,
                    fats = state.fats.toDoubleOrNull() ?: 0.0,
                    protein = state.protein.toDoubleOrNull() ?: 0.0
                )
                if (preview.carbs + preview.fats + preview.protein > 0) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MacroDonutChart(nutrition = preview)
                    }
                }
            }

            item("optional_heading") {
                SectionTitle(stringResource(R.string.editor_optional_heading))
            }

            item("raw_safety") {
                KeyedDropdown(
                    label = stringResource(R.string.editor_raw_safety),
                    options = RawSafety.ALL.map { it to stringResource(rawSafetyLabel(it)) },
                    selectedKey = state.rawSafety,
                    onSelected = viewModel::onRawSafetyChange
                )
            }

            item("serving") {
                NumberField(
                    label = stringResource(R.string.editor_serving_size),
                    value = state.servingSizeG,
                    isError = IngredientValidator.Field.SERVING_SIZE in state.fieldsInError,
                    onChange = viewModel::onServingSizeChange
                )
            }

            item("liquid") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.editor_is_liquid),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = state.isLiquid, onCheckedChange = viewModel::onIsLiquidChange)
                }
            }

            if (state.isLiquid) {
                item("density") {
                    NumberField(
                        label = stringResource(R.string.editor_density),
                        value = state.densityGml,
                        isError = IngredientValidator.Field.DENSITY in state.fieldsInError,
                        onChange = viewModel::onDensityChange
                    )
                }
            }

            item("notes") {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text(stringResource(R.string.editor_notes)) },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item("portions") {
                PortionsEditor(
                    portions = state.portions,
                    onAdd = viewModel::onAddPortion,
                    onChange = viewModel::onPortionChange,
                    onRemove = viewModel::onRemovePortion
                )
            }

            item("synonyms") {
                SynonymsEditor(
                    synonyms = state.synonyms,
                    onAdd = viewModel::onAddSynonym,
                    onChange = viewModel::onSynonymChange,
                    onRemove = viewModel::onRemoveSynonym
                )
            }

            item("micros_heading") {
                Column {
                    SectionTitle(stringResource(R.string.editor_micros_heading))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.editor_micros_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val defsByGroup = state.nutrientDefs.groupBy { it.nutrientGroup }
            NutrientGroups.DISPLAY_ORDER.forEach { group ->
                val defs = defsByGroup[group].orEmpty()
                if (defs.isNotEmpty()) {
                    item("micro_$group") {
                        NutrientGroupEditor(
                            title = stringResource(nutrientGroupLabel(group)),
                            defs = defs,
                            values = state.micronutrients,
                            spanish = spanish,
                            onChange = viewModel::onMicronutrientChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    isError: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    )
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

/**
 * Shows what the validator found while the form is still being filled in.
 * Errors are red and block saving; warnings are amber and only ask for a
 * second look. An energy mismatch offers the computed figure as a one-tap fix.
 */
@Composable
private fun ValidationCard(
    issues: List<IngredientValidator.Issue>,
    suggestedKcal: Int?,
    onUseSuggestedKcal: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            issues.forEachIndexed { index, issue ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                val isError = issue.severity == IngredientValidator.Severity.ERROR
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (isError) Clay else Amber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        validationMessage(issue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (issue.code == IngredientValidator.ValidationCode.ENERGY_MISMATCH &&
                    suggestedKcal != null
                ) {
                    TextButton(onClick = onUseSuggestedKcal) {
                        Text(stringResource(R.string.editor_use_atwater, suggestedKcal))
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningsDialog(
    warnings: List<IngredientValidator.Issue>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.validation_warnings_title)) },
        text = {
            Column {
                warnings.forEach { issue ->
                    Text(validationMessage(issue), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_save_anyway)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Maps a validator code to its translated message, filling in any numbers. */
@Composable
private fun validationMessage(issue: IngredientValidator.Issue): String = when (issue.code) {
    IngredientValidator.ValidationCode.NAME_REQUIRED ->
        stringResource(R.string.validation_name_required)
    IngredientValidator.ValidationCode.CATEGORY_REQUIRED ->
        stringResource(R.string.validation_category_required)
    IngredientValidator.ValidationCode.KCAL_REQUIRED ->
        stringResource(R.string.validation_kcal_required)
    IngredientValidator.ValidationCode.KCAL_INVALID ->
        stringResource(R.string.validation_kcal_invalid)
    IngredientValidator.ValidationCode.CARBS_INVALID ->
        stringResource(R.string.validation_carbs_invalid)
    IngredientValidator.ValidationCode.FATS_INVALID ->
        stringResource(R.string.validation_fats_invalid)
    IngredientValidator.ValidationCode.PROTEIN_INVALID ->
        stringResource(R.string.validation_protein_invalid)
    IngredientValidator.ValidationCode.NUTRIENT_NEGATIVE ->
        stringResource(R.string.validation_nutrient_negative)
    IngredientValidator.ValidationCode.GRAMS_EXCEED_100 ->
        stringResource(R.string.validation_grams_exceed_100)
    IngredientValidator.ValidationCode.MASS_BALANCE_OUT_OF_RANGE ->
        stringResource(R.string.validation_mass_balance, issue.args.firstOrNull().orEmpty())
    IngredientValidator.ValidationCode.ENERGY_MISMATCH ->
        stringResource(
            R.string.validation_energy_mismatch,
            issue.args.getOrElse(0) { "" },
            issue.args.getOrElse(1) { "" }
        )
    IngredientValidator.ValidationCode.SATFAT_EXCEEDS_FAT ->
        stringResource(
            R.string.validation_satfat_exceeds_fat,
            issue.args.getOrElse(0) { "" },
            issue.args.getOrElse(1) { "" }
        )
    IngredientValidator.ValidationCode.SUGARS_EXCEED_CARBS ->
        stringResource(
            R.string.validation_sugars_exceed_carbs,
            issue.args.getOrElse(0) { "" },
            issue.args.getOrElse(1) { "" }
        )
    IngredientValidator.ValidationCode.STARCH_EXCEEDS_CARBS ->
        stringResource(
            R.string.validation_starch_exceeds_carbs,
            issue.args.getOrElse(0) { "" },
            issue.args.getOrElse(1) { "" }
        )
    IngredientValidator.ValidationCode.KCAL_IMPLAUSIBLE ->
        stringResource(R.string.validation_kcal_implausible, issue.args.firstOrNull().orEmpty())
    IngredientValidator.ValidationCode.SERVING_SIZE_INVALID ->
        stringResource(R.string.validation_serving_size_invalid)
    IngredientValidator.ValidationCode.DENSITY_INVALID ->
        stringResource(R.string.validation_density_invalid)
    else -> stringResource(R.string.validation_fix_required)
}

@Composable
private fun PortionsEditor(
    portions: List<PortionDraft>,
    onAdd: () -> Unit,
    onChange: (Int, PortionDraft) -> Unit,
    onRemove: (Int) -> Unit
) {
    Column {
        Text(
            stringResource(R.string.editor_portions_heading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.editor_portions_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        portions.forEachIndexed { index, portion ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = portion.label,
                    onValueChange = { onChange(index, portion.copy(label = it)) },
                    label = { Text(stringResource(R.string.editor_portion_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1.6f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = portion.grams,
                    onValueChange = { onChange(index, portion.copy(grams = it)) },
                    label = { Text(stringResource(R.string.editor_portion_grams)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.action_remove))
                }
            }
        }
        TextButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.editor_add_portion))
        }
    }
}

@Composable
private fun SynonymsEditor(
    synonyms: List<String>,
    onAdd: () -> Unit,
    onChange: (Int, String) -> Unit,
    onRemove: (Int) -> Unit
) {
    Column {
        Text(
            stringResource(R.string.editor_synonyms_heading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.editor_synonyms_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        synonyms.forEachIndexed { index, synonym ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = synonym,
                    onValueChange = { onChange(index, it) },
                    label = { Text(stringResource(R.string.editor_synonym)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.action_remove))
                }
            }
        }
        TextButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.editor_add_synonym))
        }
    }
}

/** One collapsible group of the nutrient panel, e.g. every vitamin. */
@Composable
private fun NutrientGroupEditor(
    title: String,
    defs: List<NutrientDefEntity>,
    values: Map<String, String>,
    spanish: Boolean,
    onChange: (String, String) -> Unit
) {
    val filledCount = defs.count { values[it.tag]?.isNotBlank() == true }
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (filledCount > 0) {
                    Text(
                        filledCount.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            if (expanded) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    defs.forEach { def ->
                        OutlinedTextField(
                            value = values[def.tag].orEmpty(),
                            onValueChange = { onChange(def.tag, it) },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.value_with_unit,
                                        def.displayName(spanish),
                                        "(${def.unit})"
                                    )
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
