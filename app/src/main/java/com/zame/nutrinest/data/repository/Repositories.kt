package com.zame.nutrinest.data.repository

import androidx.room.withTransaction
import com.zame.nutrinest.data.local.NutriNestDatabase
import com.zame.nutrinest.data.local.CanonicalCatalogStore

import com.zame.nutrinest.data.local.dao.FoodReferenceDao
import com.zame.nutrinest.data.local.dao.IngredientDao
import com.zame.nutrinest.data.local.dao.RecipeDao
import com.zame.nutrinest.data.local.dao.TagValue
import com.zame.nutrinest.data.local.entity.CategoryWithCount
import com.zame.nutrinest.data.local.entity.FoodDatasetMetaEntity
import com.zame.nutrinest.data.local.entity.IngredientAliasEntity
import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.IngredientCategoryEntity
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.IngredientNutrientEntity
import com.zame.nutrinest.data.local.entity.IngredientPortionEntity
import com.zame.nutrinest.data.local.entity.IngredientWithDetails
import com.zame.nutrinest.data.local.entity.IngredientWithQuantity
import com.zame.nutrinest.data.local.entity.NutrientDefEntity
import com.zame.nutrinest.data.local.entity.NutrientValueWithDef
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.local.entity.RecipeIngredientInput
import com.zame.nutrinest.data.local.entity.RecipeUsage
import com.zame.nutrinest.data.local.entity.RecipeWithSteps
import com.zame.nutrinest.data.local.entity.ShoppingAisleEntity
import com.zame.nutrinest.util.TextNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Repositories abstract WHERE data comes from. Today: Room only.
 * When community recipes arrive, add a remote data source (e.g. Retrofit +
 * Firestore) behind these same interfaces — ViewModels won't change.
 */
interface IngredientRepository {
    fun observeAll(): Flow<List<IngredientEntity>>
    fun search(query: String): Flow<List<IngredientEntity>>

    /**
     * Browser query. [query] is normalised here so callers can pass raw user
     * input; an empty [categoryKeys] means "every category".
     */
    fun observeFiltered(
        query: String,
        categoryKeys: Set<String>,
        favoritesOnly: Boolean = false,
        customOnly: Boolean = false
    ): Flow<List<IngredientEntity>>

    fun observeRecentlyUsed(limit: Int = 12): Flow<List<IngredientEntity>>
    fun observeRecentlyAdded(limit: Int = 12): Flow<List<IngredientEntity>>
    fun observeById(id: Long): Flow<IngredientEntity?>
    fun observeDetails(id: Long): Flow<IngredientWithDetails?>
    fun observeByIds(ids: List<Long>): Flow<List<IngredientEntity>>
    fun observeNutrients(id: Long): Flow<List<NutrientValueWithDef>>
    fun observeRecipesUsing(id: Long): Flow<List<RecipeUsage>>

    suspend fun findById(id: Long): IngredientEntity?
    suspend fun nutrientsOf(id: Long): List<TagValue>
    suspend fun existsByName(name: String): Boolean

    /** Exact name clash, in either language, ignoring the row being edited. */
    suspend fun isDuplicateName(name: String, excludingId: Long = 0L): Boolean

    /** Near matches, best first, for the editor's "did you mean" warning. */
    suspend fun findSimilar(name: String, excludingId: Long = 0L): List<IngredientEntity>

    suspend fun create(ingredient: IngredientEntity): Long
    suspend fun save(
        ingredient: IngredientEntity,
        nutrients: List<IngredientNutrientEntity> = emptyList(),
        portions: List<IngredientPortionEntity> = emptyList(),
        aliases: List<IngredientAliasEntity> = emptyList()
    ): Long

    /** @return false when recipes or shopping lists still reference it. */
    suspend fun delete(id: Long): Boolean
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun markUsed(id: Long)
}

/** Reference data shipped with the food dataset. */
interface FoodReferenceRepository {
    fun observeCategories(): Flow<List<IngredientCategoryEntity>>
    fun observeCategoriesWithCounts(): Flow<List<CategoryWithCount>>
    fun observeAisles(): Flow<List<ShoppingAisleEntity>>
    fun observeNutrientDefs(): Flow<List<NutrientDefEntity>>
    fun observeDatasetMeta(): Flow<List<FoodDatasetMetaEntity>>
    suspend fun nutrientDefs(): List<NutrientDefEntity>
    suspend fun attribution(): String?
}

interface RecipeRepository {
    fun observeAll(): Flow<List<RecipeEntity>>
    fun observeFolders(): Flow<List<String>>
    fun observeRecipeWithSteps(id: Long): Flow<RecipeWithSteps?>
    fun observeIngredientsOf(recipeId: Long): Flow<List<IngredientWithQuantity>>

    /** Nutrition per recipe, keyed by recipe id, for list rows. */
    fun observeNutritionTotals(): Flow<Map<Long, NutritionValues>>
    suspend fun existsByName(name: String): Boolean
    suspend fun create(
        recipe: RecipeEntity,
        ingredients: List<RecipeIngredientInput>,
        steps: List<Pair<String, String?>>
    ): Long

    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** @return false when a weekly menu still plans this recipe. */
    suspend fun delete(id: Long): Boolean
}

class LocalIngredientRepository(private val database: NutriNestDatabase) : IngredientRepository {
    private val dao = database.ingredientDao()

    private fun Flow<List<IngredientEntity>>.withCatalogPresentation(): Flow<List<IngredientEntity>> =
        combine(dao.observeCatalogPresentation()) { items, presentation ->
            val byId = presentation.associateBy { it.ingredientId }
            items.map { item ->
                item.copy().apply {
                    byId[id]?.let { display ->
                        catalogNameEs = display.nameEs
                        catalogNameEn = display.nameEn
                        catalogRank = display.rank
                    }
                }
            }.sortedBy { it.catalogRank }
        }

    override fun observeAll() = dao.observeAll().withCatalogPresentation()

    override fun search(query: String) = dao.observeFiltered(TextNormalizer.normalize(query),
        IngredientCategories.ALL, 0, 0).withCatalogPresentation()

    override fun observeFiltered(
        query: String,
        categoryKeys: Set<String>,
        favoritesOnly: Boolean,
        customOnly: Boolean
    ): Flow<List<IngredientEntity>> = dao.observeFiltered(
        normalizedQuery = TextNormalizer.normalize(query),
        // `IN ()` is not valid SQLite, so "no filter" is spelled "all keys".
        categoryKeys = categoryKeys.ifEmpty { IngredientCategories.ALL.toSet() }.toList(),
        favoritesOnly = if (favoritesOnly) 1 else 0,
        customOnly = if (customOnly) 1 else 0
    ).withCatalogPresentation()

    override fun observeRecentlyUsed(limit: Int) = dao.observeRecentlyUsed(limit)
    override fun observeRecentlyAdded(limit: Int) = dao.observeRecentlyAdded(limit)
    override fun observeById(id: Long) = dao.observeById(id)
    override fun observeDetails(id: Long) = dao.observeDetails(id)
    override fun observeByIds(ids: List<Long>) = dao.observeByIds(ids)
    override fun observeNutrients(id: Long) = dao.observeNutrients(id)
    override fun observeRecipesUsing(id: Long) = dao.observeRecipesUsing(id)

    override suspend fun findById(id: Long) = dao.findById(id)
    override suspend fun nutrientsOf(id: Long) = dao.nutrientsOf(id)
    override suspend fun existsByName(name: String) = dao.countByName(name.trim()) > 0

    override suspend fun isDuplicateName(name: String, excludingId: Long): Boolean =
        dao.countByNormalizedName(TextNormalizer.normalize(name), excludingId) > 0

    override suspend fun findSimilar(name: String, excludingId: Long): List<IngredientEntity> {
        val normalized = TextNormalizer.normalize(name)
        if (normalized.length < 3) return emptyList()
        return dao.findSimilar(normalized, excludingId)
            .sortedByDescending { TextNormalizer.similarity(normalized, it.nameNorm) }
            .take(5)
    }

    override suspend fun create(ingredient: IngredientEntity) = database.withTransaction {
        val id = dao.insert(ingredient)
        CanonicalCatalogStore.backfill(database.openHelper.writableDatabase)
        id
    }

    override suspend fun save(
        ingredient: IngredientEntity,
        nutrients: List<IngredientNutrientEntity>,
        portions: List<IngredientPortionEntity>,
        aliases: List<IngredientAliasEntity>
    ) = database.withTransaction {
        // Composition edits create a local snapshot. Existing recipes keep the old ID.
        val snapshot = if (ingredient.id == 0L) ingredient else ingredient.copy(
            id = 0, isCustom = true, sourceKey = null, remoteId = null,
            createdAt = System.currentTimeMillis(), usageCount = 0, lastUsedAt = null
        )
        val id = dao.upsertFull(snapshot, nutrients, portions, aliases)
        val db = database.openHelper.writableDatabase
        CanonicalCatalogStore.backfill(db)
        if (ingredient.id != 0L) CanonicalCatalogStore.archive(db, ingredient.id, "USER_VERSIONED")
        id
    }

    override suspend fun delete(id: Long) = database.withTransaction {
        if (dao.findById(id) == null) false else {
            CanonicalCatalogStore.archive(database.openHelper.writableDatabase, id, "USER_ARCHIVED")
            true
        }
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    override suspend fun markUsed(id: Long) = dao.markUsed(id, System.currentTimeMillis())
}

class LocalFoodReferenceRepository(
    private val dao: FoodReferenceDao
) : FoodReferenceRepository {
    override fun observeCategories() = dao.observeCategories()
    override fun observeCategoriesWithCounts() = dao.observeCategoriesWithCounts()
    override fun observeAisles() = dao.observeAisles()
    override fun observeNutrientDefs() = dao.observeNutrientDefs()
    override fun observeDatasetMeta() = dao.observeDatasetMeta()
    override suspend fun nutrientDefs() = dao.nutrientDefs()
    override suspend fun attribution() = dao.meta(FoodDatasetMetaEntity.ATTRIBUTION)
}

class LocalRecipeRepository(private val dao: RecipeDao) : RecipeRepository {
    override fun observeAll() = dao.observeAll()
    override fun observeFolders() = dao.observeFolders()
    override fun observeRecipeWithSteps(id: Long) = dao.observeRecipeWithSteps(id)
    override fun observeIngredientsOf(recipeId: Long) = dao.observeIngredientsOf(recipeId)

    override fun observeNutritionTotals(): Flow<Map<Long, NutritionValues>> =
        dao.observeNutritionTotals().map { rows ->
            rows.associate { it.recipeId to it.nutrition }
        }

    override suspend fun existsByName(name: String) = dao.countByName(name.trim()) > 0
    override suspend fun create(
        recipe: RecipeEntity,
        ingredients: List<RecipeIngredientInput>,
        steps: List<Pair<String, String?>>
    ) = dao.insertFullRecipe(recipe, ingredients, steps)

    override suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    override suspend fun delete(id: Long) = dao.deleteIfUnused(id)
}
