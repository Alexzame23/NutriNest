package com.zame.nutrinest.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zame.nutrinest.util.TextNormalizer
import org.json.JSONArray
import org.json.JSONObject

/** Metadata-only editorial publication. Source compositions and source IDs are immutable. */
internal object CuratedCatalog {
    const val RULES_ASSET = "catalog-curation-v1.json"
    const val VERSION = "practical-catalog-v1"

    fun apply(context: Context, db: SupportSQLiteDatabase, assetHash: String, datasetVersion: String) {
        val text = context.assets.open(RULES_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        applyRules(db, text, assetHash, datasetVersion)
    }

    internal fun applyRules(db: SupportSQLiteDatabase, text: String, assetHash: String, datasetVersion: String) {
        val rules = JSONObject(text)
        check(rules.getString("version") == VERSION)
        check(rules.getString("sourceSha256") == assetHash && rules.getString("datasetVersion") == datasetVersion) {
            "Dataset has no matching reviewed curation manifest"
        }
        val rulesHash = CatalogFingerprint.sha256(text)
        val key = "curation:$VERSION:$assetHash"
        val owns = !db.inTransaction()
        if (owns) db.beginTransaction()
        try {
            val previous = db.query("SELECT state,manifest_json FROM catalog_revision WHERE revision_key=?", arrayOf(key)).use {
                if (it.moveToFirst()) {
                    check(it.getString(0) == "APPLIED")
                    JSONObject(it.getString(1))
                } else null
            }
            if (previous != null) {
                check(previous.getString("rulesSha256") == rulesHash) { "Rules changed without a version increment" }
            } else {
                publish(db, rules, rulesHash, key, assetHash, datasetVersion)
            }
            if (owns) db.setTransactionSuccessful()
        } finally { if (owns) db.endTransaction() }
    }

    private fun publish(db: SupportSQLiteDatabase, rules: JSONObject, rulesHash: String, key: String,
        assetHash: String, datasetVersion: String) {
        val original = CanonicalCatalogStore.legacySnapshot(db)
        val before = statistics(db)
        val revision = CanonicalCatalogStore.beginRevision(db, key, "REVIEW", "BEDCA",
            assetHash, datasetVersion, VERSION, "NutriNest editorial rules v1 (singleton policy)")
        val decisions = JSONArray()
        val preserved = JSONArray()
        val entries = rules.getJSONArray("entries")
        val seen = mutableSetOf<Long>()
        val encountered = mutableSetOf<Long>()
        for (index in 0 until entries.length()) {
            val rule = entries.getJSONObject(index)
            val foodId = rule.getLong("foodId")
            check(seen.add(foodId)) { "Duplicate rule for BEDCA:$foodId" }
            val id = FoodDatabaseImporter.IMPORTED_ID_OFFSET + foodId
            val current = metadata(db, id) ?: continue
            encountered += id
            // Accreditation is required. Legacy sourceKey text is insufficient.
            // Any prior manual/archive/editorial decision wins over later imports.
            val accredited = current.getString("source_namespace") == "BEDCA" &&
                current.getString("source_record_key") == foodId.toString() &&
                current.optInt("source_head_slot") == 1 && current.getString("source_sha256") == assetHash
            val exactLegacy = current.getString("source_namespace").startsWith("LEGACY:") &&
                matchesSnapshot(db, id, rule.getJSONObject("snapshot"))
            val eligible = current.getInt("isCustom") == 0 &&
                current.getString("sourceKey") == "BEDCA:$foodId" &&
                (accredited || exactLegacy) &&
                current.getString("reason_code") == "LEGACY_VISIBILITY" &&
                current.getString("identity_state") == "PROVISIONAL" &&
                current.getString("updated_revision_id") == current.getString("created_revision_id")
            if (!eligible) {
                preserved.put(JSONObject().put("ingredientId", id).put("reason", "PRIOR_DECISION_OR_UNVERIFIED_SOURCE"))
                continue
            }
            check(current.getString("record_sha256") == CanonicalCatalogStore.ingredientFingerprint(db, id)) {
                "Source snapshot changed: $id"
            }
            val state = rule.getString("state")
            check(state in setOf("VISIBLE", "SECONDARY", "REVIEW_REQUIRED", "HIDDEN", "OBSOLETE"))
            val es = rule.getString("nameEs").also { check(it.isNotBlank()) }
            val en = rule.getString("nameEn").also { check(it.isNotBlank()) }
            val editorialRule = JSONObject(rule.toString()).apply { remove("snapshot") }
            // Names are display labels only; no shared canonical IDs or replacements.
            db.execSQL("""
                UPDATE canonical_ingredients SET concept_key=?,variant_key=?,part_key=?,preparation_key=?,
                preservation_key=?,form_key=?,formulation_key=?,name_es=?,name_en=?,name_es_norm=?,
                name_en_norm=?,updated_revision_id=? WHERE canonical_id=?
            """.trimIndent(), arrayOf<Any?>(rule.getString("conceptKey"), rule.getString("variantKey"),
                rule.getString("partKey"), rule.getString("preparationKey"), rule.getString("preservationKey"),
                rule.getString("formKey"), rule.getString("formulationKey"), es, en,
                TextNormalizer.normalize(es), TextNormalizer.normalize(en), revision, current.getString("canonical_id")))
            db.execSQL("""
                UPDATE ingredient_catalog_status SET state=?,reason_code=?,detail=?,revision_id=?
                WHERE ingredient_id=?
            """.trimIndent(), arrayOf<Any?>(state, rule.getString("rule"), editorialRule.toString(), revision, id))
            val beforeFields = arrayOf("canonical_id", "concept_key", "variant_key", "part_key", "preparation_key",
                "preservation_key", "form_key", "formulation_key", "name_es", "name_en", "name_es_norm", "name_en_norm",
                "updated_revision_id", "state", "reason_code", "detail", "revision_id")
            decisions.put(JSONObject().put("ingredientId", id).put("before", JSONObject(current, beforeFields))
                .put("after", editorialRule)
                .put("sourceProof", if (accredited) "ACCREDITED_IMPORT" else "EXACT_LEGACY_SNAPSHOT"))
        }
        db.query("SELECT id FROM ingredients ORDER BY id").use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (id !in encountered) preserved.put(JSONObject().put("ingredientId", id).put("reason", "OUTSIDE_PINNED_SOURCE_IDS"))
            }
        }
        check(original == CanonicalCatalogStore.legacySnapshot(db)) { "Curation changed historical tables" }
        CanonicalCatalogStore.finishRevision(db, revision, JSONObject()
            // The immutable versioned asset contains full source proofs. Avoid embedding
            // it twice in a single SQLite row (Android CursorWindow size is bounded).
            .put("rulesSha256", rulesHash).put("rulesAsset", RULES_ASSET).put("rulesVersion", VERSION).put("before", before)
            .put("after", statistics(db)).put("decisions", decisions).put("preserved", preserved)
            .put("legacyTableHashes", JSONObject(original)))
    }

    /** Full imported projection, including all child values. No name-only legacy matching. */
    private fun matchesSnapshot(db: SupportSQLiteDatabase, id: Long, snapshot: JSONObject): Boolean {
        val attributes = snapshot.getJSONObject("attributes")
        fun same(expected: Any?, cursor: android.database.Cursor, index: Int): Boolean = when {
            expected == null || expected == JSONObject.NULL -> cursor.isNull(index)
            cursor.isNull(index) -> false
            expected is Number -> expected.toDouble() == cursor.getDouble(index)
            else -> expected.toString() == cursor.getString(index)
        }
        val attributesMatch = db.query("SELECT * FROM ingredients WHERE id=?", arrayOf<Any>(id)).use { c ->
            c.moveToFirst() && attributes.keys().asSequence().all { key ->
                same(attributes.get(key), c, c.getColumnIndexOrThrow(key))
            }
        }
        if (!attributesMatch) return false
        val children = mapOf(
            "ingredient_nutrients" to "tag,value",
            "ingredient_portions" to "labelEn,labelEs,grams,portionType,isDefault",
            "ingredient_aliases" to "alias,aliasNorm,lang",
            "ingredient_quality_issues" to "code,severity,detail")
        return children.all { (table, columns) ->
            val expected = snapshot.getJSONObject("children").getJSONArray(table)
            val unmatched = (0 until expected.length()).map { expected.getJSONArray(it) }.toMutableList()
            db.query("SELECT $columns FROM $table WHERE ingredientId=?", arrayOf<Any>(id)).use { c ->
                var valid = true
                while (c.moveToNext()) {
                    val index = unmatched.indexOfFirst { row ->
                        row.length() == c.columnCount && (0 until c.columnCount).all { same(row.get(it), c, it) }
                    }
                    if (index < 0) { valid = false; break }
                    unmatched.removeAt(index)
                }
                valid && unmatched.isEmpty()
            }
        }
    }

    /** Manual visibility review only. Identity equivalence needs a separate reviewed publisher. */
    fun review(db: SupportSQLiteDatabase, ingredientId: Long, expectedRevision: String, state: String,
        reviewer: String, evidence: String) {
        require(reviewer.isNotBlank() && evidence.isNotBlank())
        require(state in setOf("VISIBLE", "SECONDARY", "HIDDEN", "REVIEW_REQUIRED", "OBSOLETE"))
        check(!db.inTransaction()) { "Review owns its transaction" }
        db.beginTransaction()
        try {
            val before = metadata(db, ingredientId) ?: error("Unknown ingredient")
            check(before.getString("revision_id") == expectedRevision) { "Stale review; reload the current decision" }
            val key = "manual:" + CatalogFingerprint.sha256("$ingredientId|$expectedRevision|$state|$reviewer|$evidence")
            val revision = CanonicalCatalogStore.beginRevision(db, key, "REVIEW", "LOCAL",
                rulesVersion = "manual-visibility-v1", actor = reviewer)
            db.execSQL("UPDATE ingredient_catalog_status SET state=?,reason_code='MANUAL_REVIEW',detail=?,revision_id=? WHERE ingredient_id=?",
                arrayOf<Any>(state, evidence, revision, ingredientId))
            CanonicalCatalogStore.finishRevision(db, revision, JSONObject().put("before", before)
                .put("after", metadata(db, ingredientId)).put("reviewer", reviewer).put("evidence", evidence))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    internal fun statistics(db: SupportSQLiteDatabase): JSONObject {
        val counts = JSONObject()
        db.query("SELECT state,COUNT(*) FROM ingredient_catalog_status GROUP BY state ORDER BY state").use {
            while (it.moveToNext()) counts.put(it.getString(0), it.getLong(1))
        }
        fun count(sql: String) = db.query(sql).use { it.moveToFirst(); it.getLong(0) }
        return JSONObject().put("states", counts)
            .put("ingredients", count("SELECT COUNT(*) FROM ingredients"))
            .put("mapped", count("SELECT COUNT(*) FROM ingredient_canonical_map"))
            .put("bilingual", count("SELECT COUNT(*) FROM canonical_ingredients WHERE length(name_es)>0 AND length(name_en)>0"))
            .put("recipeLinks", count("SELECT COUNT(*) FROM recipe_ingredients"))
            .put("missingRecipeReferences", count("SELECT COUNT(*) FROM recipe_ingredients ri LEFT JOIN ingredients i ON i.id=ri.ingredientId WHERE i.id IS NULL"))
            .put("favorites", count("SELECT COUNT(*) FROM ingredients WHERE isFavorite=1"))
            .put("custom", count("SELECT COUNT(*) FROM ingredients WHERE isCustom=1"))
    }

    private fun metadata(db: SupportSQLiteDatabase, id: Long): JSONObject? = db.query("""
        SELECT i.isCustom,i.sourceKey,c.*,s.state,s.reason_code,s.detail,s.replacement_ingredient_id,s.revision_id,
               m.source_namespace,m.source_record_key,m.source_head_slot,m.record_sha256,r.source_sha256
        FROM ingredients i JOIN ingredient_canonical_map m ON m.ingredient_id=i.id
        JOIN canonical_ingredients c ON c.canonical_id=m.canonical_id
        JOIN ingredient_catalog_status s ON s.ingredient_id=i.id
        JOIN catalog_revision r ON r.revision_id=m.origin_revision_id WHERE i.id=?
    """.trimIndent(), arrayOf<Any>(id)).use { c ->
        if (!c.moveToFirst()) null else JSONObject().apply {
            c.columnNames.forEachIndexed { index, name ->
                put(name, if (c.isNull(index)) JSONObject.NULL else c.getString(index))
            }
        }
    }
}
