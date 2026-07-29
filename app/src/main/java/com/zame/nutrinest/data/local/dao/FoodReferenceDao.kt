package com.zame.nutrinest.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.zame.nutrinest.data.local.entity.CategoryWithCount
import com.zame.nutrinest.data.local.entity.FoodDatasetMetaEntity
import com.zame.nutrinest.data.local.entity.IngredientCategoryEntity
import com.zame.nutrinest.data.local.entity.NutrientDefEntity
import com.zame.nutrinest.data.local.entity.ShoppingAisleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Read-only access to the reference tables the food dataset brings with it.
 * These are rewritten wholesale on import and never edited by the user.
 */
@Dao
interface FoodReferenceDao {

    @Query("SELECT * FROM ingredient_categories ORDER BY sortOrder")
    fun observeCategories(): Flow<List<IngredientCategoryEntity>>

    /** Categories with how many ingredients each currently holds. */
    @Query(
        """
        SELECT c.`key` AS `key`, c.nameEn AS nameEn, c.nameEs AS nameEs,
               c.colorHex AS colorHex, c.iconKey AS iconKey, c.sortOrder AS sortOrder,
               COUNT(i.id) AS ingredientCount
        FROM ingredient_categories c
        LEFT JOIN ingredients i ON i.foodType = c.`key`
        GROUP BY c.`key`
        ORDER BY c.sortOrder
        """
    )
    fun observeCategoriesWithCounts(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM shopping_aisles ORDER BY sortOrder")
    fun observeAisles(): Flow<List<ShoppingAisleEntity>>

    @Query("SELECT * FROM nutrient_defs ORDER BY nutrientGroup, sortOrder")
    fun observeNutrientDefs(): Flow<List<NutrientDefEntity>>

    @Query("SELECT * FROM nutrient_defs ORDER BY nutrientGroup, sortOrder")
    suspend fun nutrientDefs(): List<NutrientDefEntity>

    @Query("SELECT * FROM food_dataset_meta")
    fun observeDatasetMeta(): Flow<List<FoodDatasetMetaEntity>>

    @Query("SELECT value FROM food_dataset_meta WHERE `key` = :key")
    suspend fun meta(key: String): String?
}
