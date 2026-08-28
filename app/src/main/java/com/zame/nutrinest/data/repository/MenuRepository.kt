package com.zame.nutrinest.data.repository

import androidx.room.withTransaction
import com.zame.nutrinest.data.local.NutriNestDatabase
import com.zame.nutrinest.data.local.dao.MenuDao
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.IntakeEntryEntity
import com.zame.nutrinest.data.local.entity.MealCourse
import com.zame.nutrinest.data.local.entity.MealPortionEntity
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.PlannedMealEntity
import com.zame.nutrinest.data.local.entity.PlannedMealWithPortions
import com.zame.nutrinest.data.local.entity.PlannedMealWithRecipe
import com.zame.nutrinest.data.local.entity.PortionWithProfile
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.RecipeStepEntity
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.local.entity.ShoppingItemWithIngredient
import com.zame.nutrinest.data.local.entity.WeeklyMenuEntity
import com.zame.nutrinest.domain.planner.DailyPlan
import com.zame.nutrinest.domain.planner.GenerationConfig
import com.zame.nutrinest.domain.planner.GenerationResult
import com.zame.nutrinest.domain.planner.IntakeAdjuster
import com.zame.nutrinest.domain.planner.MealLock
import com.zame.nutrinest.domain.planner.MenuGenerator
import com.zame.nutrinest.domain.planner.MenuResolver
import com.zame.nutrinest.domain.planner.PlannableRecipe
import com.zame.nutrinest.domain.planner.ShoppingListCalculator
import com.zame.nutrinest.util.WeekUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** Which side of the household a menu edit applies to. */
sealed interface MealScopeTarget {
    /** Everyone eating this dish. */
    data object Household : MealScopeTarget

    /** This person only; the rest of the household keeps what it had. */
    data class Person(val profileId: Long) : MealScopeTarget
}

interface MenuRepository {
    fun observeMenu(weekStart: Long): Flow<WeeklyMenuEntity?>
    fun observeMeals(menuId: Long): Flow<List<PlannedMealWithRecipe>>
    fun observeMeal(mealId: Long): Flow<PlannedMealWithRecipe?>
    fun observePortions(mealId: Long): Flow<List<PortionWithProfile>>
    fun observeSteps(recipeId: Long): Flow<List<RecipeStepEntity>>
    fun observeShoppingItems(weekStart: Long): Flow<List<ShoppingItemWithIngredient>>
    fun observeProfiles(): Flow<List<ProfileEntity>>
    fun observeIntake(profileId: Long, dayStart: Long): Flow<List<IntakeEntryEntity>>

    /** One day of the plan, portions included, for the intake screen. */
    fun observeDayMeals(weekStart: Long, day: Int): Flow<List<PlannedMealWithPortions>>

    suspend fun setPurchased(itemId: Long, purchased: Boolean)
    suspend fun getIngredientsOf(recipeId: Long): List<IngredientWithQuantity>
    suspend fun hasMenu(weekStart: Long): Boolean

    /** Runs the generator for [weekStart] and persists the result. */
    suspend fun generate(weekStart: Long, config: GenerationConfig): GenerationResult

    /** Wipes the given weeks entirely — meals, portions and shopping list. */
    suspend fun eraseWeeks(weekStarts: List<Long>)

    /** Pins or unpins a meal. The row's own scope decides who it holds for. */
    suspend fun setMealLocked(mealId: Long, locked: Boolean)

    /**
     * Swaps the recipe of a planned meal. For [MealScopeTarget.Person] the rest
     * of the household is left alone: that person's whole slot becomes theirs,
     * with the other courses carried over unchanged.
     */
    suspend fun replaceMeal(mealId: Long, newRecipeId: Long, target: MealScopeTarget)

    /** Records food eaten off-plan and re-portions the rest of that person's day. */
    suspend fun logIntake(
        profileId: Long,
        dayStart: Long,
        label: String,
        nutrition: NutritionValues,
        grams: Double? = null
    )

    suspend fun deleteIntake(entryId: Long, profileId: Long, dayStart: Long)

    /** That person's calorie plan for the week's slot layout. */
    suspend fun dailyPlanFor(profileId: Long, weekStart: Long): DailyPlan?
}

@OptIn(ExperimentalCoroutinesApi::class)
class LocalMenuRepository(
    private val database: NutriNestDatabase,
    private val generator: MenuGenerator = MenuGenerator()
) : MenuRepository {

    private val dao: MenuDao = database.menuDao()

    override fun observeMenu(weekStart: Long) = dao.observeMenu(weekStart)
    override fun observeMeals(menuId: Long) = dao.observeMeals(menuId)
    override fun observeMeal(mealId: Long) = dao.observeMeal(mealId)
    override fun observePortions(mealId: Long) = dao.observePortions(mealId)
    override fun observeSteps(recipeId: Long) = dao.observeSteps(recipeId)
    override fun observeShoppingItems(weekStart: Long) = dao.observeShoppingItems(weekStart)
    override fun observeProfiles() = dao.observeProfiles()
    override fun observeIntake(profileId: Long, dayStart: Long) =
        dao.observeIntake(profileId, dayStart)

    override fun observeDayMeals(weekStart: Long, day: Int) =
        dao.observeMenu(weekStart).flatMapLatest { menu ->
            if (menu == null) flowOf(emptyList()) else dao.observeDay(menu.id, day)
        }

    override suspend fun setPurchased(itemId: Long, purchased: Boolean) =
        dao.setPurchased(itemId, purchased)
    override suspend fun getIngredientsOf(recipeId: Long) = dao.getIngredientsOf(recipeId)
    override suspend fun hasMenu(weekStart: Long) = dao.getMenu(weekStart) != null

    // ── Generation ──────────────────────────────────────────────────────

    override suspend fun generate(
        weekStart: Long,
        config: GenerationConfig
    ): GenerationResult {
        val slots = config.slotWeights.keys.sortedBy { it.ordinal }
        val plans = buildPlans(slots, config.slotWeights)
        val profiles = dao.getProfiles()
        val ingredientsByRecipe = dao.getAllRecipeIngredients()
            .groupBy { it.recipeId }
            .mapValues { (_, rows) -> rows.map { it.ingredientWithQuantity() } }
        val recipes = dao.getAllRecipes().map { recipe ->
            PlannableRecipe(recipe, ingredientsByRecipe[recipe.id].orEmpty())
        }

        // Context for variety + leftovers: what last week used/bought.
        val previousWeek = WeekUtils.previousWeekStart(weekStart)
        val previousMenu = dao.getMenu(previousWeek)
        val recentRecipeIds = previousMenu
            ?.let { dao.recipeIdsOfMenu(it.id).toSet() } ?: emptySet()
        val leftoverIngredientIds = dao.ingredientIdsOfWeek(previousWeek).toSet()

        // A meal the user pinned survives a regeneration, with its scope: the
        // whole point of locking it was not to have it thrown away.
        val carriedLocks = dao.getMenu(weekStart)
            ?.let { dao.lockedMeals(it.id) }
            .orEmpty()
            .mapNotNull { meal ->
                meal.recipeId?.let {
                    MealLock(
                        days = setOf(meal.dayOfWeek),
                        slot = meal.slot,
                        recipeId = it,
                        profileId = meal.profileId,
                        course = meal.course
                    )
                }
            }

        val result = withContext(Dispatchers.Default) {
            generator.generate(
                plans = plans,
                recipes = recipes,
                config = config.copy(locks = carriedLocks + config.locks),
                profiles = profiles,
                recentRecipeIds = recentRecipeIds,
                leftoverIngredientIds = leftoverIngredientIds
            )
        }

        if (result is GenerationResult.Success) {
            val menu = result.menu
            dao.replaceMenu(
                weekStart = weekStart,
                slotsSpec = WeeklyMenuEntity.encodeSlots(config.slotWeights),
                scope = config.scope,
                coursesSpec = WeeklyMenuEntity.encodeCourses(config.slotCourses),
                meals = menu.meals.map {
                    PlannedMealEntity(
                        menuId = 0, // filled by replaceMenu
                        dayOfWeek = it.dayOfWeek,
                        slot = it.slot,
                        recipeId = it.recipeId,
                        isLocked = it.isLocked,
                        isCheatMeal = it.isCheatMeal,
                        course = it.course,
                        profileId = it.profileId,
                        isFree = it.isFree
                    )
                },
                portionsPerMeal = menu.meals.map { meal ->
                    meal.portions.map { (profileId, factor) ->
                        MealPortionEntity(
                            plannedMealId = 0, // filled by replaceMenu
                            profileId = profileId,
                            factor = factor,
                            baseFactor = factor
                        )
                    }
                },
                shoppingItems = menu.shoppingTotals.map { (ingredientId, grams) ->
                    ShoppingItemEntity(menuId = 0, ingredientId = ingredientId, grams = grams)
                }
            )
        }
        return result
    }

    override suspend fun eraseWeeks(weekStarts: List<Long>) {
        database.withTransaction {
            weekStarts.distinct().forEach { dao.deleteMenu(it) }
        }
    }

    override suspend fun setMealLocked(mealId: Long, locked: Boolean) =
        dao.setMealLocked(mealId, locked)

    // ── Swapping a meal ─────────────────────────────────────────────────

    override suspend fun replaceMeal(
        mealId: Long,
        newRecipeId: Long,
        target: MealScopeTarget
    ) {
        database.withTransaction {
            val meal = dao.getMeal(mealId) ?: return@withTransaction
            val menu = dao.getMenuById(meal.menuId) ?: return@withTransaction
            val plans = plansFor(menu).associateBy { it.profileId }
            val courses = coursesOf(menu, meal.slot)

            when (target) {
                is MealScopeTarget.Household -> {
                    // A free row has no portions yet, so fall back to everyone who
                    // isn't already eating apart in this slot.
                    val eaters = dao.getPortions(mealId).map { it.profileId }.ifEmpty {
                        val apart = dao.getMealsAt(menu.id, meal.dayOfWeek, meal.slot)
                            .mapNotNull { it.profileId }.toSet()
                        (plans.keys - apart).toList()
                    }
                    dao.updateMeal(meal.copy(recipeId = newRecipeId, isFree = false))
                    writePortions(mealId, eaters, plans, meal.slot, meal.course, courses, newRecipeId)
                }

                is MealScopeTarget.Person -> {
                    if (meal.profileId == target.profileId) {
                        dao.updateMeal(meal.copy(recipeId = newRecipeId, isFree = false))
                        writePortions(
                            mealId, listOf(target.profileId), plans,
                            meal.slot, meal.course, courses, newRecipeId
                        )
                    } else {
                        carveOutSlot(menu.id, meal, target.profileId, newRecipeId, plans, courses)
                    }
                }
            }
            recomputeShoppingList(menu.id)
        }
    }

    /**
     * Moves one person out of the household's version of a slot.
     *
     * Their own copy of every course in the slot is created — the swapped one
     * with the new recipe, the rest carried over — and their portion is taken
     * off the household rows, which disappear if nobody is left on them. Doing
     * the whole slot rather than the single course keeps a person's day
     * readable: a slot belongs either to them or to the household, never half
     * to each.
     */
    private suspend fun carveOutSlot(
        menuId: Long,
        target: PlannedMealEntity,
        profileId: Long,
        newRecipeId: Long,
        plans: Map<Long, DailyPlan>,
        courses: List<MealCourse>
    ) {
        val shared = dao.getMealsAt(menuId, target.dayOfWeek, target.slot)
            .filter { it.profileId == null }

        for (row in shared) {
            val swapped = row.id == target.id
            val ownId = dao.insertMeal(
                row.copy(
                    id = 0,
                    profileId = profileId,
                    recipeId = if (swapped) newRecipeId else row.recipeId,
                    isFree = if (swapped) false else row.isFree,
                    isLocked = false
                )
            )
            writePortions(
                mealId = ownId,
                profileIds = listOf(profileId),
                plans = plans,
                slot = row.slot,
                course = row.course,
                courses = courses,
                recipeId = if (swapped) newRecipeId else row.recipeId
            )

            dao.deletePortion(row.id, profileId)
        }

        // A household row nobody resolves to any more is dead weight — which
        // is the normal outcome in a one-person household.
        val remaining = dao.getMealsAt(menuId, target.dayOfWeek, target.slot)
        if (remaining.mapNotNull { it.profileId }.toSet().containsAll(plans.keys)) {
            remaining.filter { it.profileId == null }.forEach { dao.deleteMeal(it.id) }
        }
    }

    /** (Re)writes portions for [profileIds] on one meal, from their budgets. */
    private suspend fun writePortions(
        mealId: Long,
        profileIds: List<Long>,
        plans: Map<Long, DailyPlan>,
        slot: MealSlot,
        course: MealCourse,
        courses: List<MealCourse>,
        recipeId: Long?
    ) {
        val recipeKcal = recipeId?.let { kcalOf(it) } ?: return
        dao.insertPortions(
            profileIds.mapNotNull { profileId ->
                val plan = plans[profileId] ?: return@mapNotNull null
                val factor = MenuGenerator.portionFactor(
                    plan.courseKcal(slot, course, courses),
                    recipeKcal
                )
                MealPortionEntity(mealId, profileId, factor, factor)
            }
        )
    }

    // ── Ad-hoc intake ───────────────────────────────────────────────────

    override suspend fun logIntake(
        profileId: Long,
        dayStart: Long,
        label: String,
        nutrition: NutritionValues,
        grams: Double?
    ) {
        database.withTransaction {
            dao.insertIntake(
                IntakeEntryEntity(
                    profileId = profileId,
                    dayStart = dayStart,
                    label = label,
                    grams = grams,
                    nutrition = nutrition
                )
            )
            rebalanceDay(profileId, dayStart)
        }
    }

    override suspend fun deleteIntake(entryId: Long, profileId: Long, dayStart: Long) {
        database.withTransaction {
            dao.deleteIntake(entryId)
            rebalanceDay(profileId, dayStart)
        }
    }

    /**
     * Re-portions one person's remaining meals for a day. Only their rows of
     * `meal_portions` are touched, so a shared dish still feeds everyone else
     * exactly as planned.
     */
    private suspend fun rebalanceDay(profileId: Long, dayStart: Long) {
        val menu = dao.getMenu(WeekUtils.weekStartOf(dayStart)) ?: return
        val day = WeekUtils.dayIndexOf(dayStart)
        val mine = MenuResolver.forProfile(
            items = dao.getMealsWithPortions(menu.id).filter { it.meal.dayOfWeek == day },
            profileId = profileId,
            meal = PlannedMealWithPortions::meal
        )

        val kcalCache = mutableMapOf<Long, Double>()
        val portions = mine.mapNotNull { row ->
            val portion = row.portions.firstOrNull { it.profileId == profileId }
                ?: return@mapNotNull null
            val recipeId = row.meal.recipeId ?: return@mapNotNull null
            IntakeAdjuster.PlannedPortion(
                plannedMealId = row.meal.id,
                slot = row.meal.slot,
                baseFactor = portion.baseFactor,
                factor = portion.factor,
                recipeKcal = kcalCache.getOrPut(recipeId) { kcalOf(recipeId) },
                isFree = row.meal.isFree
            )
        }
        if (portions.isEmpty()) return

        val logged = dao.getIntake(profileId, dayStart).sumOf { it.nutrition.kcal }
        val updates =
            if (logged <= 0.0) IntakeAdjuster.resetToBase(portions)
            else IntakeAdjuster.rebalance(portions, logged, WeekUtils.hourWithin(dayStart))

        updates.forEach { (mealId, factor) -> dao.setPortionFactor(mealId, profileId, factor) }
    }

    override suspend fun dailyPlanFor(profileId: Long, weekStart: Long): DailyPlan? {
        val menu = dao.getMenu(weekStart) ?: return null
        return plansFor(menu).firstOrNull { it.profileId == profileId }
    }

    // ── Shared helpers ──────────────────────────────────────────────────

    private suspend fun buildPlans(
        slots: List<MealSlot>,
        fallbackWeights: Map<MealSlot, Float>
    ): List<DailyPlan> {
        val rows = dao.getAllMealPlans().groupBy { it.profileId }
        return dao.getProfiles().map { profile ->
            DailyPlan.of(profile, rows[profile.id].orEmpty(), slots, fallbackWeights)
        }
    }

    private suspend fun plansFor(menu: WeeklyMenuEntity): List<DailyPlan> {
        val weights = menu.slotWeights()
        return buildPlans(weights.keys.sortedBy { it.ordinal }, weights)
    }

    private fun coursesOf(menu: WeeklyMenuEntity, slot: MealSlot): List<MealCourse> =
        menu.slotCourses()[slot]?.takeIf { it.isNotEmpty() } ?: listOf(MealCourse.SINGLE)

    private suspend fun kcalOf(recipeId: Long): Double =
        dao.getIngredientsOf(recipeId).sumOf { it.nutrition.kcal }

    /**
     * Rebuilds a week's shopping list from what is now planned.
     *
     * Driven by the planned portions rather than the adjusted ones — see
     * [ShoppingListCalculator] — and free slots drop out for free, having no
     * recipe to buy for.
     */
    private suspend fun recomputeShoppingList(menuId: Long) {
        val meals = dao.getMealsWithPortions(menuId)
        val recipeIds = meals.mapNotNull { it.meal.recipeId }.distinct()
        val ingredients = if (recipeIds.isEmpty()) {
            emptyMap()
        } else {
            dao.getIngredientsOfRecipes(recipeIds)
                .groupBy { it.recipeId }
                .mapValues { (_, rows) -> rows.map { it.ingredientWithQuantity() } }
        }
        dao.rewriteShoppingList(
            menuId = menuId,
            totals = ShoppingListCalculator.totals(
                demands = meals.map { row ->
                    ShoppingListCalculator.Demand(
                        recipeId = row.meal.recipeId,
                        householdFactor = row.portions.sumOf { it.baseFactor }
                    )
                },
                ingredientsByRecipe = ingredients
            )
        )
    }
}
