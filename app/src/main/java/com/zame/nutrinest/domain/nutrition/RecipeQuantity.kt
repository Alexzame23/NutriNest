package com.zame.nutrinest.domain.nutrition

import kotlin.math.abs

/** The edible mass is authoritative. Non-gram input keeps its original amount and evidence. */
data class RecipeQuantity(
    val grams: Double,
    val unitKey: String = "GRAM",
    val originalAmount: Double? = null,
    val evidenceId: String? = null
) {
    val isValid: Boolean get() = grams.isFinite() && grams > 0.0 && grams <= 100_000.0

    operator fun plus(other: RecipeQuantity): RecipeQuantity {
        val total = grams + other.grams
        return if (unitKey == other.unitKey && evidenceId == other.evidenceId &&
            originalAmount != null && other.originalAmount != null
        ) {
            copy(grams = total, originalAmount = originalAmount + other.originalAmount)
        } else {
            RecipeQuantity(total)
        }
    }
}

data class RecipeMeasure(
    val unit: UnitDefinition,
    val labelEn: String,
    val labelEs: String = labelEn,
    val isDefault: Boolean = false,
    val factor: FoodConversionFactor? = null
) {
    private val context get() = factor?.let { IngredientConversionContext(it.ingredientId, listOf(it)) }
    private val evidenceId get() = factor?.evidence?.id

    private fun toGrams(amount: Double) =
        FoodQuantityConversions.toGrams(Quantity(amount, unit), context)

    fun enter(amount: Double): RecipeQuantity? {
        val grams = (toGrams(amount) as? ConversionResult.Success)?.quantity?.amount ?: return null
        return create(grams, amount).takeIf { it.isValid }
    }

    fun forMass(quantity: RecipeQuantity): RecipeQuantity? {
        if (quantity.unitKey == unit.key && quantity.evidenceId == evidenceId &&
            (quantity.originalAmount != null || unit == UnitDefinition.GRAM)) return quantity
        val result = FoodQuantityConversions.convert(
            Quantity(quantity.grams, UnitDefinition.GRAM), unit, context
        ) as? ConversionResult.Success ?: return null
        return create(quantity.grams, result.quantity.amount)
    }

    fun supports(quantity: RecipeQuantity): Boolean {
        if (!quantity.isValid || quantity.unitKey != unit.key || quantity.evidenceId != evidenceId) return false
        val amount = if (unit == UnitDefinition.GRAM) quantity.grams else quantity.originalAmount ?: return false
        val converted = (toGrams(amount) as? ConversionResult.Success)?.quantity?.amount ?: return false
        // Inverting a conversion for display can differ by a few floating-point bits.
        return abs(converted - quantity.grams) <= quantity.grams * 1e-12
    }

    private fun create(grams: Double, amount: Double) = RecipeQuantity(
        grams, unit.key, amount.takeUnless { unit == UnitDefinition.GRAM }, evidenceId
    )

    companion object {
        val GRAMS = RecipeMeasure(UnitDefinition.GRAM, "g")

        fun default(options: List<RecipeMeasure>): RecipeMeasure =
            options.firstOrNull { it.isDefault } ?: GRAMS
    }
}
