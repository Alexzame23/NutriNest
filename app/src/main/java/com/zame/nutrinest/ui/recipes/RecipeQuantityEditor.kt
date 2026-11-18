package com.zame.nutrinest.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zame.nutrinest.R
import com.zame.nutrinest.domain.nutrition.QuantityDimension
import com.zame.nutrinest.domain.nutrition.RecipeMeasure
import com.zame.nutrinest.domain.nutrition.RecipeQuantity
import com.zame.nutrinest.domain.nutrition.UnitDefinition
import com.zame.nutrinest.ui.components.formatRecipeAmount
import com.zame.nutrinest.util.toLocalizedDoubleOrNull

internal fun RecipeMeasure.label(spanish: Boolean) = if (spanish) labelEs else labelEn

internal class RecipeQuantityEditor(
    options: List<RecipeMeasure>,
    saved: RecipeQuantity? = null
) {
    var measure by mutableStateOf(
        if (saved == null) RecipeMeasure.default(options)
        else options.firstOrNull { it.supports(saved) } ?: RecipeMeasure.GRAMS
    )
        private set

    private var anchor = if (saved == null) {
        val defaultAmount = if (measure.unit in listOf(UnitDefinition.GRAM, UnitDefinition.MILLILITRE)) 100.0 else 1.0
        checkNotNull(measure.enter(defaultAmount) ?: measure.enter(1.0))
    } else {
        measure.forMass(saved) ?: RecipeQuantity(saved.grams)
    }

    private val initial = anchor
    var quantity: RecipeQuantity? by mutableStateOf(anchor.takeIf { it.isValid })
        private set
    var text by mutableStateOf(anchor.inputText())
        private set

    val hasChanges get() = quantity != initial

    fun enter(text: String) {
        this.text = text
        val amount = text.toLocalizedDoubleOrNull()
        val unchanged = measure.forMass(anchor)
        quantity = if (unchanged?.isValid == true && amount == unchanged.amount()) {
            unchanged
        } else {
            amount?.let(measure::enter)?.also { anchor = it }
        }
    }

    fun select(measure: RecipeMeasure) {
        if (quantity == null || this.measure == measure) return
        val converted = measure.forMass(anchor) ?: return
        this.measure = measure
        quantity = converted
        text = converted.inputText()
    }

    private fun RecipeQuantity.amount() = originalAmount ?: grams
    private fun RecipeQuantity.inputText() = amount().toString().removeSuffix(".0")
}

@Composable
internal fun RecipeQuantityFields(
    editor: RecipeQuantityEditor,
    options: List<RecipeMeasure>,
    spanish: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (options.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options, key = { it.unit.key }) { measure ->
                    FilterChip(
                        selected = editor.measure == measure,
                        enabled = editor.quantity != null,
                        onClick = { editor.select(measure) },
                        label = { Text(measure.label(spanish)) }
                    )
                }
            }
        }
        val current = editor.quantity
        OutlinedTextField(
            value = editor.text,
            onValueChange = editor::enter,
            label = { Text(stringResource(R.string.create_recipe_quantity, editor.measure.label(spanish))) },
            supportingText = {
                if (current == null) {
                    Text(stringResource(R.string.recipe_error_invalid_quantity))
                } else if (editor.measure.unit.dimension != QuantityDimension.MASS) {
                    Text(stringResource(R.string.recipe_edible_weight, formatRecipeAmount(current.grams)))
                }
            },
            isError = current == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun EditRecipeQuantityDialog(
    ingredientName: String,
    saved: RecipeQuantity,
    options: List<RecipeMeasure>,
    spanish: Boolean,
    onDismiss: () -> Unit,
    onSave: (RecipeQuantity) -> Unit
) {
    val editor = remember(saved) { RecipeQuantityEditor(options, saved) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ingredientName) },
        text = { RecipeQuantityFields(editor, options, spanish) },
        confirmButton = {
            TextButton(
                enabled = editor.quantity != null && editor.hasChanges,
                onClick = { editor.quantity?.let(onSave) }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
