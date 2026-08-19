package com.zame.nutrinest.domain.planner

import com.zame.nutrinest.data.local.entity.IngredientWithQuantity

/**
 * Turns a week of planned meals into "how much of each ingredient to buy".
 *
 * Deliberately driven by the *planned* portion factors rather than the
 * adjusted ones: the list is what you take to the shop on Sunday, and it
 * should not shift under you every time somebody logs a biscuit on Wednesday.
 *
 * Free slots carry no recipe, so they drop out on their own.
 */
object ShoppingListCalculator {

    /** One planned dish and the household portion of it, summed over everyone. */
    data class Demand(val recipeId: Long?, val householdFactor: Double)

    /** ingredientId → grams for the whole week. */
    fun totals(
        demands: List<Demand>,
        ingredientsByRecipe: Map<Long, List<IngredientWithQuantity>>
    ): Map<Long, Double> {
        val totals = mutableMapOf<Long, Double>()
        for (demand in demands) {
            val recipeId = demand.recipeId ?: continue
            val ingredients = ingredientsByRecipe[recipeId] ?: continue
            for (ingredient in ingredients) {
                totals.merge(
                    ingredient.ingredient.id,
                    ingredient.grams * demand.householdFactor,
                    Double::plus
                )
            }
        }
        return totals
    }

    /** The same, straight off a freshly generated menu. */
    fun totalsOf(
        meals: List<GeneratedMeal>,
        recipes: Map<Long, PlannableRecipe>
    ): Map<Long, Double> = totals(
        demands = meals.map { Demand(it.recipeId, it.portions.values.sum()) },
        ingredientsByRecipe = recipes.mapValues { it.value.ingredients }
    )
}
