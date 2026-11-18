#!/usr/bin/env python3
"""Compile every DAO query against the real migrated schema.

Room checks `@Query` SQL at build time, so a wrong column name is a compile
error rather than a crash — but only on a machine with the Android SDK. This
does the same check with sqlite3: it takes the database `verify_migration.py`
builds by running the actual migration chain, then prepares every `@Query`
string found in the DAOs against it.

Room's named parameters (`:id`) happen to be Python's sqlite3 syntax too, so the
statements go in unmodified. Queries whose parameters Room expands at build time
(`IN (:keys)`) cannot be prepared this way and are reported as skipped.

Usage:
    python3 tools/verify_migration.py     # builds the migrated database
    python3 tools/check_dao_sql.py
"""

from __future__ import annotations

import os
import pathlib
import re
import sqlite3
import sys
import tempfile

REPO = pathlib.Path(__file__).resolve().parent.parent
DAO_DIR = REPO / "app/src/main/java/com/zame/nutrinest/data/local/dao"
MIGRATED_DB = pathlib.Path(tempfile.gettempdir()) / "nutrinest_migration_test.db"

# `@Query("...")` / `@Query("""...""")`, keeping the SQL only.
QUERY = re.compile(r'@Query\(\s*("""(?P<raw>(?:.|\n)*?)"""|"(?P<line>(?:[^"\\]|\\.)*)")')
PARAM = re.compile(r":(\w+)")


def statements(path: pathlib.Path) -> list[tuple[int, str]]:
    source = path.read_text(encoding="utf-8")
    found = []
    for match in QUERY.finditer(source):
        sql = match.group("raw")
        if sql is None:
            sql = match.group("line").replace('\\"', '"').replace("\\n", "\n")
        line = source.count("\n", 0, match.start()) + 1
        found.append((line, sql.strip()))
    return found


def main() -> None:
    if not MIGRATED_DB.exists():
        sys.exit(
            f"Migrated database not found at {MIGRATED_DB}.\n"
            f"Run tools/verify_migration.py first."
        )

    conn = sqlite3.connect(MIGRATED_DB.resolve().as_uri() + "?mode=ro", uri=True)
    checked = skipped = 0
    failures: list[str] = []

    for dao in sorted(DAO_DIR.glob("*.kt")):
        print(f"\n{dao.name}")
        for line, sql in statements(dao):
            label = f"{dao.name}:{line}"
            # Room expands collection parameters before SQLite ever sees them.
            if re.search(r"IN\s*\(\s*:", sql, re.I):
                print(f"  skip  {label}  (Room expands a collection parameter)")
                skipped += 1
                continue
            params = {name: 0 for name in PARAM.findall(sql)}
            try:
                conn.execute("EXPLAIN " + sql, params)
                print(f"  ok    {label}")
                checked += 1
            except sqlite3.Error as error:
                print(f"  FAIL  {label}: {error}")
                failures.append(f"{label}: {error}\n    {sql.splitlines()[0]}")

    conn.close()

    print(f"\n{checked} statement(s) compiled, {skipped} skipped")
    if failures:
        print(f"\n{len(failures)} statement(s) the schema will not accept:\n")
        for failure in failures:
            print(f"  {failure}")
        sys.exit(1)
    print("every DAO query compiles against the migrated schema")


if __name__ == "__main__":
    main()
