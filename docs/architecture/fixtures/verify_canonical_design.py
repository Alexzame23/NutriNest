#!/usr/bin/env python3
"""Phase 03 executable specification; only :memory: databases are writable.

Uses the exported Room v8 schema and read-only source records as fixtures.
This is NOT the future importer, a Room migration, or nutritional validation.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sqlite3
import sys

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
ASSET = ROOT / "app/src/main/assets/nutrinest_foods.db"
SCHEMA = ROOT / "app/schemas/com.zame.nutrinest.data.local.NutriNestDatabase/8.json"
DDL = HERE / "03-canonical-schema.sql"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha(value):
    return hashlib.sha256(encoded(value).encode("utf-8")).hexdigest()


def insert(db, table, row):
    cols = ",".join('"' + key + '"' for key in row)
    db.execute(f'INSERT INTO "{table}" ({cols}) VALUES ({",".join("?" for _ in row)})', tuple(row.values()))


def table_rows(db, table):
    return sorted([list(row) for row in db.execute(f'SELECT * FROM "{table}"')], key=encoded)


def validate_publication(db):
    """Demonstrates transaction gates beyond declarative FK/UNIQUE constraints.

    Not a complete production validator: provenance, manifest hash/chain and
    semantic review still require the phase 04 implementation described in ADR.
    """
    missing = db.execute("""SELECT i.id FROM ingredients i
        LEFT JOIN ingredient_canonical_map m ON m.ingredient_id=i.id
        LEFT JOIN ingredient_catalog_status s ON s.ingredient_id=i.id
        WHERE m.ingredient_id IS NULL OR s.ingredient_id IS NULL""").fetchall()
    if missing:
        raise ValueError("missing mapping or status")
    invalid = db.execute("""SELECT o.option_id FROM ingredient_unit_options o
        JOIN conversion_evidence e ON e.evidence_id=o.evidence_id
        WHERE o.enabled=1 AND (e.status<>'ACCEPTED' OR e.mass_basis<>'EDIBLE')""").fetchall()
    if invalid:
        raise ValueError("unaccepted conversion")
    mismatch = db.execute("""SELECT m.ingredient_id FROM ingredient_canonical_map m
        JOIN ingredients i ON i.id=m.ingredient_id
        JOIN canonical_ingredients c ON c.canonical_id=m.canonical_id
        WHERE i.prepState IN ('RAW','BOILED','COOKED','FRIED','GRILLED','ROASTED','STEAMED')
          AND c.preparation_key<>i.prepState""").fetchall()
    if mismatch:
        raise ValueError("preparation conflict requires explicit adjudication")
    if db.execute("PRAGMA foreign_key_check").fetchall():
        raise ValueError("foreign keys")


def main():
    before_hash = digest(ASSET)
    asset = sqlite3.connect(ASSET.resolve().as_uri() + "?mode=ro", uri=True)
    asset.row_factory = sqlite3.Row
    asset.execute("PRAGMA query_only=ON")
    db = sqlite3.connect(":memory:", isolation_level=None)
    db.execute("PRAGMA foreign_keys=ON")
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))["database"]
    assert schema["version"] == 8
    for entity in schema["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    tables = [entity["tableName"] for entity in schema["entities"]]
    foods = [dict(row) for row in asset.execute("SELECT * FROM foods ORDER BY id")]
    macros = {row["food_id"]: dict(row) for row in asset.execute("SELECT * FROM food_macros")}
    categories = {row["id"]: row["key"] for row in asset.execute("SELECT * FROM food_categories")}
    # Includes all deprecated/excluded source records deliberately: a fixture
    # for historical retention, NOT a simulation of the current active importer.
    for food in foods:
        m = macros[food["id"]]
        insert(db, "ingredients", dict(id=1000000 + food["id"], name=food["name_en"] or food["name_es"],
            nameEs=food["name_es"], nameNorm=food["name_en_norm"] or food["name_norm"],
            nameEsNorm=food["name_norm"], foodType=categories[food["category_id"]],
            prepState=food["prep_state"], isCustom=0, sourceKey=f'BEDCA:{food["id"]}',
            isLiquid=food["is_liquid"], densityGml=food["density_g_ml"],
            kcal=m["kcal"], carbs=m["carbs_g"], fats=m["fat_g"], protein=m["protein_g"]))
    for row in asset.execute("SELECT * FROM food_nutrients"):
        insert(db, "ingredient_nutrients", dict(ingredientId=1000000 + row["food_id"], tag=row["tag"], value=row["value"]))
    for row in asset.execute("SELECT * FROM food_portions"):
        insert(db, "ingredient_portions", dict(id=row["id"], ingredientId=1000000 + row["food_id"],
            labelEn=row["label_en"], labelEs=row["label_es"], grams=row["grams"],
            portionType=row["portion_type"], isDefault=row["is_default"]))
    for row in asset.execute("SELECT * FROM food_aliases"):
        insert(db, "ingredient_aliases", dict(id=row["id"], ingredientId=1000000 + row["food_id"],
            alias=row["alias"], aliasNorm=row["alias_norm"], lang=row["lang"]))
    insert(db, "ingredients", dict(id=2000001, name="Fixture personal sin composición acreditada",
        foodType="MISC", isCustom=1, kcal=0.0, carbs=0.0, fats=0.0, protein=0.0))
    insert(db, "recipes", dict(id=1, name="Fixture de referencias históricas", category="Main Dish", createdAt=0, isFavorite=1))
    insert(db, "weekly_menus", dict(id=1, weekStart=0, slotsSpec="LUNCH:1", createdAt=0))
    for food_id in (904, 1009, 1173, 2125, 1216, 2433, 2434):
        insert(db, "recipe_ingredients", dict(recipeId=1, ingredientId=1000000+food_id, grams=100, quantityUnit="GRAM"))
        insert(db, "shopping_items", dict(menuId=1, ingredientId=1000000+food_id, grams=200, purchased=1))
    insert(db, "planned_meals", dict(id=1, menuId=1, dayOfWeek=0, slot="LUNCH", recipeId=1,
        isLocked=1, isCheatMeal=0, course="SINGLE", isFree=0))
    baseline = {t: table_rows(db, t) for t in tables}
    structure = db.execute("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name").fetchall()
    total_sql = """SELECT sum(i.kcal*r.grams/100),sum(i.carbs*r.grams/100),
        sum(i.fats*r.grams/100),sum(i.protein*r.grams/100)
        FROM recipe_ingredients r JOIN ingredients i ON i.id=r.ingredientId WHERE r.recipeId=1"""
    totals_before = db.execute(total_sql).fetchone()
    db.executescript(DDL.read_text(encoding="utf-8"))
    manifest = encoded({"fixture": True, "source_sha256": before_hash, "nutrition_units": "preserved, not adjudicated"})
    revision = dict(revision_id="rev-fixture-0", revision_key="fixture-baseline", parent_revision_id=None,
        kind="BASELINE", state="APPLIED", source_namespace="BEDCA", dataset_version="2026.07.29",
        source_sha256=before_hash, manifest_json=manifest, manifest_sha256=hashlib.sha256(manifest.encode()).hexdigest(),
        rules_version="design-03.1", actor="FIXTURE_ONLY", created_at=0, applied_at=0, applied_seq=1)
    insert(db, "catalog_revision", revision)
    ingredient_columns = [r[1] for r in db.execute("PRAGMA table_info(ingredients)")]
    all_ingredients = [dict(zip(ingredient_columns, r)) for r in db.execute("SELECT * FROM ingredients ORDER BY id")]
    for ing in all_ingredients:
        iid = ing["id"]
        scope = "local:fixture" if ing["isCustom"] else "global"
        prep = ing["prepState"]
        preservation = prep if prep in {"CANNED", "FROZEN", "DRIED", "CURED", "SMOKED"} else "UNSPECIFIED"
        insert(db, "canonical_ingredients", dict(canonical_id=f"legacy:{iid}", scope_key=scope,
            concept_key=f"legacy:{iid}", part_key="UNSPECIFIED", preparation_key="UNSPECIFIED" if preservation != "UNSPECIFIED" else prep,
            preservation_key=preservation, form_key="UNSPECIFIED", formulation_key="UNSPECIFIED", variant_key=f"legacy:{iid}",
            identity_state="PROVISIONAL", name_es=ing["nameEs"], name_en=ing["name"],
            name_es_norm=ing["nameEsNorm"], name_en_norm=ing["nameNorm"], created_revision_id="rev-fixture-0", updated_revision_id="rev-fixture-0"))
        insert(db, "ingredient_canonical_map", dict(ingredient_id=iid, canonical_id=f"legacy:{iid}", scope_key=scope,
            source_namespace="LOCAL:fixture" if ing["isCustom"] else "BEDCA",
            source_record_key=str(iid) if ing["isCustom"] else str(iid-1000000), source_head_slot=1,
            record_sha256=sha(ing), mapping_basis="LOCAL" if ing["isCustom"] else "LEGACY_SINGLETON",
            origin_revision_id="rev-fixture-0", assigned_revision_id="rev-fixture-0"))
        original = next((f for f in foods if f["id"] == iid-1000000), None)
        state = "OBSOLETE" if original and original["is_deprecated"] else "HIDDEN" if iid==1002494 else "VISIBLE"
        insert(db, "ingredient_catalog_status", dict(ingredient_id=iid, state=state,
            reason_code="FIXTURE_BASELINE", detail="Fixture only; no live catalog decision", revision_id="rev-fixture-0"))
    checks = []

    def check(name, value):
        if not value:
            raise AssertionError(name)
        checks.append({"name": name, "result": "PASS"})

    def rejected(name, sql, args=(), error=sqlite3.IntegrityError, gate=False):
        db.execute("SAVEPOINT invalid_case")
        try:
            db.execute(sql, args)
            if gate:
                validate_publication(db)
        except error:
            checks.append({"name": name, "result": "PASS"})
        else:
            raise AssertionError(f"Expected rejection: {name}")
        finally:
            db.execute("ROLLBACK TO invalid_case")
            db.execute("RELEASE invalid_case")

    check("v8_20_tables_data_unchanged_after_backfill", baseline == {t: table_rows(db, t) for t in tables})
    check("v8_existing_ddl_and_indexes_unchanged", all(db.execute("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name=?", (s[1],)).fetchone()==s for s in structure))
    check("six_additional_tables", db.execute("SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").fetchone()[0] == 26)
    check("937_singleton_mappings_and_statuses", db.execute("SELECT count(*) FROM ingredient_canonical_map").fetchone()[0] == 937)
    canonical_columns = [r[1] for r in db.execute("PRAGMA table_info(canonical_ingredients)")]
    template = dict(zip(canonical_columns, db.execute("SELECT * FROM canonical_ingredients WHERE canonical_id='legacy:1000904'").fetchone()))
    # Semantic examples are design fixtures; none is a new BEDCA adjudication.
    examples = [
        ("egg-whole", "chicken_egg", "WHOLE", "RAW", "UNSPECIFIED", "PLAIN"),
        ("egg-white", "chicken_egg", "WHITE", "RAW", "UNSPECIFIED", "PLAIN"),
        ("egg-yolk", "chicken_egg", "YOLK", "RAW", "UNSPECIFIED", "PLAIN"),
        ("rice-raw", "brown_rice", "WHOLE", "RAW", "UNSPECIFIED", "PLAIN"),
        ("rice-boiled", "brown_rice", "WHOLE", "BOILED", "UNSPECIFIED", "PLAIN"),
        ("tomato-fresh", "tomato", "EDIBLE", "UNSPECIFIED", "FRESH", "PLAIN"),
        ("tomato-canned", "tomato", "EDIBLE", "UNSPECIFIED", "CANNED", "PLAIN"),
        ("coffee", "coffee_infusion", "EDIBLE", "UNSPECIFIED", "UNSPECIFIED", "REGULAR"),
        ("decaf", "coffee_infusion", "EDIBLE", "UNSPECIFIED", "UNSPECIFIED", "DECAFFEINATED"),
    ]
    for key, concept, part, prep, preservation, formulation in examples:
        insert(db, "canonical_ingredients", dict(template, canonical_id="fixture:"+key, concept_key=concept,
            part_key=part, preparation_key=prep, preservation_key=preservation, formulation_key=formulation,
            variant_key="FIXTURE", identity_state="REVIEWED", name_en="Fixture only", name_es=None,
            name_en_norm="fixture only", name_es_norm=None))
    check("egg_parts_rice_states_preservation_and_decaf_are_distinct", db.execute("SELECT count(*) FROM canonical_ingredients WHERE canonical_id LIKE 'fixture:%'").fetchone()[0]==9)
    insert(db, "canonical_ingredients", dict(template, canonical_id="fixture:local-rice", scope_key="local:fixture"))
    check("same_semantic_tuple_in_different_scope_allowed", True)
    validate_publication(db)
    check("publication_gate_accepts_complete_baseline", True)
    rejected("canonical_semantic_tuple_unique", "UPDATE canonical_ingredients SET concept_key='legacy:1000904',variant_key='legacy:1000904' WHERE canonical_id='legacy:1001173'")
    rejected("canonical_pk_not_null", "UPDATE canonical_ingredients SET canonical_id=NULL WHERE canonical_id='legacy:1000904'")
    rejected("canonical_identity_dimensions_not_null", "UPDATE canonical_ingredients SET part_key=NULL WHERE canonical_id='legacy:1000904'")
    rejected("one_mapping_per_ingredient", "INSERT INTO ingredient_canonical_map SELECT * FROM ingredient_canonical_map WHERE ingredient_id=1000904")
    rejected("mapping_missing_ingredient_fk", "UPDATE ingredient_canonical_map SET ingredient_id=999999999 WHERE ingredient_id=1000904")
    rejected("mapping_missing_canonical_fk", "UPDATE ingredient_canonical_map SET canonical_id='missing' WHERE ingredient_id=1000904")
    rejected("mapping_wrong_scope_fk", "UPDATE ingredient_canonical_map SET scope_key='local:fixture' WHERE ingredient_id=1000904")
    rejected("source_head_unique", "UPDATE ingredient_canonical_map SET source_record_key='904' WHERE ingredient_id=1001009")
    rejected("source_head_slot_domain", "UPDATE ingredient_canonical_map SET source_head_slot=2 WHERE ingredient_id=1000904")
    rejected("one_status_per_ingredient", "INSERT INTO ingredient_catalog_status SELECT * FROM ingredient_catalog_status WHERE ingredient_id=1000904")
    rejected("status_domain", "UPDATE ingredient_catalog_status SET state='DELETED' WHERE ingredient_id=1000904")
    rejected("replacement_target_fk", "UPDATE ingredient_catalog_status SET replacement_ingredient_id=999999999 WHERE ingredient_id=1001216")
    rejected("replacement_cannot_be_self", "UPDATE ingredient_catalog_status SET replacement_ingredient_id=ingredient_id WHERE ingredient_id=1001216")
    rejected("delete_referenced_legacy_ingredient_restricted", "DELETE FROM ingredients WHERE id=1000904")
    rejected("delete_unreferenced_but_mapped_ingredient_restricted", "DELETE FROM ingredients WHERE id=1000757")
    rejected("delete_canonical_restricted", "DELETE FROM canonical_ingredients WHERE canonical_id='legacy:1000904'")
    rejected("delete_revision_restricted", "DELETE FROM catalog_revision WHERE revision_id='rev-fixture-0'")
    rejected("revision_idempotency_key_unique", "INSERT INTO catalog_revision SELECT 'rev-other',revision_key,parent_revision_id,kind,state,source_namespace,dataset_version,source_sha256,manifest_json,manifest_sha256,rules_version,actor,created_at,applied_at,2 FROM catalog_revision")
    rejected("revision_applied_requires_sequence", "UPDATE catalog_revision SET applied_seq=NULL")
    rejected("gate_requires_mapping_coverage", "DELETE FROM ingredient_canonical_map WHERE ingredient_id=1000904", error=ValueError, gate=True)
    rejected("gate_requires_status_coverage", "DELETE FROM ingredient_catalog_status WHERE ingredient_id=1000904", error=ValueError, gate=True)
    rejected("gate_rejects_raw_to_cooked_mapping", "UPDATE ingredient_canonical_map SET canonical_id='legacy:1001009' WHERE ingredient_id=1000904", error=ValueError, gate=True)
    for state in ("VISIBLE", "SECONDARY", "HIDDEN", "REVIEW_REQUIRED", "OBSOLETE"):
        db.execute("UPDATE ingredient_catalog_status SET state=? WHERE ingredient_id=1000904", (state,))
        check(f"{state.lower()}_preserves_old_recipe_and_shopping_references", table_rows(db,"recipe_ingredients")==baseline["recipe_ingredients"] and table_rows(db,"shopping_items")==baseline["shopping_items"] and db.execute(total_sql).fetchone()==totals_before)
    db.execute("UPDATE ingredient_catalog_status SET state='VISIBLE' WHERE ingredient_id=1000904")
    portion = dict(asset.execute("SELECT * FROM food_portions WHERE food_id=2125 ORDER BY id LIMIT 1").fetchone())
    evidence = dict(evidence_id="ev-fixture", ingredient_id=1002125, unit_key="FIXTURE_ONLY",
        grams_per_unit=portion["grams"], mass_basis="UNKNOWN", method="LEGACY_PORTION", status="UNVERIFIED",
        source_locator=f'asset:{before_hash}#food_portions/{portion["id"]}', source_excerpt=encoded(portion),
        conditions="Relational fixture, NOT acceptance of this portion's edible weight", revision_id="rev-fixture-0")
    insert(db, "conversion_evidence", evidence)
    option = dict(option_id="op-fixture", ingredient_id=1002125, unit_key="FIXTURE_ONLY", unit_kind="PORTION",
        label_es=portion["label_es"], label_en=portion["label_en"], evidence_id="ev-fixture", enabled=0,
        default_slot=None, revision_id="rev-fixture-0")
    insert(db, "ingredient_unit_options", option)
    rejected("unit_evidence_bound_to_same_ingredient", "UPDATE ingredient_unit_options SET ingredient_id=1001173")
    rejected("unit_evidence_bound_to_same_unit", "UPDATE ingredient_unit_options SET unit_key='OTHER'")
    rejected("unique_unit_per_ingredient", "INSERT INTO ingredient_unit_options SELECT 'op-other',ingredient_id,unit_key,unit_kind,label_es,label_en,evidence_id,enabled,default_slot,revision_id FROM ingredient_unit_options")
    rejected("conversion_negative_rejected", "UPDATE conversion_evidence SET grams_per_unit=-1")
    rejected("conversion_non_numeric_rejected", "UPDATE conversion_evidence SET grams_per_unit='unknown'")
    rejected("conversion_infinity_rejected", "UPDATE conversion_evidence SET grams_per_unit=?", (float("inf"),))
    rejected("accepted_evidence_requires_edible_basis_and_reviewer", "UPDATE conversion_evidence SET status='ACCEPTED'")
    rejected("gate_rejects_enabled_unverified_option", "UPDATE ingredient_unit_options SET enabled=1", error=ValueError, gate=True)
    # Simulate formal acceptance ONLY to test the relational state machine.
    # Does not certify the source value or persist it anywhere outside :memory:.
    db.execute("UPDATE conversion_evidence SET status='ACCEPTED',mass_basis='EDIBLE',reviewed_by='FIXTURE_ONLY',reviewed_at=0")
    rejected("accepted_evidence_requires_mass", "UPDATE conversion_evidence SET grams_per_unit=NULL")
    db.execute("UPDATE ingredient_unit_options SET enabled=1,default_slot=1")
    second = dict(evidence, evidence_id="ev-fixture-2", unit_key="FIXTURE_SECOND", status="ACCEPTED", mass_basis="EDIBLE", reviewed_by="FIXTURE_ONLY", reviewed_at=0)
    insert(db, "conversion_evidence", second)
    insert(db, "ingredient_unit_options", dict(option, option_id="op-fixture-2", unit_key="FIXTURE_SECOND", evidence_id="ev-fixture-2", enabled=1))
    rejected("one_default_unit_per_ingredient", "UPDATE ingredient_unit_options SET default_slot=1 WHERE option_id='op-fixture-2'")
    rejected("delete_used_evidence_restricted", "DELETE FROM conversion_evidence WHERE evidence_id='ev-fixture'")
    check("accepted_unit_query_returns_only_accepted_edible_options", db.execute("""SELECT count(*) FROM ingredient_unit_options o JOIN conversion_evidence e
        ON (e.evidence_id=o.evidence_id AND e.ingredient_id=o.ingredient_id AND e.unit_key=o.unit_key)
        WHERE o.enabled=1 AND e.status='ACCEPTED' AND e.mass_basis='EDIBLE'""").fetchone()[0]==2)
    # A future source revision is simulated by adding an unmodified copy. The
    # test checks identity/reference retention, not an invented nutrient value.
    old = next(i for i in all_ingredients if i["id"]==1000904)
    insert(db, "ingredients", dict(old, id=2000002))
    oldmap = dict(zip([r[1] for r in db.execute("PRAGMA table_info(ingredient_canonical_map)")], db.execute("SELECT * FROM ingredient_canonical_map WHERE ingredient_id=1000904").fetchone()))
    db.execute("UPDATE ingredient_canonical_map SET source_head_slot=NULL WHERE ingredient_id=1000904")
    insert(db, "ingredient_canonical_map", dict(oldmap, ingredient_id=2000002))
    insert(db, "ingredient_catalog_status", dict(ingredient_id=2000002, state="REVIEW_REQUIRED", reason_code="FIXTURE_NEXT", detail="Simulated retained revision", revision_id="rev-fixture-0"))
    check("source_head_changes_without_rewriting_recipe_or_shopping", db.execute("SELECT ingredient_id FROM ingredient_canonical_map WHERE source_namespace='BEDCA' AND source_record_key='904' AND source_head_slot=1").fetchall()==[(2000002,)] and table_rows(db,"recipe_ingredients")==baseline["recipe_ingredients"] and table_rows(db,"shopping_items")==baseline["shopping_items"])
    db.execute("UPDATE ingredient_canonical_map SET source_head_slot=NULL WHERE ingredient_id=2000002")
    check("multiple_historical_null_heads_allowed", db.execute("SELECT count(*) FROM ingredient_canonical_map WHERE source_namespace='BEDCA' AND source_record_key='904' AND source_head_slot IS NULL").fetchone()[0]==2)
    db.execute("UPDATE ingredient_canonical_map SET source_head_slot=1 WHERE ingredient_id=2000002")
    validate_publication(db)
    check("all_original_nutrients_and_portions_and_aliases_unchanged", all(table_rows(db,t)==baseline[t] for t in ("ingredient_nutrients","ingredient_portions","ingredient_aliases")))
    check("all_937_original_ingredient_rows_unchanged", table_rows(db,"ingredients")==sorted(baseline["ingredients"]+[list(db.execute("SELECT * FROM ingredients WHERE id=2000002").fetchone())], key=encoded))
    check("nutrition_total_exactly_preserved", db.execute(total_sql).fetchone()==totals_before)
    check("foreign_key_check_empty", db.execute("PRAGMA foreign_key_check").fetchall()==[])
    check("integrity_check_ok", db.execute("PRAGMA integrity_check").fetchone()[0]=="ok")
    # Failed multi-step publication must roll back all six tables together.
    new_tables = ["catalog_revision","canonical_ingredients","ingredient_canonical_map","ingredient_catalog_status","conversion_evidence","ingredient_unit_options"]
    state_before = {t:table_rows(db,t) for t in new_tables}
    db.execute("BEGIN IMMEDIATE")
    try:
        db.execute("UPDATE ingredient_catalog_status SET state='HIDDEN' WHERE ingredient_id=1000904")
        db.execute("UPDATE ingredient_canonical_map SET canonical_id='missing' WHERE ingredient_id=1000904")
    except sqlite3.IntegrityError:
        db.execute("ROLLBACK")
    else:
        raise AssertionError("expected failing publication")
    check("atomic_rollback_after_mid_publication_failure", state_before=={t:table_rows(db,t) for t in new_tables})
    asset.close()
    db.close()
    check("asset_sha256_unchanged", before_hash==digest(ASSET))
    report = dict(design="03.1", command="C:\\Python313\\python.exe docs/architecture/fixtures/verify_canonical_design.py",
        exit_code=0, python=sys.version.split()[0], sqlite=sqlite3.sqlite_version,
        source_sha256_before=before_hash, source_sha256_after=digest(ASSET),
        ddl_sha256=digest(DDL), verifier_sha256=digest(Path(__file__)), room_v8_schema_sha256=digest(SCHEMA),
        fixture=dict(source_foods=len(foods), custom_rows=1, historical_recipe_links=7, shopping_items=7,
                     original_tables=len(tables), added_tables=6, nutrient_rows=len(baseline["ingredient_nutrients"])),
        checks_passed=len(checks), checks=checks,
        limitations=["Python SQLite only; Android API 26 and Room MigrationTestHelper NOT executed",
                      "Fixture deliberately includes all 936 source foods, not just 923 active importable foods",
                      "Simulated evidence acceptance tests constraints, not edible weight or scientific validity",
                      "Production importer/seeder, semantic manifest validator and fresh Room database creation NOT implemented"])
    output = HERE.parent / "03-design-verification.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True)+"\n", encoding="utf-8", newline="\n")
    print(json.dumps({"checks_passed":len(checks),"exit_code":0,"sqlite":sqlite3.sqlite_version,
        "asset_unchanged":True,"report":output.relative_to(ROOT).as_posix()}, ensure_ascii=False))


if __name__ == "__main__":
    main()
