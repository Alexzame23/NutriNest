package com.zame.nutrinest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.local.entity.RecipeIngredientCrossRef
import com.zame.nutrinest.data.local.entity.RecipeIngredientInput
import com.zame.nutrinest.data.local.entity.RecipeNutrition
import com.zame.nutrinest.data.local.entity.RecipeStepEntity
import com.zame.nutrinest.data.local.entity.RecipeWithSteps
import com.zame.nutrinest.domain.nutrition.RecipeQuantity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeRecipeWithSteps(id: Long): Flow<RecipeWithSteps?>

    @Query(
        """
        SELECT i.*, ri.grams, ri.quantityUnit, ri.originalQuantity, ri.quantityEvidenceId FROM ingredients i
        INNER JOIN recipe_ingredients ri ON ri.ingredientId = i.id
        WHERE ri.recipeId = :recipeId
        """
    )
    fun observeIngredientsOf(recipeId: Long): Flow<List<IngredientWithQuantity>>

    @Query("SELECT DISTINCT folder FROM recipes WHERE folder IS NOT NULL ORDER BY folder COLLATE NOCASE")
    fun observeFolders(): Flow<List<String>>

    /**
     * Per-recipe nutrition totals for the list rows. Ingredient figures are
     * stored per 100 g, hence the `grams / 100` scaling. Recipes with no
     * ingredients simply have no row.
     */
    @Query(
        """
        SELECT ri.recipeId AS recipeId,
               SUM(i.kcal * ri.grams / 100.0) AS kcal,
               SUM(i.carbs * ri.grams / 100.0) AS carbs,
               SUM(i.fats * ri.grams / 100.0) AS fats,
               SUM(i.protein * ri.grams / 100.0) AS protein
        FROM recipe_ingredients ri
        INNER JOIN ingredients i ON i.id = ri.ingredientId
        GROUP BY ri.recipeId
        """
    )
    fun observeNutritionTotals(): Flow<List<RecipeNutrition>>

    @Query("SELECT COUNT(*) FROM recipes WHERE name = :name COLLATE NOCASE")
    suspend fun countByName(name: String): Int

    @Query("UPDATE recipes SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /**
     * Planned meals hold a `RESTRICT` foreign key on the recipe, so a recipe in
     * a weekly menu cannot be deleted. Counted up front to explain why instead
     * of letting SQLite throw.
     */
    @Query("SELECT COUNT(*) FROM planned_meals WHERE recipeId = :recipeId")
    suspend fun countReferences(recipeId: Long): Int

    /** Steps and ingredient links go with it — both cascade. */
    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Checks menu references and deletes atomically. */
    @Transaction
    suspend fun deleteIfUnused(id: Long): Boolean {
        if (countReferences(id) > 0) return false
        deleteById(id)
        return true
    }

    @Insert
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Insert
    suspend fun insertSteps(steps: List<RecipeStepEntity>)

    @Insert
    suspend fun insertCrossRefs(refs: List<RecipeIngredientCrossRef>)

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId AND ingredientId = :ingredientId")
    suspend fun ingredientLink(recipeId: Long, ingredientId: Long): RecipeIngredientCrossRef?

    @Update
    suspend fun updateIngredientLink(ref: RecipeIngredientCrossRef)

    @Transaction
    suspend fun updateIngredientQuantity(
        recipeId: Long, previous: RecipeQuantity, input: RecipeIngredientInput
    ): Boolean {
        if (ingredientLink(recipeId, input.ingredientId)?.quantity != previous) return false
        updateIngredientLink(input.crossRef(recipeId))
        return true
    }

    /**
     * Atomically saves a full recipe: header row, ingredient links, and steps.
     * If any insert fails the whole recipe is rolled back.
     */
    @Transaction
    suspend fun insertFullRecipe(
        recipe: RecipeEntity,
        ingredients: List<RecipeIngredientInput>,
        steps: List<Pair<String, String?>>     // text to optional imagePath
    ): Long {
        require(ingredients.isNotEmpty()) { "A recipe needs at least one ingredient" }
        require(ingredients.all { it.grams.isFinite() && it.grams > 0.0 }) {
            "Ingredient quantities must be finite and positive"
        }
        require(steps.any { (text, _) -> text.isNotBlank() }) {
            "A recipe needs at least one non-empty step"
        }
        val recipeId = insertRecipe(recipe)
        insertCrossRefs(ingredients.map { it.crossRef(recipeId) })
        insertSteps(
            steps.mapIndexed { index, (text, imagePath) ->
                RecipeStepEntity(
                    recipeId = recipeId,
                    position = index,
                    text = text,
                    imagePath = imagePath
                )
            }
        )
        return recipeId
    }
}
