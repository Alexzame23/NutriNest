package com.zame.nutrinest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zame.nutrinest.data.local.entity.IngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {

    @Query("SELECT * FROM ingredients ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<IngredientEntity>>

    @Query(
        """
        SELECT * FROM ingredients
        WHERE name LIKE '%' || :query || '%'
        ORDER BY isCustom DESC, name COLLATE NOCASE
        """
    )
    fun search(query: String): Flow<List<IngredientEntity>>

    @Query("SELECT COUNT(*) FROM ingredients WHERE name = :name COLLATE NOCASE")
    suspend fun countByName(name: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(ingredient: IngredientEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(ingredients: List<IngredientEntity>)

    @Query("SELECT COUNT(*) FROM ingredients")
    suspend fun count(): Int
}
