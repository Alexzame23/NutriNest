package com.zame.nutrinest.domain.nutrition

import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.IngredientPortionEntity

/** Bridge from schema v8. Presence of a legacy number does not constitute accepted evidence. */
object IngredientQuantityConversions {
    fun legacyContext(ingredient: IngredientEntity): IngredientConversionContext =
        IngredientConversionContext(ingredient.id, listOfNotNull(ingredient.densityGml?.let {
            FoodConversionFactor.Density(
                ingredient.id, it, MassBasis.UNKNOWN,
                ConversionEvidence(
                    id = "legacy-density:${ingredient.id}:${ingredient.updatedAt}",
                    source = "ingredients/${ingredient.id}/densityGml",
                    version = "schema-8:row-${ingredient.updatedAt}",
                    confidence = ConversionConfidence.UNVERIFIED,
                    status = EvidenceStatus.UNVERIFIED,
                    conditions = "Legacy value; mass basis, individual source and temperature not recorded."
                )
            )
        }))

    /** Caller supplies reviewed identity, basis and evidence; no inference from UNIT or food name. */
    fun portionFactor(
        portion: IngredientPortionEntity,
        unit: UnitDefinition,
        massBasis: MassBasis,
        evidence: ConversionEvidence
    ): FoodConversionFactor.UnitWeight = FoodConversionFactor.UnitWeight(
        ingredientId = portion.ingredientId,
        unit = unit,
        gramsPerUnit = portion.grams,
        massBasis = massBasis,
        evidence = evidence
    )

    fun toGrams(amount: Double, key: String, ingredient: IngredientEntity): ConversionResult {
        val unit = UnitDefinition.fromLegacyKey(key)
            ?: return ConversionResult.Failure(ConversionError.INVALID_UNIT)
        return FoodQuantityConversions.toGrams(Quantity(amount, unit), legacyContext(ingredient))
    }

    fun fromGrams(grams: Double, key: String, ingredient: IngredientEntity): ConversionResult {
        val unit = UnitDefinition.fromLegacyKey(key)
            ?: return ConversionResult.Failure(ConversionError.INVALID_UNIT)
        return FoodQuantityConversions.convert(Quantity(grams, UnitDefinition.GRAM), unit, legacyContext(ingredient))
    }
}
