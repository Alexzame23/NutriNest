package com.zame.nutrinest.data.repository

import com.zame.nutrinest.data.local.dao.IngredientDao
import com.zame.nutrinest.data.local.dao.RecipeDao
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.local.entity.RecipeWithSteps
import kotlinx.coroutines.flow.Flow

/**
 * Repositories abstract WHERE data comes from. Today: Room only.
 * When community recipes arrive, add a remote data source (e.g. Retrofit +
 * Firestore) behind these same interfaces — ViewModels won't change.
 */
interface IngredientRepository {
    fun observeAll(): Flow<List<IngredientEntity>>
    fun search(query: String): Flow<List<IngredientEntity>>
    suspend fun existsByName(name: String): Boolean
    suspend fun create(ingredient: IngredientEntity): Long
}

interface RecipeRepository {
    fun observeAll(): Flow<List<RecipeEntity>>
    fun observeFolders(): Flow<List<String>>
    fun observeRecipeWithSteps(id: Long): Flow<RecipeWithSteps?>
    fun observeIngredientsOf(recipeId: Long): Flow<List<IngredientWithQuantity>>
    suspend fun existsByName(name: String): Boolean
    suspend fun create(
        recipe: RecipeEntity,
        ingredients: List<Pair<Long, Double>>,
        steps: List<Pair<String, String?>>
    ): Long
}

class LocalIngredientRepository(private val dao: IngredientDao) : IngredientRepository {
    override fun observeAll() = dao.observeAll()
    override fun search(query: String) = dao.search(query)
    override suspend fun existsByName(name: String) = dao.countByName(name.trim()) > 0
    override suspend fun create(ingredient: IngredientEntity) = dao.insert(ingredient)
}

class LocalRecipeRepository(private val dao: RecipeDao) : RecipeRepository {
    override fun observeAll() = dao.observeAll()
    override fun observeFolders() = dao.observeFolders()
    override fun observeRecipeWithSteps(id: Long) = dao.observeRecipeWithSteps(id)
    override fun observeIngredientsOf(recipeId: Long) = dao.observeIngredientsOf(recipeId)
    override suspend fun existsByName(name: String) = dao.countByName(name.trim()) > 0
    override suspend fun create(
        recipe: RecipeEntity,
        ingredients: List<Pair<Long, Double>>,
        steps: List<Pair<String, String?>>
    ) = dao.insertFullRecipe(recipe, ingredients, steps)
}
