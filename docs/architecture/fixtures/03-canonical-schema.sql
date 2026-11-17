-- Executable design specification only. NOT an Android/Room migration.
-- Run only through verify_canonical_design.py, against an in-memory v8 fixture.
-- All new FKs use RESTRICT; existing tables and their constraints are untouched.
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
);
CREATE UNIQUE INDEX index_catalog_revision_key ON catalog_revision(revision_key);
CREATE UNIQUE INDEX index_catalog_revision_sequence ON catalog_revision(applied_seq);
CREATE INDEX index_catalog_revision_parent ON catalog_revision(parent_revision_id);
CREATE INDEX index_catalog_revision_source ON catalog_revision(source_namespace,dataset_version);

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
);
CREATE UNIQUE INDEX index_canonical_identity ON canonical_ingredients(
    scope_key,concept_key,part_key,preparation_key,preservation_key,form_key,formulation_key,variant_key);
CREATE UNIQUE INDEX index_canonical_id_scope ON canonical_ingredients(canonical_id,scope_key);
CREATE INDEX index_canonical_name_es ON canonical_ingredients(name_es_norm);
CREATE INDEX index_canonical_name_en ON canonical_ingredients(name_en_norm);
CREATE INDEX index_canonical_created_revision ON canonical_ingredients(created_revision_id);
CREATE INDEX index_canonical_updated_revision ON canonical_ingredients(updated_revision_id);

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
);
-- NULL means a retained historical/candidate row; 1 means the sole source head.
-- Ordinary UNIQUE index, not a partial index. Multiple NULLs are intentional.
CREATE UNIQUE INDEX index_map_source_head ON ingredient_canonical_map(source_namespace,source_record_key,source_head_slot);
CREATE INDEX index_map_canonical_scope ON ingredient_canonical_map(canonical_id,scope_key);
CREATE INDEX index_map_origin_revision ON ingredient_canonical_map(origin_revision_id);
CREATE INDEX index_map_assigned_revision ON ingredient_canonical_map(assigned_revision_id);

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
);
CREATE INDEX index_catalog_status_state ON ingredient_catalog_status(state,ingredient_id);
CREATE INDEX index_catalog_status_replacement ON ingredient_catalog_status(replacement_ingredient_id);
CREATE INDEX index_catalog_status_revision ON ingredient_catalog_status(revision_id);

CREATE TABLE conversion_evidence (
    evidence_id TEXT NOT NULL PRIMARY KEY,
    ingredient_id INTEGER NOT NULL,
    unit_key TEXT NOT NULL,
    grams_per_unit REAL,
    mass_basis TEXT NOT NULL CHECK(mass_basis IN ('EDIBLE','GROSS','DRAINED','UNKNOWN')),
    method TEXT NOT NULL CHECK(method IN ('LEGACY_PORTION','LEGACY_DENSITY','MEASURED','DOCUMENTED','USER_DECLARED')),
    status TEXT NOT NULL CHECK(status IN ('UNVERIFIED','ACCEPTED','REJECTED')),
    source_locator TEXT NOT NULL,
    source_excerpt TEXT NOT NULL,
    conditions TEXT NOT NULL,
    reviewed_by TEXT,
    reviewed_at INTEGER,
    supersedes_evidence_id TEXT,
    revision_id TEXT NOT NULL,
    CHECK(length(unit_key)>0 AND length(source_locator)>0),
    CHECK(grams_per_unit IS NULL OR (typeof(grams_per_unit) IN ('real','integer') AND grams_per_unit>0 AND grams_per_unit<1.0e308)),
    CHECK(status<>'ACCEPTED' OR (grams_per_unit IS NOT NULL AND mass_basis='EDIBLE'
      AND reviewed_by IS NOT NULL AND length(trim(reviewed_by))>0 AND reviewed_at IS NOT NULL)),
    CHECK(supersedes_evidence_id IS NULL OR supersedes_evidence_id<>evidence_id),
    FOREIGN KEY(ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    FOREIGN KEY(revision_id) REFERENCES catalog_revision(revision_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    FOREIGN KEY(supersedes_evidence_id,ingredient_id,unit_key)
      REFERENCES conversion_evidence(evidence_id,ingredient_id,unit_key) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE UNIQUE INDEX index_evidence_binding ON conversion_evidence(evidence_id,ingredient_id,unit_key);
CREATE INDEX index_evidence_ingredient_unit ON conversion_evidence(ingredient_id,unit_key);
CREATE INDEX index_evidence_supersedes ON conversion_evidence(supersedes_evidence_id,ingredient_id,unit_key);
CREATE INDEX index_evidence_revision ON conversion_evidence(revision_id);

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
);
CREATE UNIQUE INDEX index_unit_ingredient_key ON ingredient_unit_options(ingredient_id,unit_key);
CREATE UNIQUE INDEX index_unit_default ON ingredient_unit_options(ingredient_id,default_slot);
CREATE INDEX index_unit_evidence_binding ON ingredient_unit_options(evidence_id,ingredient_id,unit_key);
CREATE INDEX index_unit_revision ON ingredient_unit_options(revision_id);
