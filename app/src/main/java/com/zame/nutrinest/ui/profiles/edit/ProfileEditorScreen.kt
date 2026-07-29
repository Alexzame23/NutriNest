package com.zame.nutrinest.ui.profiles.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zame.nutrinest.data.local.entity.ActivityLevel
import com.zame.nutrinest.data.local.entity.BiologicalSex
import com.zame.nutrinest.data.local.entity.BmrFormula
import com.zame.nutrinest.data.local.entity.Goal
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.UnitSystem
import com.zame.nutrinest.data.local.entity.WeightLossRate
import com.zame.nutrinest.domain.nutrition.NutritionTargets
import com.zame.nutrinest.ui.components.MacroDonutChart
import com.zame.nutrinest.ui.components.OptionDropdown
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    onBack: () -> Unit,
    viewModel: ProfileEditorViewModel = viewModel(factory = ProfileEditorViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }
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
                    Text(
                        if (state.editingId == null) "New Profile" else "Edit Profile",
                        style = MaterialTheme.typography.headlineSmall
                    )
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
                onClick = viewModel::save,
                icon = { Icon(Icons.Rounded.Check, null) },
                text = { Text("Save") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Basics ──────────────────────────────────────────────
            item { SectionTitle("About") }
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Name *") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                // Date of birth field opens the Material date picker.
                OutlinedTextField(
                    value = state.dateOfBirth?.let {
                        DateFormat.getDateInstance().format(Date(it))
                    } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Date of birth *") },
                    trailingIcon = { Icon(Icons.Rounded.CalendarToday, null) },
                    shape = MaterialTheme.shapes.small,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                )
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    BiologicalSex.entries.forEachIndexed { index, sex ->
                        SegmentedButton(
                            selected = state.sex == sex,
                            onClick = { viewModel.onSexChange(sex) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index, BiologicalSex.entries.size
                            )
                        ) { Text(sex.label) }
                    }
                }
            }

            // ── Body ────────────────────────────────────────────────
            item { SectionTitle("Body") }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    UnitSystem.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = state.unitSystem == unit,
                            onClick = { viewModel.onUnitSystemChange(unit) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index, UnitSystem.entries.size
                            )
                        ) { Text(if (unit == UnitSystem.METRIC) "Metric" else "Imperial") }
                    }
                }
            }
            item {
                if (state.unitSystem == UnitSystem.METRIC) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            label = "Height (cm) *",
                            value = state.heightText,
                            onChange = viewModel::onHeightChange,
                            modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            label = "Weight (kg) *",
                            value = state.weightText,
                            onChange = viewModel::onWeightChange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            label = "Height (ft) *",
                            value = state.heightText,
                            onChange = viewModel::onHeightChange,
                            modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            label = "(in)",
                            value = state.heightInchesText,
                            onChange = viewModel::onHeightInchesChange,
                            modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            label = "Weight (lb) *",
                            value = state.weightText,
                            onChange = viewModel::onWeightChange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item {
                NumberField(
                    label = "Body fat % (optional — enables Katch-McArdle)",
                    value = state.bodyFatText,
                    onChange = viewModel::onBodyFatChange
                )
            }

            // ── Lifestyle & goal ────────────────────────────────────
            item { SectionTitle("Lifestyle & Goal") }
            item {
                OptionDropdown(
                    label = "Activity level *",
                    options = ActivityLevel.entries.map { "${it.label} — ${it.description}" },
                    selected = "${state.activityLevel.label} — ${state.activityLevel.description}",
                    onSelected = { selected ->
                        ActivityLevel.entries
                            .find { selected.startsWith(it.label) }
                            ?.let(viewModel::onActivityChange)
                    }
                )
            }
            item {
                OptionDropdown(
                    label = "Primary goal *",
                    options = Goal.entries.map { it.label },
                    selected = state.goal.label,
                    onSelected = { selected ->
                        Goal.entries.find { it.label == selected }
                            ?.let(viewModel::onGoalChange)
                    }
                )
            }
            // Deficit size — only relevant when losing weight.
            item {
                AnimatedVisibility(visible = state.goal == Goal.LOSE_WEIGHT) {
                    OptionDropdown(
                        label = "Weight loss pace",
                        options = WeightLossRate.entries.map { it.label },
                        selected = state.weightLossRate.label,
                        onSelected = { selected ->
                            WeightLossRate.entries.find { it.label == selected }
                                ?.let(viewModel::onWeightLossRateChange)
                        }
                    )
                }
            }
            item {
                OptionDropdown(
                    label = "Calculation formula",
                    options = BmrFormula.entries.map { it.label },
                    selected = state.formula.label,
                    onSelected = { selected ->
                        BmrFormula.entries.find { it.label == selected }
                            ?.let(viewModel::onFormulaChange)
                    }
                )
            }

            // ── Live results ────────────────────────────────────────
            item {
                AnimatedVisibility(visible = state.liveTargets != null) {
                    state.liveTargets?.let { TargetsCard(it) }
                }
            }

            // ── Optional extras ─────────────────────────────────────
            item { SectionTitle("Preferences (optional)") }
            item {
                OutlinedTextField(
                    value = state.dietaryPreferences,
                    onValueChange = viewModel::onDietaryChange,
                    label = { Text("Dietary preferences") },
                    placeholder = { Text("e.g. vegetarian, low-carb…") },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = state.allergies,
                    onValueChange = viewModel::onAllergiesChange,
                    label = { Text("Allergies") },
                    placeholder = { Text("e.g. peanuts, shellfish…") },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.dateOfBirth
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let(viewModel::onDateOfBirthChange)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** Card showing BMR, TDEE, target calories, and the macro breakdown. */
@Composable
private fun TargetsCard(targets: NutritionTargets) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Your daily targets",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Calculated with ${targets.formulaUsed.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            targets.note?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MacroDonutChart(
                    nutrition = NutritionValues(
                        kcal = targets.dailyCalories.toDouble(),
                        carbs = targets.carbsG.toDouble(),
                        fats = targets.fatG.toDouble(),
                        protein = targets.proteinG.toDouble()
                    )
                )
            }
            Spacer(Modifier.height(16.dp))
            StatRow("BMR (at rest)", "${targets.bmr} kcal")
            StatRow("TDEE (with activity)", "${targets.tdee} kcal")
            StatRow("Daily target", "${targets.dailyCalories} kcal", highlight = true)
            Spacer(Modifier.height(8.dp))
            StatRow("Protein", "${targets.proteinG} g")
            StatRow("Carbohydrates", "${targets.carbsG} g")
            StatRow("Fats", "${targets.fatG} g")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 10.dp)
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    )
}
