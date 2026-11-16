package com.zame.nutrinest.domain.nutrition

/** Pure conversion engine. Never consults isLiquid, food names or category defaults. */
object FoodQuantityConversions {
    fun toGrams(quantity: Quantity, context: IngredientConversionContext? = null): ConversionResult =
        convert(quantity, UnitDefinition.GRAM, context, MassBasis.EDIBLE)

    fun convert(
        quantity: Quantity,
        target: UnitDefinition,
        context: IngredientConversionContext? = null,
        targetBasis: MassBasis = quantity.massBasis
    ): ConversionResult {
        if (!quantity.amount.isFinite() || quantity.amount <= 0.0) {
            return ConversionResult.Failure(ConversionError.INVALID_AMOUNT)
        }
        return try {
            Session(context).convert(quantity, target, targetBasis)
        } catch (failure: InvalidConversion) {
            ConversionResult.Failure(failure.error)
        }
    }

    private class InvalidConversion(val error: ConversionError) : RuntimeException()
    private fun fail(error: ConversionError): Nothing = throw InvalidConversion(error)

    private class Session(val context: IngredientConversionContext?) {
        private val applied = mutableListOf<FoodConversionFactor>()

        fun convert(quantity: Quantity, target: UnitDefinition, targetBasis: MassBasis): ConversionResult {
            validateUnit(quantity.unit)
            validateUnit(target)
            if (quantity.massBasis == MassBasis.UNKNOWN || targetBasis == MassBasis.UNKNOWN) {
                fail(ConversionError.UNKNOWN_MASS_BASIS)
            }
            // Dimensional conversions do not need food evidence or a detour through mass.
            val sameDimension = quantity.unit.dimension == target.dimension &&
                target.dimension in listOf(QuantityDimension.MASS, QuantityDimension.VOLUME)
            val amount = if (sameDimension && quantity.massBasis == targetBasis) {
                checked(checked(quantity.amount * quantity.unit.baseMultiplier) / target.baseMultiplier)
            } else if (quantity.massBasis == targetBasis &&
                hasVolumeDefinition(quantity.unit) && hasVolumeDefinition(target)) {
                checked(checked(quantity.amount * mlPerUnit(quantity.unit)) / mlPerUnit(target))
            } else {
                val sourceMass = checked(quantity.amount * gramsPerUnit(quantity.unit, quantity.massBasis))
                val targetMass = changeBasis(sourceMass, quantity.massBasis, targetBasis)
                checked(targetMass / gramsPerUnit(target, targetBasis))
            }
            return ConversionResult.Success(Quantity(amount, target, targetBasis), applied.toList())
        }

        private fun hasVolumeDefinition(unit: UnitDefinition): Boolean =
            unit.dimension == QuantityDimension.VOLUME ||
                (unit.dimension == QuantityDimension.HOUSEHOLD && context?.factors.orEmpty().any {
                    it is FoodConversionFactor.HouseholdVolume && it.unit == unit
                })

        private fun mlPerUnit(unit: UnitDefinition): Double {
            if (unit.dimension == QuantityDimension.VOLUME) return unit.baseMultiplier
            val factor = select(context?.factors.orEmpty().filter {
                (it is FoodConversionFactor.HouseholdVolume && it.unit == unit) ||
                    (it is FoodConversionFactor.UnitWeight && it.unit == unit)
            }, ConversionError.MISSING_UNIT_FACTOR)
            return (factor as? FoodConversionFactor.HouseholdVolume)?.mlPerUnit
                ?: fail(ConversionError.INCOMPATIBLE_UNIT)
        }

        private fun validateUnit(unit: UnitDefinition) {
            val known = UnitDefinition.fromLegacyKey(unit.key)
            val valid = if (known != null) unit == known else when (unit.dimension) {
                QuantityDimension.COUNT -> unit.key.startsWith("UNIT:") && unit.key.removePrefix("UNIT:").isNotBlank()
                QuantityDimension.PORTION -> unit.key.startsWith("PORTION:") && unit.key.removePrefix("PORTION:").isNotBlank()
                QuantityDimension.HOUSEHOLD -> unit.key.startsWith("HOUSEHOLD:") && unit.key.removePrefix("HOUSEHOLD:").isNotBlank()
                else -> false
            }
            if (!valid || !unit.baseMultiplier.isFinite() || unit.baseMultiplier <= 0 ||
                (known == null && unit.baseMultiplier != 1.0)) fail(ConversionError.INVALID_UNIT)
        }

        private fun gramsPerUnit(unit: UnitDefinition, basis: MassBasis): Double = when (unit.dimension) {
            QuantityDimension.MASS -> unit.baseMultiplier
            QuantityDimension.VOLUME -> checked(unit.baseMultiplier * density(basis))
            QuantityDimension.COUNT, QuantityDimension.PORTION, QuantityDimension.HOUSEHOLD -> {
                val candidates = context?.factors.orEmpty().filter {
                    (it is FoodConversionFactor.UnitWeight && it.unit == unit) ||
                        (it is FoodConversionFactor.HouseholdVolume && it.unit == unit)
                }
                when (val factor = select(candidates, ConversionError.MISSING_UNIT_FACTOR)) {
                    is FoodConversionFactor.UnitWeight -> {
                        if (factor.massBasis != basis) fail(ConversionError.INCOMPATIBLE_UNIT)
                        factor.gramsPerUnit
                    }
                    is FoodConversionFactor.HouseholdVolume -> {
                        if (unit.dimension != QuantityDimension.HOUSEHOLD) fail(ConversionError.INCOMPATIBLE_UNIT)
                        checked(factor.mlPerUnit * density(basis))
                    }
                    else -> fail(ConversionError.INCOMPATIBLE_UNIT)
                }
            }
        }

        private fun density(basis: MassBasis): Double {
            val density = select(context?.factors.orEmpty().filterIsInstance<FoodConversionFactor.Density>(),
                ConversionError.MISSING_DENSITY) as FoodConversionFactor.Density
            if (density.massBasis != basis) fail(ConversionError.INCOMPATIBLE_UNIT)
            return density.gramsPerMl
        }

        private fun changeBasis(mass: Double, from: MassBasis, to: MassBasis): Double {
            if (from == to) return mass
            val edible = if (from == MassBasis.EDIBLE) mass else checked(mass * edibleYield(from))
            return if (to == MassBasis.EDIBLE) edible else checked(edible / edibleYield(to))
        }

        private fun edibleYield(basis: MassBasis): Double =
            (select(context?.factors.orEmpty().filterIsInstance<FoodConversionFactor.EdibleYield>()
                .filter { it.fromBasis == basis }, ConversionError.MISSING_EDIBLE_YIELD)
                as FoodConversionFactor.EdibleYield).edibleFraction

        private fun select(candidates: List<FoodConversionFactor>, missing: ConversionError): FoodConversionFactor {
            if (candidates.isEmpty()) fail(missing)
            if (candidates.size != 1) fail(ConversionError.AMBIGUOUS_FACTOR)
            val factor = candidates.single()
            if (context == null || factor.ingredientId != context.ingredientId) fail(ConversionError.INGREDIENT_MISMATCH)
            val number = when (factor) {
                is FoodConversionFactor.Density -> factor.gramsPerMl
                is FoodConversionFactor.UnitWeight -> factor.gramsPerUnit
                is FoodConversionFactor.HouseholdVolume -> factor.mlPerUnit
                is FoodConversionFactor.EdibleYield -> factor.edibleFraction
            }
            if (!number.isFinite() || number <= 0.0) fail(ConversionError.INVALID_FACTOR)
            if (factor is FoodConversionFactor.EdibleYield &&
                (number > 1.0 || factor.fromBasis in listOf(MassBasis.EDIBLE, MassBasis.UNKNOWN))) {
                fail(ConversionError.INVALID_FACTOR)
            }
            val evidence = factor.evidence
            if (listOf(evidence.id, evidence.source, evidence.version, evidence.conditions).any { it.isBlank() }) {
                fail(ConversionError.INVALID_EVIDENCE)
            }
            if (evidence.status != EvidenceStatus.ACCEPTED || evidence.confidence == ConversionConfidence.UNVERIFIED) {
                fail(ConversionError.UNVERIFIED_EVIDENCE)
            }
            if (factor !in applied) applied += factor
            return factor
        }

        private fun checked(value: Double): Double {
            if (!value.isFinite() || value <= 0.0) fail(ConversionError.NUMERIC_OUT_OF_RANGE)
            return value
        }
    }
}
