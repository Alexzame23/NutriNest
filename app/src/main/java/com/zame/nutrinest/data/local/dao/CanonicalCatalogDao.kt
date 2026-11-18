package com.zame.nutrinest.data.local.dao

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zame.nutrinest.data.local.entity.*
import kotlinx.coroutines.flow.Flow

data class CatalogRecipeMeasure(
    @Embedded val evidence: ConversionEvidenceEntity,
    @ColumnInfo(name = "unit_kind") val kind: String,
    @ColumnInfo(name = "label_en") val labelEn: String?,
    @ColumnInfo(name = "label_es") val labelEs: String?,
    @ColumnInfo(name = "default_slot") val defaultSlot: Long?
)

/** Historical lookup never filters status. Publication remains an explicit reviewed operation. */
@Dao
interface CanonicalCatalogDao {
    @Query("SELECT * FROM ingredient_canonical_map WHERE ingredient_id=:ingredientId")
    suspend fun mapping(ingredientId: Long): IngredientCanonicalMapEntity?

    @Query("SELECT * FROM ingredient_catalog_status WHERE ingredient_id=:ingredientId")
    suspend fun status(ingredientId: Long): IngredientCatalogStatusEntity?

    @Query("SELECT * FROM canonical_ingredients WHERE canonical_id=:canonicalId")
    suspend fun identity(canonicalId: String): CanonicalIngredientEntity?

    @Query("SELECT * FROM catalog_revision ORDER BY created_at, revision_id")
    fun revisions(): Flow<List<CatalogRevisionEntity>>

    @Query("""
        SELECT e.* FROM conversion_evidence e
        JOIN ingredient_unit_options o ON o.evidence_id=e.evidence_id
          AND o.ingredient_id=e.ingredient_id AND o.unit_key=e.unit_key
        JOIN catalog_revision r ON r.revision_id=e.revision_id
        JOIN catalog_revision ro ON ro.revision_id=o.revision_id
        WHERE e.ingredient_id=:ingredientId AND o.enabled=1
          AND e.status='ACCEPTED' AND e.mass_basis='EDIBLE' AND e.confidence<>'UNVERIFIED'
          AND r.state='APPLIED' AND ro.state='APPLIED'
    """)
    suspend fun acceptedEvidence(ingredientId: Long): List<ConversionEvidenceEntity>

    @Query("""
        SELECT e.*, o.unit_kind, o.label_en, o.label_es, o.default_slot
        FROM ingredient_unit_options o
        JOIN conversion_evidence e ON e.evidence_id=o.evidence_id
          AND e.ingredient_id=o.ingredient_id AND e.unit_key=o.unit_key
        JOIN catalog_revision r ON r.revision_id=e.revision_id
        JOIN catalog_revision ro ON ro.revision_id=o.revision_id
        WHERE o.enabled=1 AND e.status='ACCEPTED' AND e.mass_basis='EDIBLE'
          AND e.confidence<>'UNVERIFIED' AND r.state='APPLIED' AND ro.state='APPLIED'
        ORDER BY o.default_slot DESC, e.unit_key
    """)
    fun observeRecipeMeasures(): Flow<List<CatalogRecipeMeasure>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvidence(evidence: ConversionEvidenceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUnitOption(option: IngredientUnitOptionEntity)
}
