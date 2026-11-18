package com.zame.nutrinest.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.local.entity.RecipeIngredientInput
import com.zame.nutrinest.data.repository.LocalIngredientRepository
import com.zame.nutrinest.domain.nutrition.RecipeMeasure
import com.zame.nutrinest.domain.nutrition.RecipeQuantity
import com.zame.nutrinest.ui.recipes.RecipeQuantityEditor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeNaturalQuantitiesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "recipe-natural-quantities.db"
    private val eggId = 1_000_758L
    private val milkId = 1_002_493L
    private val riceId = 1_000_904L

    @Test fun catalogMeasuresPersistThroughEditsAndReopening() = runBlocking {
        context.deleteDatabase(name)
        try {
            var recipeId = 0L
            lateinit var saved: Map<Long, RecipeQuantity>
            withDatabase { database ->
                val measures = LocalIngredientRepository(database).observeRecipeMeasures().first()
                val egg = RecipeMeasure.default(measures.getValue(eggId))
                val milk = RecipeMeasure.default(measures.getValue(milkId))
                assertEquals("UNIT:egg_large_poached", egg.unit.key)
                assertEquals("MILLILITRE", milk.unit.key)
                assertNull(measures[riceId])
                assertNull(measures[1_001_009L]) // Boiled brown rice is a different ingredient.
                assertNull(measures[1_001_173L]) // A raw egg white cannot inherit the whole-egg factor.
                val dao = database.recipeDao()
                val rice = RecipeMeasure.GRAMS.enter(100.0)!!

                listOf(100.0, 200.0, 250.0).forEachIndexed { index, ml ->
                    val eggs = (index + 1).toDouble()
                    val eggQuantity = egg.enter(eggs)!!
                    val milkQuantity = milk.enter(ml)!!
                    val inputs = mapOf(eggId to eggQuantity, milkId to milkQuantity, riceId to rice)
                    val edibleMilk = listOf(103.0, 206.0, 257.5)[index]
                    assertEquals(50.0 * eggs, eggQuantity.grams, 1e-12)
                    assertEquals(edibleMilk, milkQuantity.grams, 1e-12)

                    if (index == 0) {
                        recipeId = dao.insertFullRecipe(
                            RecipeEntity(name = "Quantity round trip", category = "Main Dish", coverImagePath = "cover.jpg"),
                            inputs.map { (id, amount) -> RecipeIngredientInput(id, amount) },
                            listOf("Measure the ingredients" to "step.jpg")
                        )
                    } else {
                        assertTrue(dao.updateIngredientQuantity(recipeId, saved.getValue(eggId), RecipeIngredientInput(eggId, eggQuantity)))
                        assertTrue(dao.updateIngredientQuantity(recipeId, saved.getValue(milkId), RecipeIngredientInput(milkId, milkQuantity)))
                        assertFalse(dao.updateIngredientQuantity(recipeId, saved.getValue(milkId), RecipeIngredientInput(milkId, milkQuantity)))
                    }
                    saved = inputs
                    val rows = dao.observeIngredientsOf(recipeId).first().associateBy { it.ingredient.id }
                    assertEquals(inputs, rows.mapValues { it.value.quantity })
                    assertEquals("RAW", rows.getValue(riceId).ingredient.prepState)
                    val eggMacros = NutritionValues(kcal = 146.17, protein = 12.6, carbs = 0.3, fats = 10.602)
                    val milkMacros = NutritionValues(kcal = 65.13, protein = 3.06, carbs = 4.7, fats = 3.8)
                    val riceMacros = NutritionValues(kcal = 384.11, protein = 7.5, carbs = 78.3, fats = 2.635)
                    assertNutrition(eggMacros.forGrams(50.0 * eggs), rows.getValue(eggId).nutrition)
                    assertNutrition(milkMacros.forGrams(edibleMilk), rows.getValue(milkId).nutrition)
                    assertNutrition(riceMacros, rows.getValue(riceId).nutrition)
                    val totals = dao.observeNutritionTotals().first().single { it.recipeId == recipeId }.nutrition
                    assertNutrition(eggMacros.forGrams(50.0 * eggs) + milkMacros.forGrams(edibleMilk) + riceMacros, totals)
                }
            }
            withDatabase { database ->
                val dao = database.recipeDao()
                assertEquals(saved, dao.observeIngredientsOf(recipeId).first().associate { it.ingredient.id to it.quantity })
                assertEquals(saved.getValue(milkId), database.menuDao().getIngredientsOf(recipeId).single { it.ingredient.id == milkId }.quantity)
                val header = dao.observeRecipeWithSteps(recipeId).first()!!
                assertEquals("cover.jpg", header.recipe.coverImagePath)
                assertEquals("step.jpg", header.steps.single().imagePath)
                assertEquals("Measure the ingredients", header.steps.single().text)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun editorDoesNotReconvertUntilTheAmountChanges() = runBlocking {
        context.deleteDatabase(name)
        try {
            withDatabase { database ->
                val options = LocalIngredientRepository(database).observeRecipeMeasures().first().getValue(milkId)
                val milk = RecipeMeasure.default(options)
                val original = milk.enter(213.9876543210987)!!
                val editor = RecipeQuantityEditor(options, original)
                repeat(10) {
                    editor.select(RecipeMeasure.GRAMS)
                    assertEquals(original.grams.toRawBits(), editor.quantity!!.grams.toRawBits())
                    editor.select(milk)
                    assertEquals(original, editor.quantity)
                    assertFalse(editor.hasChanges)
                }
                editor.enter("")
                assertNull(editor.quantity)
                editor.select(RecipeMeasure.GRAMS)
                assertEquals(milk, editor.measure)
                editor.enter("250,5")
                assertEquals(milk.enter(250.5), editor.quantity)
                assertTrue(editor.hasChanges)

                val legacy = RecipeQuantity(137.123456789, "LITRE")
                val historical = RecipeQuantityEditor(options, legacy)
                assertEquals(RecipeMeasure.GRAMS, historical.measure)
                assertEquals(legacy.grams, historical.quantity!!.grams, 0.0)
                assertFalse(historical.hasChanges)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private suspend fun withDatabase(block: suspend (NutriNestDatabase) -> Unit) {
        val database = NutriNestDatabase.build(context, name)
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun assertNutrition(expected: NutritionValues, actual: NutritionValues) {
        assertEquals(expected.kcal, actual.kcal, 1e-9)
        assertEquals(expected.protein, actual.protein, 1e-9)
        assertEquals(expected.carbs, actual.carbs, 1e-9)
        assertEquals(expected.fats, actual.fats, 1e-9)
    }
}
