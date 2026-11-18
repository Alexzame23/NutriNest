package com.zame.nutrinest.ui.menu

import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.MealCourse
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.local.entity.ShoppingItemWithIngredient
import com.zame.nutrinest.domain.nutrition.ConversionConfidence
import com.zame.nutrinest.domain.nutrition.ConversionEvidence
import com.zame.nutrinest.domain.nutrition.EvidenceStatus
import com.zame.nutrinest.domain.nutrition.FoodConversionFactor
import com.zame.nutrinest.domain.nutrition.MassBasis
import com.zame.nutrinest.domain.nutrition.RecipeMeasure
import com.zame.nutrinest.domain.nutrition.UnitDefinition
import com.zame.nutrinest.domain.planner.GeneratedMeal
import com.zame.nutrinest.domain.planner.PlannableRecipe
import com.zame.nutrinest.domain.planner.ShoppingListCalculator
import com.zame.nutrinest.ui.menu.shopping.ShoppingRow
import com.zame.nutrinest.ui.menu.shopping.groupShoppingRows
import org.junit.Assert.*
import org.junit.Test

class ShoppingQuantityTest {
    private val source = ConversionEvidence(
        "fixture-shop", "fixture://shopping", "test-v1", ConversionConfidence.HIGH,
        EvidenceStatus.ACCEPTED, "Synthetic edible mass fixture"
    )
    private val egg = RecipeMeasure(
        UnitDefinition.count("large"), "large egg", "huevo grande", isDefault = true,
        factor = FoodConversionFactor.UnitWeight(1L, UnitDefinition.count("large"), 50.0, MassBasis.EDIBLE, source)
    )
    private val milk = RecipeMeasure(
        UnitDefinition.MILLILITRE, "ml", isDefault = true,
        factor = FoodConversionFactor.Density(2L, 1.03, MassBasis.EDIBLE, source)
    )

    @Test fun mixedRecipeUnitsAggregateAsGramsBeforeConvertingTheWeeklyDemand() {
        val eggs = ingredient(1, "Egg")
        val liquid = ingredient(2, "Milk")
        val rice = ingredient(3, "Raw rice")
        val boiled = ingredient(4, "Boiled rice")
        val recipes = mapOf(
            10L to recipe(10, listOf(
                IngredientWithQuantity(eggs, 50.0, "UNIT:large", 1.0, source.id),
                IngredientWithQuantity(liquid, 515.0, "MILLILITRE", 500.0, source.id),
                IngredientWithQuantity(rice, 80.0)
            )),
            20L to recipe(20, listOf(
                IngredientWithQuantity(eggs, 20.0),
                IngredientWithQuantity(liquid, 1030.0, "LITRE", 1.0, source.id),
                IngredientWithQuantity(rice, 40.0),
                IngredientWithQuantity(boiled, 25.0)
            ))
        )
        val meals = listOf(
            GeneratedMeal(0, MealSlot.DINNER, MealCourse.SINGLE, null, 10L, true, false, false, mapOf(1L to 0.5, 2L to 1.0)),
            GeneratedMeal(0, MealSlot.DINNER, MealCourse.SINGLE, 3L, 20L, false, false, false, mapOf(3L to 0.6)),
            GeneratedMeal(1, MealSlot.DINNER, MealCourse.SINGLE, null, null, false, true, true, emptyMap())
        )
        val gramsOnly = recipes.mapValues { (_, recipe) ->
            recipe.copy(ingredients = recipe.ingredients.map { IngredientWithQuantity(it.ingredient, it.grams) })
        }
        val totals = ShoppingListCalculator.totalsOf(meals, recipes)
        assertEquals(ShoppingListCalculator.totalsOf(meals, gramsOnly), totals)
        assertEquals(mapOf(1L to 87.0, 2L to 1390.5, 3L to 144.0, 4L to 15.0), totals)

        val eggsRequired = ShoppingQuantity.from(totals.getValue(1), listOf(RecipeMeasure.GRAMS, egg))
        assertEquals(87.0, eggsRequired.requiredGrams, 0.0)
        assertEquals(1.74, eggsRequired.required!!.amount, 1e-12)
        assertEquals(2.0, eggsRequired.toBuy!!.amount, 0.0)
        // Rounding the two recipe contributions independently would have suggested three eggs.
        val milkRequired = ShoppingQuantity.from(totals.getValue(2), listOf(milk))
        assertEquals(UnitDefinition.LITRE, milkRequired.required!!.unit)
        assertEquals(1.35, milkRequired.required.amount, 1e-12)
        assertNull(milkRequired.toBuy)
        assertEquals(ShoppingQuantity(144.0, null, null), ShoppingQuantity.from(totals.getValue(3), emptyList()))
    }

    @Test fun pieceCountRoundsUpOnlyAfterAggregationAndHandlesFloatingPointBoundaries() {
        assertEquals(3.0, ShoppingQuantity.from(150.0, listOf(egg)).toBuy!!.amount, 0.0)
        assertEquals(3.0, ShoppingQuantity.from(Math.nextUp(150.0), listOf(egg)).toBuy!!.amount, 0.0)
        assertEquals(4.0, ShoppingQuantity.from(150.001, listOf(egg)).toBuy!!.amount, 0.0)
        assertEquals(1.0, ShoppingQuantity.from(0.1, listOf(egg)).toBuy!!.amount, 0.0)
    }

    @Test fun onlyValidEdibleEvidenceAllowsNaturalQuantitiesAndPortionsAreNotPackages() {
        val invalid = milk.copy(factor = FoodConversionFactor.Density(
            2L, 1.0, MassBasis.EDIBLE, source.copy(status = EvidenceStatus.UNVERIFIED)
        ))
        assertNull(ShoppingQuantity.from(100.0, listOf(invalid)).required)
        val small = ShoppingQuantity.from(257.5, listOf(milk))
        assertEquals(250.0, small.required!!.amount, 1e-12)
        assertEquals(UnitDefinition.MILLILITRE, small.required.unit)
        val portion = UnitDefinition.portion("bowl")
        val bowl = RecipeMeasure(portion, "bowl", factor = FoodConversionFactor.UnitWeight(
            3L, portion, 100.0, MassBasis.EDIBLE, source
        ))
        val rice = ShoppingQuantity.from(235.0, listOf(bowl))
        assertEquals(2.35, rice.required!!.amount, 1e-12)
        assertNull(rice.toBuy)
    }

    @Test fun groupsKeepCatalogCategoryOrderAndSortByTheDisplayedLanguage() {
        fun row(id: Long, category: String, en: String, es: String): ShoppingRow {
            val source = ShoppingItemWithIngredient(
                ShoppingItemEntity(id = id, menuId = 1, ingredientId = id, grams = 100.0),
                ingredient(id, "Legacy $id").copy(categoryKey = category)
            )
            return ShoppingRow(source, en, es, ShoppingQuantity(100.0, null, null))
        }
        val dairy = row(1, IngredientCategories.DAIRY, "Milk", "Leche")
        val first = row(2, IngredientCategories.FRUITS, "Peach", "Albaricoque")
        val second = row(3, IngredientCategories.FRUITS, "Apple", "Manzana")
        val rows = listOf(dairy, second, first)
        val groups = groupShoppingRows(rows, spanish = true)
        assertEquals(listOf(IngredientCategories.FRUITS, IngredientCategories.DAIRY), groups.map { it.first })
        assertEquals(listOf(first, second), groups.first().second)
        assertEquals(listOf(second, first), groupShoppingRows(rows, spanish = false).first().second)
    }

    private fun ingredient(id: Long, name: String) =
        IngredientEntity(id = id, name = name, categoryKey = IngredientCategories.MISC, nutrition = NutritionValues())

    private fun recipe(id: Long, ingredients: List<IngredientWithQuantity>) =
        PlannableRecipe(RecipeEntity(id = id, name = "Fixture $id", category = "Main Dish"), ingredients)
}
