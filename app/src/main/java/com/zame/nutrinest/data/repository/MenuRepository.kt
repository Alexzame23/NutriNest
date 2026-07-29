package com.zame.nutrinest.data.repository

import com.zame.nutrinest.data.local.dao.MenuDao
import com.zame.nutrinest.data.local.entity.MealPortionEntity
import com.zame.nutrinest.data.local.entity.PlannedMealEntity
import com.zame.nutrinest.data.local.entity.PlannedMealWithRecipe
import com.zame.nutrinest.data.local.entity.PortionWithProfile
import com.zame.nutrinest.data.local.entity.RecipeStepEntity
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.local.entity.ShoppingItemWithIngredient
import com.zame.nutrinest.data.local.entity.WeeklyMenuEntity
import com.zame.nutrinest.domain.planner.GenerationConfig
import com.zame.nutrinest.domain.planner.GenerationResult
import com.zame.nutrinest.domain.planner.MenuGenerator
import com.zame.nutrinest.domain.planner.PlannableRecipe
import com.zame.nutrinest.util.WeekUtils
import kotlinx.coroutines.flow.Flow

interface MenuRepository {
    fun observeMenu(weekStart: Long): Flow<WeeklyMenuEntity?>
    fun observeMeals(menuId: Long): Flow<List<PlannedMealWithRecipe>>
    fun observeMeal(mealId: Long): Flow<PlannedMealWithRecipe?>
    fun observePortions(mealId: Long): Flow<List<PortionWithProfile>>
    fun observeSteps(recipeId: Long): Flow<List<RecipeStepEntity>>
    fun observeShoppingItems(weekStart: Long): Flow<List<ShoppingItemWithIngredient>>
    suspend fun setPurchased(itemId: Long, purchased: Boolean)
    suspend fun getIngredientsOf(recipeId: Long): List<com.zame.nutrinest.data.local.entity.IngredientWithQuantity>
    suspend fun hasMenu(weekStart: Long): Boolean

    /** Runs the generator for [weekStart] and persists the result. */
    suspend fun generate(weekStart: Long, config: GenerationConfig): GenerationResult
}

class LocalMenuRepository(
    private val dao: MenuDao,
    private val generator: MenuGenerator = MenuGenerator()
) : MenuRepository {

    override fun observeMenu(weekStart: Long) = dao.observeMenu(weekStart)
    override fun observeMeals(menuId: Long) = dao.observeMeals(menuId)
    override fun observeMeal(mealId: Long) = dao.observeMeal(mealId)
    override fun observePortions(mealId: Long) = dao.observePortions(mealId)
    override fun observeSteps(recipeId: Long) = dao.observeSteps(recipeId)
    override fun observeShoppingItems(weekStart: Long) = dao.observeShoppingItems(weekStart)
    override suspend fun setPurchased(itemId: Long, purchased: Boolean) =
        dao.setPurchased(itemId, purchased)
    override suspend fun getIngredientsOf(recipeId: Long) = dao.getIngredientsOf(recipeId)
    override suspend fun hasMenu(weekStart: Long) = dao.getMenu(weekStart) != null

    override suspend fun generate(
        weekStart: Long,
        config: GenerationConfig
    ): GenerationResult {
        val profiles = dao.getProfiles()
        val recipes = dao.getAllRecipes().map { recipe ->
            PlannableRecipe(recipe, dao.getIngredientsOf(recipe.id))
        }

        // Context for variety + leftovers: what last week used/bought.
        val previousWeek = WeekUtils.previousWeekStart(weekStart)
        val previousMenu = dao.getMenu(previousWeek)
        val recentRecipeIds = previousMenu
            ?.let { dao.recipeIdsOfMenu(it.id).toSet() } ?: emptySet()
        val leftoverIngredientIds = dao.ingredientIdsOfWeek(previousWeek).toSet()

        val result = generator.generate(
            profiles = profiles,
            recipes = recipes,
            config = config,
            recentRecipeIds = recentRecipeIds,
            leftoverIngredientIds = leftoverIngredientIds
        )

        if (result is GenerationResult.Success) {
            val menu = result.menu
            dao.replaceMenu(
                weekStart = weekStart,
                slotsSpec = WeeklyMenuEntity.encodeSlots(config.slotWeights),
                meals = menu.meals.map {
                    PlannedMealEntity(
                        menuId = 0, // filled by replaceMenu
                        dayOfWeek = it.dayOfWeek,
                        slot = it.slot,
                        recipeId = it.recipeId,
                        isLocked = it.isLocked,
                        isCheatMeal = it.isCheatMeal
                    )
                },
                portionsPerMeal = menu.meals.map { meal ->
                    meal.portions.map { (profileId, factor) ->
                        MealPortionEntity(
                            plannedMealId = 0, // filled by replaceMenu
                            profileId = profileId,
                            factor = factor
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
}
