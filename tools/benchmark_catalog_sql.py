#!/usr/bin/env python3
"""Inspect real Room SELECTs on the shipped data and describe SQLite's access paths."""
import json
import sqlite3
from contextlib import closing, redirect_stdout
from pathlib import Path
from statistics import median
from time import perf_counter_ns
import sys

from check_dao_sql import DAO_DIR, statements
from verify_catalog_migration import metadata_fixture
from verify_migration import ASSET, run_import

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / "app/schemas/com.zame.nutrinest.data.local.NutriNestDatabase/11.json"


def statement(file, fragment):
    return next(sql for _, sql in statements(DAO_DIR / file) if fragment in sql)


def inspect(conn, sql, params):
    plan = [row[3] for row in conn.execute("EXPLAIN QUERY PLAN " + sql, params)]
    times = []
    for iteration in range(36):
        started = perf_counter_ns()
        rows = conn.execute(sql, params).fetchall()
        if iteration >= 5:
            times.append((perf_counter_ns() - started) / 1e6)
    return {"rows": len(rows), "median_ms": median(times), "plan": plan}


def main():
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))["database"]
    with closing(sqlite3.connect(":memory:")) as conn, closing(sqlite3.connect(f"file:{ASSET}?mode=ro", uri=True)) as asset:
        conn.execute("PRAGMA foreign_keys=ON")
        for entity in schema["entities"]:
            for definition in [entity, *entity.get("indices", [])]:
                conn.execute(definition["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        dataset = asset.execute("SELECT value FROM db_metadata WHERE key='dataset_version'").fetchone()[0]
        with redirect_stdout(sys.stderr):
            imported = run_import(conn, dataset)
        metadata_fixture(conn)
        conn.executemany(
            "INSERT INTO recipes(name, category, createdAt, remoteId, isFavorite) VALUES (?, 'Main Dish', 0, ?, 0)",
            [(f"Recipe {i}", f"builtin:fixture-{i}") for i in range(250)],
        )
        categories = [row[0] for row in conn.execute("SELECT key FROM ingredient_categories")]
        search = statement("IngredientDao.kt", "WHERE foodType IN")
        search = search.replace(":categoryKeys", ",".join(f":category{i}" for i in range(len(categories))))
        params = {f"category{i}": category for i, category in enumerate(categories)}
        params.update(favoritesOnly=0, customOnly=0)
        conn.execute("ANALYZE")
        checks = {
            f"search_{term}": inspect(conn, search, dict(params, normalizedQuery=term))
            for term in ("aceite", "puerco", "zzzz")
        }
        checks["canonical_lookup"] = inspect(conn, statement("CanonicalCatalogDao.kt", "ingredient_id=:ingredientId"),
                                             {"ingredientId": 1000042})
        checks["nutrient_lookup"] = inspect(conn, statement("IngredientDao.kt", "JOIN nutrient_defs"),
                                            {"ingredientId": 1000042})
        checks["reference_lookup"] = inspect(conn, statement("IngredientDao.kt", "COUNT(*) FROM recipe_ingredients"),
                                             {"ingredientId": 1000042})
        checks["snapshot_lookup"] = inspect(conn, statement("PriceSnapshotDao.kt", "WHERE regionId"),
                                            {"regionId": "test-region"})
        checks["seeder_remote_id"] = inspect(conn, "SELECT id FROM recipes WHERE remoteId = ?", ("builtin:fixture-42",))
        checks["seeder_nocase_name"] = inspect(conn, "SELECT id FROM recipes WHERE name = ? COLLATE NOCASE", ("recipe 42",))
        checks["import_existing_ids"] = inspect(conn, "SELECT id FROM ingredients WHERE id >= ?", (1_000_000,))
        print(json.dumps({"sqlite": sqlite3.sqlite_version, "dataset": dataset, "imported": imported,
                          "queries": checks}, indent=2))


if __name__ == "__main__":
    main()
