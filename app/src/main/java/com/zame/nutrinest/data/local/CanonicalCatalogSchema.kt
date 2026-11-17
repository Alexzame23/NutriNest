package com.zame.nutrinest.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/** v9 DDL; identical validation triggers are installed on fresh and migrated databases. */
internal object CanonicalCatalogSchema {
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE catalog_revision (
                revision_id TEXT NOT NULL PRIMARY KEY,
                revision_key TEXT NOT NULL,
                parent_revision_id TEXT,
                kind TEXT NOT NULL CHECK(kind IN ('BASELINE','DATASET','REVIEW','USER_EDIT')),
                state TEXT NOT NULL CHECK(state IN ('STAGED','APPLIED','REJECTED')),
                source_namespace TEXT NOT NULL,
                dataset_version TEXT,
                source_sha256 TEXT CHECK(source_sha256 IS NULL OR (length(source_sha256)=64 AND source_sha256 NOT GLOB '*[^0-9a-f]*')),
                manifest_json TEXT NOT NULL,
                manifest_sha256 TEXT NOT NULL CHECK(length(manifest_sha256)=64 AND manifest_sha256 NOT GLOB '*[^0-9a-f]*'),
                rules_version TEXT NOT NULL,
                actor TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                applied_at INTEGER,
                applied_seq INTEGER CHECK(applied_seq IS NULL OR applied_seq > 0),
                CHECK(parent_revision_id IS NULL OR parent_revision_id <> revision_id),
                CHECK((state='APPLIED' AND applied_at IS NOT NULL AND applied_seq IS NOT NULL)
                   OR (state<>'APPLIED' AND applied_at IS NULL AND applied_seq IS NULL)),
                FOREIGN KEY(parent_revision_id) REFERENCES catalog_revision(revision_id) ON DELETE RESTRICT ON UPDATE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX index_catalog_revision_key ON catalog_revision(revision_key)
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX index_catalog_revision_sequence ON catalog_revision(applied_seq)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_catalog_revision_parent ON catalog_revision(parent_revision_id)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_catalog_revision_source ON catalog_revision(source_namespace,dataset_version)
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE canonical_ingredients (
                canonical_id TEXT NOT NULL PRIMARY KEY,
                scope_key TEXT NOT NULL,
                concept_key TEXT NOT NULL,
                part_key TEXT NOT NULL,
                preparation_key TEXT NOT NULL,
                preservation_key TEXT NOT NULL,
                form_key TEXT NOT NULL,
                formulation_key TEXT NOT NULL,
                variant_key TEXT NOT NULL,
                identity_state TEXT NOT NULL CHECK(identity_state IN ('PROVISIONAL','REVIEWED')),
                name_es TEXT,
                name_en TEXT,
                name_es_norm TEXT,
                name_en_norm TEXT,
                created_revision_id TEXT NOT NULL,
                updated_revision_id TEXT NOT NULL,
                CHECK(length(scope_key)>0 AND length(concept_key)>0 AND length(part_key)>0
                  AND length(preparation_key)>0 AND length(preservation_key)>0
                  AND length(form_key)>0 AND length(formulation_key)>0 AND length(variant_key)>0),
                CHECK((name_es IS NOT NULL AND length(trim(name_es))>0)
                   OR (name_en IS NOT NULL AND length(trim(name_en))>0)),
                FOREIGN KEY(created_revision_id) REFERENCES catalog_revision(revision_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
                FOREIGN KEY(updated_revision_id) REFERENCES catalog_revision(revision_id) ON DELETE RESTRICT ON UPDATE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX index_canonical_identity ON canonical_ingredients(
                scope_key,concept_key,part_key,preparation_key,preservation_key,form_key,formulation_key,variant_key)
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX index_canonical_id_scope ON canonical_ingredients(canonical_id,scope_key)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_canonical_name_es ON canonical_ingredients(name_es_norm)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_canonical_name_en ON canonical_ingredients(name_en_norm)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_canonical_created_revision ON canonical_ingredients(created_revision_id)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_canonical_updated_revision ON canonical_ingredients(updated_revision_id)
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE ingredient_canonical_map (
                ingredient_id INTEGER NOT NULL PRIMARY KEY,
                canonical_id TEXT NOT NULL,
                scope_key TEXT NOT NULL,
                source_namespace TEXT NOT NULL,
                source_record_key TEXT NOT NULL,
                source_head_slot INTEGER CHECK(source_head_slot IS NULL OR source_head_slot=1),
                record_sha256 TEXT NOT NULL CHECK(length(record_sha256)=64 AND record_sha256 NOT GLOB '*[^0-9a-f]*'),
                mapping_basis TEXT NOT NULL CHECK(mapping_basis IN ('LEGACY_SINGLETON','REVIEWED','LOCAL')),
                origin_revision_id TEXT NOT NULL,
                assigned_revision_id TEXT NOT NULL,
                CHECK(length(source_namespace)>0 AND length(source_record_key)>0),
                FOREIGN KEY(ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
                FOREIGN KEY(canonical_id,scope_key) REFERENCES canonical_ingredients(canonical_id,scope_key) ON DELETE RESTRICT ON UPDATE RESTRICT,
                FOREIGN KEY(origin_revision_id) REFERENCES catalog_revision(revision_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
                FOREIGN KEY(assigned_revision_id) REFERENCES catalog_revision(revision_id) ON DELETE RESTRICT ON UPDATE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX index_map_source_head ON ingredient_canonical_map(source_namespace,source_record_key,source_head_slot)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_map_canonical_scope ON ingredient_canonical_map(canonical_id,scope_key)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_map_origin_revision ON ingredient_canonical_map(origin_revision_id)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_map_assigned_revision ON ingredient_canonical_map(assigned_revision_id)
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE ingredient_catalog_status (
                ingredient_id INTEGER NOT NULL PRIMARY KEY,
                state TEXT NOT NULL CHECK(state IN ('VISIBLE','SECONDARY','HIDDEN','REVIEW_REQUIRED','OBSOLETE')),
                reason_code TEXT NOT NULL,
                detail TEXT NOT NULL,
                replacement_ingredient_id INTEGER,
                revision_id TEXT NOT NULL,
                CHECK(length(reason_code)>0),
                CHECK(replacement_ingredient_id IS NULL OR replacement_ingredient_id<>ingredient_id),
                FOREIGN KEY(ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
                FOREIGN KEY(replacement_ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
                FOREIGN KEY(revision_id) REFERENCES catalog_revision(revision_id) ON DELETE RESTRICT ON UPDATE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_catalog_status_state ON ingredient_catalog_status(state,ingredient_id)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_catalog_status_replacement ON ingredient_catalog_status(replacement_ingredient_id)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_catalog_status_revision ON ingredient_catalog_status(revision_id)
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE conversion_evidence (
                evidence_id TEXT NOT NULL PRIMARY KEY,
                ingredient_id INTEGER NOT NULL,
                unit_key TEXT NOT NULL,
                grams_per_unit REAL,
                mass_basis TEXT NOT NULL CHECK(mass_basis IN ('EDIBLE','GROSS','NET','DRAINED','UNKNOWN')),
                method TEXT NOT NULL CHECK(method IN ('LEGACY_PORTION','LEGACY_DENSITY','MEASURED','DOCUMENTED','USER_DECLARED')),
                status TEXT NOT NULL CHECK(status IN ('UNVERIFIED','ACCEPTED','REJECTED')),
                evidence_version TEXT NOT NULL,
                confidence TEXT NOT NULL CHECK(confidence IN ('HIGH','MEDIUM','LOW','UNVERIFIED')),
                source_locator TEXT NOT NULL,
                source_excerpt TEXT NOT NULL,
                conditions TEXT NOT NULL,
                reviewed_by TEXT,
                reviewed_at INTEGER,
                supersedes_evidence_id TEXT,
                revision_id TEXT NOT NULL,
                CHECK(length(unit_key)>0 AND length(source_locator)>0 AND length(evidence_version)>0),
                CHECK(grams_per_unit IS NULL OR (typeof(grams_per_unit) IN ('real','integer') AND grams_per_unit>0 AND grams_per_unit<1.0e308)),
                CHECK(status<>'ACCEPTED' OR (grams_per_unit IS NOT NULL AND mass_basis='EDIBLE' AND confidence<>'UNVERIFIED'
                  AND reviewed_by IS NOT NULL AND length(trim(reviewed_by))>0 AND reviewed_at IS NOT NULL)),
                CHECK(supersedes_evidence_id IS NULL OR supersedes_evidence_id<>evidence_id),
                FOREIGN KEY(ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
                FOREIGN KEY(revision_id) REFERENCES catalog_revision(revision_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
                FOREIGN KEY(supersedes_evidence_id,ingredient_id,unit_key)
                  REFERENCES conversion_evidence(evidence_id,ingredient_id,unit_key) ON DELETE RESTRICT ON UPDATE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX index_evidence_binding ON conversion_evidence(evidence_id,ingredient_id,unit_key)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_evidence_ingredient_unit ON conversion_evidence(ingredient_id,unit_key)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_evidence_supersedes ON conversion_evidence(supersedes_evidence_id,ingredient_id,unit_key)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_evidence_revision ON conversion_evidence(revision_id)
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE ingredient_unit_options (
                option_id TEXT NOT NULL PRIMARY KEY,
                ingredient_id INTEGER NOT NULL,
                unit_key TEXT NOT NULL,
                unit_kind TEXT NOT NULL CHECK(unit_kind IN ('COUNT','VOLUME','PORTION')),
                label_es TEXT,
                label_en TEXT,
                evidence_id TEXT NOT NULL,
                enabled INTEGER NOT NULL CHECK(enabled IN (0,1)),
                default_slot INTEGER CHECK(default_slot IS NULL OR default_slot=1),
                revision_id TEXT NOT NULL,
                CHECK(default_slot IS NULL OR enabled=1),
                CHECK((label_es IS NOT NULL AND length(trim(label_es))>0)
                   OR (label_en IS NOT NULL AND length(trim(label_en))>0)),
                FOREIGN KEY(ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
                FOREIGN KEY(evidence_id,ingredient_id,unit_key)
                  REFERENCES conversion_evidence(evidence_id,ingredient_id,unit_key) ON DELETE RESTRICT ON UPDATE RESTRICT,
                FOREIGN KEY(revision_id) REFERENCES catalog_revision(revision_id) ON DELETE RESTRICT ON UPDATE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX index_unit_ingredient_key ON ingredient_unit_options(ingredient_id,unit_key)
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX index_unit_default ON ingredient_unit_options(ingredient_id,default_slot)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_unit_evidence_binding ON ingredient_unit_options(evidence_id,ingredient_id,unit_key)
        """.trimIndent())
        db.execSQL("""
            CREATE INDEX index_unit_revision ON ingredient_unit_options(revision_id)
        """.trimIndent())
        installValidationTriggers(db)
    }

    fun installValidationTriggers(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_catalog_revision_insert BEFORE INSERT ON catalog_revision
            WHEN NOT (COALESCE((NEW.kind IN ('BASELINE','DATASET','REVIEW','USER_EDIT')),1) AND COALESCE((NEW.state IN ('STAGED','APPLIED','REJECTED')),1) AND COALESCE((NEW.source_sha256 IS NULL OR (length(NEW.source_sha256)=64 AND NEW.source_sha256 NOT GLOB '*[^0-9a-f]*')),1) AND COALESCE((length(NEW.manifest_sha256)=64 AND NEW.manifest_sha256 NOT GLOB '*[^0-9a-f]*'),1) AND COALESCE((NEW.applied_seq IS NULL OR NEW.applied_seq > 0),1) AND COALESCE((NEW.parent_revision_id IS NULL OR NEW.parent_revision_id <> NEW.revision_id),1) AND COALESCE(((NEW.state='APPLIED' AND NEW.applied_at IS NOT NULL AND NEW.applied_seq IS NOT NULL)
                   OR (NEW.state<>'APPLIED' AND NEW.applied_at IS NULL AND NEW.applied_seq IS NULL)),1))
            BEGIN SELECT RAISE(ABORT, 'Invalid catalog_revision'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_catalog_revision_update BEFORE UPDATE ON catalog_revision
            WHEN NOT (COALESCE((NEW.kind IN ('BASELINE','DATASET','REVIEW','USER_EDIT')),1) AND COALESCE((NEW.state IN ('STAGED','APPLIED','REJECTED')),1) AND COALESCE((NEW.source_sha256 IS NULL OR (length(NEW.source_sha256)=64 AND NEW.source_sha256 NOT GLOB '*[^0-9a-f]*')),1) AND COALESCE((length(NEW.manifest_sha256)=64 AND NEW.manifest_sha256 NOT GLOB '*[^0-9a-f]*'),1) AND COALESCE((NEW.applied_seq IS NULL OR NEW.applied_seq > 0),1) AND COALESCE((NEW.parent_revision_id IS NULL OR NEW.parent_revision_id <> NEW.revision_id),1) AND COALESCE(((NEW.state='APPLIED' AND NEW.applied_at IS NOT NULL AND NEW.applied_seq IS NOT NULL)
                   OR (NEW.state<>'APPLIED' AND NEW.applied_at IS NULL AND NEW.applied_seq IS NULL)),1))
            BEGIN SELECT RAISE(ABORT, 'Invalid catalog_revision'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_canonical_ingredients_insert BEFORE INSERT ON canonical_ingredients
            WHEN NOT (COALESCE((NEW.identity_state IN ('PROVISIONAL','REVIEWED')),1) AND COALESCE((length(NEW.scope_key)>0 AND length(NEW.concept_key)>0 AND length(NEW.part_key)>0
                  AND length(NEW.preparation_key)>0 AND length(NEW.preservation_key)>0
                  AND length(NEW.form_key)>0 AND length(NEW.formulation_key)>0 AND length(NEW.variant_key)>0),1) AND COALESCE(((NEW.name_es IS NOT NULL AND length(trim(NEW.name_es))>0)
                   OR (NEW.name_en IS NOT NULL AND length(trim(NEW.name_en))>0)),1))
            BEGIN SELECT RAISE(ABORT, 'Invalid canonical_ingredients'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_canonical_ingredients_update BEFORE UPDATE ON canonical_ingredients
            WHEN NOT (COALESCE((NEW.identity_state IN ('PROVISIONAL','REVIEWED')),1) AND COALESCE((length(NEW.scope_key)>0 AND length(NEW.concept_key)>0 AND length(NEW.part_key)>0
                  AND length(NEW.preparation_key)>0 AND length(NEW.preservation_key)>0
                  AND length(NEW.form_key)>0 AND length(NEW.formulation_key)>0 AND length(NEW.variant_key)>0),1) AND COALESCE(((NEW.name_es IS NOT NULL AND length(trim(NEW.name_es))>0)
                   OR (NEW.name_en IS NOT NULL AND length(trim(NEW.name_en))>0)),1))
            BEGIN SELECT RAISE(ABORT, 'Invalid canonical_ingredients'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_ingredient_canonical_map_insert BEFORE INSERT ON ingredient_canonical_map
            WHEN NOT (COALESCE((NEW.source_head_slot IS NULL OR NEW.source_head_slot=1),1) AND COALESCE((length(NEW.record_sha256)=64 AND NEW.record_sha256 NOT GLOB '*[^0-9a-f]*'),1) AND COALESCE((NEW.mapping_basis IN ('LEGACY_SINGLETON','REVIEWED','LOCAL')),1) AND COALESCE((length(NEW.source_namespace)>0 AND length(NEW.source_record_key)>0),1))
            BEGIN SELECT RAISE(ABORT, 'Invalid ingredient_canonical_map'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_ingredient_canonical_map_update BEFORE UPDATE ON ingredient_canonical_map
            WHEN NOT (COALESCE((NEW.source_head_slot IS NULL OR NEW.source_head_slot=1),1) AND COALESCE((length(NEW.record_sha256)=64 AND NEW.record_sha256 NOT GLOB '*[^0-9a-f]*'),1) AND COALESCE((NEW.mapping_basis IN ('LEGACY_SINGLETON','REVIEWED','LOCAL')),1) AND COALESCE((length(NEW.source_namespace)>0 AND length(NEW.source_record_key)>0),1))
            BEGIN SELECT RAISE(ABORT, 'Invalid ingredient_canonical_map'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_ingredient_catalog_status_insert BEFORE INSERT ON ingredient_catalog_status
            WHEN NOT (COALESCE((NEW.state IN ('VISIBLE','SECONDARY','HIDDEN','REVIEW_REQUIRED','OBSOLETE')),1) AND COALESCE((length(NEW.reason_code)>0),1) AND COALESCE((NEW.replacement_ingredient_id IS NULL OR NEW.replacement_ingredient_id<>NEW.ingredient_id),1))
            BEGIN SELECT RAISE(ABORT, 'Invalid ingredient_catalog_status'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_ingredient_catalog_status_update BEFORE UPDATE ON ingredient_catalog_status
            WHEN NOT (COALESCE((NEW.state IN ('VISIBLE','SECONDARY','HIDDEN','REVIEW_REQUIRED','OBSOLETE')),1) AND COALESCE((length(NEW.reason_code)>0),1) AND COALESCE((NEW.replacement_ingredient_id IS NULL OR NEW.replacement_ingredient_id<>NEW.ingredient_id),1))
            BEGIN SELECT RAISE(ABORT, 'Invalid ingredient_catalog_status'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_conversion_evidence_insert BEFORE INSERT ON conversion_evidence
            WHEN NOT (COALESCE((NEW.mass_basis IN ('EDIBLE','GROSS','NET','DRAINED','UNKNOWN')),1) AND COALESCE((NEW.method IN ('LEGACY_PORTION','LEGACY_DENSITY','MEASURED','DOCUMENTED','USER_DECLARED')),1) AND COALESCE((NEW.status IN ('UNVERIFIED','ACCEPTED','REJECTED')),1) AND COALESCE((NEW.confidence IN ('HIGH','MEDIUM','LOW','UNVERIFIED')),1) AND COALESCE((length(NEW.unit_key)>0 AND length(NEW.source_locator)>0 AND length(NEW.evidence_version)>0),1) AND COALESCE((NEW.grams_per_unit IS NULL OR (typeof(NEW.grams_per_unit) IN ('real','integer') AND NEW.grams_per_unit>0 AND NEW.grams_per_unit<1.0e308)),1) AND COALESCE((NEW.status<>'ACCEPTED' OR (NEW.grams_per_unit IS NOT NULL AND NEW.mass_basis='EDIBLE' AND NEW.confidence<>'UNVERIFIED'
                  AND NEW.reviewed_by IS NOT NULL AND length(trim(NEW.reviewed_by))>0 AND NEW.reviewed_at IS NOT NULL)),1) AND COALESCE((NEW.supersedes_evidence_id IS NULL OR NEW.supersedes_evidence_id<>NEW.evidence_id),1))
            BEGIN SELECT RAISE(ABORT, 'Invalid conversion_evidence'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_conversion_evidence_update BEFORE UPDATE ON conversion_evidence
            WHEN NOT (COALESCE((NEW.mass_basis IN ('EDIBLE','GROSS','NET','DRAINED','UNKNOWN')),1) AND COALESCE((NEW.method IN ('LEGACY_PORTION','LEGACY_DENSITY','MEASURED','DOCUMENTED','USER_DECLARED')),1) AND COALESCE((NEW.status IN ('UNVERIFIED','ACCEPTED','REJECTED')),1) AND COALESCE((NEW.confidence IN ('HIGH','MEDIUM','LOW','UNVERIFIED')),1) AND COALESCE((length(NEW.unit_key)>0 AND length(NEW.source_locator)>0 AND length(NEW.evidence_version)>0),1) AND COALESCE((NEW.grams_per_unit IS NULL OR (typeof(NEW.grams_per_unit) IN ('real','integer') AND NEW.grams_per_unit>0 AND NEW.grams_per_unit<1.0e308)),1) AND COALESCE((NEW.status<>'ACCEPTED' OR (NEW.grams_per_unit IS NOT NULL AND NEW.mass_basis='EDIBLE' AND NEW.confidence<>'UNVERIFIED'
                  AND NEW.reviewed_by IS NOT NULL AND length(trim(NEW.reviewed_by))>0 AND NEW.reviewed_at IS NOT NULL)),1) AND COALESCE((NEW.supersedes_evidence_id IS NULL OR NEW.supersedes_evidence_id<>NEW.evidence_id),1))
            BEGIN SELECT RAISE(ABORT, 'Invalid conversion_evidence'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_ingredient_unit_options_insert BEFORE INSERT ON ingredient_unit_options
            WHEN NOT (COALESCE((NEW.unit_kind IN ('COUNT','VOLUME','PORTION')),1) AND COALESCE((NEW.enabled IN (0,1)),1) AND COALESCE((NEW.default_slot IS NULL OR NEW.default_slot=1),1) AND COALESCE((NEW.default_slot IS NULL OR NEW.enabled=1),1) AND COALESCE(((NEW.label_es IS NOT NULL AND length(trim(NEW.label_es))>0)
                   OR (NEW.label_en IS NOT NULL AND length(trim(NEW.label_en))>0)),1))
            BEGIN SELECT RAISE(ABORT, 'Invalid ingredient_unit_options'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_ingredient_unit_options_update BEFORE UPDATE ON ingredient_unit_options
            WHEN NOT (COALESCE((NEW.unit_kind IN ('COUNT','VOLUME','PORTION')),1) AND COALESCE((NEW.enabled IN (0,1)),1) AND COALESCE((NEW.default_slot IS NULL OR NEW.default_slot=1),1) AND COALESCE((NEW.default_slot IS NULL OR NEW.enabled=1),1) AND COALESCE(((NEW.label_es IS NOT NULL AND length(trim(NEW.label_es))>0)
                   OR (NEW.label_en IS NOT NULL AND length(trim(NEW.label_en))>0)),1))
            BEGIN SELECT RAISE(ABORT, 'Invalid ingredient_unit_options'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_enabled_option_insert BEFORE INSERT ON ingredient_unit_options
            WHEN NEW.enabled=1 AND NOT EXISTS (SELECT 1 FROM conversion_evidence e WHERE e.evidence_id=NEW.evidence_id AND e.ingredient_id=NEW.ingredient_id AND e.unit_key=NEW.unit_key AND e.status='ACCEPTED' AND e.mass_basis='EDIBLE' AND e.confidence<>'UNVERIFIED')
            BEGIN SELECT RAISE(ABORT, 'Option requires accepted edible evidence'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS validate_enabled_option_update BEFORE UPDATE ON ingredient_unit_options
            WHEN NEW.enabled=1 AND NOT EXISTS (SELECT 1 FROM conversion_evidence e WHERE e.evidence_id=NEW.evidence_id AND e.ingredient_id=NEW.ingredient_id AND e.unit_key=NEW.unit_key AND e.status='ACCEPTED' AND e.mass_basis='EDIBLE' AND e.confidence<>'UNVERIFIED')
            BEGIN SELECT RAISE(ABORT, 'Option requires accepted edible evidence'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_revision_update BEFORE UPDATE ON catalog_revision WHEN OLD.state='APPLIED' BEGIN SELECT RAISE(ABORT, 'Applied revisions are immutable'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_evidence_update BEFORE UPDATE ON conversion_evidence BEGIN SELECT RAISE(ABORT, 'Evidence is append only'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_revision_delete BEFORE DELETE ON catalog_revision WHEN OLD.state='APPLIED' BEGIN SELECT RAISE(ABORT, 'Applied revisions are immutable'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_evidence_delete BEFORE DELETE ON conversion_evidence BEGIN SELECT RAISE(ABORT, 'Evidence is append only'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_composition BEFORE UPDATE ON ingredients WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=OLD.id) AND (NEW.`id` IS NOT OLD.`id` OR NEW.`name` IS NOT OLD.`name` OR NEW.`nameEs` IS NOT OLD.`nameEs` OR NEW.`nameNorm` IS NOT OLD.`nameNorm` OR NEW.`nameEsNorm` IS NOT OLD.`nameEsNorm` OR NEW.`baseName` IS NOT OLD.`baseName` OR NEW.`foodType` IS NOT OLD.`foodType` OR NEW.`aisleKey` IS NOT OLD.`aisleKey` OR NEW.`prepState` IS NOT OLD.`prepState` OR NEW.`rawSafety` IS NOT OLD.`rawSafety` OR NEW.`notes` IS NOT OLD.`notes` OR NEW.`servingSizeG` IS NOT OLD.`servingSizeG` OR NEW.`isLiquid` IS NOT OLD.`isLiquid` OR NEW.`densityGml` IS NOT OLD.`densityGml` OR NEW.`completenessPct` IS NOT OLD.`completenessPct` OR NEW.`quality` IS NOT OLD.`quality` OR NEW.`sourceKey` IS NOT OLD.`sourceKey` OR NEW.`isCustom` IS NOT OLD.`isCustom` OR NEW.`kcal` IS NOT OLD.`kcal` OR NEW.`carbs` IS NOT OLD.`carbs` OR NEW.`fats` IS NOT OLD.`fats` OR NEW.`protein` IS NOT OLD.`protein`) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_nutrients_insert BEFORE INSERT ON ingredient_nutrients WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=NEW.ingredientId) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_nutrients_update BEFORE UPDATE ON ingredient_nutrients WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=OLD.ingredientId) OR EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=NEW.ingredientId) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_nutrients_delete BEFORE DELETE ON ingredient_nutrients WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=OLD.ingredientId) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_portions_insert BEFORE INSERT ON ingredient_portions WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=NEW.ingredientId) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_portions_update BEFORE UPDATE ON ingredient_portions WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=OLD.ingredientId) OR EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=NEW.ingredientId) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_portions_delete BEFORE DELETE ON ingredient_portions WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=OLD.ingredientId) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_aliases_insert BEFORE INSERT ON ingredient_aliases WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=NEW.ingredientId) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_aliases_update BEFORE UPDATE ON ingredient_aliases WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=OLD.ingredientId) OR EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=NEW.ingredientId) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_aliases_delete BEFORE DELETE ON ingredient_aliases WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=OLD.ingredientId) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_quality_issues_insert BEFORE INSERT ON ingredient_quality_issues WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=NEW.ingredientId) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_quality_issues_update BEFORE UPDATE ON ingredient_quality_issues WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=OLD.ingredientId) OR EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=NEW.ingredientId) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_ingredient_quality_issues_delete BEFORE DELETE ON ingredient_quality_issues WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map WHERE ingredient_id=OLD.ingredientId) BEGIN SELECT RAISE(ABORT, 'Create a new ingredient snapshot'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_nutrient_defs_update BEFORE UPDATE ON nutrient_defs WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map) BEGIN SELECT RAISE(ABORT, 'Nutrient definitions are frozen'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS immutable_nutrient_defs_delete BEFORE DELETE ON nutrient_defs WHEN EXISTS (SELECT 1 FROM ingredient_canonical_map) BEGIN SELECT RAISE(ABORT, 'Nutrient definitions are frozen'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS staged_canonical_ingredients_insert BEFORE INSERT ON canonical_ingredients WHEN NOT EXISTS (SELECT 1 FROM catalog_revision WHERE revision_id=NEW.updated_revision_id AND state='STAGED') BEGIN SELECT RAISE(ABORT, 'Catalog changes require a staged revision'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS staged_canonical_ingredients_update BEFORE UPDATE ON canonical_ingredients WHEN NOT EXISTS (SELECT 1 FROM catalog_revision WHERE revision_id=NEW.updated_revision_id AND state='STAGED') BEGIN SELECT RAISE(ABORT, 'Catalog changes require a staged revision'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS staged_ingredient_canonical_map_insert BEFORE INSERT ON ingredient_canonical_map WHEN NOT EXISTS (SELECT 1 FROM catalog_revision WHERE revision_id=NEW.assigned_revision_id AND state='STAGED') BEGIN SELECT RAISE(ABORT, 'Catalog changes require a staged revision'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS staged_ingredient_canonical_map_update BEFORE UPDATE ON ingredient_canonical_map WHEN NOT EXISTS (SELECT 1 FROM catalog_revision WHERE revision_id=NEW.assigned_revision_id AND state='STAGED') BEGIN SELECT RAISE(ABORT, 'Catalog changes require a staged revision'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS staged_ingredient_catalog_status_insert BEFORE INSERT ON ingredient_catalog_status WHEN NOT EXISTS (SELECT 1 FROM catalog_revision WHERE revision_id=NEW.revision_id AND state='STAGED') BEGIN SELECT RAISE(ABORT, 'Catalog changes require a staged revision'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS staged_ingredient_catalog_status_update BEFORE UPDATE ON ingredient_catalog_status WHEN NOT EXISTS (SELECT 1 FROM catalog_revision WHERE revision_id=NEW.revision_id AND state='STAGED') BEGIN SELECT RAISE(ABORT, 'Catalog changes require a staged revision'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS staged_conversion_evidence_insert BEFORE INSERT ON conversion_evidence WHEN NOT EXISTS (SELECT 1 FROM catalog_revision WHERE revision_id=NEW.revision_id AND state='STAGED') BEGIN SELECT RAISE(ABORT, 'Catalog changes require a staged revision'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS staged_ingredient_unit_options_insert BEFORE INSERT ON ingredient_unit_options WHEN NOT EXISTS (SELECT 1 FROM catalog_revision WHERE revision_id=NEW.revision_id AND state='STAGED') BEGIN SELECT RAISE(ABORT, 'Catalog changes require a staged revision'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS staged_ingredient_unit_options_update BEFORE UPDATE ON ingredient_unit_options WHEN NOT EXISTS (SELECT 1 FROM catalog_revision WHERE revision_id=NEW.revision_id AND state='STAGED') BEGIN SELECT RAISE(ABORT, 'Catalog changes require a staged revision'); END
        """.trimIndent())
    }
}
