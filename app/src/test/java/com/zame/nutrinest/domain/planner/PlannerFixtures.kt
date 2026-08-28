package com.zame.nutrinest.domain.planner

import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.data.local.entity.RecipeEntity

/**
 * Shared fixtures for the planner tests.
 *
 * Recipes are built so that 100 g of the single ingredient is exactly 100 kcal,
 * which makes every portion factor in the assertions readable: factor 6.0 means
 * 600 kcal.
 */
internal object PlannerFixtures {

    val THREE_SLOTS = listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER)

    /** A recipe of [kcal] calories at factor 1.0. */
    fun recipe(
        id: Long,
        name: String = "Recipe $id",
        category: String = RecipeCategories.MAIN_DISH,
        kcal: Double = 500.0,
        folder: String? = null
    ): PlannableRecipe {
        val ingredient = IngredientEntity(
            id = id,
            name = "Ingredient $id",
            categoryKey = IngredientCategories.MISC,
            // Per 100 g; the quantity below then scales it to exactly [kcal].
            nutrition = NutritionValues(kcal = 100.0, carbs = 10.0, fats = 2.0, protein = 5.0),
            isCustom = false
        )
        return PlannableRecipe(
            recipe = RecipeEntity(id = id, name = name, category = category, folder = folder),
            ingredients = listOf(IngredientWithQuantity(ingredient, grams = kcal))
        )
    }

    /** Enough variety that the repeat cap never forces a fallback. */
    fun recipePool(count: Int = 24): List<PlannableRecipe> =
        (1L..count).map { id ->
            recipe(
                id = id,
                category = when {
                    id % 5 == 0L -> RecipeCategories.DESSERT
                    id % 3 == 0L -> RecipeCategories.SIDE_DISH
                    else -> RecipeCategories.MAIN_DISH
                }
            )
        }

    fun plan(
        profileId: Long,
        dailyCalories: Int = 2000,
        shares: Map<MealSlot, Float> = mapOf(
            MealSlot.BREAKFAST to 0.3f, MealSlot.LUNCH to 0.4f, MealSlot.DINNER to 0.3f
        ),
        freeSlots: Set<MealSlot> = emptySet()
    ) = DailyPlan(profileId, dailyCalories, shares, freeSlots)

    fun config(
        slotWeights: Map<MealSlot, Float> = mapOf(
            MealSlot.BREAKFAST to 0.3f, MealSlot.LUNCH to 0.4f, MealSlot.DINNER to 0.3f
        ),
        block: GenerationConfig.() -> GenerationConfig = { this }
    ) = GenerationConfig(slotWeights = slotWeights).block()

    /** Unwraps a successful generation, failing loudly when it isn't one. */
    fun GenerationResult.menu(): GeneratedMenu = when (this) {
        is GenerationResult.Success -> menu
        is GenerationResult.Failure -> error("generation failed: $reason")
    }
}
