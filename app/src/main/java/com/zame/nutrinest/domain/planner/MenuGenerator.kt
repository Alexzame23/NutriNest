package com.zame.nutrinest.domain.planner

import androidx.annotation.StringRes
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.domain.nutrition.NutritionCalculator
import kotlin.random.Random

// ── Configuration ────────────────────────────────────────────────────────

/** A user-defined lock: on these days, this slot must be this recipe. */
data class MealLock(
    val days: Set<Int>,        // 0=Mon … 6=Sun
    val slot: MealSlot,
    val recipeId: Long
)

data class GenerationConfig(
    val slotWeights: Map<MealSlot, Float>,
    val cheatMeals: Int = 0,
    val maxRecipeRepeats: Int = 2,
    /** Recipe categories to skip, e.g. "Dessert". */
    val excludedCategories: Set<String> = emptySet(),
    /** Ingredient category keys to avoid, e.g. `IngredientCategories.FISH`. */
    val excludedIngredientCategories: Set<String> = emptySet(),
    val excludedRecipeIds: Set<Long> = emptySet(),
    /** Empty = all folders allowed; otherwise only recipes in these folders (or no folder). */
    val includedFolders: Set<String> = emptySet(),
    val excludeRecentlyUsed: Boolean = true,
    val locks: List<MealLock> = emptyList()
)

// ── Inputs & outputs ─────────────────────────────────────────────────────

data class PlannableRecipe(
    val recipe: RecipeEntity,
    val ingredients: List<IngredientWithQuantity>
) {
    val totalNutrition: NutritionValues =
        ingredients.fold(NutritionValues()) { acc, i -> acc + i.nutrition }
    val ingredientIds: Set<Long> = ingredients.map { it.ingredient.id }.toSet()
    val ingredientCategories: Set<String> =
        ingredients.map { it.ingredient.categoryKey }.toSet()
}

data class GeneratedMeal(
    val dayOfWeek: Int,
    val slot: MealSlot,
    val recipeId: Long,
    val isLocked: Boolean,
    val isCheatMeal: Boolean,
    /** profileId → portion factor (multiplier of base recipe grams). */
    val portions: Map<Long, Double>
)

data class GeneratedMenu(
    val meals: List<GeneratedMeal>,
    /** ingredientId → total household grams for the whole week. */
    val shoppingTotals: Map<Long, Double>
)

sealed class GenerationResult {
    data class Success(val menu: GeneratedMenu) : GenerationResult()

    /** Carries a code, not a sentence — the UI layer owns the wording. */
    data class Failure(val reason: FailureReason) : GenerationResult()
}

enum class FailureReason(@StringRes val messageRes: Int) {
    NO_PROFILES(R.string.menu_error_no_profiles),
    NO_MATCHING_RECIPES(R.string.menu_error_no_matching_recipes),
    NOT_ENOUGH_RECIPES(R.string.menu_error_not_enough_recipes)
}

// ── Generator ────────────────────────────────────────────────────────────

/**
 * Weekly menu generator. Pure Kotlin, deterministic given a [Random] seed —
 * which makes it unit-testable and easy to swap for a smarter (or AI-based)
 * implementation later: everything upstream only depends on
 * [GenerationConfig] in and [GeneratedMenu] out.
 *
 * Selection is score-based per (day, slot):
 *   + slot/category affinity  (breakfast recipes at breakfast, etc.)
 *   + leftover bonus          (moderate: reduces waste without killing variety)
 *   − repetition penalty      (grows with each use; hard cap at maxRepeats)
 *   − same-recipe-yesterday penalty
 *   + small random jitter     (keeps weeks from looking identical)
 */
class MenuGenerator(private val random: Random = Random.Default) {

    fun generate(
        profiles: List<ProfileEntity>,
        recipes: List<PlannableRecipe>,
        config: GenerationConfig,
        recentRecipeIds: Set<Long> = emptySet(),
        leftoverIngredientIds: Set<Long> = emptySet()
    ): GenerationResult {
        if (profiles.isEmpty()) {
            return GenerationResult.Failure(FailureReason.NO_PROFILES)
        }

        // ── Filter the candidate pool ──────────────────────────────────
        val pool = recipes.filter { candidate ->
            candidate.recipe.id !in config.excludedRecipeIds &&
                candidate.recipe.category !in config.excludedCategories &&
                candidate.ingredientCategories.none { it in config.excludedIngredientCategories } &&
                (config.includedFolders.isEmpty() ||
                    candidate.recipe.folder in config.includedFolders) &&
                candidate.totalNutrition.kcal > 0
        }
        // Recently-used exclusion is soft: if it would empty the pool, ignore it.
        val freshPool =
            if (config.excludeRecentlyUsed)
                pool.filter { it.recipe.id !in recentRecipeIds }.ifEmpty { pool }
            else pool

        if (freshPool.isEmpty()) {
            return GenerationResult.Failure(FailureReason.NO_MATCHING_RECIPES)
        }

        val byId = recipes.associateBy { it.recipe.id }
        val slots = config.slotWeights.keys.sortedBy { it.ordinal }
        val normalizedWeights = normalize(config.slotWeights)
        val targetsByProfile = profiles.associate {
            it.id to NutritionCalculator.targetsFor(it)
        }

        // ── Resolve locks into a (day, slot) → recipeId map ────────────
        val lockMap = mutableMapOf<Pair<Int, MealSlot>, Long>()
        for (lock in config.locks) {
            if (byId[lock.recipeId] == null) continue // locked recipe was deleted
            for (day in lock.days) lockMap[day to lock.slot] = lock.recipeId
        }

        // ── Pick cheat-meal slots among non-locked ones ────────────────
        val allSlots = (0..6).flatMap { day -> slots.map { day to it } }
        val cheatSlots = allSlots
            .filter { it !in lockMap.keys }
            .shuffled(random)
            .take(config.cheatMeals.coerceIn(0, allSlots.size))
            .toSet()

        // ── Fill the week ──────────────────────────────────────────────
        val useCount = mutableMapOf<Long, Int>()
        val meals = mutableListOf<GeneratedMeal>()

        for (day in 0..6) {
            for (slot in slots) {
                val key = day to slot
                val lockedId = lockMap[key]
                val chosen: PlannableRecipe = if (lockedId != null) {
                    byId.getValue(lockedId)
                } else {
                    pickRecipe(
                        pool = freshPool,
                        slot = slot,
                        day = day,
                        useCount = useCount,
                        maxRepeats = config.maxRecipeRepeats,
                        leftoverIngredientIds = leftoverIngredientIds,
                        yesterdayRecipeIds = meals
                            .filter { it.dayOfWeek == day - 1 }
                            .map { it.recipeId }
                            .toSet()
                    ) ?: return GenerationResult.Failure(FailureReason.NOT_ENOUGH_RECIPES)
                }
                useCount[chosen.recipe.id] = (useCount[chosen.recipe.id] ?: 0) + 1

                val isCheat = key in cheatSlots
                val slotWeight = normalizedWeights.getValue(slot).toDouble()
                val portions = profiles.associate { profile ->
                    val factor = if (isCheat) 1.0 else {
                        val slotKcal =
                            targetsByProfile.getValue(profile.id).dailyCalories * slotWeight
                        (slotKcal / chosen.totalNutrition.kcal).coerceIn(0.25, 4.0)
                    }
                    profile.id to factor
                }

                meals += GeneratedMeal(
                    dayOfWeek = day,
                    slot = slot,
                    recipeId = chosen.recipe.id,
                    isLocked = lockedId != null,
                    isCheatMeal = isCheat,
                    portions = portions
                )
            }
        }

        // ── Aggregate the household shopping list ──────────────────────
        val totals = mutableMapOf<Long, Double>()
        for (meal in meals) {
            val recipe = byId.getValue(meal.recipeId)
            val householdFactor = meal.portions.values.sum()
            for (ingredient in recipe.ingredients) {
                val grams = ingredient.grams * householdFactor
                totals.merge(ingredient.ingredient.id, grams, Double::plus)
            }
        }

        return GenerationResult.Success(GeneratedMenu(meals, totals))
    }

    private fun pickRecipe(
        pool: List<PlannableRecipe>,
        slot: MealSlot,
        day: Int,
        useCount: Map<Long, Int>,
        maxRepeats: Int,
        leftoverIngredientIds: Set<Long>,
        yesterdayRecipeIds: Set<Long>
    ): PlannableRecipe? {
        // Hard cap first; if that empties the pool, allow repeats as a fallback
        // so a small recipe collection still yields a full week.
        val candidates = pool
            .filter { (useCount[it.recipe.id] ?: 0) < maxRepeats }
            .ifEmpty { pool }
            .ifEmpty { return null }

        return candidates.maxByOrNull { candidate ->
            var score = 0.0
            score += slotAffinity(candidate.recipe, slot)
            score -= 2.5 * (useCount[candidate.recipe.id] ?: 0)
            if (candidate.recipe.id in yesterdayRecipeIds) score -= 2.0
            // Moderate leftover bonus, deliberately smaller than the variety
            // penalties so waste reduction never dominates variety.
            val leftoverHits = candidate.ingredientIds.count { it in leftoverIngredientIds }
            score += (leftoverHits * 0.4).coerceAtMost(1.2)
            score += random.nextDouble() * 1.5
            score
        }
    }

    /** Category ↔ slot affinity, mild enough that any recipe CAN appear anywhere. */
    private fun slotAffinity(recipe: RecipeEntity, slot: MealSlot): Double = when (slot) {
        MealSlot.BREAKFAST ->
            if (recipe.category == "Breakfast") 3.0 else 0.0
        MealSlot.MORNING_SNACK, MealSlot.AFTERNOON_SNACK -> when (recipe.category) {
            "Snack" -> 3.0
            "Dessert", "Drink" -> 1.5
            else -> 0.0
        }
        MealSlot.LUNCH, MealSlot.DINNER -> when (recipe.category) {
            "Main Dish" -> 3.0
            "Side Dish" -> 1.0
            "Breakfast", "Snack" -> -1.0
            else -> 0.0
        }
    }

    private fun normalize(weights: Map<MealSlot, Float>): Map<MealSlot, Float> {
        val sum = weights.values.sum().takeIf { it > 0f } ?: 1f
        return weights.mapValues { it.value / sum }
    }
}
