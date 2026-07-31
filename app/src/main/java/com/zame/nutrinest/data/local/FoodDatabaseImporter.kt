package com.zame.nutrinest.data.local

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zame.nutrinest.data.local.entity.FoodDatasetMetaEntity
import java.io.File

/**
 * Loads the shipped food database (`assets/nutrinest_foods.db`, derived from
 * BEDCA) into the app's own Room database.
 *
 * Room's `createFromAsset` is not an option: it replaces the whole database, and
 * ours already holds the user's recipes, profiles and menus. So the asset is
 * copied out of the APK, opened read-only on a **separate** connection, and its
 * rows are streamed into Room's connection through prepared statements.
 *
 * Deliberately no `ATTACH DATABASE`, even though it would make the mapping a
 * tidier `INSERT .. SELECT`. Android's `SQLiteDatabase` treats ATTACH as a
 * special statement and reconfigures its connection pool around it (ATTACH and
 * WAL do not mix), which throws away the `TEMP` tables Room's invalidation
 * tracker keeps per connection. The next `Flow` query then dies with
 * "no such table: room_table_modification_log". Writing through Room's own
 * connection also means the tracker sees the inserts, so observed queries
 * refresh once the import lands.
 */
object FoodDatabaseImporter {

    private const val TAG = "FoodDatabaseImporter"
    private const val ASSET_NAME = "nutrinest_foods.db"

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
     * One table's worth of copying: a query against the asset, and the insert it
     * feeds. The insert's placeholders are bound positionally from the query's
     * columns, so the two column lists must stay in the same order.
     */
    private class CopyStep(
        val label: String,
        val read: String,
        val write: String,
        val readArgs: (Long) -> Array<String> = { emptyArray() }
    )

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
            val asset = SQLiteDatabase.openDatabase(
                staging.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
            try {
                val assetVersion = asset.stringOf(
                    "SELECT value FROM db_metadata WHERE key = ?",
                    FoodDatasetMetaEntity.DATASET_VERSION
                )
                if (assetVersion == null) {
                    Log.w(TAG, "Asset has no dataset_version; skipping import")
                    return
                }
                val installedVersion = db.stringOf(
                    "SELECT value FROM food_dataset_meta WHERE `key` = ?",
                    FoodDatasetMetaEntity.DATASET_VERSION
                )
                if (installedVersion == assetVersion) return

                val lastImportAt = db.stringOf(
                    "SELECT value FROM food_dataset_meta WHERE `key` = ?", LAST_IMPORT_AT
                )?.toLongOrNull() ?: 0L
                val now = System.currentTimeMillis()

                db.beginTransaction()
                try {
                    val userState = captureUserState(db)
                    clearReferenceData(db)
                    dropStaleImports(db, lastImportAt)
                    COPY_STEPS.forEach { step -> copy(asset, db, step, now) }
                    restoreUserState(db, userState)
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
                asset.close()
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

    /**
     * Streams one table across, binding each result column to the matching
     * placeholder. Types come from the cursor rather than a schema, so a column
     * that is null for one row and a number for the next is handled correctly.
     */
    private fun copy(
        asset: SQLiteDatabase,
        db: SupportSQLiteDatabase,
        step: CopyStep,
        now: Long
    ) {
        var rows = 0
        db.compileStatement(step.write).use { statement ->
            asset.rawQuery(step.read, step.readArgs(now)).use { cursor ->
                while (cursor.moveToNext()) {
                    statement.clearBindings()
                    for (column in 0 until cursor.columnCount) {
                        val placeholder = column + 1
                        when (cursor.getType(column)) {
                            Cursor.FIELD_TYPE_NULL -> statement.bindNull(placeholder)
                            Cursor.FIELD_TYPE_INTEGER ->
                                statement.bindLong(placeholder, cursor.getLong(column))
                            Cursor.FIELD_TYPE_FLOAT ->
                                statement.bindDouble(placeholder, cursor.getDouble(column))
                            else -> statement.bindString(placeholder, cursor.getString(column))
                        }
                    }
                    statement.executeInsert()
                    rows++
                }
            }
        }
        Log.d(TAG, "${step.label}: $rows rows")
    }

    // ── Import steps ────────────────────────────────────────────────────

    /**
     * Reference tables carry no user data, so they are replaced wholesale.
     * Ingredient rows are not touched here — [dropStaleImports] decides which of
     * those may be replaced.
     */
    private fun clearReferenceData(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM ingredient_categories")
        db.execSQL("DELETE FROM shopping_aisles")
        db.execSQL("DELETE FROM nutrient_defs")
    }

    /**
     * Clears out imported ingredients so a newer dataset can replace them.
     *
     * Rows the user has edited (their `updatedAt` moved past the last import) are
     * left alone, as are any referenced by a recipe or shopping list — those
     * foreign keys are RESTRICT, and silently rewriting an ingredient someone
     * cooked with would be worse than keeping slightly older figures. Deleting
     * the ingredient cascades to its nutrients, portions, aliases and notes.
     */
    private fun dropStaleImports(db: SupportSQLiteDatabase, lastImportAt: Long) {
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

    /** Favourites, usage counts and photos, held across a dataset refresh. */
    private class UserState(
        val id: Long,
        val isFavorite: Int,
        val usageCount: Int,
        val lastUsedAt: Long?,
        val imagePath: String?
    )

    private fun captureUserState(db: SupportSQLiteDatabase): List<UserState> {
        val state = mutableListOf<UserState>()
        db.query(
            "SELECT id, isFavorite, usageCount, lastUsedAt, imagePath " +
                "FROM ingredients WHERE isCustom = 0"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                state += UserState(
                    id = cursor.getLong(0),
                    isFavorite = cursor.getInt(1),
                    usageCount = cursor.getInt(2),
                    lastUsedAt = if (cursor.isNull(3)) null else cursor.getLong(3),
                    imagePath = cursor.getString(4)
                )
            }
        }
        return state
    }

    private fun restoreUserState(db: SupportSQLiteDatabase, state: List<UserState>) {
        if (state.isEmpty()) return
        db.compileStatement(
            """
            UPDATE ingredients
            SET isFavorite = ?, usageCount = ?, lastUsedAt = ?,
                imagePath = COALESCE(?, imagePath)
            WHERE id = ? AND isCustom = 0
            """.trimIndent()
        ).use { statement ->
            state.forEach { row ->
                statement.clearBindings()
                statement.bindLong(1, row.isFavorite.toLong())
                statement.bindLong(2, row.usageCount.toLong())
                row.lastUsedAt?.let { statement.bindLong(3, it) } ?: statement.bindNull(3)
                row.imagePath?.let { statement.bindString(4, it) } ?: statement.bindNull(4)
                statement.bindLong(5, row.id)
                statement.executeUpdateDelete()
            }
        }
    }

    /**
     * Pushes the autoincrement counter above the block reserved for imports, so
     * ingredients the user creates from here on can never collide with a food
     * added by a future dataset. The row is guaranteed to exist: the import above
     * just inserted into an AUTOINCREMENT table.
     */
    private fun reserveUserIdRange(db: SupportSQLiteDatabase) {
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

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun SupportSQLiteDatabase.stringOf(sql: String, vararg args: Any): String? =
        query(sql, args).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }

    private fun SQLiteDatabase.stringOf(sql: String, vararg args: String): String? =
        rawQuery(sql, args).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }

    /**
     * The whole import, as declarative read/write pairs.
     *
     * Keeping each step's two column lists side by side is what makes the
     * positional binding safe to read — and lets `tools/verify_migration.py`
     * replay exactly these statements offline.
     */
    private val COPY_STEPS = listOf(
        CopyStep(
            label = "categories",
            read = "SELECT key, name_en, name_es, icon_key, color_hex, sort_order " +
                "FROM food_categories",
            write = "INSERT INTO ingredient_categories " +
                "(`key`, nameEn, nameEs, iconKey, colorHex, sortOrder) VALUES (?,?,?,?,?,?)"
        ),
        CopyStep(
            label = "aisles",
            read = "SELECT key, name_en, name_es, sort_order FROM shopping_aisles",
            write = "INSERT INTO shopping_aisles (`key`, nameEn, nameEs, sortOrder) " +
                "VALUES (?,?,?,?)"
        ),
        CopyStep(
            label = "nutrient definitions",
            read = "SELECT tag, name_en, name_es, unit, nutrient_group, is_primary, " +
                "sort_order, nrv FROM nutrient_defs",
            write = "INSERT INTO nutrient_defs " +
                "(tag, nameEn, nameEs, unit, nutrientGroup, isPrimary, sortOrder, nrv) " +
                "VALUES (?,?,?,?,?,?,?,?)"
        ),
        CopyStep(
            label = "dataset metadata",
            read = "SELECT key, value FROM db_metadata",
            write = "INSERT OR REPLACE INTO food_dataset_meta (`key`, value) VALUES (?,?)"
        ),
        CopyStep(
            label = "ingredients",
            // The two trailing placeholders are the import timestamp, bound by
            // readArgs so createdAt and updatedAt agree with food_dataset_meta.
            read = """
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
                    (SELECT p.grams FROM food_portions p
                     WHERE p.food_id = f.id AND p.is_default = 1 LIMIT 1),
                    f.is_liquid,
                    f.density_g_ml,
                    f.completeness_pct,
                    f.quality,
                    'BEDCA:' || f.id,
                    0,
                    CAST(? AS INTEGER),
                    CAST(? AS INTEGER)
                FROM foods f
                JOIN food_macros m ON m.food_id = f.id
                JOIN food_categories c ON c.id = f.category_id
                JOIN shopping_aisles a ON a.id = f.aisle_id
                WHERE f.is_deprecated = 0
            """.trimIndent(),
            // `isCustom` is NOT NULL with no default, so it has to be listed —
            // omitting it made every row fail NOT NULL, which OR IGNORE then
            // swallowed without a word.
            write = """
                INSERT OR IGNORE INTO ingredients (
                    id, name, nameEs, nameNorm, nameEsNorm, baseName, foodType, aisleKey,
                    kcal, carbs, fats, protein,
                    prepState, rawSafety, servingSizeG, isLiquid, densityGml,
                    completenessPct, quality, sourceKey, isCustom, createdAt, updatedAt
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            readArgs = { now -> arrayOf(now.toString(), now.toString()) }
        ),
        // Each of these filters on the foods actually imported, so a deprecated
        // or already-present food brings no orphans with it.
        CopyStep(
            label = "nutrients",
            read = """
                SELECT n.food_id + $IMPORTED_ID_OFFSET, n.tag, n.value
                FROM food_nutrients n
                JOIN foods f ON f.id = n.food_id
                WHERE f.is_deprecated = 0
            """.trimIndent(),
            write = "INSERT OR IGNORE INTO ingredient_nutrients (ingredientId, tag, value) " +
                "VALUES (?,?,?)"
        ),
        CopyStep(
            label = "portions",
            read = """
                SELECT p.food_id + $IMPORTED_ID_OFFSET, p.label_en, p.label_es,
                       p.grams, p.portion_type, p.is_default
                FROM food_portions p
                JOIN foods f ON f.id = p.food_id
                WHERE f.is_deprecated = 0
            """.trimIndent(),
            // Deduplicated on the measure itself, not merely on the ingredient:
            // a food with a spoon, a cup and a pinch must keep all three.
            write = """
                INSERT INTO ingredient_portions
                    (ingredientId, labelEn, labelEs, grams, portionType, isDefault)
                SELECT ?,?,?,?,?,?
                WHERE EXISTS (SELECT 1 FROM ingredients WHERE id = ?1)
                  AND NOT EXISTS (
                      SELECT 1 FROM ingredient_portions
                      WHERE ingredientId = ?1 AND labelEn = ?2 AND grams = ?4
                  )
            """.trimIndent()
        ),
        CopyStep(
            label = "aliases",
            read = """
                SELECT al.food_id + $IMPORTED_ID_OFFSET, al.alias, al.alias_norm, al.lang
                FROM food_aliases al
                JOIN foods f ON f.id = al.food_id
                WHERE f.is_deprecated = 0
            """.trimIndent(),
            write = """
                INSERT INTO ingredient_aliases (ingredientId, alias, aliasNorm, lang)
                SELECT ?,?,?,?
                WHERE EXISTS (SELECT 1 FROM ingredients WHERE id = ?1)
                  AND NOT EXISTS (
                      SELECT 1 FROM ingredient_aliases
                      WHERE ingredientId = ?1 AND aliasNorm = ?3
                  )
            """.trimIndent()
        ),
        CopyStep(
            label = "quality issues",
            read = """
                SELECT q.food_id + $IMPORTED_ID_OFFSET, q.code, q.severity, q.detail
                FROM food_quality_issues q
                JOIN foods f ON f.id = q.food_id
                WHERE f.is_deprecated = 0
            """.trimIndent(),
            write = """
                INSERT INTO ingredient_quality_issues (ingredientId, code, severity, detail)
                SELECT ?,?,?,?
                WHERE EXISTS (SELECT 1 FROM ingredients WHERE id = ?1)
                  AND NOT EXISTS (
                      SELECT 1 FROM ingredient_quality_issues
                      WHERE ingredientId = ?1 AND code = ?2
                  )
            """.trimIndent()
        )
    )
}
