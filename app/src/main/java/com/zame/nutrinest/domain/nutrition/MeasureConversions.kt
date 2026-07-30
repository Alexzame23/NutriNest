package com.zame.nutrinest.domain.nutrition

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Converts between the units an ingredient can be measured in.
 *
 * Everything in the app is stored per 100 g, so every unit ultimately resolves
 * to grams. Mass units convert unconditionally; volume units need a density,
 * which the shipped dataset provides for all 115 of its liquids. Household
 * measures that vary by food ("Glass (200 ml)" of juice is 209 g) come from
 * `ingredient_portions` rather than from here.
 *
 * Pure and Android-free so it unit-tests directly, matching [UnitConversions]
 * in NutritionCalculator.kt which handles body weight and height.
 */
object MeasureConversions {

    enum class Family { MASS, VOLUME }

    /**
     * @param mlPerUnit millilitres per unit for [Family.VOLUME]
     * @param gramsPerUnit grams per unit for [Family.MASS]
     */
    enum class MeasureUnit(
        val family: Family,
        val gramsPerUnit: Double = 0.0,
        val mlPerUnit: Double = 0.0
    ) {
        GRAM(Family.MASS, gramsPerUnit = 1.0),
        KILOGRAM(Family.MASS, gramsPerUnit = 1_000.0),
        OUNCE(Family.MASS, gramsPerUnit = 28.349523125),
        POUND(Family.MASS, gramsPerUnit = 453.59237),
        MILLILITRE(Family.VOLUME, mlPerUnit = 1.0),
        LITRE(Family.VOLUME, mlPerUnit = 1_000.0),

        // Metric kitchen measures, matching the dataset's household portions.
        CUP(Family.VOLUME, mlPerUnit = 240.0),
        TABLESPOON(Family.VOLUME, mlPerUnit = 15.0),
        TEASPOON(Family.VOLUME, mlPerUnit = 5.0);

        val isVolume: Boolean get() = family == Family.VOLUME
    }

    /**
     * Density to use for volume conversion, or null when there is none.
     *
     * Falls back to water for a liquid with no measured density — an
     * approximation, but a far better one than refusing to convert. Imported
     * liquids always carry a real density, so this only affects user creations.
     */
    fun effectiveDensity(isLiquid: Boolean, densityGml: Double?): Double? =
        densityGml?.takeIf { it > 0 } ?: if (isLiquid) 1.0 else null

    /** Units offered for an ingredient; volume ones appear only when convertible. */
    fun availableUnits(isLiquid: Boolean, densityGml: Double?): List<MeasureUnit> {
        val convertible = effectiveDensity(isLiquid, densityGml) != null
        return MeasureUnit.entries.filter { convertible || !it.isVolume }
    }

    /** Amount in [unit] to grams, or null when the unit cannot be converted. */
    fun toGrams(amount: Double, unit: MeasureUnit, isLiquid: Boolean, densityGml: Double?): Double? =
        when (unit.family) {
            Family.MASS -> amount * unit.gramsPerUnit
            Family.VOLUME -> effectiveDensity(isLiquid, densityGml)
                ?.let { amount * unit.mlPerUnit * it }
        }

    /** Grams back into [unit], or null when the unit cannot be converted. */
    fun fromGrams(grams: Double, unit: MeasureUnit, isLiquid: Boolean, densityGml: Double?): Double? =
        when (unit.family) {
            Family.MASS -> grams / unit.gramsPerUnit
            Family.VOLUME -> effectiveDensity(isLiquid, densityGml)
                ?.takeIf { it > 0 }
                ?.let { grams / it / unit.mlPerUnit }
        }

    /**
     * Picks the tidiest way to state a weight: 850 g stays grams, 1 500 g
     * becomes 1.5 kg. Returns the value and the unit to label it with.
     */
    fun humanizeGrams(grams: Double): Pair<Double, MeasureUnit> =
        if (grams >= 1_000.0) grams / 1_000.0 to MeasureUnit.KILOGRAM
        else grams to MeasureUnit.GRAM

    /**
     * A quantity in grams, always in grams: "120 g", "37.5 g".
     *
     * For amounts inside one recipe, where grams are what a cook measures.
     */
    fun formatGrams(grams: Double, locale: Locale = Locale.getDefault()): String =
        if (grams == grams.roundToInt().toDouble()) "${grams.roundToInt()} g"
        else "%.1f g".format(locale, grams)

    /**
     * A quantity that may be large: grams below a kilo, kilograms above it.
     *
     * A week of meals for a household runs into thousands of grams, and
     * "2450 g" is not a number anyone shops with. Kilograms carry up to two
     * decimals with trailing zeros trimmed, so 2000 g reads "2 kg", 2500 g
     * "2.5 kg" and 2450 g "2.45 kg" — precise enough to weigh out, short
     * enough to scan down a shopping list.
     *
     * The symbols are hardcoded: "g" and "kg" are SI and identical in every
     * language the app ships. The number follows the locale, so a Spanish
     * device shows "2,45 kg".
     */
    fun formatWeight(grams: Double, locale: Locale = Locale.getDefault()): String {
        val (value, unit) = humanizeGrams(grams)
        return if (unit == MeasureUnit.KILOGRAM) {
            "${trimTrailingZeros("%.2f".format(locale, value))} kg"
        } else {
            formatGrams(value, locale)
        }
    }

    /** "1,50" -> "1,5" and "2,00" -> "2", whichever separator the locale uses. */
    private fun trimTrailingZeros(text: String): String =
        if (text.contains('.') || text.contains(',')) {
            text.trimEnd('0').trimEnd('.', ',')
        } else {
            text
        }
}
