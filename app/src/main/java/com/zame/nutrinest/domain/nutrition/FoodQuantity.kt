package com.zame.nutrinest.domain.nutrition

/** The basis of a measured mass; net contents need not all be edible. */
enum class MassBasis { EDIBLE, GROSS, NET, DRAINED, UNKNOWN }

enum class QuantityDimension { MASS, VOLUME, COUNT, PORTION, HOUSEHOLD }

/** Keys of existing measures remain identical to MeasureUnit.name. */
data class UnitDefinition(
    val key: String,
    val dimension: QuantityDimension,
    val baseMultiplier: Double = 1.0
) {
    companion object {
        val GRAM = UnitDefinition("GRAM", QuantityDimension.MASS)
        val KILOGRAM = UnitDefinition("KILOGRAM", QuantityDimension.MASS, 1_000.0)
        val OUNCE = UnitDefinition("OUNCE", QuantityDimension.MASS, 28.349523125)
        val POUND = UnitDefinition("POUND", QuantityDimension.MASS, 453.59237)
        val MILLILITRE = UnitDefinition("MILLILITRE", QuantityDimension.VOLUME)
        val LITRE = UnitDefinition("LITRE", QuantityDimension.VOLUME, 1_000.0)
        // No universal cup/spoon volume is assumed by the validated engine.
        val CUP = UnitDefinition("CUP", QuantityDimension.HOUSEHOLD)
        val TABLESPOON = UnitDefinition("TABLESPOON", QuantityDimension.HOUSEHOLD)
        val TEASPOON = UnitDefinition("TEASPOON", QuantityDimension.HOUSEHOLD)

        fun count(key: String) = UnitDefinition("UNIT:$key", QuantityDimension.COUNT)
        fun portion(key: String) = UnitDefinition("PORTION:$key", QuantityDimension.PORTION)
        fun household(key: String) = UnitDefinition("HOUSEHOLD:$key", QuantityDimension.HOUSEHOLD)

        /** Unknown persisted keys must be reported, never silently interpreted as grams. */
        fun fromLegacyKey(key: String): UnitDefinition? = listOf(
            GRAM, KILOGRAM, OUNCE, POUND, MILLILITRE, LITRE, CUP, TABLESPOON, TEASPOON
        ).firstOrNull { it.key == key }
    }
}

data class Quantity(
    val amount: Double,
    val unit: UnitDefinition,
    val massBasis: MassBasis = MassBasis.EDIBLE
)

enum class ConversionConfidence { HIGH, MEDIUM, LOW, UNVERIFIED }
enum class EvidenceStatus { ACCEPTED, UNVERIFIED, REJECTED }

/** Version is the evidence/snapshot version, not an inferred BEDCA release. */
data class ConversionEvidence(
    val id: String,
    val source: String,
    val version: String,
    val confidence: ConversionConfidence,
    val status: EvidenceStatus,
    val conditions: String
)

/** All food-dependent factors belong to one concrete ingredient snapshot. */
sealed interface FoodConversionFactor {
    val ingredientId: Long
    val evidence: ConversionEvidence

    data class Density(
        override val ingredientId: Long,
        val gramsPerMl: Double,
        val massBasis: MassBasis,
        override val evidence: ConversionEvidence
    ) : FoodConversionFactor

    data class UnitWeight(
        override val ingredientId: Long,
        val unit: UnitDefinition,
        val gramsPerUnit: Double,
        val massBasis: MassBasis,
        override val evidence: ConversionEvidence
    ) : FoodConversionFactor

    data class HouseholdVolume(
        override val ingredientId: Long,
        val unit: UnitDefinition,
        val mlPerUnit: Double,
        override val evidence: ConversionEvidence
    ) : FoodConversionFactor

    /** Explicit edible fraction of gross, net or drained mass, in (0, 1]. */
    data class EdibleYield(
        override val ingredientId: Long,
        val fromBasis: MassBasis,
        val edibleFraction: Double,
        override val evidence: ConversionEvidence
    ) : FoodConversionFactor
}

data class IngredientConversionContext(
    val ingredientId: Long,
    val factors: List<FoodConversionFactor> = emptyList()
)

enum class ConversionError {
    INVALID_AMOUNT, INVALID_UNIT, INCOMPATIBLE_UNIT, UNKNOWN_MASS_BASIS,
    MISSING_DENSITY, MISSING_UNIT_FACTOR, MISSING_EDIBLE_YIELD,
    INVALID_FACTOR, INVALID_EVIDENCE, UNVERIFIED_EVIDENCE, INGREDIENT_MISMATCH,
    AMBIGUOUS_FACTOR, NUMERIC_OUT_OF_RANGE
}

sealed interface ConversionResult {
    /** Full factor snapshots are returned for audit/persistence by the caller. No rounding. */
    data class Success(
        val quantity: Quantity,
        val appliedFactors: List<FoodConversionFactor> = emptyList(),
        val engineVersion: String = "1"
    ) : ConversionResult

    data class Failure(val error: ConversionError) : ConversionResult
}
