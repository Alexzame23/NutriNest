package com.zame.nutrinest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.local.entity.RecipeIngredientCrossRef
import com.zame.nutrinest.data.local.entity.RecipeStepEntity
import com.zame.nutrinest.data.local.entity.RecipeWithSteps
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
        SELECT i.*, ri.grams FROM ingredients i
        INNER JOIN recipe_ingredients ri ON ri.ingredientId = i.id
        WHERE ri.recipeId = :recipeId
        """
    )
    fun observeIngredientsOf(recipeId: Long): Flow<List<IngredientWithQuantity>>

    @Query("SELECT DISTINCT folder FROM recipes WHERE folder IS NOT NULL ORDER BY folder COLLATE NOCASE")
    fun observeFolders(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM recipes WHERE name = :name COLLATE NOCASE")
    suspend fun countByName(name: String): Int

    @Insert
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Insert
    suspend fun insertSteps(steps: List<RecipeStepEntity>)

    @Insert
    suspend fun insertCrossRefs(refs: List<RecipeIngredientCrossRef>)

    /**
     * Atomically saves a full recipe: header row, ingredient links, and steps.
     * If any insert fails the whole recipe is rolled back.
     */
    @Transaction
    suspend fun insertFullRecipe(
        recipe: RecipeEntity,
        ingredients: List<Pair<Long, Double>>, // ingredientId to grams
        steps: List<Pair<String, String?>>     // text to optional imagePath
    ): Long {
        val recipeId = insertRecipe(recipe)
        insertCrossRefs(
            ingredients.map { (ingredientId, grams) ->
                RecipeIngredientCrossRef(recipeId, ingredientId, grams)
            }
        )
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
