package com.zame.nutrinest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zame.nutrinest.data.local.entity.IngredientAliasEntity
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.IngredientNutrientEntity
import com.zame.nutrinest.data.local.entity.IngredientPortionEntity
import com.zame.nutrinest.data.local.entity.IngredientWithDetails
import com.zame.nutrinest.data.local.entity.NutrientValueWithDef
import com.zame.nutrinest.data.local.entity.RecipeUsage
import kotlinx.coroutines.flow.Flow

/** A nutrient tag and its value, for callers that only need the numbers. */
data class TagValue(val tag: String, val value: Double)

/**
 * Reads and writes for ingredients.
 *
 * Filtering happens here; ordering and grouping happen in the ViewModel with a
 * [java.text.Collator], because SQLite's `COLLATE NOCASE` only folds ASCII and
 * would misplace accented names for a Spanish user.
 */
@Dao
interface IngredientDao {

    @Query("SELECT * FROM ingredients WHERE sourceKey IS NOT 'BEDCA:2494' AND id NOT IN (SELECT ingredient_id FROM ingredient_catalog_status WHERE state='HIDDEN') ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<IngredientEntity>>

    @Query(
        """
        SELECT * FROM ingredients
        WHERE name LIKE '%' || :query || '%'
          AND sourceKey IS NOT 'BEDCA:2494' AND id NOT IN (SELECT ingredient_id FROM ingredient_catalog_status WHERE state='HIDDEN')
        ORDER BY isCustom DESC, name COLLATE NOCASE
        """
    )
    fun search(query: String): Flow<List<IngredientEntity>>

    /**
     * The ingredient browser's main query.
     *
     * [normalizedQuery] must already be accent-stripped and lowercased (see
     * `TextNormalizer`); it is matched against the English name, the Spanish
     * name and every synonym, so "capsicum" finds "bell pepper" whichever
     * language the device is in.
     *
     * [categoryKeys] is never empty — when the user has selected no category
     * the repository passes all of them, since `IN ()` is not valid SQLite.
     */
    @Query(
        """
        SELECT * FROM ingredients
        WHERE foodType IN (:categoryKeys)
          AND sourceKey IS NOT 'BEDCA:2494' AND id NOT IN (SELECT ingredient_id FROM ingredient_catalog_status WHERE state='HIDDEN')
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:customOnly = 0 OR isCustom = 1)
          AND (
                :normalizedQuery = ''
                OR nameNorm LIKE '%' || :normalizedQuery || '%'
                OR nameEsNorm LIKE '%' || :normalizedQuery || '%'
                OR id IN (
                    SELECT ingredientId FROM ingredient_aliases
                    WHERE aliasNorm LIKE '%' || :normalizedQuery || '%'
                )
          )
        """
    )
    fun observeFiltered(
        normalizedQuery: String,
        categoryKeys: List<String>,
        favoritesOnly: Int,
        customOnly: Int
    ): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE isFavorite = 1 AND sourceKey IS NOT 'BEDCA:2494' AND id NOT IN (SELECT ingredient_id FROM ingredient_catalog_status WHERE state='HIDDEN')")
    fun observeFavorites(): Flow<List<IngredientEntity>>

    /** Most recently used, for the "Recent" view and the recipe picker. */
    @Query(
        """
        SELECT * FROM ingredients
        WHERE lastUsedAt IS NOT NULL AND sourceKey IS NOT 'BEDCA:2494' AND id NOT IN (SELECT ingredient_id FROM ingredient_catalog_status WHERE state='HIDDEN')
        ORDER BY lastUsedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyUsed(limit: Int): Flow<List<IngredientEntity>>

    /** Newest user creations first; imported rows all share one import stamp. */
    @Query(
        """
        SELECT * FROM ingredients
        WHERE isCustom = 1 AND id NOT IN (SELECT ingredient_id FROM ingredient_catalog_status WHERE state='HIDDEN')
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyAdded(limit: Int): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    fun observeById(id: Long): Flow<IngredientEntity?>

    @Transaction
    @Query("SELECT * FROM ingredients WHERE id = :id")
    fun observeDetails(id: Long): Flow<IngredientWithDetails?>

    @Query("SELECT * FROM ingredients WHERE id IN (:ids)")
    fun observeByIds(ids: List<Long>): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    suspend fun findById(id: Long): IngredientEntity?

    // ── Nutrients ───────────────────────────────────────────────────────

    /**
     * Every recorded nutrient for an ingredient, joined to its definition so
     * the screen has the label, unit, group and daily reference value to hand.
     */
    @Query(
        """
        SELECT n.tag AS tag, n.value AS value,
               d.nameEn AS nameEn, d.nameEs AS nameEs, d.unit AS unit,
               d.nutrientGroup AS nutrientGroup, d.isPrimary AS isPrimary,
               d.sortOrder AS sortOrder, d.nrv AS nrv
        FROM ingredient_nutrients n
        JOIN nutrient_defs d ON d.tag = n.tag
        WHERE n.ingredientId = :ingredientId
        ORDER BY d.nutrientGroup, d.sortOrder
        """
    )
    fun observeNutrients(ingredientId: Long): Flow<List<NutrientValueWithDef>>

    @Query("SELECT tag, value FROM ingredient_nutrients WHERE ingredientId = :ingredientId")
    suspend fun nutrientsOf(ingredientId: Long): List<TagValue>

    @Query(
        """
        SELECT tag, value FROM ingredient_nutrients
        WHERE ingredientId = :ingredientId AND tag IN (:tags)
        """
    )
    fun observeNutrientValues(ingredientId: Long, tags: List<String>): Flow<List<TagValue>>

    // ── Usage ───────────────────────────────────────────────────────────

    @Query(
        """
        SELECT r.id AS recipeId, r.name AS recipeName, r.nameEs AS recipeNameEs,
               ri.grams AS grams
        FROM recipe_ingredients ri
        JOIN recipes r ON r.id = ri.recipeId
        WHERE ri.ingredientId = :ingredientId
        ORDER BY r.name COLLATE NOCASE
        """
    )
    fun observeRecipesUsing(ingredientId: Long): Flow<List<RecipeUsage>>

    /**
     * How many rows still point at this ingredient. Both foreign keys are
     * RESTRICT, so deleting while this is non-zero would throw.
     */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM recipe_ingredients WHERE ingredientId = :ingredientId)
             + (SELECT COUNT(*) FROM shopping_items WHERE ingredientId = :ingredientId)
             + (SELECT COUNT(*) FROM ingredient_canonical_map WHERE ingredient_id = :ingredientId)
        """
    )
    suspend fun countReferences(ingredientId: Long): Int

    @Query("UPDATE ingredients SET usageCount = usageCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)

    @Query("UPDATE ingredients SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    // ── Duplicate detection ─────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM ingredients WHERE name = :name COLLATE NOCASE AND id NOT IN (SELECT ingredient_id FROM ingredient_catalog_status WHERE state='HIDDEN')")
    suspend fun countByName(name: String): Int

    /** Exact normalised match in either language, ignoring the row being edited. */
    @Query(
        """
        SELECT COUNT(*) FROM ingredients
        WHERE (nameNorm = :normalizedName OR nameEsNorm = :normalizedName)
          AND id != :excludingId AND id NOT IN (SELECT ingredient_id FROM ingredient_catalog_status WHERE state='HIDDEN')
        """
    )
    suspend fun countByNormalizedName(normalizedName: String, excludingId: Long): Int

    /**
     * Candidates for the "did you mean" warning: anything whose name contains
     * the new one or is contained by it. The similarity ranking itself happens
     * in Kotlin, where `TextNormalizer.similarity` can be applied.
     */
    @Query(
        """
        SELECT * FROM ingredients
        WHERE id != :excludingId AND id NOT IN (SELECT ingredient_id FROM ingredient_catalog_status WHERE state='HIDDEN')
          AND (
                nameNorm LIKE '%' || :normalizedName || '%'
                OR :normalizedName LIKE '%' || nameNorm || '%'
                OR nameEsNorm LIKE '%' || :normalizedName || '%'
          )
        LIMIT 20
        """
    )
    suspend fun findSimilar(normalizedName: String, excludingId: Long): List<IngredientEntity>

    // ── Writes ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(ingredient: IngredientEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(ingredients: List<IngredientEntity>)

    @Update
    suspend fun update(ingredient: IngredientEntity)

    @Delete
    suspend fun delete(ingredient: IngredientEntity)

    @Query("DELETE FROM ingredients WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Checks references and deletes without a race between the two operations. */
    @Transaction
    suspend fun deleteIfUnused(id: Long): Boolean {
        if (countReferences(id) > 0) return false
        deleteById(id)
        return true
    }

    @Query("SELECT COUNT(*) FROM ingredients")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNutrients(nutrients: List<IngredientNutrientEntity>)

    @Query("DELETE FROM ingredient_nutrients WHERE ingredientId = :ingredientId")
    suspend fun clearNutrients(ingredientId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPortions(portions: List<IngredientPortionEntity>)

    @Query("DELETE FROM ingredient_portions WHERE ingredientId = :ingredientId")
    suspend fun clearPortions(ingredientId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAliases(aliases: List<IngredientAliasEntity>)

    @Query("DELETE FROM ingredient_aliases WHERE ingredientId = :ingredientId")
    suspend fun clearAliases(ingredientId: Long)

    /**
     * Saves an ingredient together with its nutrients, portions and synonyms,
     * so a failure part-way through cannot leave a food carrying someone else's
     * vitamin panel.
     */
    @Transaction
    suspend fun upsertFull(
        ingredient: IngredientEntity,
        nutrients: List<IngredientNutrientEntity>,
        portions: List<IngredientPortionEntity>,
        aliases: List<IngredientAliasEntity>
    ): Long {
        val id = if (ingredient.id == 0L) {
            insert(ingredient)
        } else {
            update(ingredient)
            ingredient.id
        }
        clearNutrients(id)
        clearPortions(id)
        clearAliases(id)
        insertNutrients(nutrients.map { it.copy(ingredientId = id) })
        insertPortions(portions.map { it.copy(id = 0, ingredientId = id) })
        insertAliases(aliases.map { it.copy(id = 0, ingredientId = id) })
        return id
    }
}
