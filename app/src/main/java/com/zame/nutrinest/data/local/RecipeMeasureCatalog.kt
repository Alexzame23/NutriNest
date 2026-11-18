package com.zame.nutrinest.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Adds documented measures to the verified food snapshots in the shipped catalog. */
internal object RecipeMeasureCatalog {
    private const val VERSION = "recipe-measures-v1"
    private const val BEDCA_HASH = "66682591bb5ae18bd53f7b0d9f720718e3dd42abe81fc08a2d90e2141da90359"
    private const val REVIEWER = "NutriNest recipe measure catalog"

    private data class Reference(
        val foodId: Long,
        val unitKey: String,
        val kind: String,
        val gramsPerUnit: Double,
        val labelEs: String,
        val labelEn: String,
        val source: String,
        val excerpt: String,
        val conditions: String
    ) {
        val ingredientId get() = FoodDatabaseImporter.IMPORTED_ID_OFFSET + foodId
        val evidenceId get() = "$VERSION:BEDCA:$foodId:$unitKey"
    }

    private val references = listOf(
        Reference(
            758, "UNIT:egg_large_poached", "COUNT", 50.0, "huevo grande", "large egg",
            "https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR/SR28/reports/sr28fg01.pdf",
            "NDB 01131, measure 1: 1 large, 50 g",
            "Whole poached chicken egg, large, edible weight without shell. Reference size."
        ),
        Reference(
            2493, "MILLILITRE", "VOLUME", 1.03, "ml", "ml",
            "https://www.fao.org/fileadmin/templates/food_composition/documents/density_DB_v2_0_01.pdf",
            "Milk, liquid, whole: 1.030 g/ml (TB), FAO/INFOODS Density Database v2.0",
            "Liquid whole cow milk. Reference density; temperature is unspecified for the TB entry."
        )
    )

    /** Runs inside the import transaction, after source verification and curation. */
    fun publish(db: SupportSQLiteDatabase, assetHash: String, datasetVersion: String) {
        if (assetHash != BEDCA_HASH) return
        val key = "$VERSION:$assetHash"
        if (db.query("SELECT 1 FROM catalog_revision WHERE revision_key=?", arrayOf(key)).use { it.moveToFirst() }) return
        val curationKey = "curation:${CuratedCatalog.VERSION}:$assetHash"
        val eligible = references.filter { reference ->
            val hash = db.query("""
                SELECT m.record_sha256 FROM ingredient_canonical_map m
                JOIN canonical_ingredients c ON c.canonical_id=m.canonical_id
                JOIN catalog_revision r ON r.revision_id=c.updated_revision_id
                WHERE m.ingredient_id=? AND c.variant_key=? AND r.revision_key=? AND r.state='APPLIED'
                  AND NOT EXISTS (SELECT 1 FROM ingredient_unit_options o WHERE o.ingredient_id=m.ingredient_id)
            """.trimIndent(), arrayOf<Any>(reference.ingredientId, "bedca:${reference.foodId}", curationKey))
                .use { if (it.moveToFirst()) it.getString(0) else null }
            hash != null && hash == CanonicalCatalogStore.ingredientFingerprint(db, reference.ingredientId)
        }
        val revision = CanonicalCatalogStore.beginRevision(
            db, key, "REVIEW", "NUTRINEST", assetHash, datasetVersion, VERSION, REVIEWER
        )
        val reviewedAt = System.currentTimeMillis()
        eligible.forEach { reference ->
            db.execSQL("""
                INSERT INTO conversion_evidence
                  (evidence_id,ingredient_id,unit_key,grams_per_unit,mass_basis,method,status,evidence_version,
                   confidence,source_locator,source_excerpt,conditions,reviewed_by,reviewed_at,revision_id)
                VALUES (?,?,?,?,'EDIBLE','DOCUMENTED','ACCEPTED',?,'MEDIUM',?,?,?,?,?,?)
            """.trimIndent(), arrayOf<Any>(
                reference.evidenceId, reference.ingredientId, reference.unitKey, reference.gramsPerUnit,
                VERSION, reference.source, reference.excerpt, reference.conditions, REVIEWER, reviewedAt, revision
            ))
            db.execSQL("""
                INSERT INTO ingredient_unit_options
                  (option_id,ingredient_id,unit_key,unit_kind,label_es,label_en,evidence_id,enabled,default_slot,revision_id)
                VALUES (?,?,?,?,?,?,?,1,1,?)
            """.trimIndent(), arrayOf<Any>(
                reference.evidenceId, reference.ingredientId, reference.unitKey, reference.kind,
                reference.labelEs, reference.labelEn, reference.evidenceId, revision
            ))
        }
        CanonicalCatalogStore.finishRevision(db, revision,
            JSONObject().put("evidenceIds", JSONArray(eligible.map { it.evidenceId })))
    }
}
