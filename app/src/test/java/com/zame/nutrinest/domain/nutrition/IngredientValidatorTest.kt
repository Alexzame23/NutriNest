package com.zame.nutrinest.domain.nutrition

import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.NutrientTags
import com.zame.nutrinest.domain.nutrition.IngredientValidator.Severity
import com.zame.nutrinest.domain.nutrition.IngredientValidator.ValidationCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientValidatorTest {

    /** Olive oil, straight from the dataset — should pass cleanly. */
    private fun oliveOil() = IngredientValidator.Draft(
        name = "Olive oil",
        categoryKey = IngredientCategories.FATS_OILS,
        kcal = 899.0,
        carbs = 0.0,
        fats = 99.9,
        protein = 0.0
    )

    private fun IngredientValidator.Result.codes() = issues.map { it.code }

    @Test
    fun `a real ingredient passes`() {
        val result = IngredientValidator.validate(oliveOil())
        assertTrue(result.issues.toString(), result.issues.isEmpty())
        assertTrue(result.canSave)
    }

    @Test
    fun `required fields are reported`() {
        val result = IngredientValidator.validate(
            IngredientValidator.Draft(
                name = "  ",
                categoryKey = null,
                kcal = null,
                carbs = null,
                fats = null,
                protein = null
            )
        )
        assertTrue(ValidationCode.NAME_REQUIRED in result.codes())
        assertTrue(ValidationCode.CATEGORY_REQUIRED in result.codes())
        assertTrue(ValidationCode.KCAL_REQUIRED in result.codes())
        assertTrue(ValidationCode.CARBS_INVALID in result.codes())
        assertFalse(result.canSave)
    }

    @Test
    fun `negative macros are rejected`() {
        val result = IngredientValidator.validate(oliveOil().copy(protein = -1.0))
        assertTrue(ValidationCode.PROTEIN_INVALID in result.codes())
        assertFalse(result.canSave)
    }

    @Test
    fun `a macro above 100 g per 100 g is impossible`() {
        val result = IngredientValidator.validate(oliveOil().copy(carbs = 120.0))
        assertTrue(ValidationCode.GRAMS_EXCEED_100 in result.codes())
        assertFalse(result.canSave)
    }

    @Test
    fun `calories above pure fat are impossible`() {
        val result = IngredientValidator.validate(oliveOil().copy(kcal = 2000.0))
        assertTrue(ValidationCode.KCAL_IMPLAUSIBLE in result.codes())
        assertFalse(result.canSave)
    }

    @Test
    fun `nutrients cannot outweigh the 100 g they are measured in`() {
        val result = IngredientValidator.validate(
            oliveOil().copy(carbs = 50.0, fats = 50.0, protein = 30.0, kcal = 770.0)
        )
        assertTrue(ValidationCode.MASS_BALANCE_OUT_OF_RANGE in result.codes())
        assertFalse(result.canSave)
    }

    @Test
    fun `water and fibre count towards the mass balance`() {
        // 20 + 5 + 10 + 5 fibre + 70 water = 110 g.
        val result = IngredientValidator.validate(
            oliveOil().copy(
                carbs = 20.0, fats = 5.0, protein = 10.0, kcal = 175.0,
                extraNutrients = mapOf(NutrientTags.FIBRE to 5.0, NutrientTags.WATER to 70.0)
            )
        )
        assertTrue(ValidationCode.MASS_BALANCE_OUT_OF_RANGE in result.codes())
    }

    @Test
    fun `saturated fat cannot exceed total fat`() {
        val result = IngredientValidator.validate(
            oliveOil().copy(extraNutrients = mapOf(NutrientTags.SAT_FAT to 150.0))
        )
        assertTrue(ValidationCode.SATFAT_EXCEEDS_FAT in result.codes())
        assertFalse(result.canSave)
    }

    @Test
    fun `sugars cannot exceed carbohydrate`() {
        val result = IngredientValidator.validate(
            oliveOil().copy(
                carbs = 10.0, fats = 0.0, kcal = 40.0,
                extraNutrients = mapOf(NutrientTags.SUGARS to 20.0)
            )
        )
        assertTrue(ValidationCode.SUGARS_EXCEED_CARBS in result.codes())
    }

    @Test
    fun `starch cannot exceed carbohydrate`() {
        val result = IngredientValidator.validate(
            oliveOil().copy(
                carbs = 10.0, fats = 0.0, kcal = 40.0,
                extraNutrients = mapOf(NutrientTags.STARCH to 20.0)
            )
        )
        assertTrue(ValidationCode.STARCH_EXCEEDS_CARBS in result.codes())
    }

    @Test
    fun `calories that disagree with the macros only warn`() {
        // Macros work out to ~400 kcal; 40 is off by an order of magnitude.
        val result = IngredientValidator.validate(
            oliveOil().copy(kcal = 40.0, carbs = 0.0, fats = 44.0, protein = 4.0)
        )
        assertTrue(ValidationCode.ENERGY_MISMATCH in result.codes())
        assertEquals(Severity.WARNING, result.issues.first {
            it.code == ValidationCode.ENERGY_MISMATCH
        }.severity)
        assertTrue("a warning must not block saving", result.canSave)
        assertTrue(result.needsConfirmation)
    }

    @Test
    fun `small energy differences are tolerated`() {
        // Atwater says 899.1 for olive oil; the dataset reports 899.
        val result = IngredientValidator.validate(oliveOil())
        assertFalse(ValidationCode.ENERGY_MISMATCH in result.codes())
    }

    @Test
    fun `near-zero energy foods are not flagged`() {
        val water = IngredientValidator.Draft(
            name = "Water",
            categoryKey = IngredientCategories.BEVERAGES,
            kcal = 0.0, carbs = 0.0, fats = 0.0, protein = 0.0
        )
        assertFalse(ValidationCode.ENERGY_MISMATCH in IngredientValidator.validate(water).codes())
    }

    @Test
    fun `serving size and density are range checked`() {
        val result = IngredientValidator.validate(
            oliveOil().copy(servingSizeG = 0.0, densityGml = 9.0)
        )
        assertTrue(ValidationCode.SERVING_SIZE_INVALID in result.codes())
        assertTrue(ValidationCode.DENSITY_INVALID in result.codes())
    }

    @Test
    fun `honey density is accepted`() {
        val result = IngredientValidator.validate(oliveOil().copy(densityGml = 1.42))
        assertFalse(ValidationCode.DENSITY_INVALID in result.codes())
    }

    @Test
    fun `negative micronutrients are rejected`() {
        val result = IngredientValidator.validate(
            oliveOil().copy(extraNutrients = mapOf("VITC" to -3.0))
        )
        assertTrue(ValidationCode.NUTRIENT_NEGATIVE in result.codes())
        assertFalse(result.canSave)
    }

    @Test
    fun `atwater follows the EU energy factors`() {
        // 10 g protein + 20 g carbs + 5 g fat + 2 g fibre = 40 + 80 + 45 + 4.
        assertEquals(
            169.0,
            IngredientValidator.atwaterKcal(protein = 10.0, carbs = 20.0, fats = 5.0, fibre = 2.0),
            0.001
        )
    }

    @Test
    fun `fields in error are reported for highlighting`() {
        val result = IngredientValidator.validate(oliveOil().copy(name = "", carbs = -1.0))
        assertTrue(IngredientValidator.Field.NAME in result.fieldsInError())
        assertTrue(IngredientValidator.Field.CARBS in result.fieldsInError())
    }
}
