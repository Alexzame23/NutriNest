package com.zame.nutrinest.domain.nutrition

import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.IngredientPortionEntity
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.NutritionValues
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class FoodQuantityConversionsTest {
    @Test fun `imperial mass uses exact standard factors and preserves the net basis`() {
        val pound = Quantity(1.0, UnitDefinition.POUND, MassBasis.NET)
        val grams = success(FoodQuantityConversions.convert(pound, UnitDefinition.GRAM))
        assertEquals(Quantity(453.59237, UnitDefinition.GRAM, MassBasis.NET), grams.quantity)
        assertEquals(16.0, amount(FoodQuantityConversions.convert(pound, UnitDefinition.OUNCE)), 1e-12)
        assertTrue(grams.appliedFactors.isEmpty())
    }

    @Test fun `a household measure with both volume and weight evidence is ambiguous`() {
        val cup = UnitDefinition.CUP
        val volume = FoodConversionFactor.HouseholdVolume(7, cup, 240.0, evidence)
        val mass = weight(unit = cup, grams = 216.0)
        val quantity = Quantity(0.5, cup)
        for (factors in listOf(listOf(volume, mass), listOf(mass, volume))) {
            val context = IngredientConversionContext(7, factors)
            error(ConversionError.AMBIGUOUS_FACTOR, FoodQuantityConversions.toGrams(quantity, context))
            error(ConversionError.AMBIGUOUS_FACTOR,
                FoodQuantityConversions.convert(quantity, UnitDefinition.MILLILITRE, context))
        }
    }
    // All food numbers below are synthetic FIXTURES, never reference weights/densities.
    private val evidence = ConversionEvidence(
        "fixture-evidence", "test fixture, not food reference data", "fixture-v1",
        ConversionConfidence.HIGH, EvidenceStatus.ACCEPTED, "Synthetic edible sample for unit tests"
    )
    private val egg = UnitDefinition.count("fixture-egg-edible")
    private val portion = UnitDefinition.portion("fixture-bowl")
    private fun density(value: Double = 1.07) = FoodConversionFactor.Density(7, value, MassBasis.EDIBLE, evidence)
    private fun weight(unit: UnitDefinition = egg, grams: Double = 43.7, basis: MassBasis = MassBasis.EDIBLE) =
        FoodConversionFactor.UnitWeight(7, unit, grams, basis, evidence)
    private fun context(vararg factors: FoodConversionFactor) = IngredientConversionContext(7, factors.toList())
    private fun success(result: ConversionResult) = result as ConversionResult.Success
    private fun amount(result: ConversionResult) = success(result).quantity.amount
    private fun error(expected: ConversionError, result: ConversionResult) =
        assertEquals(ConversionResult.Failure(expected), result)
    private fun grams(q: Quantity, vararg factors: FoodConversionFactor) =
        FoodQuantityConversions.toGrams(q, context(*factors))

    @Test fun `g and kg convert both ways without food evidence`() {
        assertEquals(1234.5, amount(grams(Quantity(1.2345, UnitDefinition.KILOGRAM))), 0.0)
        assertEquals(1.2345, amount(FoodQuantityConversions.convert(
            Quantity(1234.5, UnitDefinition.GRAM), UnitDefinition.KILOGRAM)), 1e-12)
    }

    @Test fun `ml and L convert without density`() {
        assertEquals(1234.5, amount(FoodQuantityConversions.convert(
            Quantity(1.2345, UnitDefinition.LITRE), UnitDefinition.MILLILITRE)), 0.0)
        assertEquals(1.2345, amount(FoodQuantityConversions.convert(
            Quantity(1234.5, UnitDefinition.MILLILITRE), UnitDefinition.LITRE)), 1e-12)
    }

    @Test fun `volume to edible mass records density and its provenance`() {
        val result = success(grams(Quantity(123.45, UnitDefinition.MILLILITRE), density()))
        assertEquals(132.0915, result.quantity.amount, 1e-12)
        assertEquals(listOf(density()), result.appliedFactors)
        assertEquals("fixture-v1", result.appliedFactors.single().evidence.version)
        assertEquals(ConversionConfidence.HIGH, result.appliedFactors.single().evidence.confidence)
        assertEquals(MassBasis.EDIBLE, result.quantity.massBasis)
        assertEquals("1", result.engineVersion)
    }

    @Test fun `unknown density fails in both directions`() {
        error(ConversionError.MISSING_DENSITY, grams(Quantity(1.0, UnitDefinition.LITRE)))
        error(ConversionError.MISSING_DENSITY, FoodQuantityConversions.convert(
            Quantity(100.0, UnitDefinition.GRAM), UnitDefinition.LITRE))
    }

    @Test fun `edible egg fixture uses edible unit weight in both directions`() {
        assertEquals(87.4, amount(grams(Quantity(2.0, egg), weight())), 1e-12)
        assertEquals(2.0, amount(FoodQuantityConversions.convert(
            Quantity(87.4, UnitDefinition.GRAM), egg, context(weight()))), 1e-12)
        error(ConversionError.MISSING_UNIT_FACTOR, grams(Quantity(2.0, egg)))
    }

    @Test fun `gross egg needs an explicit edible yield and never treats shell as nutrients`() {
        val grossEgg = UnitDefinition.count("fixture-egg-with-shell")
        val grossWeight = weight(grossEgg, 61.0, MassBasis.GROSS)
        val q = Quantity(2.0, grossEgg, MassBasis.GROSS)
        error(ConversionError.MISSING_EDIBLE_YIELD, grams(q, grossWeight))
        val yield = FoodConversionFactor.EdibleYield(7, MassBasis.GROSS, 0.79, evidence)
        val result = success(grams(q, grossWeight, yield))
        assertEquals(96.38, result.quantity.amount, 1e-12)
        assertEquals(listOf(grossWeight, yield), result.appliedFactors)
        assertEquals(2.0, amount(FoodQuantityConversions.convert(
            result.quantity, grossEgg, context(grossWeight, yield), MassBasis.GROSS)), 1e-12)
        error(ConversionError.INCOMPATIBLE_UNIT, grams(Quantity(2.0, grossEgg), grossWeight))
    }

    @Test fun `net and drained mass are not automatically edible or gross`() {
        for (basis in listOf(MassBasis.NET, MassBasis.DRAINED, MassBasis.GROSS)) {
            val q = Quantity(200.0, UnitDefinition.GRAM, basis)
            error(ConversionError.MISSING_EDIBLE_YIELD, grams(q))
            val yield = FoodConversionFactor.EdibleYield(7, basis, 0.62, evidence)
            assertEquals(124.0, amount(grams(q, yield)), 1e-12)
        }
        error(ConversionError.UNKNOWN_MASS_BASIS, grams(Quantity(200.0, UnitDefinition.GRAM, MassBasis.UNKNOWN)))
    }

    @Test fun `ingredient portions use their exact factor without rounding`() {
        val factor = weight(portion, 127.123456789)
        assertEquals(317.8086419725, amount(grams(Quantity(2.5, portion), factor)), 1e-10)
        assertEquals(2.5, amount(FoodQuantityConversions.convert(
            Quantity(317.8086419725, UnitDefinition.GRAM), portion, context(factor))), 1e-12)
    }

    @Test fun `household measures need explicit volume or ingredient weight`() {
        val spoon = UnitDefinition.TABLESPOON
        val volume = FoodConversionFactor.HouseholdVolume(7, spoon, 12.3, evidence)
        error(ConversionError.MISSING_UNIT_FACTOR, grams(Quantity(1.0, spoon), density()))
        error(ConversionError.MISSING_DENSITY, grams(Quantity(1.0, spoon), volume))
        assertEquals(26.322, amount(grams(Quantity(2.0, spoon), volume, density())), 1e-12)
        assertEquals(24.6, amount(FoodQuantityConversions.convert(
            Quantity(2.0, spoon), UnitDefinition.MILLILITRE, context(volume))), 1e-12)
        assertEquals(2.0, amount(FoodQuantityConversions.convert(
            Quantity(24.6, UnitDefinition.MILLILITRE), spoon, context(volume))), 1e-12)
        assertEquals(18.8, amount(grams(Quantity(2.0, spoon), weight(spoon, 9.4))), 1e-12)
    }

    @Test fun `all supported dimensions round trip within relative tolerance`() {
        val cup = FoodConversionFactor.HouseholdVolume(7, UnitDefinition.CUP, 213.7, evidence)
        val ctx = context(density(), weight(), weight(portion, 113.27), cup)
        val units = listOf(UnitDefinition.GRAM, UnitDefinition.KILOGRAM, UnitDefinition.OUNCE,
            UnitDefinition.POUND, UnitDefinition.MILLILITRE, UnitDefinition.LITRE, egg, portion, UnitDefinition.CUP)
        for (unit in units) for (value in listOf(1e-8, 0.1234567890123, 2.75, 12345.6789)) {
            val mass = success(FoodQuantityConversions.toGrams(Quantity(value, unit), ctx)).quantity
            val back = amount(FoodQuantityConversions.convert(mass, unit, ctx))
            assertEquals("${unit.key}: $value", value, back, value * 1e-12)
        }
    }

    @Test fun `invalid amounts fail even for identity conversions`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -0.0, -1.0)) {
            error(ConversionError.INVALID_AMOUNT, grams(Quantity(value, UnitDefinition.GRAM)))
            error(ConversionError.INVALID_AMOUNT, FoodQuantityConversions.convert(Quantity(value, egg), egg))
        }
    }

    @Test fun `all factor kinds reject invalid numbers`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0)) {
            error(ConversionError.INVALID_FACTOR, grams(Quantity(1.0, UnitDefinition.LITRE), density(value)))
            error(ConversionError.INVALID_FACTOR, grams(Quantity(1.0, egg), weight(grams = value)))
            val volume = FoodConversionFactor.HouseholdVolume(7, UnitDefinition.CUP, value, evidence)
            error(ConversionError.INVALID_FACTOR, grams(Quantity(1.0, UnitDefinition.CUP), volume, density()))
            val yield = FoodConversionFactor.EdibleYield(7, MassBasis.GROSS, value, evidence)
            error(ConversionError.INVALID_FACTOR, grams(Quantity(1.0, UnitDefinition.GRAM, MassBasis.GROSS), yield))
        }
        error(ConversionError.INVALID_FACTOR, grams(Quantity(1.0, UnitDefinition.GRAM, MassBasis.NET),
            FoodConversionFactor.EdibleYield(7, MassBasis.NET, 1.01, evidence)))
    }

    @Test fun `overflow and underflow cannot produce valid quantities`() {
        error(ConversionError.NUMERIC_OUT_OF_RANGE, grams(Quantity(Double.MAX_VALUE, UnitDefinition.KILOGRAM)))
        error(ConversionError.NUMERIC_OUT_OF_RANGE, FoodQuantityConversions.convert(
            Quantity(Double.MIN_VALUE, UnitDefinition.GRAM), UnitDefinition.KILOGRAM))
    }

    @Test fun `unreviewed rejected and undocumented factors fail`() {
        for (status in listOf(EvidenceStatus.UNVERIFIED, EvidenceStatus.REJECTED)) {
            error(ConversionError.UNVERIFIED_EVIDENCE, grams(Quantity(1.0, egg),
                weight().copy(evidence = evidence.copy(status = status))))
        }
        error(ConversionError.UNVERIFIED_EVIDENCE, grams(Quantity(1.0, egg),
            weight().copy(evidence = evidence.copy(confidence = ConversionConfidence.UNVERIFIED))))
        for (invalid in listOf(evidence.copy(id = ""), evidence.copy(source = " "),
            evidence.copy(version = ""), evidence.copy(conditions = ""))) {
            error(ConversionError.INVALID_EVIDENCE, grams(Quantity(1.0, egg), weight().copy(evidence = invalid)))
        }
    }

    @Test fun `factors cannot leak across ingredients or unit keys`() {
        error(ConversionError.INGREDIENT_MISMATCH, grams(Quantity(1.0, egg), weight().copy(ingredientId = 8)))
        error(ConversionError.MISSING_UNIT_FACTOR, grams(Quantity(1.0, UnitDefinition.count("other-egg")), weight()))
        error(ConversionError.INCOMPATIBLE_UNIT, grams(Quantity(1.0, UnitDefinition.LITRE),
            density().copy(massBasis = MassBasis.GROSS)))
        error(ConversionError.INCOMPATIBLE_UNIT, grams(Quantity(1.0, egg),
            FoodConversionFactor.HouseholdVolume(7, egg, 100.0, evidence), density()))
    }

    @Test fun `multiple candidates require an explicit selection`() {
        error(ConversionError.AMBIGUOUS_FACTOR, grams(Quantity(1.0, egg), weight(), weight(grams = 50.0)))
        error(ConversionError.AMBIGUOUS_FACTOR, grams(Quantity(1.0, UnitDefinition.LITRE), density(), density(1.03)))
    }

    @Test fun `unit keys preserve legacy enums and reject unknown or malformed definitions`() {
        for (unit in MeasureConversions.MeasureUnit.entries) {
            assertEquals(unit.name, UnitDefinition.fromLegacyKey(unit.name)?.key)
        }
        assertNull(UnitDefinition.fromLegacyKey("unknown"))
        error(ConversionError.INVALID_UNIT, grams(Quantity(1.0, UnitDefinition.count(" "))))
        error(ConversionError.INVALID_UNIT, grams(Quantity(1.0, UnitDefinition.GRAM.copy(baseMultiplier = 42.0))))
    }

    private fun ingredient(density: Double? = null) = IngredientEntity(
        id = 7, name = "Synthetic fixture", categoryKey = "EGGS",
        nutrition = NutritionValues(123.4, 4.3, 6.2, 12.6), isLiquid = true, densityGml = density
    )

    @Test fun `schema v8 adapter does not promote unverified legacy density`() {
        error(ConversionError.MISSING_DENSITY, IngredientQuantityConversions.toGrams(100.0, "MILLILITRE", ingredient()))
        error(ConversionError.UNVERIFIED_EVIDENCE, IngredientQuantityConversions.toGrams(100.0, "MILLILITRE", ingredient(1.07)))
        error(ConversionError.INVALID_UNIT, IngredientQuantityConversions.toGrams(100.0, "typo", ingredient()))
        assertEquals(1000.0, amount(IngredientQuantityConversions.toGrams(1.0, "KILOGRAM", ingredient())), 0.0)
    }

    @Test fun `portion adapter keeps ingredient grams and explicit basis`() {
        val row = IngredientPortionEntity(23, 7, "Fixture", "Fixture", 71.23, "UNIT")
        val factor = IngredientQuantityConversions.portionFactor(row, egg, MassBasis.EDIBLE, evidence)
        assertEquals(142.46, amount(grams(Quantity(2.0, egg), factor)), 1e-12)
        assertEquals(7L, factor.ingredientId)
        val unknown = IngredientQuantityConversions.portionFactor(row, egg, MassBasis.UNKNOWN, evidence)
        error(ConversionError.INCOMPATIBLE_UNIT, grams(Quantity(1.0, egg), unknown))
    }

    @Suppress("DEPRECATION")
    @Test fun `historical grams display and nutrient arithmetic stay unchanged`() {
        val food = ingredient()
        val storedGrams = 137.123456789
        val before = food.nutrition.forGrams(storedGrams)
        error(ConversionError.MISSING_DENSITY, IngredientQuantityConversions.toGrams(100.0, "MILLILITRE", food))
        val f = storedGrams / 100.0
        assertEquals(NutritionValues(123.4 * f, 4.3 * f, 6.2 * f, 12.6 * f), before)
        assertEquals(before, food.nutrition.forGrams(storedGrams))
        assertEquals(100.0, MeasureConversions.toGrams(100.0, MeasureConversions.MeasureUnit.MILLILITRE, true, null)!!, 0.0)
        assertEquals("0.137 L", MeasureConversions.formatRecipeQuantity(storedGrams,
            MeasureConversions.MeasureUnit.LITRE, true, null, Locale.UK))
    }

    @Test fun `stored recipe nutrition ignores unit keys and replacement conversion evidence`() {
        val food = ingredient()
        val stored = IngredientWithQuantity(food, 137.5, "LITRE")
        val expected = NutritionValues(169.675, 5.9125, 8.525, 17.325)
        assertEquals(expected.kcal, stored.nutrition.kcal, 1e-12)
        assertEquals(expected.carbs, stored.nutrition.carbs, 1e-12)
        assertEquals(expected.fats, stored.nutrition.fats, 1e-12)
        assertEquals(expected.protein, stored.nutrition.protein, 1e-12)
        assertEquals(stored.nutrition, stored.copy(quantityUnit = "UNKNOWN_HISTORICAL_KEY").nutrition)
        assertEquals(stored.nutrition, stored.copy(ingredient = food.copy(densityGml = 1.42)).nutrition)
        assertEquals(137.5, stored.grams, 0.0)
        // Zero still has its historical meaning in nutrition aggregation, though new input rejects it.
        assertEquals(NutritionValues(), food.nutrition.forGrams(0.0))
    }
}
