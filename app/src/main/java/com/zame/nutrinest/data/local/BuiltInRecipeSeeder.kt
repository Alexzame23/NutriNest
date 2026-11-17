package com.zame.nutrinest.data.local

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zame.nutrinest.data.local.entity.RecipeBooks

/**
 * Installs the offline recipe library after the BEDCA-derived food import.
 *
 * This is intentionally an `onOpen` companion to [FoodDatabaseImporter] rather
 * than Room prepackaged data: existing users receive the library too, and all
 * ingredient links point at the exact imported rows already in their database.
 */
internal object BuiltInRecipeSeeder {
    private const val TAG = "BuiltInRecipeSeeder"
    private const val LIBRARY_VERSION = "3"
    private const val VERSION_META_KEY = "built_in_recipe_library_version"
    private const val WARNING_META_KEY = "built_in_recipe_library_warnings"

    fun seedIfNeeded(db: SupportSQLiteDatabase) {
        runCatching { seed(db) }
            .onFailure { Log.e(TAG, "Built-in recipe import failed", it) }
    }

    private fun seed(db: SupportSQLiteDatabase) {
        if (db.meta(VERSION_META_KEY) == LIBRARY_VERSION) return

        val ingredientIds = mutableMapOf<Long, Long>()
        db.query(
            "SELECT i.id, i.sourceKey FROM ingredients i " +
                "JOIN ingredient_canonical_map m ON m.ingredient_id=i.id " +
                "JOIN catalog_revision r ON r.revision_id=m.origin_revision_id " +
                "WHERE m.source_namespace='BEDCA' AND m.source_head_slot=1 AND i.isCustom=0 " +
                "AND r.source_sha256=(SELECT value FROM food_dataset_meta WHERE `key`='dataset_asset_sha256')"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(1)
                    .removePrefix("BEDCA:")
                    .toLongOrNull()
                    ?.let { bedcaId -> ingredientIds[bedcaId] = cursor.getLong(0) }
            }
        }
        // The food import may have failed or not run yet. Do not burn the
        // version marker: a later open should get another chance.
        if (ingredientIds.isEmpty()) {
            Log.w(TAG, "BEDCA ingredients unavailable; postponing recipe import")
            return
        }

        val warnings = mutableListOf<String>()
        var inserted = 0
        val startedTransaction = !db.inTransaction()
        if (startedTransaction) db.beginTransaction()
        try {
            BuiltInRecipeLibrary.recipes.forEachIndexed { index, recipe ->
                val remoteId = RecipeBooks.BUILT_IN_ID_PREFIX + recipe.key
                // Saved recipes, including built-ins, retain their exact amounts and steps.
                if (db.longOrNull("SELECT id FROM recipes WHERE remoteId = ?", remoteId) != null) {
                    return@forEachIndexed
                }
                val missing = recipe.ingredients
                    .map { it.bedcaFoodId }
                    .filterNot { it in ingredientIds }
                    .distinct()
                if (missing.isNotEmpty()) {
                    warnings += "${recipe.key}: missing ${missing.joinToString { "BEDCA:$it" }}"
                    return@forEachIndexed
                }

                // Recipe names are unique in the current schema. Preserve a
                // user's same-named recipe and give the built-in a clear suffix.
                val name = listOf(
                    recipe.name,
                    "${recipe.name} · ${recipe.book}",
                    "${recipe.name} · ${recipe.key}"
                ).firstOrNull { candidate ->
                    db.longOrNull(
                        "SELECT id FROM recipes WHERE name = ? COLLATE NOCASE", candidate
                    ) == null
                }
                if (name == null) {
                    warnings += "${recipe.key}: could not choose a unique recipe name"
                    return@forEachIndexed
                }

                val recipeId = db.compileStatement(
                    """
                    INSERT INTO recipes (
                        name, nameEs, category, folder, coverImagePath, coverImageCrop,
                        createdAt, remoteId, isFavorite
                    ) VALUES (?, ?, ?, ?, NULL, 'CENTER', ?, ?, 0)
                    """.trimIndent()
                ).use { statement ->
                    statement.bindString(1, name)
                    statement.bindString(2, recipe.nameEs)
                    statement.bindString(3, recipe.category)
                    statement.bindString(4, recipe.book)
                    // Stable ordering: built-ins appear together and do not
                    // masquerade as recipes the user recently created.
                    statement.bindLong(5, BUILT_IN_CREATED_AT + index)
                    statement.bindString(6, remoteId)
                    statement.executeInsert()
                }

                db.insertIngredients(recipeId, recipe, ingredientIds)
                db.insertSteps(recipeId, recipe)
                inserted++
            }

            db.writeMeta(WARNING_META_KEY, warnings.joinToString(" | ").ifEmpty { "none" })
            if (warnings.isEmpty()) db.writeMeta(VERSION_META_KEY, LIBRARY_VERSION)
            if (startedTransaction) db.setTransactionSuccessful()
        } finally {
            if (startedTransaction) db.endTransaction()
        }

        warnings.forEach { Log.w(TAG, it) }
        Log.i(
            TAG,
            "Installed $inserted built-in recipes; " +
                "${warnings.size} warnings"
        )
    }

    private fun SupportSQLiteDatabase.insertSteps(
        recipeId: Long,
        recipe: BuiltInRecipe
    ) {
        check(recipe.steps.size == recipe.stepsEs.size)
        compileStatement(
            "INSERT INTO recipe_steps (recipeId, position, text, textEs, imagePath) " +
                "VALUES (?, ?, ?, ?, NULL)"
        ).use { statement ->
            recipe.steps.forEachIndexed { position, step ->
                statement.clearBindings()
                statement.bindLong(1, recipeId)
                statement.bindLong(2, position.toLong())
                statement.bindString(3, step)
                statement.bindString(4, recipe.stepsEs[position])
                statement.executeInsert()
            }
        }
    }

    private fun SupportSQLiteDatabase.insertIngredients(
        recipeId: Long,
        recipe: BuiltInRecipe,
        ingredientIds: Map<Long, Long>
    ) {
        compileStatement(
            """
            INSERT INTO recipe_ingredients
                (recipeId, ingredientId, grams, quantityUnit)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            recipe.ingredients.forEach { ingredient ->
                statement.clearBindings()
                statement.bindLong(1, recipeId)
                statement.bindLong(2, ingredientIds.getValue(ingredient.bedcaFoodId))
                statement.bindDouble(3, ingredient.grams)
                statement.bindString(4, ingredient.quantityUnit)
                statement.executeInsert()
            }
        }
    }

    private fun SupportSQLiteDatabase.meta(key: String): String? =
        query("SELECT value FROM food_dataset_meta WHERE `key` = ?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun SupportSQLiteDatabase.longOrNull(sql: String, arg: String): Long? =
        query(sql, arrayOf(arg)).use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun SupportSQLiteDatabase.writeMeta(key: String, value: String) {
        execSQL("UPDATE food_dataset_meta SET value=? WHERE `key`=?", arrayOf(value, key))
        execSQL("INSERT INTO food_dataset_meta (`key`,value) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM food_dataset_meta WHERE `key`=?1)", arrayOf(key, value))
    }

    private const val BUILT_IN_CREATED_AT = 1_700_000_000_000L
}
