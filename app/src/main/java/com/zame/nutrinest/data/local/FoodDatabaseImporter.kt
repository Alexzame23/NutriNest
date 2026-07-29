package com.zame.nutrinest.data.local

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zame.nutrinest.data.local.entity.FoodDatasetMetaEntity
import java.io.File

/**
 * Loads the shipped food database (`assets/nutrinest_foods.db`, derived from
 * BEDCA) into the app's own Room database.
 *
 * Room's `createFromAsset` is not an option: it replaces the whole database,
 * and ours already holds the user's recipes, profiles and menus. Instead the
 * asset is copied out, `ATTACH`ed, and copied across with `INSERT .. SELECT`.
 * That keeps the mapping in readable SQL and imports ~35 000 rows in a single
 * transaction.
 *
 * Runs from [NutriNestDatabase]'s `onOpen` callback rather than from the
 * migration, because SQLite refuses to `ATTACH` inside a transaction and Room
 * wraps every migration in one. [importIfNeeded] is therefore written to be
 * cheap (one indexed lookup) when there is nothing to do.
 */
object FoodDatabaseImporter {

    private const val TAG = "FoodDatabaseImporter"
    private const val ASSET_NAME = "nutrinest_foods.db"
    private const val ATTACH_ALIAS = "src"

    /**
     * Imported ingredients keep the dataset's own food id, shifted by this much.
     *
     * The dataset numbers foods from 3 to ~2400, which would collide with the
     * ids Room hands out to user-created ingredients. Shifting the dataset into
     * its own block, and then pushing the autoincrement counter past the end of
     * that block, means neither can ever tread on the other — including when a
     * later dataset adds foods.
     */
    const val IMPORTED_ID_OFFSET = 1_000_000L
    private const val USER_ID_FLOOR = 2_000_000L

    /** Marks the last successful import, so user edits since then are respected. */
    private const val LAST_IMPORT_AT = "last_import_at"

    /**
     * Imports the dataset when the database has never seen it, or has seen an
     * older version. Failures are logged and swallowed: a missing or corrupt
     * asset should leave the user with an empty ingredient list, not a crash
     * loop on every app start.
     */
    fun importIfNeeded(context: Context, db: SupportSQLiteDatabase) {
        runCatching { doImport(context, db) }
            .onFailure { Log.e(TAG, "Food database import failed", it) }
    }

    private fun doImport(context: Context, db: SupportSQLiteDatabase) {
        val staging = File(context.cacheDir, "food-import.db")
        copyAsset(context, staging)
        try {
            // Path is interpolated rather than bound: some SQLite builds reject
            // parameters in ATTACH. cacheDir is app-private, so it is trusted.
            db.execSQL("ATTACH DATABASE '${staging.absolutePath}' AS $ATTACH_ALIAS")
            try {
                val assetVersion = stringOf(
                    db,
                    "SELECT value FROM $ATTACH_ALIAS.db_metadata WHERE key = ?",
                    FoodDatasetMetaEntity.DATASET_VERSION
                )
                if (assetVersion == null) {
                    Log.w(TAG, "Asset has no dataset_version; skipping import")
                    return
                }
                val installedVersion = stringOf(
                    db,
                    "SELECT value FROM food_dataset_meta WHERE key = ?",
                    FoodDatasetMetaEntity.DATASET_VERSION
                )
                if (installedVersion == assetVersion) return

                val lastImportAt = stringOf(
                    db, "SELECT value FROM food_dataset_meta WHERE key = ?", LAST_IMPORT_AT
                )?.toLongOrNull() ?: 0L
                val now = System.currentTimeMillis()

                db.beginTransaction()
                try {
                    importReferenceData(db)
                    refreshStaleImports(db, lastImportAt)
                    importIngredients(db, now)
                    importIngredientDetails(db)
                    restoreUserState(db)
                    reserveUserIdRange(db)
                    writeMeta(db, FoodDatasetMetaEntity.DATASET_VERSION, assetVersion)
                    writeMeta(db, LAST_IMPORT_AT, now.toString())
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                Log.i(
                    TAG,
                    "Imported food dataset $assetVersion" +
                        (installedVersion?.let { " (replacing $it)" } ?: "")
                )
            } finally {
                db.execSQL("DETACH DATABASE $ATTACH_ALIAS")
            }
        } finally {
            staging.delete()
        }
    }

    private fun copyAsset(context: Context, target: File) {
        context.assets.open(ASSET_NAME).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    // ── Import steps ────────────────────────────────────────────────────

    /** Categories, aisles, nutrient definitions and dataset metadata. */
    private fun importReferenceData(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM ingredient_categories")
        db.execSQL(
            """
            INSERT INTO ingredient_categories (`key`, nameEn, nameEs, iconKey, colorHex, sortOrder)
            SELECT c.key, c.name_en, c.name_es, c.icon_key, c.color_hex, c.sort_order
            FROM $ATTACH_ALIAS.food_categories c
            """.trimIndent()
        )

        db.execSQL("DELETE FROM shopping_aisles")
        db.execSQL(
            """
            INSERT INTO shopping_aisles (`key`, nameEn, nameEs, sortOrder)
            SELECT a.key, a.name_en, a.name_es, a.sort_order
            FROM $ATTACH_ALIAS.shopping_aisles a
            """.trimIndent()
        )

        db.execSQL("DELETE FROM nutrient_defs")
        db.execSQL(
            """
            INSERT INTO nutrient_defs (tag, nameEn, nameEs, unit, nutrientGroup, isPrimary, sortOrder, nrv)
            SELECT n.tag, n.name_en, n.name_es, n.unit, n.nutrient_group, n.is_primary, n.sort_order, n.nrv
            FROM $ATTACH_ALIAS.nutrient_defs n
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT OR REPLACE INTO food_dataset_meta (`key`, value)
            SELECT m.key, m.value FROM $ATTACH_ALIAS.db_metadata m
            """.trimIndent()
        )
    }

    /**
     * Clears out imported ingredients so a newer dataset can replace them.
     *
     * Rows the user has edited (their `updatedAt` moved past the last import)
     * are left alone, as are any referenced by a recipe or shopping list — the
     * foreign keys are RESTRICT, and silently rewriting an ingredient someone
     * cooked with would be worse than keeping slightly older figures.
     */
    private fun refreshStaleImports(db: SupportSQLiteDatabase, lastImportAt: Long) {
        db.execSQL(
            """
            CREATE TEMP TABLE import_user_state AS
            SELECT id, isFavorite, usageCount, lastUsedAt, imagePath
            FROM ingredients WHERE isCustom = 0
            """.trimIndent()
        )
        db.execSQL(
            """
            DELETE FROM ingredients
            WHERE isCustom = 0
              AND updatedAt <= ?
              AND id NOT IN (SELECT ingredientId FROM recipe_ingredients)
              AND id NOT IN (SELECT ingredientId FROM shopping_items)
            """.trimIndent(),
            arrayOf<Any>(lastImportAt)
        )
    }

    private fun importIngredients(db: SupportSQLiteDatabase, now: Long) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO ingredients (
                id, name, nameEs, nameNorm, nameEsNorm, baseName, foodType, aisleKey,
                kcal, carbs, fats, protein,
                prepState, rawSafety, notes, servingSizeG, isLiquid, densityGml,
                completenessPct, quality, sourceKey, imagePath, isCustom,
                isFavorite, usageCount, lastUsedAt, createdAt, updatedAt, remoteId
            )
            SELECT
                f.id + $IMPORTED_ID_OFFSET,
                COALESCE(NULLIF(f.name_en, ''), f.name_es),
                f.name_es,
                f.name_en_norm,
                f.name_norm,
                f.base_name_es,
                c.key,
                a.key,
                m.kcal, m.carbs_g, m.fat_g, m.protein_g,
                f.prep_state,
                f.raw_safety,
                NULL,
                (SELECT p.grams FROM $ATTACH_ALIAS.food_portions p
                 WHERE p.food_id = f.id AND p.is_default = 1 LIMIT 1),
                f.is_liquid,
                f.density_g_ml,
                f.completeness_pct,
                f.quality,
                'BEDCA:' || f.id,
                NULL,
                0,
                0, 0, NULL, ?, ?, NULL
            FROM $ATTACH_ALIAS.foods f
            JOIN $ATTACH_ALIAS.food_macros m ON m.food_id = f.id
            JOIN $ATTACH_ALIAS.food_categories c ON c.id = f.category_id
            JOIN $ATTACH_ALIAS.shopping_aisles a ON a.id = f.aisle_id
            WHERE f.is_deprecated = 0
            """.trimIndent(),
            arrayOf<Any>(now, now)
        )
    }

    /**
     * Nutrients, portions, aliases and quality notes.
     *
     * Each join goes through `ingredients`, so a food that was skipped above
     * (already present, or deprecated) brings no orphans with it.
     */
    private fun importIngredientDetails(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO ingredient_nutrients (ingredientId, tag, value)
            SELECT i.id, n.tag, n.value
            FROM $ATTACH_ALIAS.food_nutrients n
            JOIN ingredients i ON i.id = n.food_id + $IMPORTED_ID_OFFSET
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO ingredient_portions (ingredientId, labelEn, labelEs, grams, portionType, isDefault)
            SELECT i.id, p.label_en, p.label_es, p.grams, p.portion_type, p.is_default
            FROM $ATTACH_ALIAS.food_portions p
            JOIN ingredients i ON i.id = p.food_id + $IMPORTED_ID_OFFSET
            WHERE NOT EXISTS (
                SELECT 1 FROM ingredient_portions existing WHERE existing.ingredientId = i.id
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO ingredient_aliases (ingredientId, alias, aliasNorm, lang)
            SELECT i.id, al.alias, al.alias_norm, al.lang
            FROM $ATTACH_ALIAS.food_aliases al
            JOIN ingredients i ON i.id = al.food_id + $IMPORTED_ID_OFFSET
            WHERE NOT EXISTS (
                SELECT 1 FROM ingredient_aliases existing
                WHERE existing.ingredientId = i.id AND existing.aliasNorm = al.alias_norm
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO ingredient_quality_issues (ingredientId, code, severity, detail)
            SELECT i.id, q.code, q.severity, q.detail
            FROM $ATTACH_ALIAS.food_quality_issues q
            JOIN ingredients i ON i.id = q.food_id + $IMPORTED_ID_OFFSET
            WHERE NOT EXISTS (
                SELECT 1 FROM ingredient_quality_issues existing
                WHERE existing.ingredientId = i.id AND existing.code = q.code
            )
            """.trimIndent()
        )
    }

    /** Puts favourites, usage counts and photos back on refreshed rows. */
    private fun restoreUserState(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE ingredients SET
                isFavorite = COALESCE(
                    (SELECT s.isFavorite FROM import_user_state s WHERE s.id = ingredients.id), isFavorite),
                usageCount = COALESCE(
                    (SELECT s.usageCount FROM import_user_state s WHERE s.id = ingredients.id), usageCount),
                lastUsedAt = COALESCE(
                    (SELECT s.lastUsedAt FROM import_user_state s WHERE s.id = ingredients.id), lastUsedAt),
                imagePath = COALESCE(
                    (SELECT s.imagePath FROM import_user_state s WHERE s.id = ingredients.id), imagePath)
            WHERE isCustom = 0
              AND id IN (SELECT id FROM import_user_state)
            """.trimIndent()
        )
        db.execSQL("DROP TABLE import_user_state")
    }

    /**
     * Pushes the autoincrement counter above the block reserved for imports, so
     * ingredients the user creates from here on can never collide with a food
     * added by a future dataset.
     */
    private fun reserveUserIdRange(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT OR IGNORE INTO sqlite_sequence (name, seq) VALUES ('ingredients', ?)",
            arrayOf<Any>(USER_ID_FLOOR)
        )
        db.execSQL(
            "UPDATE sqlite_sequence SET seq = ? WHERE name = 'ingredients' AND seq < ?",
            arrayOf<Any>(USER_ID_FLOOR, USER_ID_FLOOR)
        )
    }

    private fun writeMeta(db: SupportSQLiteDatabase, key: String, value: String) {
        db.execSQL(
            "INSERT OR REPLACE INTO food_dataset_meta (`key`, value) VALUES (?, ?)",
            arrayOf<Any>(key, value)
        )
    }

    private fun stringOf(db: SupportSQLiteDatabase, sql: String, vararg args: Any): String? =
        db.query(sql, args).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
}
