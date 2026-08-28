package com.zame.nutrinest.domain.planner

import androidx.annotation.StringRes
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.MealCourse
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.MenuScope
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.data.local.entity.RecipeEntity
import kotlin.random.Random

// ── Configuration ────────────────────────────────────────────────────────

/**
 * A user-defined lock: on these days, this slot must be this recipe.
 *
 * [profileId] gives the lock its scope — null pins the meal for the whole
 * household, a value pins it for that person only and leaves everyone else to
 * the generator. [course] is null for "whichever course the slot's main dish
 * is", which is what a lock means unless the user says otherwise.
 */
data class MealLock(
    val days: Set<Int>,        // 0=Mon … 6=Sun
    val slot: MealSlot,
    val recipeId: Long,
    val profileId: Long? = null,
    val course: MealCourse? = null
)

/** "This person eats something else here" — the per-meal escape hatch. */
data class MealOverride(
    val day: Int,
    val slot: MealSlot,
    val profileId: Long
)

data class GenerationConfig(
    val slotWeights: Map<MealSlot, Float>,
    val scope: MenuScope = MenuScope.SHARED,
    /** Slots that hold more than one dish, e.g. LUNCH to [FIRST, MAIN]. */
    val slotCourses: Map<MealSlot, List<MealCourse>> = emptyMap(),
    /** Only consulted when [scope] is [MenuScope.PER_MEAL]. */
    val overrides: Set<MealOverride> = emptySet(),
    /** Randomly scattered free meals, on top of each profile's own free slots. */
    val cheatMeals: Int = 0,
    val maxRecipeRepeats: Int = 2,
    /** Recipe categories to skip, e.g. "Dessert". */
    val excludedCategories: Set<String> = emptySet(),
    /** Ingredient category keys to avoid, e.g. `IngredientCategories.FISH`. */
    val excludedIngredientCategories: Set<String> = emptySet(),
    val excludedRecipeIds: Set<Long> = emptySet(),
    /** null = every book; an empty set intentionally enables no recipe book. */
    val includedFolders: Set<String?>? = null,
    val excludeRecentlyUsed: Boolean = true,
    val locks: List<MealLock> = emptyList()
) {
    /** The courses a slot is planned with; every slot has at least one. */
    fun coursesOf(slot: MealSlot): List<MealCourse> =
        slotCourses[slot]?.takeIf { it.isNotEmpty() } ?: listOf(MealCourse.SINGLE)
}

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
    val course: MealCourse,
    /** null = the whole household eats this; otherwise this person's own meal. */
    val profileId: Long?,
    /** null only for a free slot. */
    val recipeId: Long?,
    val isLocked: Boolean,
    val isCheatMeal: Boolean,
    val isFree: Boolean,
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
    NOT_ENOUGH_RECIPES(R.string.menu_error_not_enough_recipes),
    SAVE_FAILED(R.string.menu_error_save_failed)
}

// ── Generator ────────────────────────────────────────────────────────────

/**
 * Weekly menu generator. Pure Kotlin, deterministic given a [Random] seed —
 * which makes it unit-testable and easy to swap for a smarter (or AI-based)
 * implementation later: everything upstream only depends on
 * [GenerationConfig] + [DailyPlan] in and [GeneratedMenu] out.
 *
 * A week is filled (day, slot, course) at a time. For each of those the
 * household is split into *audiences*: the people eating the same dish. A
 * shared menu has one audience, an individual menu has one per person, and a
 * per-meal menu has one shared audience plus one for every person with an
 * override or a lock of their own here.
 *
 * Selection within an audience is score-based:
 *   + slot/course affinity   (breakfast recipes at breakfast, desserts last)
 *   + leftover bonus         (moderate: reduces waste without killing variety)
 *   − repetition penalty     (grows with each use; hard cap at maxRepeats)
 *   − same-recipe-yesterday penalty
 *   + small random jitter    (keeps weeks from looking identical)
 */
class MenuGenerator(private val random: Random = Random.Default) {

    private data class LockKey(
        val day: Int,
        val slot: MealSlot,
        val course: MealCourse,
        val profileId: Long?
    )

    /** The people sharing one dish, and whose row it is. */
    private data class Audience(val owner: Long?, val plans: List<DailyPlan>)

    fun generate(
        plans: List<DailyPlan>,
        recipes: List<PlannableRecipe>,
        config: GenerationConfig,
        profiles: List<ProfileEntity> = emptyList(),
        recentRecipeIds: Set<Long> = emptySet(),
        leftoverIngredientIds: Set<Long> = emptySet()
    ): GenerationResult {
        if (plans.isEmpty()) {
            return GenerationResult.Failure(FailureReason.NO_PROFILES)
        }

        // ── Filter the candidate pool ──────────────────────────────────
        val eligibleRecipes = recipes.filter { candidate ->
            candidate.totalNutrition.kcal.isFinite() && candidate.totalNutrition.kcal > 0 &&
                candidate.ingredients.all { it.grams.isFinite() && it.grams > 0 } &&
                DietarySafety.recipeAllowed(candidate, profiles)
        }
        val pool = eligibleRecipes.filter { candidate ->
            candidate.recipe.id !in config.excludedRecipeIds &&
                candidate.recipe.category !in config.excludedCategories &&
                candidate.ingredientCategories.none { it in config.excludedIngredientCategories } &&
                (config.includedFolders == null ||
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

        val byId = eligibleRecipes.associateBy { it.recipe.id }
        val slots = config.slotWeights.keys.sortedBy { it.ordinal }

        // ── Resolve locks into a (day, slot, course, who) → recipeId map ─
        val lockMap = mutableMapOf<LockKey, Long>()
        for (lock in config.locks) {
            if (byId[lock.recipeId] == null) continue // locked recipe was deleted
            val course = lock.course ?: MealCourse.principal(config.coursesOf(lock.slot))
            for (day in lock.days) {
                lockMap[LockKey(day, lock.slot, course, lock.profileId)] = lock.recipeId
            }
        }
        val lockedDaySlots = lockMap.keys.map { it.day to it.slot }.toSet()

        // ── Scatter the random free meals among the untouched slots ─────
        val allDaySlots = (0..6).flatMap { day -> slots.map { day to it } }
        val cheatSlots = allDaySlots
            .filterNot { it in lockedDaySlots }
            .shuffled(random)
            .take(config.cheatMeals.coerceAtLeast(0))
            .toSet()

        // ── Fill the week ──────────────────────────────────────────────
        val useCount = mutableMapOf<Long, Int>()
        val meals = mutableListOf<GeneratedMeal>()

        for (day in 0..6) {
            val yesterdayRecipeIds = meals
                .filter { it.dayOfWeek == day - 1 }
                .mapNotNull { it.recipeId }
                .toSet()

            for (slot in slots) {
                val courses = config.coursesOf(slot)
                val householdCheat = (day to slot) in cheatSlots

                // Free = this person left the slot untracked, or the whole
                // household drew a random free meal here.
                val free = if (householdCheat) plans else plans.filter { it.isFree(slot) }
                val eating = plans - free.toSet()

                if (free.isNotEmpty()) {
                    if (eating.isEmpty()) {
                        meals += freeMeal(day, slot, profileId = null, cheat = householdCheat)
                    } else {
                        free.forEach {
                            meals += freeMeal(day, slot, it.profileId, cheat = false)
                        }
                    }
                }
                if (eating.isEmpty()) continue

                val apart = eating.filter { plan ->
                    config.scope == MenuScope.INDIVIDUAL ||
                        (config.scope == MenuScope.PER_MEAL &&
                            MealOverride(day, slot, plan.profileId) in config.overrides) ||
                        lockMap.keys.any {
                            it.day == day && it.slot == slot && it.profileId == plan.profileId
                        }
                }
                val together = eating - apart.toSet()
                val audiences = buildList {
                    apart.forEach { add(Audience(it.profileId, listOf(it))) }
                    if (together.isNotEmpty()) add(Audience(owner = null, plans = together))
                }

                val courseShares = MealCourse.shares(courses)
                val usedHere = mutableSetOf<Long>()

                for (audience in audiences) {
                    for (course in courses) {
                        val lockedId = lockMap[LockKey(day, slot, course, audience.owner)]
                        val chosen = if (lockedId != null) {
                            byId.getValue(lockedId)
                        } else {
                            pickRecipe(
                                pool = freshPool,
                                slot = slot,
                                course = course,
                                useCount = useCount,
                                maxRepeats = config.maxRecipeRepeats.coerceAtLeast(1),
                                leftoverIngredientIds = leftoverIngredientIds,
                                yesterdayRecipeIds = yesterdayRecipeIds,
                                alreadyInSlot = usedHere
                            ) ?: return GenerationResult.Failure(FailureReason.NOT_ENOUGH_RECIPES)
                        }
                        useCount[chosen.recipe.id] = (useCount[chosen.recipe.id] ?: 0) + 1
                        usedHere += chosen.recipe.id

                        val share = courseShares[course] ?: 1.0
                        val portions = audience.plans.associate { plan ->
                            val kcal = plan.slotKcal(slot) * share
                            plan.profileId to portionFactor(kcal, chosen.totalNutrition.kcal)
                        }

                        meals += GeneratedMeal(
                            dayOfWeek = day,
                            slot = slot,
                            course = course,
                            profileId = audience.owner,
                            recipeId = chosen.recipe.id,
                            isLocked = lockedId != null,
                            isCheatMeal = false,
                            isFree = false,
                            portions = portions
                        )
                    }
                }
            }
        }

        return GenerationResult.Success(
            GeneratedMenu(meals, ShoppingListCalculator.totalsOf(meals, byId))
        )
    }

    private fun freeMeal(day: Int, slot: MealSlot, profileId: Long?, cheat: Boolean) =
        GeneratedMeal(
            dayOfWeek = day,
            slot = slot,
            course = MealCourse.SINGLE,
            profileId = profileId,
            recipeId = null,
            isLocked = false,
            isCheatMeal = cheat,
            isFree = true,
            portions = emptyMap()
        )

    private fun pickRecipe(
        pool: List<PlannableRecipe>,
        slot: MealSlot,
        course: MealCourse,
        useCount: Map<Long, Int>,
        maxRepeats: Int,
        leftoverIngredientIds: Set<Long>,
        yesterdayRecipeIds: Set<Long>,
        alreadyInSlot: Set<Long>
    ): PlannableRecipe? {
        // The configured repeat count is a real upper bound. If it cannot fill
        // the week, the caller gets NOT_ENOUGH_RECIPES and can ask the user to
        // add recipes or raise the limit.
        val candidates = pool
            .filterNot { it.recipe.id in alreadyInSlot }
            .ifEmpty { pool }
            .filter { (useCount[it.recipe.id] ?: 0) < maxRepeats }
            .ifEmpty { return null }

        return candidates.maxByOrNull { candidate ->
            var score = 0.0
            score += affinity(candidate.recipe, slot, course)
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

    /**
     * Category ↔ position affinity, mild enough that any recipe CAN appear
     * anywhere — except a dessert outside the dessert course, which is the one
     * thing worth being firm about.
     */
    private fun affinity(recipe: RecipeEntity, slot: MealSlot, course: MealCourse): Double =
        when (course) {
            MealCourse.DESSERT ->
                if (recipe.category == RecipeCategories.DESSERT) 4.0 else -3.0
            MealCourse.FIRST -> when (recipe.category) {
                RecipeCategories.SIDE_DISH -> 3.0
                RecipeCategories.MAIN_DISH -> 1.0
                RecipeCategories.SNACK -> 0.5
                RecipeCategories.DESSERT -> -3.0
                else -> 0.0
            }
            MealCourse.MAIN -> when (recipe.category) {
                RecipeCategories.MAIN_DISH -> 3.0
                RecipeCategories.SIDE_DISH -> 0.5
                RecipeCategories.DESSERT -> -3.0
                RecipeCategories.BREAKFAST, RecipeCategories.SNACK -> -1.0
                else -> 0.0
            }
            MealCourse.SINGLE -> slotAffinity(recipe, slot)
        }

    private fun slotAffinity(recipe: RecipeEntity, slot: MealSlot): Double = when (slot) {
        MealSlot.BREAKFAST ->
            if (recipe.category == RecipeCategories.BREAKFAST) 3.0 else 0.0
        MealSlot.MORNING_SNACK, MealSlot.AFTERNOON_SNACK -> when (recipe.category) {
            RecipeCategories.SNACK -> 3.0
            RecipeCategories.DESSERT, RecipeCategories.DRINK -> 1.5
            else -> 0.0
        }
        MealSlot.LUNCH, MealSlot.DINNER -> when (recipe.category) {
            RecipeCategories.MAIN_DISH -> 3.0
            RecipeCategories.SIDE_DISH -> 1.0
            RecipeCategories.BREAKFAST, RecipeCategories.SNACK -> -1.0
            else -> 0.0
        }
    }

    companion object {
        /**
         * Sanity rails on a portion. The floor is low because a dessert course
         * is a genuinely small slice of a recipe — clamping it higher would
         * silently push a slot over the budget it was given.
         */
        const val MIN_FACTOR = 0.1
        const val MAX_FACTOR = 4.0

        /**
         * How much of a recipe covers [targetKcal]. Shared by generation and by
         * every later recalculation (swaps, intake logging), so a portion always
         * means the same thing.
         */
        fun portionFactor(targetKcal: Double, recipeKcal: Double): Double {
            if (recipeKcal <= 0.0) return 1.0
            return (targetKcal / recipeKcal).coerceIn(MIN_FACTOR, MAX_FACTOR)
        }
    }
}
