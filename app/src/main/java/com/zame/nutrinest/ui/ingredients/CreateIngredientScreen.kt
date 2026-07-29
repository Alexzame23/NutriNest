package com.zame.nutrinest.ui.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zame.nutrinest.data.local.entity.FoodTypes
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.ui.components.ImagePickerBox
import com.zame.nutrinest.ui.components.MacroDonutChart
import com.zame.nutrinest.ui.components.OptionDropdown
import com.zame.nutrinest.ui.ingredients.CreateIngredientUiState.Field

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIngredientScreen(
    onBack: () -> Unit,
    viewModel: CreateIngredientViewModel = viewModel(factory = CreateIngredientViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = { Text("New Ingredient", style = MaterialTheme.typography.headlineSmall) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ImagePickerBox(
                    imagePath = state.imagePath,
                    onImagePicked = viewModel::onImagePicked,
                    placeholderText = "Add photo (optional)"
                )
            }
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Ingredient name *") },
                    isError = Field.NAME in state.fieldErrors,
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OptionDropdown(
                    label = "Food type *",
                    options = FoodTypes.ALL,
                    selected = state.foodType,
                    onSelected = viewModel::onFoodTypeChange,
                    isError = Field.FOOD_TYPE in state.fieldErrors
                )
            }
            item {
                Text(
                    "Nutrition per 100 g",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                NutritionField(
                    label = "Calories (kcal) *",
                    value = state.kcal,
                    isError = Field.KCAL in state.fieldErrors,
                    onChange = viewModel::onKcalChange
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutritionField(
                        label = "Carbs (g) *",
                        value = state.carbs,
                        isError = Field.CARBS in state.fieldErrors,
                        onChange = viewModel::onCarbsChange,
                        modifier = Modifier.weight(1f)
                    )
                    NutritionField(
                        label = "Fats (g) *",
                        value = state.fats,
                        isError = Field.FATS in state.fieldErrors,
                        onChange = viewModel::onFatsChange,
                        modifier = Modifier.weight(1f)
                    )
                    NutritionField(
                        label = "Protein (g) *",
                        value = state.protein,
                        isError = Field.PROTEIN in state.fieldErrors,
                        onChange = viewModel::onProteinChange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            // Live preview of the macro balance as the user types —
            // instant feedback, and typos (e.g. 400 g carbs) become obvious.
            item {
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
        }
    }
}

@Composable
private fun NutritionField(
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
