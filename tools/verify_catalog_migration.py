"""Replay actual v9 DDL and compare constraints with fresh Room-generated DDL.

The metadata below is a deterministic TEST FIXTURE, not an implementation of the
Kotlin backfill. MigrationTest executes that backfill on Android and checks every
historical cell. This tool checks extracted SQL, FK grouping, preservation and rollback.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sqlite3

from ksql import extract_exec_sql

ROOT = Path(__file__).resolve().parent.parent
LOCAL = ROOT / "app/src/main/java/com/zame/nutrinest/data/local"
SCHEMA = ROOT / "app/schemas/com.zame.nutrinest.data.local.NutriNestDatabase/9.json"


def snapshot(conn, tables):
    return {t: sorted(conn.execute(f'SELECT * FROM "{t}"').fetchall(), key=repr) for t in tables}


def metadata_fixture(conn):
    """Bind fixture values to the actual INSERT/UPDATE statements from the store."""
    pending = conn.execute("SELECT id,name,nameEs,nameNorm,nameEsNorm,prepState,isCustom,sourceKey FROM ingredients WHERE id NOT IN (SELECT ingredient_id FROM ingredient_canonical_map) ORDER BY id").fetchall()
    if not pending:
        return
    statements = extract_exec_sql((LOCAL / "CanonicalCatalogStore.kt").read_text(encoding="utf-8"))
    find = lambda prefix: next(s for s in statements if s.startswith(prefix))
    digest = hashlib.sha256(b'fixture').hexdigest()
    conn.execute(find("INSERT INTO catalog_revision"), ("fixture-revision", "fixture-baseline", None,
        "BASELINE", "LEGACY:fixture", None, None, digest, 1))
    for row_id, name, es, norm, es_norm, prep, custom, source in pending:
        scope = "local:fixture" if custom else "global"
        conn.execute(find("INSERT INTO canonical_ingredients"), (f"fixture-{row_id}", scope,
            f"legacy:{row_id}", prep, "UNSPECIFIED", "UNSPECIFIED", f"legacy:{row_id}",
            es, name, es_norm, norm, "fixture-revision", "fixture-revision"))
        conn.execute(find("INSERT INTO ingredient_canonical_map"), (row_id, f"fixture-{row_id}", scope,
            "LEGACY:fixture", f"legacy:{row_id}", None, digest, "LEGACY_SINGLETON", "fixture-revision", "fixture-revision"))
        conn.execute(find("INSERT INTO ingredient_catalog_status"), (row_id, "VISIBLE", "FIXTURE", "Test metadata only", "fixture-revision"))
    conn.execute(find("UPDATE catalog_revision"), ('{"fixture":true}', digest, 2, "fixture-revision"))


def constraints(conn):
    ids = [r[0] for r in conn.execute("SELECT id FROM ingredients ORDER BY id LIMIT 2")]
    ingredient, other = ids
    rev = "review-fixture"
    evidence = "INSERT INTO conversion_evidence (evidence_id,ingredient_id,unit_key,grams_per_unit,mass_basis,method,status,evidence_version,confidence,source_locator,source_excerpt,conditions,reviewed_by,reviewed_at,revision_id) VALUES (?,?,?,?,?,'DOCUMENTED',?,'fixture-v1','HIGH','test','test','synthetic','tester',1,?)"
    checks = 0

    def reject(sql, args=()):
        nonlocal checks
        try:
            conn.execute(sql, args)
        except sqlite3.IntegrityError:
            checks += 1
        else:
            raise AssertionError(f"Invalid write accepted: {sql} {args}")

    conn.execute("SAVEPOINT constraint_tests")
    conn.execute("INSERT INTO catalog_revision (revision_id,revision_key,parent_revision_id,kind,state,source_namespace,manifest_json,manifest_sha256,rules_version,actor,created_at) VALUES (?,?,'fixture-revision','REVIEW','STAGED','FIXTURE','{}',?,'fixture-v1','tester',1)",
                 (rev, rev, hashlib.sha256(b'{}').hexdigest()))
    for value in (-1, 0, None, float("inf")):
        reject(evidence, ("bad", ingredient, "UNIT:fixture", value, "EDIBLE", "ACCEPTED", rev))
    for basis in ("GROSS", "NET", "DRAINED", "UNKNOWN", "wrong"):
        reject(evidence, ("bad", ingredient, "UNIT:fixture", 43.7, basis, "ACCEPTED", rev))
    conn.execute(evidence, ("accepted", ingredient, "UNIT:fixture", 43.7, "EDIBLE", "ACCEPTED", rev))
    conn.execute(evidence, ("legacy", ingredient, "PORTION:fixture", None, "UNKNOWN", "UNVERIFIED", rev))
    option = "INSERT INTO ingredient_unit_options (option_id,ingredient_id,unit_key,unit_kind,label_en,evidence_id,enabled,revision_id) VALUES (?,?,?,'COUNT','Fixture',?,1,?)"
    reject(option, ("other-food", other, "UNIT:fixture", "accepted", rev))
    reject(option, ("other-unit", ingredient, "UNIT:other", "accepted", rev))
    reject(option, ("unverified", ingredient, "PORTION:fixture", "legacy", rev))
    conn.execute(option, ("ok", ingredient, "UNIT:fixture", "accepted", rev))
    reject(option, ("duplicate", ingredient, "UNIT:fixture", "accepted", rev))
    reject("UPDATE ingredient_catalog_status SET state='INVALID'")
    reject("UPDATE ingredient_canonical_map SET record_sha256='invalid'")
    reject("UPDATE canonical_ingredients SET name_es=NULL,name_en=''")
    reject("UPDATE ingredients SET kcal=999 WHERE id=?", (ingredient,))
    reject("DELETE FROM ingredients WHERE id=?", (ingredient,))
    reject("DELETE FROM conversion_evidence WHERE evidence_id='accepted'")
    reject("UPDATE catalog_revision SET actor='changed' WHERE revision_id=?", ("fixture-revision",))
    assert not conn.execute("PRAGMA foreign_key_check").fetchall()
    conn.execute("ROLLBACK TO constraint_tests")
    conn.execute("RELEASE constraint_tests")
    return checks


def verify_catalog(conn):
    source = (LOCAL / "CanonicalCatalogSchema.kt").read_text(encoding="utf-8")
    ddl = extract_exec_sql(source)
    triggers = extract_exec_sql(source[source.index("fun installValidationTriggers"):])
    tables = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'")]
    before = snapshot(conn, tables)
    conn.commit()
    conn.execute("SAVEPOINT interrupted_migration")
    for statement in ddl:
        conn.execute(statement)
    conn.execute("ROLLBACK TO interrupted_migration")
    conn.execute("RELEASE interrupted_migration")
    assert before == snapshot(conn, tables)
    assert not conn.execute("SELECT 1 FROM sqlite_master WHERE name='catalog_revision'").fetchall()
    print("  PASS  interrupted v9 DDL rolls back on disposable fixture")

    for statement in ddl:
        conn.execute(statement)
    metadata_fixture(conn)
    assert before == snapshot(conn, tables)
    all_tables = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
    first = snapshot(conn, all_tables)
    metadata_fixture(conn)
    assert first == snapshot(conn, all_tables)
    assert not conn.execute("PRAGMA foreign_key_check").fetchall()
    print("  PASS  metadata fixture is additive and idempotent; historical cells and FKs preserved")
    migrated_checks = constraints(conn)

    fresh = sqlite3.connect(":memory:")
    fresh.execute("PRAGMA foreign_keys=ON")
    schema = json.loads(SCHEMA.read_text())["database"]
    for entity in schema["entities"]:
        fresh.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            fresh.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for statement in triggers:
        fresh.execute(statement)
    # Seed only unprotected original tables, in their original FK insertion order.
    ordered = []
    pending = set(tables)
    while pending:
        ready = sorted(t for t in pending if not ({r[2] for r in fresh.execute(f'PRAGMA foreign_key_list("{t}")')} & pending))
        assert ready, "Cyclic fixture dependencies"
        ordered.extend(ready)
        pending.difference_update(ready)
    for table in ordered:
        rows = before[table]
        if rows:
            cols = [r[1] for r in conn.execute(f'PRAGMA table_info("{table}")')]
            fields = ','.join(f'"{c}"' for c in cols)
            fresh.executemany(f'INSERT INTO "{table}" ({fields}) VALUES ({",".join("?" for _ in cols)})', rows)
    metadata_fixture(fresh)
    assert constraints(fresh) == migrated_checks
    fresh.close()
    print(f"  PASS  {migrated_checks} invalid writes rejected identically by migrated and fresh schemas")
