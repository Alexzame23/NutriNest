package com.zame.nutrinest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.MealPortionEntity
import com.zame.nutrinest.data.local.entity.PlannedMealEntity
import com.zame.nutrinest.data.local.entity.PlannedMealWithRecipe
import com.zame.nutrinest.data.local.entity.PortionWithProfile
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.local.entity.RecipeStepEntity
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.local.entity.ShoppingItemWithIngredient
import com.zame.nutrinest.data.local.entity.WeeklyMenuEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {

    // ── Menus ───────────────────────────────────────────────────────────

    @Query("SELECT * FROM weekly_menus WHERE weekStart = :weekStart")
    fun observeMenu(weekStart: Long): Flow<WeeklyMenuEntity?>

    @Query("SELECT * FROM weekly_menus WHERE weekStart = :weekStart")
    suspend fun getMenu(weekStart: Long): WeeklyMenuEntity?

    @Query("DELETE FROM weekly_menus WHERE weekStart = :weekStart")
    suspend fun deleteMenu(weekStart: Long)

    @Insert
    suspend fun insertMenu(menu: WeeklyMenuEntity): Long

    // ── Meals ───────────────────────────────────────────────────────────

    @Transaction
    @Query("SELECT * FROM planned_meals WHERE menuId = :menuId ORDER BY dayOfWeek, slot")
    fun observeMeals(menuId: Long): Flow<List<PlannedMealWithRecipe>>

    @Transaction
    @Query("SELECT * FROM planned_meals WHERE id = :mealId")
    fun observeMeal(mealId: Long): Flow<PlannedMealWithRecipe?>

    @Query("SELECT DISTINCT recipeId FROM planned_meals WHERE menuId = :menuId")
    suspend fun recipeIdsOfMenu(menuId: Long): List<Long>

    @Insert
    suspend fun insertMeals(meals: List<PlannedMealEntity>): List<Long>

    // ── Portions ────────────────────────────────────────────────────────

    @Transaction
    @Query("SELECT * FROM meal_portions WHERE plannedMealId = :mealId")
    fun observePortions(mealId: Long): Flow<List<PortionWithProfile>>

    @Insert
    suspend fun insertPortions(portions: List<MealPortionEntity>)

    // ── Shopping list ───────────────────────────────────────────────────

    @Transaction
    @Query(
        """
        SELECT si.* FROM shopping_items si
        INNER JOIN weekly_menus m ON m.id = si.menuId
        WHERE m.weekStart = :weekStart
        """
    )
    fun observeShoppingItems(weekStart: Long): Flow<List<ShoppingItemWithIngredient>>

    @Query("UPDATE shopping_items SET purchased = :purchased WHERE id = :itemId")
    suspend fun setPurchased(itemId: Long, purchased: Boolean)

    @Insert
    suspend fun insertShoppingItems(items: List<ShoppingItemEntity>)

    /** Ingredient ids that appeared on a week's shopping list (for leftovers). */
    @Query(
        """
        SELECT DISTINCT si.ingredientId FROM shopping_items si
        INNER JOIN weekly_menus m ON m.id = si.menuId
        WHERE m.weekStart = :weekStart
        """
    )
    suspend fun ingredientIdsOfWeek(weekStart: Long): List<Long>

    // ── Inputs for the generator ────────────────────────────────────────

    @Query("SELECT * FROM recipes")
    suspend fun getAllRecipes(): List<RecipeEntity>

    @Query(
        """
        SELECT i.*, ri.grams FROM ingredients i
        INNER JOIN recipe_ingredients ri ON ri.ingredientId = i.id
        WHERE ri.recipeId = :recipeId
        """
    )
    suspend fun getIngredientsOf(recipeId: Long): List<IngredientWithQuantity>

    @Query("SELECT * FROM recipe_steps WHERE recipeId = :recipeId ORDER BY position")
    fun observeSteps(recipeId: Long): Flow<List<RecipeStepEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAt")
    suspend fun getProfiles(): List<ProfileEntity>

    // ── Atomic full replace ─────────────────────────────────────────────

    /**
     * Replaces the menu for [weekStart] in one transaction: deletes the old
     * one (cascading meals/portions/shopping items) and writes the new plan.
     */
    @Transaction
    suspend fun replaceMenu(
        weekStart: Long,
        slotsSpec: String,
        meals: List<PlannedMealEntity>,
        portionsPerMeal: List<List<MealPortionEntity>>,
        shoppingItems: List<ShoppingItemEntity>
    ): Long {
        deleteMenu(weekStart)
        val menuId = insertMenu(WeeklyMenuEntity(weekStart = weekStart, slotsSpec = slotsSpec))
        val mealIds = insertMeals(meals.map { it.copy(menuId = menuId) })
        val allPortions = mealIds.zip(portionsPerMeal).flatMap { (mealId, portions) ->
            portions.map { it.copy(plannedMealId = mealId) }
        }
        insertPortions(allPortions)
        insertShoppingItems(shoppingItems.map { it.copy(menuId = menuId) })
        return menuId
    }
}
