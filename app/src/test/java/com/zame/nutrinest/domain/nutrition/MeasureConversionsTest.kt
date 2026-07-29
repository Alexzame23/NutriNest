package com.zame.nutrinest.domain.nutrition

import com.zame.nutrinest.domain.nutrition.MeasureConversions.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureConversionsTest {

    private fun grams(amount: Double, unit: MeasureUnit, liquid: Boolean = false, density: Double? = null) =
        MeasureConversions.toGrams(amount, unit, liquid, density)

    @Test
    fun `mass units convert without a density`() {
        assertEquals(1000.0, grams(1.0, MeasureUnit.KILOGRAM)!!, 0.001)
        assertEquals(28.3495, grams(1.0, MeasureUnit.OUNCE)!!, 0.001)
        assertEquals(453.592, grams(1.0, MeasureUnit.POUND)!!, 0.001)
        assertEquals(250.0, grams(250.0, MeasureUnit.GRAM)!!, 0.001)
    }

    @Test
    fun `volume units need a density`() {
        assertNull(grams(1.0, MeasureUnit.CUP, liquid = false, density = null))
        assertNull(grams(1.0, MeasureUnit.MILLILITRE, liquid = false, density = null))
    }

    @Test
    fun `volume converts with the dataset's density`() {
        // Grapefruit juice, density 1.045 g/ml: a 200 ml glass weighs 209 g,
        // which is exactly what the dataset's household portion records.
        assertEquals(209.0, grams(200.0, MeasureUnit.MILLILITRE, true, 1.045)!!, 0.001)
    }

    @Test
    fun `kitchen measures use metric volumes`() {
        assertEquals(240.0, grams(1.0, MeasureUnit.CUP, true, 1.0)!!, 0.001)
        assertEquals(15.0, grams(1.0, MeasureUnit.TABLESPOON, true, 1.0)!!, 0.001)
        assertEquals(5.0, grams(1.0, MeasureUnit.TEASPOON, true, 1.0)!!, 0.001)
        // Olive oil is lighter than water, so a spoonful weighs less.
        assertEquals(13.65, grams(1.0, MeasureUnit.TABLESPOON, true, 0.91)!!, 0.001)
    }

    @Test
    fun `a liquid without a measured density falls back to water`() {
        assertEquals(1.0, MeasureConversions.effectiveDensity(isLiquid = true, densityGml = null))
        assertEquals(100.0, grams(100.0, MeasureUnit.MILLILITRE, liquid = true)!!, 0.001)
    }

    @Test
    fun `a solid without a density cannot be measured by volume`() {
        assertNull(MeasureConversions.effectiveDensity(isLiquid = false, densityGml = null))
    }

    @Test
    fun `a stored density wins over the liquid fallback`() {
        assertEquals(0.91, MeasureConversions.effectiveDensity(isLiquid = true, densityGml = 0.91))
    }

    @Test
    fun `round trips return the original amount`() {
        MeasureUnit.entries.forEach { unit ->
            val asGrams = MeasureConversions.toGrams(2.0, unit, isLiquid = true, densityGml = 1.03)
                ?: error("$unit should convert for a liquid")
            val back = MeasureConversions.fromGrams(asGrams, unit, isLiquid = true, densityGml = 1.03)
            assertEquals(unit.name, 2.0, back!!, 0.000001)
        }
    }

    @Test
    fun `available units hide volume for a solid with no density`() {
        val solid = MeasureConversions.availableUnits(isLiquid = false, densityGml = null)
        assertTrue(MeasureUnit.GRAM in solid)
        assertFalse(MeasureUnit.CUP in solid)
        assertFalse(MeasureUnit.MILLILITRE in solid)
    }

    @Test
    fun `available units include volume once a density is known`() {
        val liquid = MeasureConversions.availableUnits(isLiquid = true, densityGml = 1.0)
        assertTrue(MeasureUnit.entries.all { it in liquid })
    }

    @Test
    fun `weights are humanized at a kilogram`() {
        assertEquals(850.0 to MeasureUnit.GRAM, MeasureConversions.humanizeGrams(850.0))
        assertEquals(1.5 to MeasureUnit.KILOGRAM, MeasureConversions.humanizeGrams(1500.0))
        assertEquals(1.0 to MeasureUnit.KILOGRAM, MeasureConversions.humanizeGrams(1000.0))
    }
}
