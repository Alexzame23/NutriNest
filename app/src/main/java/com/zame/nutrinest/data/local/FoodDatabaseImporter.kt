package com.zame.nutrinest.data.local

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zame.nutrinest.data.local.entity.FoodDatasetMetaEntity
import java.io.File
import java.security.MessageDigest

/** Additive asset bootstrap. Existing ingredient snapshots and definitions are never replaced. */
object FoodDatabaseImporter {
    private const val TAG = "FoodDatabaseImporter"
    private const val ASSET_NAME = "nutrinest_foods.db"
    const val IMPORTED_ID_OFFSET = 1_000_000L
    const val HUMAN_MILK_FOOD_ID = 2494L
    private const val USER_ID_FLOOR = 2_000_000L
    private const val LAST_IMPORT_AT = "last_import_at"
    internal const val ASSET_HASH_KEY = "dataset_asset_sha256"

    private class CopyStep(
        val label: String,
        val read: String,
        val write: String,
        val readArgs: (Long) -> Array<String> = { emptyArray() }
    )

    /** False keeps the seeder from treating a failed/unsupported import as successful. */
    fun importIfNeeded(context: Context, db: SupportSQLiteDatabase): Boolean =
        runCatching { doImport(context, db) }
            .onFailure { Log.e(TAG, "Food import deferred; historical data retained", it) }
            .getOrDefault(false)

    private fun doImport(context: Context, db: SupportSQLiteDatabase): Boolean {
        val staging = File.createTempFile("food-import-", ".db", context.cacheDir)
        try {
            context.assets.open(ASSET_NAME).use { input -> staging.outputStream().use { input.copyTo(it) } }
            return importStaged(context, db, staging)
        } finally { staging.delete() }
    }

    /** Shared by the asset path and update simulations; staging must be an isolated delivery file. */
    internal fun importStaged(context: Context, db: SupportSQLiteDatabase, staging: File): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        staging.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read >= 0) { digest.update(buffer, 0, read); read = input.read(buffer) }
        }
        val assetHash = CatalogFingerprint.hex(digest.digest())
        SQLiteDatabase.openDatabase(staging.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { asset ->
            val assetVersion = asset.rawQuery("SELECT value FROM db_metadata WHERE key='dataset_version'", null)
                .use { check(it.moveToFirst()); it.getString(0) }
            db.beginTransaction()
            try {
                val installedVersion = meta(db, FoodDatasetMetaEntity.DATASET_VERSION)
                val installedHash = meta(db, ASSET_HASH_KEY)
                if (installedVersion == assetVersion && installedHash == assetHash) {
                    // Editorial revisions must also run on an already installed dataset.
                    CuratedCatalog.apply(context, db, assetHash, assetVersion)
                    RecipeMeasureCatalog.publish(db, assetHash, assetVersion)
                    db.setTransactionSuccessful()
                    return true
                }
                // Publishing a changed dataset requires a reviewed snapshot manifest, not
                // the old delete-and-reimport path. Defer it explicitly without burning markers.
                check(installedVersion == null || installedVersion == assetVersion) {
                    "Dataset revision requires reviewed snapshot publication"
                }
                check(installedHash == null || installedHash == assetHash) {
                    "Dataset hash changed without a reviewed publication"
                }
                val now = System.currentTimeMillis()
                val inserted = mutableSetOf<Long>()
                COPY_STEPS.forEach { copy(asset, db, it, now, inserted) }
                reserveUserIdRange(db)
                CanonicalCatalogStore.backfill(db, inserted, assetHash, assetVersion)
                CuratedCatalog.apply(context, db, assetHash, assetVersion)
                RecipeMeasureCatalog.publish(db, assetHash, assetVersion)
                writeMeta(db, FoodDatasetMetaEntity.DATASET_VERSION, assetVersion)
                writeMeta(db, ASSET_HASH_KEY, assetHash)
                writeMeta(db, LAST_IMPORT_AT, now.toString())
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        return true
    }

    private fun copy(asset: SQLiteDatabase, db: SupportSQLiteDatabase, step: CopyStep, now: Long,
        inserted: MutableSet<Long>) {
        val importingIngredients = step.label == "ingredients"
        val existingIngredients = if (importingIngredients) {
            db.query("SELECT id FROM ingredients WHERE id >= ?", arrayOf<Any>(IMPORTED_ID_OFFSET)).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) }
            }
        } else emptySet()
        db.compileStatement(step.write).use { statement ->
            asset.rawQuery(step.read, step.readArgs(now)).use { cursor ->
                while (cursor.moveToNext()) {
                    if (importingIngredients) {
                        val id = cursor.getLong(0)
                        if (id in existingIngredients || id in inserted) continue
                    } else if (skipExisting(db, step.label, cursor, inserted)) continue
                    statement.clearBindings()
                    for (column in 0 until cursor.columnCount) {
                        when (cursor.getType(column)) {
                            Cursor.FIELD_TYPE_NULL -> statement.bindNull(column + 1)
                            Cursor.FIELD_TYPE_INTEGER -> statement.bindLong(column + 1, cursor.getLong(column))
                            Cursor.FIELD_TYPE_FLOAT -> statement.bindDouble(column + 1, cursor.getDouble(column))
                            else -> statement.bindString(column + 1, cursor.getString(column))
                        }
                    }
                    statement.executeInsert()
                    if (importingIngredients) inserted += cursor.getLong(0)
                }
            }
        }
    }

    private fun skipExisting(db: SupportSQLiteDatabase, label: String, row: Cursor, inserted: Set<Long>): Boolean {
        val table = when (label) {
            "nutrients", "portions", "aliases", "quality issues" -> return row.getLong(0) !in inserted
            "categories" -> "ingredient_categories"
            "aisles" -> "shopping_aisles"
            "dataset metadata" -> "food_dataset_meta"
            "nutrient definitions" -> "nutrient_defs"
            else -> error("Unknown import step: $label")
        }
        if (label == "nutrient definitions") {
            db.query("SELECT tag,nameEn,nameEs,unit,nutrientGroup,isPrimary,sortOrder,nrv FROM nutrient_defs WHERE tag=?", arrayOf(row.getString(0))).use { existing ->
                if (!existing.moveToFirst()) return false
                for (i in 0 until row.columnCount) {
                    val same = if (row.isNull(i) || existing.isNull(i)) row.isNull(i) == existing.isNull(i)
                        else if (row.getType(i) in listOf(Cursor.FIELD_TYPE_FLOAT, Cursor.FIELD_TYPE_INTEGER)) row.getDouble(i) == existing.getDouble(i)
                        else row.getString(i) == existing.getString(i)
                    check(same) { "Nutrient definition changed: ${row.getString(0)}; historical semantics retained" }
                }
                return true
            }
        }
        return db.query("SELECT 1 FROM `$table` WHERE `key`=?", arrayOf(row.getString(0))).use { it.moveToFirst() }
    }

    private fun reserveUserIdRange(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE sqlite_sequence SET seq = ? WHERE name = 'ingredients' AND seq < ?",
            arrayOf<Any>(USER_ID_FLOOR, USER_ID_FLOOR))
    }

    private fun meta(db: SupportSQLiteDatabase, key: String): String? =
        db.query("SELECT value FROM food_dataset_meta WHERE `key`=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun writeMeta(db: SupportSQLiteDatabase, key: String, value: String) {
        db.execSQL("UPDATE food_dataset_meta SET value=? WHERE `key`=?", arrayOf(value, key))
        db.execSQL("INSERT INTO food_dataset_meta (`key`,value) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM food_dataset_meta WHERE `key`=?1)", arrayOf(key, value))
    }

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
            write = "INSERT INTO food_dataset_meta (`key`, value) VALUES (?,?)"
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
                WHERE f.is_deprecated = 0 AND f.id != $HUMAN_MILK_FOOD_ID
            """.trimIndent(),
            // `isCustom` is NOT NULL with no default, so it has to be listed —
            // omitting it made every row fail NOT NULL, which OR IGNORE then
            // swallowed without a word.
            write = """
                INSERT INTO ingredients (
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
                WHERE f.is_deprecated = 0 AND f.id != $HUMAN_MILK_FOOD_ID
            """.trimIndent(),
            write = "INSERT INTO ingredient_nutrients (ingredientId, tag, value) " +
                "VALUES (?,?,?)"
        ),
        CopyStep(
            label = "portions",
            read = """
                SELECT p.food_id + $IMPORTED_ID_OFFSET, p.label_en, p.label_es,
                       p.grams, p.portion_type, p.is_default
                FROM food_portions p
                JOIN foods f ON f.id = p.food_id
                WHERE f.is_deprecated = 0 AND f.id != $HUMAN_MILK_FOOD_ID
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
                WHERE f.is_deprecated = 0 AND f.id != $HUMAN_MILK_FOOD_ID
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
                WHERE f.is_deprecated = 0 AND f.id != $HUMAN_MILK_FOOD_ID
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
