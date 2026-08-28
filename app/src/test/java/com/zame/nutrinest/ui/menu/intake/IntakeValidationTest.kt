package com.zame.nutrinest.ui.menu.intake

import com.zame.nutrinest.data.local.entity.NutritionValues
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntakeValidationTest {

    @Test
    fun `accepts a normal intake entry`() {
        assertTrue(isValidIntake("Snack", NutritionValues(250.0, 30.0, 8.0, 5.0)))
    }

    @Test
    fun `rejects values that would corrupt nutrition totals`() {
        assertFalse(isValidIntake("Snack", NutritionValues(Double.NaN, 0.0, 0.0, 0.0)))
        assertFalse(isValidIntake("Snack", NutritionValues(250.0, -1.0, 0.0, 0.0)))
        assertFalse(isValidIntake("Snack", NutritionValues(250.0, Double.POSITIVE_INFINITY, 0.0, 0.0)))
        assertFalse(isValidIntake(" ", NutritionValues(250.0, 0.0, 0.0, 0.0)))
    }
}
