package com.zame.nutrinest.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Additive v9 catalog metadata. Composition and historical references remain in v8 tables. */
@Entity(
    tableName = "catalog_revision",
    primaryKeys = ["revision_id"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogRevisionEntity::class,
            parentColumns = ["revision_id"],
            childColumns = ["parent_revision_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
    ],
    indices = [
        Index(value = ["source_namespace", "dataset_version"], name = "index_catalog_revision_source", unique = false),
        Index(value = ["parent_revision_id"], name = "index_catalog_revision_parent", unique = false),
        Index(value = ["applied_seq"], name = "index_catalog_revision_sequence", unique = true),
        Index(value = ["revision_key"], name = "index_catalog_revision_key", unique = true),
    ]
)
data class CatalogRevisionEntity(
    @ColumnInfo(name = "revision_id") val revisionId: String,
    @ColumnInfo(name = "revision_key") val revisionKey: String,
    @ColumnInfo(name = "parent_revision_id") val parentRevisionId: String? = null,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "source_namespace") val sourceNamespace: String,
    @ColumnInfo(name = "dataset_version") val datasetVersion: String? = null,
    @ColumnInfo(name = "source_sha256") val sourceSha256: String? = null,
    @ColumnInfo(name = "manifest_json") val manifestJson: String,
    @ColumnInfo(name = "manifest_sha256") val manifestSha256: String,
    @ColumnInfo(name = "rules_version") val rulesVersion: String,
    @ColumnInfo(name = "actor") val actor: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "applied_at") val appliedAt: Long? = null,
    @ColumnInfo(name = "applied_seq") val appliedSeq: Long? = null,
)

@Entity(
    tableName = "canonical_ingredients",
    primaryKeys = ["canonical_id"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogRevisionEntity::class,
            parentColumns = ["revision_id"],
            childColumns = ["updated_revision_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CatalogRevisionEntity::class,
            parentColumns = ["revision_id"],
            childColumns = ["created_revision_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
    ],
    indices = [
        Index(value = ["updated_revision_id"], name = "index_canonical_updated_revision", unique = false),
        Index(value = ["created_revision_id"], name = "index_canonical_created_revision", unique = false),
        Index(value = ["name_en_norm"], name = "index_canonical_name_en", unique = false),
        Index(value = ["name_es_norm"], name = "index_canonical_name_es", unique = false),
        Index(value = ["canonical_id", "scope_key"], name = "index_canonical_id_scope", unique = true),
        Index(value = ["scope_key", "concept_key", "part_key", "preparation_key", "preservation_key", "form_key", "formulation_key", "variant_key"], name = "index_canonical_identity", unique = true),
    ]
)
data class CanonicalIngredientEntity(
    @ColumnInfo(name = "canonical_id") val canonicalId: String,
    @ColumnInfo(name = "scope_key") val scopeKey: String,
    @ColumnInfo(name = "concept_key") val conceptKey: String,
    @ColumnInfo(name = "part_key") val partKey: String,
    @ColumnInfo(name = "preparation_key") val preparationKey: String,
    @ColumnInfo(name = "preservation_key") val preservationKey: String,
    @ColumnInfo(name = "form_key") val formKey: String,
    @ColumnInfo(name = "formulation_key") val formulationKey: String,
    @ColumnInfo(name = "variant_key") val variantKey: String,
    @ColumnInfo(name = "identity_state") val identityState: String,
    @ColumnInfo(name = "name_es") val nameEs: String? = null,
    @ColumnInfo(name = "name_en") val nameEn: String? = null,
    @ColumnInfo(name = "name_es_norm") val nameEsNorm: String? = null,
    @ColumnInfo(name = "name_en_norm") val nameEnNorm: String? = null,
    @ColumnInfo(name = "created_revision_id") val createdRevisionId: String,
    @ColumnInfo(name = "updated_revision_id") val updatedRevisionId: String,
)

@Entity(
    tableName = "ingredient_canonical_map",
    primaryKeys = ["ingredient_id"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogRevisionEntity::class,
            parentColumns = ["revision_id"],
            childColumns = ["assigned_revision_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CatalogRevisionEntity::class,
            parentColumns = ["revision_id"],
            childColumns = ["origin_revision_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CanonicalIngredientEntity::class,
            parentColumns = ["canonical_id", "scope_key"],
            childColumns = ["canonical_id", "scope_key"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
    ],
    indices = [
        Index(value = ["assigned_revision_id"], name = "index_map_assigned_revision", unique = false),
        Index(value = ["origin_revision_id"], name = "index_map_origin_revision", unique = false),
        Index(value = ["canonical_id", "scope_key"], name = "index_map_canonical_scope", unique = false),
        Index(value = ["source_namespace", "source_record_key", "source_head_slot"], name = "index_map_source_head", unique = true),
    ]
)
data class IngredientCanonicalMapEntity(
    @ColumnInfo(name = "ingredient_id") val ingredientId: Long,
    @ColumnInfo(name = "canonical_id") val canonicalId: String,
    @ColumnInfo(name = "scope_key") val scopeKey: String,
    @ColumnInfo(name = "source_namespace") val sourceNamespace: String,
    @ColumnInfo(name = "source_record_key") val sourceRecordKey: String,
    @ColumnInfo(name = "source_head_slot") val sourceHeadSlot: Long? = null,
    @ColumnInfo(name = "record_sha256") val recordSha256: String,
    @ColumnInfo(name = "mapping_basis") val mappingBasis: String,
    @ColumnInfo(name = "origin_revision_id") val originRevisionId: String,
    @ColumnInfo(name = "assigned_revision_id") val assignedRevisionId: String,
)

@Entity(
    tableName = "ingredient_catalog_status",
    primaryKeys = ["ingredient_id"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogRevisionEntity::class,
            parentColumns = ["revision_id"],
            childColumns = ["revision_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["replacement_ingredient_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
    ],
    indices = [
        Index(value = ["revision_id"], name = "index_catalog_status_revision", unique = false),
        Index(value = ["replacement_ingredient_id"], name = "index_catalog_status_replacement", unique = false),
        Index(value = ["state", "ingredient_id"], name = "index_catalog_status_state", unique = false),
    ]
)
data class IngredientCatalogStatusEntity(
    @ColumnInfo(name = "ingredient_id") val ingredientId: Long,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "reason_code") val reasonCode: String,
    @ColumnInfo(name = "detail") val detail: String,
    @ColumnInfo(name = "replacement_ingredient_id") val replacementIngredientId: Long? = null,
    @ColumnInfo(name = "revision_id") val revisionId: String,
)

@Entity(
    tableName = "conversion_evidence",
    primaryKeys = ["evidence_id"],
    foreignKeys = [
        ForeignKey(
            entity = ConversionEvidenceEntity::class,
            parentColumns = ["evidence_id", "ingredient_id", "unit_key"],
            childColumns = ["supersedes_evidence_id", "ingredient_id", "unit_key"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CatalogRevisionEntity::class,
            parentColumns = ["revision_id"],
            childColumns = ["revision_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
    ],
    indices = [
        Index(value = ["revision_id"], name = "index_evidence_revision", unique = false),
        Index(value = ["supersedes_evidence_id", "ingredient_id", "unit_key"], name = "index_evidence_supersedes", unique = false),
        Index(value = ["ingredient_id", "unit_key"], name = "index_evidence_ingredient_unit", unique = false),
        Index(value = ["evidence_id", "ingredient_id", "unit_key"], name = "index_evidence_binding", unique = true),
    ]
)
data class ConversionEvidenceEntity(
    @ColumnInfo(name = "evidence_id") val evidenceId: String,
    @ColumnInfo(name = "ingredient_id") val ingredientId: Long,
    @ColumnInfo(name = "unit_key") val unitKey: String,
    @ColumnInfo(name = "grams_per_unit") val gramsPerUnit: Double? = null,
    @ColumnInfo(name = "mass_basis") val massBasis: String,
    @ColumnInfo(name = "method") val method: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "evidence_version") val evidenceVersion: String,
    @ColumnInfo(name = "confidence") val confidence: String,
    @ColumnInfo(name = "source_locator") val sourceLocator: String,
    @ColumnInfo(name = "source_excerpt") val sourceExcerpt: String,
    @ColumnInfo(name = "conditions") val conditions: String,
    @ColumnInfo(name = "reviewed_by") val reviewedBy: String? = null,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long? = null,
    @ColumnInfo(name = "supersedes_evidence_id") val supersedesEvidenceId: String? = null,
    @ColumnInfo(name = "revision_id") val revisionId: String,
)

@Entity(
    tableName = "ingredient_unit_options",
    primaryKeys = ["option_id"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogRevisionEntity::class,
            parentColumns = ["revision_id"],
            childColumns = ["revision_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ConversionEvidenceEntity::class,
            parentColumns = ["evidence_id", "ingredient_id", "unit_key"],
            childColumns = ["evidence_id", "ingredient_id", "unit_key"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
    ],
    indices = [
        Index(value = ["revision_id"], name = "index_unit_revision", unique = false),
        Index(value = ["evidence_id", "ingredient_id", "unit_key"], name = "index_unit_evidence_binding", unique = false),
        Index(value = ["ingredient_id", "default_slot"], name = "index_unit_default", unique = true),
        Index(value = ["ingredient_id", "unit_key"], name = "index_unit_ingredient_key", unique = true),
    ]
)
data class IngredientUnitOptionEntity(
    @ColumnInfo(name = "option_id") val optionId: String,
    @ColumnInfo(name = "ingredient_id") val ingredientId: Long,
    @ColumnInfo(name = "unit_key") val unitKey: String,
    @ColumnInfo(name = "unit_kind") val unitKind: String,
    @ColumnInfo(name = "label_es") val labelEs: String? = null,
    @ColumnInfo(name = "label_en") val labelEn: String? = null,
    @ColumnInfo(name = "evidence_id") val evidenceId: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
    @ColumnInfo(name = "default_slot") val defaultSlot: Long? = null,
    @ColumnInfo(name = "revision_id") val revisionId: String,
)
