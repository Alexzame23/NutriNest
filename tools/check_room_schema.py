#!/usr/bin/env python3
"""Validate the migrated schema the way Room does, offline.

Room compares the database it finds after a migration against the schema it
derives from the entities, and throws `IllegalStateException: Migration didn't
properly handle: ...` on any difference — a column type, a NOT NULL, a default
value, an index name, a foreign key action. That check lives in generated code,
so it only runs on a device.

This reproduces it: it reads Room's own exported schema JSON (written to
app/schemas by the room.schemaLocation KSP argument) and compares it against
the database `tools/verify_migration.py` builds by running the real migration.

Usage:
    python3 tools/verify_migration.py      # builds the migrated database
    python3 tools/check_room_schema.py     # checks Room would accept it
"""

from __future__ import annotations

import glob
import json
import os
import sqlite3
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCHEMA_GLOB = os.path.join(REPO, "app", "schemas", "*", "*.json")
MIGRATED_DB = os.path.join(tempfile.gettempdir(), "nutrinest_migration_test.db")

failures: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


# ── Reading the database ────────────────────────────────────────────────────

def actual_columns(conn: sqlite3.Connection, table: str) -> dict[str, dict]:
    out = {}
    for _, name, decl_type, not_null, default, pk in conn.execute(
        f'PRAGMA table_info("{table}")'
    ):
        out[name] = {
            "affinity": decl_type.upper(),
            "notNull": bool(not_null),
            "default": default,
            "pk": pk,
        }
    return out


def actual_indices(conn: sqlite3.Connection, table: str) -> dict[str, dict]:
    out = {}
    for _, name, unique, origin, _partial in conn.execute(
        f'PRAGMA index_list("{table}")'
    ):
        # Room only tracks indices it created; SQLite's implicit ones for
        # UNIQUE/PRIMARY KEY constraints (origin 'u'/'pk') are not compared.
        if origin != "c":
            continue
        columns = [
            row[2] for row in conn.execute(f'PRAGMA index_info("{name}")')
        ]
        out[name] = {"unique": bool(unique), "columns": columns}
    return out


def actual_foreign_keys(conn: sqlite3.Connection, table: str) -> set[tuple]:
    out = set()
    for row in conn.execute(f'PRAGMA foreign_key_list("{table}")'):
        _id, _seq, ref_table, from_col, to_col, on_update, on_delete, _match = row
        out.add((ref_table, from_col, to_col, on_update.upper(), on_delete.upper()))
    return out


# ── Comparing against Room's expectations ───────────────────────────────────

def normalise_default(value: str | None) -> str | None:
    """Room tolerates redundant parentheses around a default value."""
    if value is None:
        return None
    text = value.strip()
    while text.startswith("(") and text.endswith(")"):
        text = text[1:-1].strip()
    return text


def check_entity(conn: sqlite3.Connection, entity: dict) -> None:
    table = entity["tableName"]

    exists = conn.execute(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?", (table,)
    ).fetchone()[0]
    if not exists:
        fail(f"{table}: table missing entirely")
        return

    columns = actual_columns(conn, table)

    # ── Columns ──
    for field in entity["fields"]:
        name = field["columnName"]
        actual = columns.get(name)
        if actual is None:
            fail(f"{table}.{name}: column missing")
            continue
        if actual["affinity"] != field["affinity"].upper():
            fail(
                f"{table}.{name}: type is {actual['affinity']}, "
                f"Room expects {field['affinity']}"
            )
        if actual["notNull"] != field.get("notNull", False):
            fail(
                f"{table}.{name}: notNull is {actual['notNull']}, "
                f"Room expects {field.get('notNull', False)}"
            )
        expected_default = normalise_default(field.get("defaultValue"))
        found_default = normalise_default(actual["default"])
        if expected_default != found_default:
            fail(
                f"{table}.{name}: default is {found_default!r}, "
                f"Room expects {expected_default!r}"
            )

    for name in sorted(set(columns) - {f["columnName"] for f in entity["fields"]}):
        fail(f"{table}.{name}: column exists in the database but not in the entity")

    # ── Primary key ──
    expected_pk = entity.get("primaryKey", {}).get("columnNames", [])
    found_pk = [
        name for name, info in sorted(columns.items(), key=lambda kv: kv[1]["pk"])
        if info["pk"] > 0
    ]
    if expected_pk and found_pk != expected_pk:
        fail(f"{table}: primary key is {found_pk}, Room expects {expected_pk}")

    # ── Indices ──
    expected_indices = {
        index["name"]: {
            "unique": index.get("unique", False),
            "columns": index["columnNames"],
        }
        for index in entity.get("indices", [])
    }
    found_indices = actual_indices(conn, table)

    for name, expected in expected_indices.items():
        found = found_indices.get(name)
        if found is None:
            fail(f"{table}: index '{name}' missing (Room expects it on {expected['columns']})")
            continue
        if found["columns"] != expected["columns"]:
            fail(
                f"{table}: index '{name}' covers {found['columns']}, "
                f"Room expects {expected['columns']}"
            )
        if found["unique"] != expected["unique"]:
            fail(f"{table}: index '{name}' unique is {found['unique']}, "
                 f"Room expects {expected['unique']}")

    for name in sorted(set(found_indices) - set(expected_indices)):
        fail(f"{table}: index '{name}' exists in the database but not in the entity")

    # ── Foreign keys ──
    expected_fks = {
        (
            fk["table"],
            fk["columns"][0],
            fk["referencedColumns"][0],
            fk.get("onUpdate", "NO ACTION").upper(),
            fk.get("onDelete", "NO ACTION").upper(),
        )
        for fk in entity.get("foreignKeys", [])
        if fk["columns"] and fk["referencedColumns"]
    }
    found_fks = actual_foreign_keys(conn, table)

    for fk in sorted(expected_fks - found_fks):
        fail(f"{table}: foreign key {fk} expected by Room but not found")
    for fk in sorted(found_fks - expected_fks):
        fail(f"{table}: foreign key {fk} found but not expected by Room")


def main() -> None:
    candidates = sorted(glob.glob(SCHEMA_GLOB))
    if not candidates:
        print("No exported Room schema found under app/schemas/.")
        print("It is written by KSP during a Gradle build, so run:")
        print("    ./gradlew :app:assembleDebug")
        print("then commit app/schemas/ and re-run this check.")
        sys.exit(2)

    # Highest version number wins, so this follows the schema forward.
    schema_path = max(candidates, key=lambda p: int(os.path.basename(p).split(".")[0]))
    schema = json.load(open(schema_path))["database"]
    print(f"Room schema : {os.path.relpath(schema_path, REPO)} (version {schema['version']})")

    if not os.path.exists(MIGRATED_DB):
        sys.exit(
            f"Migrated database not found at {MIGRATED_DB}.\n"
            f"Run tools/verify_migration.py first."
        )
    print(f"Migrated db : {MIGRATED_DB}")

    conn = sqlite3.connect(f"file:{MIGRATED_DB}?mode=ro", uri=True)
    entities = schema["entities"]
    print(f"Comparing {len(entities)} entities\n")

    for entity in entities:
        before = len(failures)
        check_entity(conn, entity)
        status = "FAIL" if len(failures) > before else "ok"
        print(f"  {status:4}  {entity['tableName']}")

    conn.close()

    if failures:
        print(f"\n{len(failures)} difference(s) — Room would reject this migration:\n")
        for failure in failures:
            print(f"  {failure}")
        sys.exit(1)
    print("\nRoom would accept the migrated schema")


if __name__ == "__main__":
    main()
