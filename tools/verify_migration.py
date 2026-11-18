#!/usr/bin/env python3
"""Run NutriNest's v4->v5 migration and food import offline.

This harness replays SQL extracted from Kotlin sources; instrumented tests
validate the actual Kotlin backfill and Room callbacks. It executes migration SQL — extracted from the sources
rather than retyped — against a database shaped like a real v4 install, and
checks the result.
"""

from __future__ import annotations

import os
import sqlite3
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ksql import (  # noqa: E402
    extract_copy_steps,
    extract_exec_sql,
    extract_sql_calls,
    extract_string_list,
)

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = f"{REPO}/app/src/main/java/com/zame/nutrinest"
DB_KT = f"{SRC}/data/local/NutriNestDatabase.kt"
IMPORTER_KT = f"{SRC}/data/local/FoodDatabaseImporter.kt"
ASSET = f"{REPO}/app/src/main/assets/nutrinest_foods.db"

IMPORTED_ID_OFFSET = 1_000_000
USER_ID_FLOOR = 2_000_000

failures: list[str] = []


def check(label: str, condition: bool, detail: str = "") -> None:
    if condition:
        print(f"  PASS  {label}")
    else:
        failures.append(label)
        print(f"  FAIL  {label}  {detail}")


# ── Fixture: a v1 database with user data in it ─────────────────────────────
# Room generates the v1 schema; it is the one thing the harness states itself.
# Everything after that comes from the real migrations in NutriNestDatabase.kt.

V1_SCHEMA = [
    """CREATE TABLE `ingredients` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL,
        `foodType` TEXT NOT NULL, `kcal` REAL NOT NULL, `carbs` REAL NOT NULL,
        `fats` REAL NOT NULL, `protein` REAL NOT NULL, `imagePath` TEXT,
        `isCustom` INTEGER NOT NULL, `remoteId` TEXT)""",
    """CREATE TABLE `recipes` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL,
        `category` TEXT NOT NULL, `folder` TEXT, `coverImagePath` TEXT,
        `createdAt` INTEGER NOT NULL, `remoteId` TEXT, `isFavorite` INTEGER NOT NULL)""",
    "CREATE UNIQUE INDEX `index_recipes_name` ON `recipes` (`name`)",
    """CREATE TABLE `recipe_ingredients` (
        `recipeId` INTEGER NOT NULL, `ingredientId` INTEGER NOT NULL, `grams` REAL NOT NULL,
        PRIMARY KEY(`recipeId`, `ingredientId`),
        FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`ingredientId`) REFERENCES `ingredients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""",
    "CREATE INDEX `index_recipe_ingredients_ingredientId` ON `recipe_ingredients` (`ingredientId`)",
    """CREATE TABLE `recipe_steps` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipeId` INTEGER NOT NULL,
        `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `imagePath` TEXT,
        FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
    "CREATE INDEX `index_recipe_steps_recipeId` ON `recipe_steps` (`recipeId`)",
]

# The starter set the app shipped before the dataset, plus a user creation.
STARTER = [
    ("Chicken breast", "Meat", 165.0, 0.0, 3.6, 31.0, 0),
    ("White rice (cooked)", "Rice", 130.0, 28.0, 0.3, 2.7, 0),
    ("Spaghetti (dry)", "Pasta", 371.0, 74.7, 1.5, 13.0, 0),
    ("Salmon", "Fish", 208.0, 0.0, 13.0, 20.0, 0),
    ("Broccoli", "Vegetables", 34.0, 6.6, 0.4, 2.8, 0),
    ("Tomato", "Vegetables", 18.0, 3.9, 0.2, 0.9, 0),
    ("Olive oil", "Oils & Fats", 884.0, 0.0, 100.0, 0.0, 0),
    ("Egg", "Eggs", 155.0, 1.1, 11.0, 13.0, 0),
    ("Whole milk", "Dairy", 61.0, 4.8, 3.3, 3.2, 0),
    ("Banana", "Fruit", 89.0, 22.8, 0.3, 1.1, 0),
    ("Lentils (cooked)", "Legumes", 116.0, 20.1, 0.4, 9.0, 0),
    ("Oats", "Grains", 389.0, 66.3, 6.9, 16.9, 0),
    ("Almonds", "Nuts & Seeds", 579.0, 21.6, 49.9, 21.2, 0),
    ("Greek yogurt", "Dairy", 59.0, 3.6, 0.4, 10.0, 0),
    ("Potato", "Vegetables", 77.0, 17.5, 0.1, 2.0, 0),
    ("Abuela's sofrito", "Spices", 120.0, 8.0, 9.0, 1.5, 1),
]


def build_v1(path: str) -> sqlite3.Connection:
    if os.path.exists(path):
        os.remove(path)
    conn = sqlite3.connect(path)
    conn.execute("PRAGMA foreign_keys = ON")
    for ddl in V1_SCHEMA:
        conn.execute(ddl)
    conn.executemany(
        "INSERT INTO ingredients (name, foodType, kcal, carbs, fats, protein, isCustom)"
        " VALUES (?,?,?,?,?,?,?)",
        STARTER,
    )
    # A recipe that uses two starter ingredients, so the migration has to keep them.
    conn.execute(
        "INSERT INTO recipes (name, category, createdAt, isFavorite) VALUES"
        " ('Tortilla', 'Main Dish', 0, 0)"
    )
    conn.execute("INSERT INTO recipe_ingredients VALUES (1, 8, 120.0)")   # Egg
    conn.execute("INSERT INTO recipe_ingredients VALUES (1, 15, 300.0)")  # Potato
    conn.commit()
    return conn


# ── Step 1: the migrations ──────────────────────────────────────────────────

def run_migrations_1_to_4(conn: sqlite3.Connection) -> None:
    """v1 -> v4, using the migrations already shipped in the app."""
    source = open(DB_KT).read()
    upto = source.index("internal val MIGRATION_4_5")
    for statement in extract_exec_sql(source[:upto]):
        conn.execute(statement)
    conn.commit()


def run_migration_4_5(conn: sqlite3.Connection) -> None:
    source = open(DB_KT).read()

    # addIngredientColumns builds its statements from two lists.
    for spec in extract_string_list(source, "private fun addIngredientColumns"):
        conn.execute(f"ALTER TABLE `ingredients` ADD COLUMN {spec}")
    now = int(time.time() * 1000)
    conn.execute(
        "UPDATE `ingredients` SET `createdAt` = ?, `updatedAt` = ? WHERE `createdAt` = 0",
        (now, now),
    )
    for column in extract_string_list(source, 'listOf("nameNorm"'):
        conn.execute(
            f"CREATE INDEX IF NOT EXISTS `index_ingredients_{column}`"
            f" ON `ingredients` (`{column}`)"
        )

    # createFoodDataTables and the cleanup steps are literal execSQL calls.
    marker = source.index("private fun createFoodDataTables")
    for statement in extract_exec_sql(source[marker:]):
        # Skip the two statements the Kotlin builds at runtime: the legacy
        # mapping (Kotlin string interpolation) and the backfill (bound args).
        if "?" in statement or "$" in statement:
            continue
        conn.execute(statement)

    # Legacy food types -> dataset category keys, built from the same Kotlin map.
    legacy = dict(
        re.findall(r'"([^"]+)"\s+to\s+([A-Z_]+)', _legacy_block(source))
    )
    cases = " ".join(f"WHEN '{k}' THEN '{v}'" for k, v in legacy.items())
    all_keys = ", ".join(f"'{k}'" for k in CATEGORY_KEYS)
    conn.execute(
        f"UPDATE `ingredients` SET `foodType` = CASE `foodType` {cases} ELSE 'MISC' END"
        f" WHERE `foodType` NOT IN ({all_keys})"
    )

    # backfillNormalisedNames walks the rows in Kotlin.
    rows = conn.execute(
        "SELECT `id`, `name` FROM `ingredients` WHERE `nameNorm` = ''"
    ).fetchall()
    for row_id, name in rows:
        conn.execute(
            "UPDATE `ingredients` SET `nameNorm` = ? WHERE `id` = ?", (normalize(name), row_id)
        )
    conn.commit()


def run_migration_5_6(conn: sqlite3.Connection) -> None:
    """Apply the actual v5 -> v6 statements and generated index loop."""
    source = open(DB_KT).read()
    marker = source.index("internal val MIGRATION_5_6")
    end = source.index("internal val MIGRATION_6_7", marker)
    for statement in extract_exec_sql(source[marker:end]):
        # rebuildPlannedMeals creates these three statements in a Kotlin loop.
        if "$it" in statement:
            continue
        conn.execute(statement)
    for column in ("menuId", "recipeId", "profileId"):
        conn.execute(
            f"CREATE INDEX IF NOT EXISTS index_planned_meals_{column} "
            f"ON planned_meals ({column})"
        )
    conn.commit()


def run_migration_6_7(conn: sqlite3.Connection) -> None:
    """Apply the recipe presentation and quantity migration."""
    source = open(DB_KT).read()
    marker = source.index("internal val MIGRATION_6_7")
    end = source.index("internal val MIGRATION_7_8", marker)
    for statement in extract_exec_sql(source[marker:end]):
        conn.execute(statement)
    conn.commit()


def run_migration_7_8(conn: sqlite3.Connection) -> None:
    """Apply the bilingual recipe-title and instruction migration."""
    source = open(DB_KT).read()
    marker = source.index("internal val MIGRATION_7_8")
    end = source.index("internal val MIGRATION_8_9", marker)
    for statement in extract_exec_sql(source[marker:end]):
        conn.execute(statement)
    conn.commit()


def seed_v5_menu(conn: sqlite3.Connection) -> None:
    """A real v5 menu with portions and a checked shopping item."""
    conn.execute(
        "INSERT INTO profiles (id, name, dateOfBirth, sex, heightCm, weightKg, "
        "bodyFatPercent, activityLevel, goal, weightLossRate, formula, unitSystem, "
        "dietaryPreferences, allergies, colorIndex, createdAt, remoteId) VALUES "
        "(1, 'Ana', 0, 'FEMALE', 165, 60, NULL, 'MODERATE', 'MAINTAIN', "
        "'MODERATE', 'AUTO', 'METRIC', NULL, NULL, 0, 0, NULL)"
    )
    conn.execute(
        "INSERT INTO weekly_menus (id, weekStart, slotsSpec, createdAt) "
        "VALUES (1, 0, 'LUNCH:0.5,DINNER:0.5', 0)"
    )
    conn.execute(
        "INSERT INTO planned_meals "
        "(id, menuId, dayOfWeek, slot, recipeId, isLocked, isCheatMeal) VALUES "
        "(1, 1, 0, 'LUNCH', 1, 1, 0), (2, 1, 0, 'DINNER', 1, 0, 0)"
    )
    conn.execute("INSERT INTO meal_portions VALUES (1, 1, 1.5), (2, 1, 0.8)")
    conn.execute(
        "INSERT INTO shopping_items "
        "(id, menuId, ingredientId, grams, purchased) VALUES (1, 1, 8, 6, 1)"
    )
    conn.commit()


def verify_profile_rebuild() -> None:
    """Exercise the v3 profile rebuild with actual profile and weight rows."""
    conn = sqlite3.connect(":memory:")
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute(
        """CREATE TABLE profiles (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
        dateOfBirth INTEGER NOT NULL, sex TEXT NOT NULL, heightCm REAL NOT NULL,
        weightKg REAL NOT NULL, bodyFatPercent REAL, activityLevel TEXT NOT NULL,
        goal TEXT NOT NULL, formula TEXT NOT NULL, unitSystem TEXT NOT NULL,
        dietaryPreferences TEXT, allergies TEXT, colorIndex INTEGER NOT NULL,
        createdAt INTEGER NOT NULL, remoteId TEXT,
        weightLossRate TEXT NOT NULL DEFAULT 'MODERATE')"""
    )
    conn.execute(
        """CREATE TABLE weight_entries (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, profileId INTEGER NOT NULL,
        weightKg REAL NOT NULL, recordedAt INTEGER NOT NULL,
        FOREIGN KEY(profileId) REFERENCES profiles(id) ON DELETE CASCADE)"""
    )
    conn.execute(
        """INSERT INTO profiles VALUES
        (7, 'Ana', 1, 'FEMALE', 165, 62, 24, 'MODERATE', 'MAINTAIN',
         'AUTO', 'METRIC', 'vegetarian', 'nuts', 2, 99, 'remote-7', 'SLOW')"""
    )
    conn.execute("INSERT INTO weight_entries VALUES (9, 7, 61.5, 1234)")

    source = open(DB_KT).read()
    start = source.index("private val MIGRATION_3_4")
    end = source.index("internal val MIGRATION_4_5", start)
    for statement in extract_exec_sql(source[start:end]):
        conn.execute(statement)

    profile = conn.execute(
        "SELECT id, name, dietaryPreferences, allergies, colorIndex, remoteId, weightLossRate "
        "FROM profiles"
    ).fetchone()
    check("v3->v4 preserves profile metadata",
          profile == (7, "Ana", "vegetarian", "nuts", 2, "remote-7", "SLOW"), str(profile))
    weight = conn.execute(
        "SELECT id, profileId, weightKg, recordedAt FROM weight_entries"
    ).fetchone()
    check("v3->v4 preserves weight history", weight == (9, 7, 61.5, 1234), str(weight))
    default = next(
        row[4] for row in conn.execute("PRAGMA table_info(profiles)")
        if row[1] == "weightLossRate"
    )
    check("v3->v4 removes the legacy SQL default", default is None, str(default))
    check("profile rebuild leaves no foreign-key violations",
          not conn.execute("PRAGMA foreign_key_check").fetchall())
    conn.close()


def _legacy_block(_: str) -> str:
    """The LEGACY_FOOD_TYPES map, which lives with the entity, not the database."""
    entity = open(f"{SRC}/data/local/entity/IngredientEntity.kt").read()
    start = entity.index("val LEGACY_FOOD_TYPES")
    return entity[start:entity.index("\n    )", start)]


# ── Step 2: the import ──────────────────────────────────────────────────────

def run_import(conn: sqlite3.Connection, expect_version: str) -> dict[str, tuple[int, int]]:
    """Replay the importer step for step, using its own SQL.

    Mirrors FoodDatabaseImporter: read each table over a separate read-only
    connection to the asset, then write the rows through the app's connection.
    No ATTACH — that is what broke Room's per-connection invalidation tracker.
    """
    source = open(IMPORTER_KT).read()
    substitutions = {
        "$IMPORTED_ID_OFFSET": str(IMPORTED_ID_OFFSET),
        "$HUMAN_MILK_FOOD_ID": "2494",
    }
    statements = extract_exec_sql(source, substitutions)
    # restoreUserState issues its UPDATE through a prepared statement.
    statements += extract_sql_calls(source, "compileStatement", substitutions)
    steps = extract_copy_steps(source, substitutions)

    installed = conn.execute("SELECT value FROM food_dataset_meta WHERE `key`='dataset_version'").fetchone()
    if installed is not None and installed[0] != expect_version:
        # Like the Kotlin bootstrap, defer changed datasets without modifying markers or rows.
        return {}
    now = int(time.time() * 1000)
    inserted = set()
    tally = {}
    asset = sqlite3.connect(f"file:{ASSET}?mode=ro", uri=True)
    try:
        for step in steps:
            args = (str(now), str(now)) if step.get("readArgs") else ()
            read = written = 0
            for row in asset.execute(step["read"], args):
                read += 1
                label = step["label"]
                if label in ("nutrients", "portions", "aliases", "quality issues"):
                    if row[0] not in inserted:
                        continue
                else:
                    table, key = {"ingredients": ("ingredients", "id"),
                        "categories": ("ingredient_categories", "key"),
                        "aisles": ("shopping_aisles", "key"),
                        "dataset metadata": ("food_dataset_meta", "key"),
                        "nutrient definitions": ("nutrient_defs", "tag")}[label]
                    if conn.execute(f'SELECT 1 FROM {table} WHERE `{key}`=?', (row[0],)).fetchone():
                        continue
                written += conn.execute(step["write"], row).rowcount or 0
                if label == "ingredients": inserted.add(row[0])
            tally[step["label"]] = (read, written)
    finally:
        asset.close()
    reserve = next(s for s in statements if s.startswith("UPDATE sqlite_sequence"))
    conn.execute(reserve, (USER_ID_FLOOR, USER_ID_FLOOR))
    for key, value in (("dataset_version", expect_version), ("last_import_at", str(now))):
        conn.execute("UPDATE food_dataset_meta SET value=? WHERE `key`=?", (value, key))
        conn.execute("INSERT INTO food_dataset_meta (`key`,value) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM food_dataset_meta WHERE `key`=?1)", (key, value))
    conn.commit()
    return tally


# ── Shared helpers ──────────────────────────────────────────────────────────

import re  # noqa: E402
import unicodedata  # noqa: E402

CATEGORY_KEYS = [
    "CEREALS", "VEGETABLES", "FRUITS", "LEGUMES", "MEAT", "FISH",
    "EGGS", "DAIRY", "FATS_OILS", "SWEETS", "BEVERAGES", "MISC",
]


def normalize(text: str) -> str:
    decomposed = unicodedata.normalize("NFD", text)
    stripped = "".join(c for c in decomposed if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", stripped.lower()).strip()


# ── The checks ──────────────────────────────────────────────────────────────

def main() -> None:
    db_path = os.path.join(tempfile.gettempdir(), "nutrinest_migration_test.db")
    asset = sqlite3.connect(f"file:{ASSET}?mode=ro", uri=True)
    version = asset.execute(
        "SELECT value FROM db_metadata WHERE key = 'dataset_version'"
    ).fetchone()[0]
    expected_foods = asset.execute(
        "SELECT COUNT(*) FROM foods WHERE is_deprecated = 0 AND id != 2494"
    ).fetchone()[0]

    print("1. v1 fixture -> v4 -> v5")
    verify_profile_rebuild()
    conn = build_v1(db_path)
    run_migrations_1_to_4(conn)
    check("menu planner tables exist after v3->v4",
          {"weekly_menus", "planned_meals", "meal_portions", "shopping_items"} <=
          {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")})
    run_migration_4_5(conn)

    cols = {r[1] for r in conn.execute("PRAGMA table_info(ingredients)")}
    check("ingredients gained every new column",
          {"nameEs", "nameNorm", "rawSafety", "isFavorite", "usageCount",
           "createdAt", "updatedAt", "densityGml"} <= cols)

    tables = {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    check("food data tables created",
          {"ingredient_categories", "shopping_aisles", "nutrient_defs",
           "ingredient_nutrients", "ingredient_portions", "ingredient_aliases",
           "ingredient_quality_issues", "food_dataset_meta"} <= tables,
          str(sorted(tables)))

    remaining = conn.execute("SELECT name, foodType, isCustom FROM ingredients").fetchall()
    check("unused starter ingredients dropped", len(remaining) == 3, str(remaining))
    check("starter ingredients used by a recipe kept",
          {r[0] for r in remaining} == {"Egg", "Potato", "Abuela's sofrito"}, str(remaining))
    check("legacy food types mapped to category keys",
          all(r[1] in CATEGORY_KEYS for r in remaining), str(remaining))
    check("Spices mapped to MISC",
          conn.execute("SELECT foodType FROM ingredients WHERE name LIKE 'Abuela%'")
          .fetchone()[0] == "MISC")
    check("normalised names backfilled",
          conn.execute("SELECT nameNorm FROM ingredients WHERE name LIKE 'Abuela%'")
          .fetchone()[0] == "abuela s sofrito")
    check("createdAt backfilled",
          conn.execute("SELECT COUNT(*) FROM ingredients WHERE createdAt = 0").fetchone()[0] == 0)

    seed_v5_menu(conn)
    run_migration_5_6(conn)
    run_migration_6_7(conn)
    run_migration_7_8(conn)
    recipe_columns = {row[1] for row in conn.execute("PRAGMA table_info(recipes)")}
    link_columns = {row[1] for row in conn.execute("PRAGMA table_info(recipe_ingredients)")}
    step_columns = {row[1] for row in conn.execute("PRAGMA table_info(recipe_steps)")}
    check("v6->v7 adds cover image framing", "coverImageCrop" in recipe_columns)
    check("v6->v7 retains the selected quantity unit", "quantityUnit" in link_columns)
    check("v7->v8 adds translated recipe names", "nameEs" in recipe_columns)
    check("v7->v8 adds translated instructions", "textEs" in step_columns)

    print("\n2. import")
    tally = run_import(conn, version)
    for label, (read, written) in tally.items():
        check(f"{label}: all {read} rows read were written", read == written,
              f"read {read}, wrote {written}")

    imported = conn.execute("SELECT COUNT(*) FROM ingredients WHERE isCustom = 0").fetchone()[0]
    check(f"{expected_foods} active foods imported",
          imported == expected_foods + 2, f"got {imported}")
    check("categories imported",
          conn.execute("SELECT COUNT(*) FROM ingredient_categories").fetchone()[0] == 12)
    check("aisles imported",
          conn.execute("SELECT COUNT(*) FROM shopping_aisles").fetchone()[0] == 14)
    check("nutrient definitions imported",
          conn.execute("SELECT COUNT(*) FROM nutrient_defs").fetchone()[0] == 81)
    # Deprecated foods are deliberately not imported, so the expected counts are
    # the asset's active rows, not its table totals.
    for table, source_table in (
        ("ingredient_nutrients", "food_nutrients"),
        ("ingredient_portions", "food_portions"),
        ("ingredient_aliases", "food_aliases"),
        ("ingredient_quality_issues", "food_quality_issues"),
    ):
        expected = asset.execute(
            f"SELECT COUNT(*) FROM {source_table} s"
            f" JOIN foods f ON f.id = s.food_id "
            f"WHERE f.is_deprecated = 0 AND f.id != 2494"
        ).fetchone()[0]
        actual = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
        check(f"{table} = {expected} (active foods only)", actual == expected, f"got {actual}")
    check("nothing imported for deprecated foods",
          conn.execute(
              "SELECT COUNT(*) FROM ingredient_nutrients WHERE ingredientId NOT IN"
              " (SELECT id FROM ingredients)").fetchone()[0] == 0)
    check("human milk is excluded from the catalogue",
          conn.execute("SELECT COUNT(*) FROM ingredients WHERE sourceKey = 'BEDCA:2494'")
          .fetchone()[0] == 0)

    check("dataset version recorded",
          conn.execute("SELECT value FROM food_dataset_meta WHERE key='dataset_version'")
          .fetchone()[0] == version)
    check("attribution carried over",
          "BEDCA" in conn.execute(
              "SELECT value FROM food_dataset_meta WHERE key='attribution'").fetchone()[0])

    conn.execute("PRAGMA foreign_keys = ON")
    violations = conn.execute("PRAGMA foreign_key_check").fetchall()
    check("no foreign key violations", not violations, str(violations[:5]))

    check("every ingredient has a known category",
          conn.execute(
              f"SELECT COUNT(*) FROM ingredients WHERE foodType NOT IN "
              f"({', '.join(repr(k) for k in CATEGORY_KEYS)})").fetchone()[0] == 0)
    check("every imported row has a searchable name",
          conn.execute(
              "SELECT COUNT(*) FROM ingredients WHERE isCustom = 0 AND "
              "(nameNorm = '' OR nameNorm IS NULL)").fetchone()[0] == 0)
    check("bilingual names present",
          conn.execute(
              "SELECT COUNT(*) FROM ingredients WHERE isCustom = 0 AND nameEs IS NOT NULL"
          ).fetchone()[0] == expected_foods)
    check("imported ids sit in their reserved block",
          conn.execute(
              f"SELECT COUNT(*) FROM ingredients WHERE isCustom = 0 AND id <= {IMPORTED_ID_OFFSET}"
          ).fetchone()[0] == 2)
    check("user ids reserved above the import block",
          conn.execute(
              "SELECT seq FROM sqlite_sequence WHERE name = 'ingredients'"
          ).fetchone()[0] >= USER_ID_FLOOR)
    check("raw safety populated",
          conn.execute(
              "SELECT COUNT(*) FROM ingredients WHERE isCustom = 0 AND rawSafety = 'UNKNOWN'"
          ).fetchone()[0] < expected_foods * 0.2)
    check("serving sizes taken from default portions",
          conn.execute(
              "SELECT COUNT(*) FROM ingredients WHERE isCustom = 0 AND servingSizeG IS NOT NULL"
          ).fetchone()[0] > 500)
    check("recipe survived the migration",
          conn.execute("SELECT COUNT(*) FROM recipe_ingredients").fetchone()[0] == 2)

    print("\n3. search behaves")
    # Accent-insensitive, bilingual, and reaching through the synonym table.
    for term in ("aceite", "olive", "platano", "plátano", "capsicum", "jugo",
                 "puerco", "rinon", "chicken"):
        q = normalize(term)
        hits = conn.execute(
            "SELECT COUNT(*) FROM ingredients WHERE nameNorm LIKE ? OR nameEsNorm LIKE ?"
            " OR id IN (SELECT ingredientId FROM ingredient_aliases WHERE aliasNorm LIKE ?)",
            (f"%{q}%", f"%{q}%", f"%{q}%"),
        ).fetchone()[0]
        check(f"search '{term}' finds something", hits > 0, f"hits={hits}")

    # "puerco" is not in any name; it only reaches pork through the alias table.
    synonym_only = conn.execute(
        "SELECT COUNT(*) FROM ingredients WHERE nameNorm LIKE '%puerco%'"
        " OR nameEsNorm LIKE '%puerco%'"
    ).fetchone()[0]
    check("synonyms reach foods no name match would", synonym_only == 0)

    print("\n4. import is idempotent")
    before = conn.execute("SELECT COUNT(*) FROM ingredients").fetchone()[0]
    nutrients_before = conn.execute("SELECT COUNT(*) FROM ingredient_nutrients").fetchone()[0]
    portions_before = conn.execute("SELECT COUNT(*) FROM ingredient_portions").fetchone()[0]
    aliases_before = conn.execute("SELECT COUNT(*) FROM ingredient_aliases").fetchone()[0]
    run_import(conn, version)
    check("re-running adds no rows",
          conn.execute("SELECT COUNT(*) FROM ingredients").fetchone()[0] == before)
    check("re-running duplicates no nutrients",
          conn.execute("SELECT COUNT(*) FROM ingredient_nutrients").fetchone()[0]
          == nutrients_before)
    check("re-running duplicates no portions",
          conn.execute("SELECT COUNT(*) FROM ingredient_portions").fetchone()[0] == portions_before)
    check("re-running duplicates no aliases",
          conn.execute("SELECT COUNT(*) FROM ingredient_aliases").fetchone()[0] == aliases_before)

    print("\n5. a changed dataset is deferred without changing favourites")
    victim = conn.execute(
        "SELECT id FROM ingredients WHERE isCustom = 0 AND id > ? LIMIT 1",
        (IMPORTED_ID_OFFSET,),
    ).fetchone()[0]
    conn.execute("UPDATE ingredients SET isFavorite = 1, usageCount = 7 WHERE id = ?", (victim,))
    conn.execute("UPDATE food_dataset_meta SET value = '0000.00.00' WHERE key = 'dataset_version'")
    conn.commit()
    run_import(conn, version)
    kept = conn.execute(
        "SELECT isFavorite, usageCount FROM ingredients WHERE id = ?", (victim,)
    ).fetchone()
    check("favourite and usage preserved when refresh is deferred", kept == (1, 7), str(kept))
    check("row count stable after refresh",
          conn.execute("SELECT COUNT(*) FROM ingredients").fetchone()[0] == before)

    print("\n6. v5 -> v6: per-person menus")
    meals = conn.execute(
        "SELECT id, slot, recipeId, isLocked, isCheatMeal, course, profileId, isFree"
        " FROM planned_meals ORDER BY id"
    ).fetchall()
    check("the week's meals survived the rebuild", len(meals) == 2, str(meals))
    check("meal ids are unchanged, so portions still point at them",
          [m[0] for m in meals] == [1, 2], str(meals))
    check("the lock survived", meals[0][3] == 1, str(meals[0]))
    check("everything already planned is still shared",
          all(m[6] is None for m in meals), str(meals))
    check("existing meals became single-course",
          all(m[5] == "SINGLE" for m in meals), str(meals))
    check("nothing was turned into a free slot",
          all(m[7] == 0 for m in meals), str(meals))
    check("recipeId is now nullable",
          not next(
              r[3] for r in conn.execute("PRAGMA table_info(planned_meals)")
              if r[1] == "recipeId"
          ))

    portions = conn.execute(
        "SELECT plannedMealId, profileId, factor, baseFactor FROM meal_portions ORDER BY plannedMealId"
    ).fetchall()
    check("portions kept their factors", [p[2] for p in portions] == [1.5, 0.8], str(portions))
    check("the planned factor was backfilled from the served one",
          all(p[2] == p[3] for p in portions), str(portions))

    check("the shopping list is untouched, ticks included",
          conn.execute(
              "SELECT COUNT(*) FROM shopping_items WHERE menuId = 1 AND purchased = 1"
          ).fetchone()[0] == 1)
    check("the menu itself defaults to a shared scope",
          conn.execute("SELECT scope, coursesSpec FROM weekly_menus WHERE id = 1").fetchone()
          == ("SHARED", ""))

    tables = {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    check("per-person tables created",
          {"profile_meal_plans", "intake_entries"} <= tables, str(sorted(tables)))
    check("the rebuilt table left no scratch copy behind", "planned_meals_new" not in tables)

    indexes = {r[0] for r in conn.execute(
        "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='planned_meals'"
    )}
    check("planned_meals indexes recreated",
          {"index_planned_meals_menuId", "index_planned_meals_recipeId",
           "index_planned_meals_profileId"} <= indexes, str(sorted(indexes)))

    # The whole point of turning foreign keys back on: the rebuilt table has to
    # satisfy every constraint pointing at it and every constraint it declares.
    violations = conn.execute("PRAGMA foreign_key_check").fetchall()
    check("no foreign key violations after the rebuild", not violations, str(violations[:5]))

    # A free slot is the one row shape v5 could not store.
    conn.execute(
        "INSERT INTO planned_meals (menuId, dayOfWeek, slot, recipeId, isLocked,"
        " isCheatMeal, course, profileId, isFree) VALUES (1, 2, 'DINNER', NULL, 0, 0,"
        " 'SINGLE', 1, 1)"
    )
    conn.commit()
    check("a free slot with no recipe is storable",
          conn.execute(
              "SELECT COUNT(*) FROM planned_meals WHERE recipeId IS NULL AND isFree = 1"
          ).fetchone()[0] == 1)

    print("\n7. v8 -> v9 additive catalog SQL")
    from verify_catalog_migration import verify_catalog
    verify_catalog(conn)

    print("\n8. v9 -> v10 additive recipe quantities")
    original = conn.execute("SELECT recipeId, ingredientId, grams, quantityUnit FROM recipe_ingredients ORDER BY 1, 2").fetchall()
    with open(DB_KT, encoding="utf-8") as database_file:
        source = database_file.read()
    start = source.index("internal val MIGRATION_9_10")
    end = source.index("private fun tableExists", start)
    for statement in extract_exec_sql(source[start:end]):
        conn.execute(statement)
    migrated = conn.execute("SELECT recipeId, ingredientId, grams, quantityUnit FROM recipe_ingredients ORDER BY 1, 2").fetchall()
    check("recipe ingredient IDs, grams and legacy units are identical", migrated == original)
    check("historical quantities have no inferred original amount or reference",
          not conn.execute("SELECT 1 FROM recipe_ingredients WHERE originalQuantity IS NOT NULL OR quantityEvidenceId IS NOT NULL").fetchall())
    conn.execute("PRAGMA user_version=10")
    conn.commit()
    conn.close()
    print()
    if failures:
        print(f"{len(failures)} FAILED: {failures}")
        sys.exit(1)
    print("all checks passed")


if __name__ == "__main__":
    main()
