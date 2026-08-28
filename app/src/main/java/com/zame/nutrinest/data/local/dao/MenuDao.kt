package com.zame.nutrinest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.IntakeEntryEntity
import com.zame.nutrinest.data.local.entity.MealPortionEntity
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.MenuScope
import com.zame.nutrinest.data.local.entity.PlannedMealEntity
import com.zame.nutrinest.data.local.entity.PlannedMealWithPortions
import com.zame.nutrinest.data.local.entity.PlannedMealWithRecipe
import com.zame.nutrinest.data.local.entity.PortionWithProfile
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.ProfileMealPlanEntity
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

    @Query("SELECT * FROM weekly_menus WHERE id = :menuId")
    suspend fun getMenuById(menuId: Long): WeeklyMenuEntity?

    @Query("DELETE FROM weekly_menus WHERE weekStart = :weekStart")
    suspend fun deleteMenu(weekStart: Long)

    @Insert
    suspend fun insertMenu(menu: WeeklyMenuEntity): Long

    // ── Meals ───────────────────────────────────────────────────────────

    @Transaction
    @Query("SELECT * FROM planned_meals WHERE menuId = :menuId ORDER BY dayOfWeek, slot, course")
    fun observeMeals(menuId: Long): Flow<List<PlannedMealWithRecipe>>

    @Transaction
    @Query("SELECT * FROM planned_meals WHERE id = :mealId")
    fun observeMeal(mealId: Long): Flow<PlannedMealWithRecipe?>

    @Query("SELECT * FROM planned_meals WHERE id = :mealId")
    suspend fun getMeal(mealId: Long): PlannedMealEntity?

    @Query("SELECT * FROM planned_meals WHERE menuId = :menuId")
    suspend fun getMeals(menuId: Long): List<PlannedMealEntity>

    @Transaction
    @Query("SELECT * FROM planned_meals WHERE menuId = :menuId")
    suspend fun getMealsWithPortions(menuId: Long): List<PlannedMealWithPortions>

    @Transaction
    @Query("SELECT * FROM planned_meals WHERE menuId = :menuId AND dayOfWeek = :day ORDER BY slot")
    fun observeDay(menuId: Long, day: Int): Flow<List<PlannedMealWithPortions>>

    /** Everything planned for one (day, slot), household row and overrides alike. */
    @Query(
        "SELECT * FROM planned_meals " +
            "WHERE menuId = :menuId AND dayOfWeek = :day AND slot = :slot"
    )
    suspend fun getMealsAt(menuId: Long, day: Int, slot: MealSlot): List<PlannedMealEntity>

    /** Meals the user pinned, so a regeneration can keep them. */
    @Query("SELECT * FROM planned_meals WHERE menuId = :menuId AND isLocked = 1")
    suspend fun lockedMeals(menuId: Long): List<PlannedMealEntity>

    @Query("SELECT DISTINCT recipeId FROM planned_meals WHERE menuId = :menuId")
    suspend fun recipeIdsOfMenu(menuId: Long): List<Long>

    @Insert
    suspend fun insertMeals(meals: List<PlannedMealEntity>): List<Long>

    @Insert
    suspend fun insertMeal(meal: PlannedMealEntity): Long

    @Update
    suspend fun updateMeal(meal: PlannedMealEntity)

    @Query("DELETE FROM planned_meals WHERE id = :mealId")
    suspend fun deleteMeal(mealId: Long)

    @Query("UPDATE planned_meals SET isLocked = :locked WHERE id = :mealId")
    suspend fun setMealLocked(mealId: Long, locked: Boolean)

    // ── Portions ────────────────────────────────────────────────────────

    @Transaction
    @Query("SELECT * FROM meal_portions WHERE plannedMealId = :mealId")
    fun observePortions(mealId: Long): Flow<List<PortionWithProfile>>

    @Query("SELECT * FROM meal_portions WHERE plannedMealId = :mealId")
    suspend fun getPortions(mealId: Long): List<MealPortionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortions(portions: List<MealPortionEntity>)

    @Query("DELETE FROM meal_portions WHERE plannedMealId = :mealId AND profileId = :profileId")
    suspend fun deletePortion(mealId: Long, profileId: Long)

    @Query(
        "UPDATE meal_portions SET factor = :factor " +
            "WHERE plannedMealId = :mealId AND profileId = :profileId"
    )
    suspend fun setPortionFactor(mealId: Long, profileId: Long, factor: Double)

    // ── Ad-hoc intake ───────────────────────────────────────────────────

    @Query(
        "SELECT * FROM intake_entries WHERE profileId = :profileId AND dayStart = :dayStart " +
            "ORDER BY loggedAt"
    )
    fun observeIntake(profileId: Long, dayStart: Long): Flow<List<IntakeEntryEntity>>

    @Query("SELECT * FROM intake_entries WHERE profileId = :profileId AND dayStart = :dayStart")
    suspend fun getIntake(profileId: Long, dayStart: Long): List<IntakeEntryEntity>

    @Insert
    suspend fun insertIntake(entry: IntakeEntryEntity): Long

    @Query("DELETE FROM intake_entries WHERE id = :entryId")
    suspend fun deleteIntake(entryId: Long)

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

    @Query("SELECT ingredientId FROM shopping_items WHERE menuId = :menuId AND purchased = 1")
    suspend fun purchasedIngredientIds(menuId: Long): List<Long>

    @Query("DELETE FROM shopping_items WHERE menuId = :menuId")
    suspend fun deleteShoppingItems(menuId: Long)

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

    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    suspend fun getRecipe(recipeId: Long): RecipeEntity?

    @Query(
        """
        SELECT i.*, ri.grams, ri.quantityUnit FROM ingredients i
        INNER JOIN recipe_ingredients ri ON ri.ingredientId = i.id
        WHERE ri.recipeId = :recipeId
        """
    )
    suspend fun getIngredientsOf(recipeId: Long): List<IngredientWithQuantity>

    @Query("SELECT * FROM recipe_steps WHERE recipeId = :recipeId ORDER BY position")
    fun observeSteps(recipeId: Long): Flow<List<RecipeStepEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAt")
    suspend fun getProfiles(): List<ProfileEntity>

    @Query("SELECT * FROM profiles ORDER BY createdAt")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile_meal_plans")
    suspend fun getAllMealPlans(): List<ProfileMealPlanEntity>

    @Query("SELECT * FROM profile_meal_plans WHERE profileId = :profileId")
    suspend fun getMealPlan(profileId: Long): List<ProfileMealPlanEntity>

    // ── Atomic writes ───────────────────────────────────────────────────

    /**
     * Replaces the menu for [weekStart] in one transaction: deletes the old
     * one (cascading meals/portions/shopping items) and writes the new plan.
     */
    @Transaction
    suspend fun replaceMenu(
        weekStart: Long,
        slotsSpec: String,
        scope: MenuScope,
        coursesSpec: String,
        meals: List<PlannedMealEntity>,
        portionsPerMeal: List<List<MealPortionEntity>>,
        shoppingItems: List<ShoppingItemEntity>
    ): Long {
        require(meals.size == portionsPerMeal.size) {
            "Each planned meal must have a matching portion list"
        }
        require(meals.all { it.dayOfWeek in 0..6 }) { "Invalid menu day" }
        require(portionsPerMeal.flatten().all { it.factor.isFinite() && it.factor > 0.0 }) {
            "Portion factors must be finite and positive"
        }
        require(shoppingItems.all { it.grams.isFinite() && it.grams > 0.0 }) {
            "Shopping quantities must be finite and positive"
        }
        deleteMenu(weekStart)
        val menuId = insertMenu(
            WeeklyMenuEntity(
                weekStart = weekStart,
                slotsSpec = slotsSpec,
                scope = scope,
                coursesSpec = coursesSpec
            )
        )
        val mealIds = insertMeals(meals.map { it.copy(menuId = menuId) })
        val allPortions = mealIds.zip(portionsPerMeal).flatMap { (mealId, portions) ->
            portions.map { it.copy(plannedMealId = mealId) }
        }
        insertPortions(allPortions)
        insertShoppingItems(shoppingItems.map { it.copy(menuId = menuId) })
        return menuId
    }

    /**
     * Rewrites a menu's shopping list, keeping the ticks: an ingredient the
     * user already bought stays bought even when a meal swap changes how much
     * of it the week needs.
     */
    @Transaction
    suspend fun rewriteShoppingList(menuId: Long, totals: Map<Long, Double>) {
        val purchased = purchasedIngredientIds(menuId).toSet()
        deleteShoppingItems(menuId)
        insertShoppingItems(
            totals.map { (ingredientId, grams) ->
                ShoppingItemEntity(
                    menuId = menuId,
                    ingredientId = ingredientId,
                    grams = grams,
                    purchased = ingredientId in purchased
                )
            }
        )
    }
}
