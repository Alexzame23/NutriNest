package com.zame.nutrinest.ui.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.zame.nutrinest.data.local.dao.CatalogPresentation
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.repository.IngredientRepository
import com.zame.nutrinest.domain.nutrition.ConversionResult
import com.zame.nutrinest.domain.nutrition.FoodQuantityConversions
import com.zame.nutrinest.domain.nutrition.MeasureConversions
import com.zame.nutrinest.domain.nutrition.Quantity
import com.zame.nutrinest.domain.nutrition.QuantityDimension
import com.zame.nutrinest.domain.nutrition.RecipeMeasure
import com.zame.nutrinest.domain.nutrition.RecipeQuantity
import com.zame.nutrinest.domain.nutrition.UnitDefinition
import com.zame.nutrinest.ui.components.formatRecipeAmount
import com.zame.nutrinest.ui.components.formatSavedRecipeQuantity
import com.zame.nutrinest.util.LocalizedNumbers
import kotlinx.coroutines.flow.combine
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

/** Presentation keyed by source ingredient ID, including historical references. */
data class MenuIngredientCatalog(
    val names: Map<Long, CatalogPresentation> = emptyMap(),
    val measures: Map<Long, List<RecipeMeasure>> = emptyMap()
) {
    fun nameOf(ingredient: IngredientEntity, spanish: Boolean): String {
        val entry = names[ingredient.id]
        return (if (spanish) entry?.nameEs else entry?.nameEn)
            ?.takeIf { it.isNotBlank() } ?: ingredient.displayName(spanish)
    }
}

internal fun IngredientRepository.observeMenuIngredientCatalog() =
    combine(observeCatalogPresentation(), observeRecipeMeasures(), ::MenuIngredientCatalog)

internal data class NaturalQuantity(
    val amount: Double,
    val unit: UnitDefinition,
    val labelEn: String,
    val labelEs: String = labelEn
)

/** Converts only after the planner has summed grams for this exact ingredient. */
internal fun naturalQuantity(grams: Double, options: List<RecipeMeasure>): NaturalQuantity? {
    val candidates = options.filter { it.unit.dimension != QuantityDimension.MASS }
        .sortedByDescending { it.isDefault }
    for (measure in candidates) {
        val amount = measure.forMass(RecipeQuantity(grams))?.originalAmount ?: continue
        displayMeasure(amount, measure)?.let { return it }
    }
    return null
}

private fun displayMeasure(amount: Double, measure: RecipeMeasure): NaturalQuantity? {
    if (measure.unit.dimension != QuantityDimension.VOLUME) {
        return NaturalQuantity(amount, measure.unit, measure.labelEn, measure.labelEs)
    }
    val volume = Quantity(amount, measure.unit)
    val ml = FoodQuantityConversions.convert(volume, UnitDefinition.MILLILITRE) as? ConversionResult.Success ?: return null
    if (ml.quantity.amount < 1_000.0) {
        return NaturalQuantity(ml.quantity.amount, UnitDefinition.MILLILITRE, "ml")
    }
    val litres = FoodQuantityConversions.convert(volume, UnitDefinition.LITRE) as? ConversionResult.Success ?: return null
    return NaturalQuantity(litres.quantity.amount, UnitDefinition.LITRE, "L")
}

internal data class ShoppingQuantity(
    val requiredGrams: Double,
    val required: NaturalQuantity?,
    val toBuy: NaturalQuantity?
) {
    companion object {
        fun from(grams: Double, options: List<RecipeMeasure>): ShoppingQuantity {
            val required = naturalQuantity(grams, options)
            val toBuy = required?.takeIf { it.unit.dimension == QuantityDimension.COUNT }?.let {
                val nearest = round(it.amount)
                // A handful of floating-point bits at an integer boundary must not add a piece.
                val whole = if (nearest >= 1.0 && abs(it.amount - nearest) <= 4 * Math.ulp(it.amount)) {
                    nearest
                } else {
                    ceil(it.amount)
                }
                it.copy(amount = whole)
            }
            return ShoppingQuantity(grams, required, toBuy)
        }
    }
}

@Composable
internal fun formatNaturalQuantity(quantity: NaturalQuantity, spanish: Boolean): String {
    val locale = LocalConfiguration.current.locales[0]
    val number = LocalizedNumbers.format(quantity.amount, locale).takeUnless { it == "0" }
        ?: formatRecipeAmount(quantity.amount)
    val label = if (spanish) quantity.labelEs else quantity.labelEn
    val separator = when (quantity.unit.dimension) {
        QuantityDimension.MASS, QuantityDimension.VOLUME -> " "
        else -> " × "
    }
    return number + separator + label
}

@Composable
internal fun formatShoppingWeight(grams: Double): String {
    val (amount, unit) = MeasureConversions.humanizeGrams(grams)
    val quantity = if (unit == MeasureConversions.MeasureUnit.KILOGRAM) {
        NaturalQuantity(amount, UnitDefinition.KILOGRAM, "kg")
    } else {
        NaturalQuantity(amount, UnitDefinition.GRAM, "g")
    }
    return formatNaturalQuantity(quantity, spanish = false)
}

@Composable
internal fun formatMealIngredientQuantity(
    ingredient: IngredientWithQuantity,
    options: List<RecipeMeasure>,
    spanish: Boolean,
    factor: Double
): String {
    val entered = ingredient.originalQuantity
    val display = if (entered == null) {
        naturalQuantity(ingredient.grams * factor, options)
    } else {
        options.firstOrNull { it.unit.key == ingredient.quantityUnit }
            ?.let { displayMeasure(entered * factor, it) }
    }
    return if (display != null) formatNaturalQuantity(display, spanish)
    else formatSavedRecipeQuantity(ingredient.quantity, options, spanish, scale = factor)
}
