package com.zame.nutrinest.data.local

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Transactional metadata writer. Never updates the nutritional/source tables. */
internal object CanonicalCatalogStore {
    private const val BASELINE_KEY = "schema-9-baseline-v1"
    private val personalColumns = setOf("isFavorite", "usageCount", "lastUsedAt", "imagePath", "createdAt", "updatedAt", "remoteId")

    fun legacySnapshot(db: SupportSQLiteDatabase): Map<String, String> {
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { c ->
            buildList { while (c.moveToNext()) {
                val name = c.getString(0)
                if (name !in catalogTables && !name.startsWith("sqlite_") && !name.startsWith("room_")) add(name)
            } }
        }
        return tables.associateWith { table ->
            val columns = db.query("PRAGMA table_info(`$table`)").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(1)) }
            }
            hashQuery(db, "SELECT * FROM `$table` ORDER BY " + columns.joinToString { "`$it`" })
        }
    }

    /** Idempotent, metadata-only. Verified IDs may only be supplied by the successful asset importer. */
    fun backfill(
        db: SupportSQLiteDatabase,
        verifiedImportedIds: Set<Long> = emptySet(),
        assetHash: String? = null,
        datasetVersion: String? = null
    ) = transaction(db) {
        val baselineExists = db.query("SELECT 1 FROM catalog_revision WHERE revision_key=?", arrayOf<Any?>(BASELINE_KEY))
            .use { it.moveToFirst() }
        if (!baselineExists) {
            val baseline = beginRevision(db, BASELINE_KEY, "BASELINE", "LEGACY:${UUID.randomUUID()}")
            fillMissing(db, baseline, emptySet())
            finishRevision(db, baseline, JSONObject().put("format", "sqlite-typed-v1")
                .put("legacyTables", JSONObject(legacySnapshot(db)))
                .put("nutrientDefinitions", encodedQuery(db, "SELECT * FROM nutrient_defs ORDER BY tag")))
        }
        val missing = db.query("SELECT id FROM ingredients WHERE id NOT IN (SELECT ingredient_id FROM ingredient_canonical_map) ORDER BY id")
            .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
        if (missing.isNotEmpty()) {
            val revision = beginRevision(db, "metadata:${UUID.randomUUID()}",
                if (verifiedImportedIds.isEmpty()) "USER_EDIT" else "DATASET",
                if (verifiedImportedIds.isEmpty()) "LOCAL" else "BEDCA", assetHash, datasetVersion)
            fillMissing(db, revision, verifiedImportedIds)
            finishRevision(db, revision, JSONObject().put("format", "sqlite-typed-v1")
                .put("addedIngredientIds", JSONArray(missing)).put("assetSha256", assetHash))
        }
        validate(db)
    }

    private fun fillMissing(db: SupportSQLiteDatabase, revision: String, verified: Set<Long>) {
        val installation = db.query("SELECT revision_id FROM catalog_revision WHERE revision_key=?", arrayOf<Any?>(BASELINE_KEY))
            .use { check(it.moveToFirst()); it.getString(0) }
        db.query("SELECT id, name, nameEs, nameNorm, nameEsNorm, prepState, isCustom, sourceKey FROM ingredients WHERE id NOT IN (SELECT ingredient_id FROM ingredient_canonical_map) ORDER BY id").use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val custom = c.getInt(6) != 0
                val sourceKey = c.getString(7)
                val accredited = id in verified && !custom && sourceKey?.startsWith("BEDCA:") == true
                val scope = if (custom) "local:$installation" else "global"
                val canonicalId = UUID.randomUUID().toString()
                val prep = c.getString(5)
                val preservation = prep.takeIf { it in setOf("CANNED", "FROZEN", "DRIED", "CURED", "SMOKED") } ?: "UNSPECIFIED"
                val preparation = if (preservation != "UNSPECIFIED" || prep == "POWDERED") "UNSPECIFIED" else prep
                val nameEn = c.getString(1).takeIf { it.isNotBlank() } ?: "Legacy ingredient $id"
                db.execSQL("""
                    INSERT INTO canonical_ingredients (canonical_id,scope_key,concept_key,part_key,preparation_key,
                    preservation_key,form_key,formulation_key,variant_key,identity_state,name_es,name_en,
                    name_es_norm,name_en_norm,created_revision_id,updated_revision_id)
                    VALUES (?,?,?,'UNSPECIFIED',?,?,?,'UNSPECIFIED',?,'PROVISIONAL',?,?,?,?,?,?)
                """.trimIndent(), arrayOf<Any?>(canonicalId, scope, "legacy:$id", preparation, preservation,
                    if (prep == "POWDERED") "POWDER" else "UNSPECIFIED", "legacy:$id", c.getString(2), nameEn,
                    c.getString(4), c.getString(3), revision, revision))
                db.execSQL("""
                    INSERT INTO ingredient_canonical_map (ingredient_id,canonical_id,scope_key,source_namespace,
                    source_record_key,source_head_slot,record_sha256,mapping_basis,origin_revision_id,assigned_revision_id)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                """.trimIndent(), arrayOf<Any?>(id, canonicalId, scope,
                    if (accredited) "BEDCA" else "${if (custom) "LOCAL" else "LEGACY"}:$installation",
                    if (accredited) sourceKey.removePrefix("BEDCA:") else "legacy:$id",
                    if (accredited) 1 else null, ingredientFingerprint(db, id),
                    if (custom) "LOCAL" else "LEGACY_SINGLETON", revision, revision))
                db.execSQL("""
                    INSERT INTO ingredient_catalog_status (ingredient_id,state,reason_code,detail,revision_id)
                    VALUES (?,?,?,?,?)
                """.trimIndent(), arrayOf<Any?>(id, if (sourceKey == "BEDCA:2494") "HIDDEN" else "VISIBLE",
                    "LEGACY_VISIBILITY", "Original sourceKey=${sourceKey ?: "NULL"}; no equivalence inferred", revision))
            }
        }
    }

    /** Hide an old snapshot; references and personal metadata remain untouched. */
    fun archive(db: SupportSQLiteDatabase, ingredientId: Long, reason: String) = transaction(db) {
        backfill(db)
        val current = db.query("SELECT state FROM ingredient_catalog_status WHERE ingredient_id=?", arrayOf<Any?>(ingredientId))
            .use { if (it.moveToFirst()) it.getString(0) else null }
        if (current != null && current != "HIDDEN") {
            val revision = beginRevision(db, "archive:${UUID.randomUUID()}", "USER_EDIT", "LOCAL")
            db.execSQL("UPDATE ingredient_catalog_status SET state='HIDDEN', reason_code=?, detail=?, revision_id=? WHERE ingredient_id=?",
                arrayOf<Any?>(reason, "Archived without deleting historical data", revision, ingredientId))
            finishRevision(db, revision, JSONObject().put("archivedIngredientId", ingredientId).put("reason", reason))
        }
    }

    fun ingredientFingerprint(db: SupportSQLiteDatabase, id: Long): String {
        val record = encodedQuery(db, "SELECT * FROM ingredients WHERE id=?", arrayOf<Any?>(id), personalColumns)
        val children = listOf("ingredient_nutrients", "ingredient_portions", "ingredient_aliases", "ingredient_quality_issues")
            .joinToString("") { table ->
                val order = when (table) { "ingredient_nutrients" -> "tag"; else -> "id" }
                CatalogFingerprint.token(table) + encodedQuery(db, "SELECT * FROM $table WHERE ingredientId=? ORDER BY $order", arrayOf<Any?>(id))
            }
        return CatalogFingerprint.sha256(record + children)
    }

    private fun beginRevision(db: SupportSQLiteDatabase, key: String, kind: String, source: String,
        assetHash: String? = null, datasetVersion: String? = null): String {
        val id = UUID.randomUUID().toString()
        val parent = db.query("SELECT revision_id FROM catalog_revision WHERE state='APPLIED' ORDER BY applied_seq DESC LIMIT 1")
            .use { if (it.moveToFirst()) it.getString(0) else null }
        db.execSQL("""
            INSERT INTO catalog_revision (revision_id,revision_key,parent_revision_id,kind,state,source_namespace,
            dataset_version,source_sha256,manifest_json,manifest_sha256,rules_version,actor,created_at)
            VALUES (?,?,?,?,'STAGED',?,?,?,'{}',?,'sqlite-typed-v1','NutriNest v9',?)
        """.trimIndent(), arrayOf<Any?>(id, key, parent, kind, source, datasetVersion, assetHash,
            CatalogFingerprint.sha256("{}"), System.currentTimeMillis()))
        return id
    }

    private fun finishRevision(db: SupportSQLiteDatabase, id: String, manifest: JSONObject) {
        validate(db)
        val hashes = JSONObject()
        db.query("SELECT ingredient_id,record_sha256 FROM ingredient_canonical_map WHERE origin_revision_id=? ORDER BY ingredient_id", arrayOf<Any?>(id)).use { c ->
            while (c.moveToNext()) hashes.put(c.getLong(0).toString(), c.getString(1))
        }
        val json = manifest.put("recordHashes", hashes).toString()
        db.execSQL("UPDATE catalog_revision SET state='APPLIED', manifest_json=?, manifest_sha256=?, applied_at=?, applied_seq=(SELECT COALESCE(MAX(applied_seq),0)+1 FROM catalog_revision) WHERE revision_id=? AND state='STAGED'",
            arrayOf<Any?>(json, CatalogFingerprint.sha256(json), System.currentTimeMillis(), id))
    }

    fun validate(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) { "Catalog foreign key violation" } }
        db.query("SELECT i.id FROM ingredients i LEFT JOIN ingredient_canonical_map m ON m.ingredient_id=i.id LEFT JOIN ingredient_catalog_status s ON s.ingredient_id=i.id WHERE m.ingredient_id IS NULL OR s.ingredient_id IS NULL LIMIT 1")
            .use { check(!it.moveToFirst()) { "Incomplete catalog metadata" } }
    }

    private fun hashQuery(db: SupportSQLiteDatabase, sql: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        db.query(sql).use { c -> while (c.moveToNext()) digest.update(encodeRow(c, emptySet()).toByteArray(Charsets.UTF_8)) }
        return CatalogFingerprint.hex(digest.digest())
    }

    private fun encodedQuery(db: SupportSQLiteDatabase, sql: String, args: Array<out Any?> = emptyArray(),
        excluded: Set<String> = emptySet()): String = db.query(sql, args).use { c ->
        buildString { while (c.moveToNext()) append(encodeRow(c, excluded)) }
    }

    private fun encodeRow(c: Cursor, excluded: Set<String>): String = buildString {
        append("row{")
        c.columnNames.sorted().filterNot { it in excluded }.forEach { name ->
            val i = c.getColumnIndexOrThrow(name)
            val type = c.getType(i)
            val value = when (type) {
                Cursor.FIELD_TYPE_NULL -> ""
                Cursor.FIELD_TYPE_INTEGER -> c.getLong(i).toString()
                Cursor.FIELD_TYPE_FLOAT -> CatalogFingerprint.real(c.getDouble(i))
                Cursor.FIELD_TYPE_BLOB -> CatalogFingerprint.hex(c.getBlob(i))
                else -> c.getString(i)
            }
            append(CatalogFingerprint.token(name)).append(type).append(CatalogFingerprint.token(value))
        }
        append('}')
    }

    private inline fun transaction(db: SupportSQLiteDatabase, action: () -> Unit) {
        val owns = !db.inTransaction()
        if (owns) db.beginTransaction()
        try { action(); if (owns) db.setTransactionSuccessful() } finally { if (owns) db.endTransaction() }
    }

    private val catalogTables = setOf("catalog_revision", "canonical_ingredients", "ingredient_canonical_map",
        "ingredient_catalog_status", "conversion_evidence", "ingredient_unit_options")
}
