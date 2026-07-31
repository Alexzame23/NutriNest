#!/usr/bin/env python3
"""Turn the raw BEDCA-derived food dataset into the APK asset the app ships.

The published dataset carries material the app does not use at runtime:

  * ``food_catalog_index``  - a catalogue of every BEDCA entry, including the
    1400 that were never imported into ``foods``.
  * ``foods_fts*``          - FTS5 shadow tables. The app searches with indexed
    LIKE over the normalised name columns instead (~1k rows, so FTS earns
    nothing but size).
  * a few indexes that only serve query shapes the app never issues.

Dropping those and vacuuming takes the asset from ~2.9 MB to ~2.2 MB, which the
APK then deflates to roughly 1 MB.

It also derives two columns the app needs but the dataset does not ship:

  * ``foods.name_en_norm`` - the English counterpart of ``name_norm``. SQLite
    cannot strip accents, so computing it here keeps the import a plain
    INSERT..SELECT and guarantees it uses the same rule as the Spanish column.
  * ``foods.raw_safety``   - whether the food can be eaten without cooking.
    The dataset's ``prep_state`` says how a food was *measured* ("Pork,
    sirloin, raw"), which is a different question, so this is derived from
    preparation state, category and name keywords.

Usage:
    python3 tools/build_food_asset.py <source.db> [-o app/src/main/assets/nutrinest_foods.db]
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import sqlite3
import sys
import tempfile
import unicodedata

DROP_TABLES = [
    "food_catalog_index",
    "foods_fts",
]

DROP_INDEXES = [
    # The app always reads nutrients by food_id (the primary key's leading
    # column), never by tag across every food.
    "index_food_nutrients_tag",
    # Sorting the whole catalogue by kcal/protein happens in memory on the
    # already-filtered list, not in SQL.
    "index_food_macros_kcal",
    "index_food_macros_protein_g",
    "index_food_catalog_index_is_imported",
]

# ── Derived: normalised English name ────────────────────────────────────────

_COMBINING_MARKS = re.compile(r"[̀-ͯ]")
_NON_ALPHANUMERIC = re.compile(r"[^a-z0-9]+")


def normalize(text: str) -> str:
    """Mirror of TextNormalizer.normalize in the app (and of the dataset's own
    name_norm rule, verified against all 936 foods and 1164 aliases)."""
    decomposed = unicodedata.normalize("NFD", text)
    stripped = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    return _NON_ALPHANUMERIC.sub(" ", stripped.lower()).strip()


# ── Derived: can this be eaten without cooking? ─────────────────────────────

SAFE, CAUTION, UNSAFE, UNKNOWN = "SAFE", "CAUTION", "UNSAFE", "UNKNOWN"

# A preparation state that already implies the food is cooked or preserved:
# the question "can I eat this as it is?" is then answered by the state itself.
READY_TO_EAT_STATES = {
    "BOILED", "COOKED", "FRIED", "GRILLED", "ROASTED", "STEAMED", "CANNED", "POWDERED",
}
# Preserved but not cooked - edible as-is for most people, worth a caveat.
PRESERVED_STATES = {"CURED", "SMOKED"}

# Keyword sets are matched against the normalised ES and EN names together.
NUTS_AND_SEEDS = (
    "almendra nuez nueces avellana pistacho cacahuete anacardo pipa pipas semilla "
    "semillas sesamo chia lino girasol calabaza almond walnut hazelnut pistachio "
    "peanut cashew seed seeds sesame sunflower pumpkin pine nut"
).split()
NEEDS_COOKING_VEG = (
    "patata patatas boniato batata yuca mandioca berenjena alcachofa cardo "
    "potato sweet potato cassava aubergine eggplant artichoke"
).split()
MUSHROOMS = "seta setas champinon champinones niscalo mushroom mushrooms".split()
RAW_FLOUR = "harina harinas flour flours semola semolina masa dough".split()
BAKED_OR_READY = (
    "pan panecillo bollo galleta galletas bizcocho magdalena tostada cereales "
    "bread roll biscuit cookie cake muffin toast breakfast cereal crouton"
).split()
CURED_MEATS = (
    "jamon chorizo salchichon lomo embuchado cecina fuet sobrasada salami "
    "mortadela pate ham prosciutto sausage cured luncheon fiambre"
).split()
# Many entries record the cooking method in the name while leaving prep_state
# UNSPECIFIED ("Egg, chicken, whole, poached"). Those are already cooked.
COOKED_IN_NAME = (
    "cocido cocida cocidos cocidas hervido hervida asado asada frito frita "
    "plancha horno guisado guisada estofado plancha plancha escalfado escalfada "
    "boiled cooked roasted fried grilled baked steamed braised stewed poached "
    "toasted tostado tostada"
).split()
RAW_EATEN_FISH = (
    "ahumado ahumada anchoa anchoas boqueron boquerones salazon surimi caviar "
    "hueva smoked anchovy marinated pickled"
).split()
RAW_MILK = "cruda crudo raw unpasteurised unpasteurized".split()


def _mentions(haystack_words: set[str], keywords: list[str]) -> bool:
    return any(word in haystack_words for word in keywords)


def derive_raw_safety(name_es: str, name_en: str | None, category_key: str,
                      prep_state: str) -> str:
    """Guidance on eating a food without cooking it.

    Deliberately conservative: anything that could carry a real pathogen risk
    (raw meat, fish, eggs, dry legumes) is UNSAFE, and anything merely
    debatable is CAUTION. The user can override it per ingredient.
    """
    words = set(normalize(f"{name_es} {name_en or ''}").split())

    if prep_state in READY_TO_EAT_STATES:
        return SAFE
    if prep_state in PRESERVED_STATES:
        return CAUTION
    # A food explicitly measured raw is raw, whatever its name suggests.
    if prep_state != "RAW" and _mentions(words, COOKED_IN_NAME):
        return SAFE

    if _mentions(words, RAW_FLOUR):
        return CAUTION

    if category_key in ("FRUITS", "FATS_OILS", "SWEETS", "BEVERAGES"):
        return SAFE

    if category_key == "VEGETABLES":
        if _mentions(words, NEEDS_COOKING_VEG):
            return CAUTION
        if _mentions(words, MUSHROOMS):
            return CAUTION
        return SAFE

    if category_key == "DAIRY":
        return CAUTION if _mentions(words, RAW_MILK) else SAFE

    if category_key == "EGGS":
        return UNSAFE

    if category_key == "MEAT":
        return CAUTION if _mentions(words, CURED_MEATS) else UNSAFE

    if category_key == "FISH":
        return CAUTION if _mentions(words, RAW_EATEN_FISH) else UNSAFE

    if category_key == "LEGUMES":
        # Nuts and seeds are eaten raw; dry pulses contain lectins and are not.
        return SAFE if _mentions(words, NUTS_AND_SEEDS) else UNSAFE

    if category_key == "CEREALS":
        return SAFE if _mentions(words, BAKED_OR_READY) else CAUTION

    return UNKNOWN


def add_derived_columns(conn: sqlite3.Connection) -> dict[str, int]:
    existing = {row[1] for row in conn.execute("PRAGMA table_info(foods)")}
    if "name_en_norm" not in existing:
        conn.execute("ALTER TABLE foods ADD COLUMN name_en_norm TEXT NOT NULL DEFAULT ''")
    if "raw_safety" not in existing:
        conn.execute("ALTER TABLE foods ADD COLUMN raw_safety TEXT NOT NULL DEFAULT 'UNKNOWN'")

    rows = conn.execute(
        """
        SELECT f.id, f.name_es, f.name_en, c.key, f.prep_state
        FROM foods f JOIN food_categories c ON c.id = f.category_id
        """
    ).fetchall()

    distribution: dict[str, int] = {}
    updates = []
    for food_id, name_es, name_en, category_key, prep_state in rows:
        safety = derive_raw_safety(name_es, name_en, category_key, prep_state)
        distribution[safety] = distribution.get(safety, 0) + 1
        updates.append((normalize(name_en or name_es), safety, food_id))

    conn.executemany(
        "UPDATE foods SET name_en_norm = ?, raw_safety = ? WHERE id = ?", updates
    )
    conn.execute(
        "CREATE INDEX IF NOT EXISTS index_foods_name_en_norm ON foods(name_en_norm)"
    )
    conn.commit()
    return distribution


# Tables the importer reads. Guards against shipping an asset that is missing
# something the ATTACH-based import expects.
REQUIRED_TABLES = [
    "db_metadata",
    "nutrient_defs",
    "food_categories",
    "shopping_aisles",
    "foods",
    "food_macros",
    "food_nutrients",
    "food_portions",
    "food_aliases",
    "food_quality_issues",
]


def table_names(conn: sqlite3.Connection) -> set[str]:
    return {
        row[0]
        for row in conn.execute("SELECT name FROM sqlite_master WHERE type = 'table'")
    }


def build(source: str, dest: str) -> None:
    if not os.path.exists(source):
        sys.exit(f"source database not found: {source}")

    os.makedirs(os.path.dirname(os.path.abspath(dest)), exist_ok=True)

    # Work on a temporary copy so a failure never leaves a half-built asset in
    # the assets directory.
    handle, staging = tempfile.mkstemp(suffix=".db")
    os.close(handle)
    shutil.copyfile(source, staging)

    try:
        conn = sqlite3.connect(staging)
        try:
            before = os.path.getsize(staging)

            for index in DROP_INDEXES:
                conn.execute(f"DROP INDEX IF EXISTS {index}")
            for table in DROP_TABLES:
                # Dropping the FTS5 virtual table also removes its shadow
                # tables (_data, _idx, _docsize, _config).
                conn.execute(f"DROP TABLE IF EXISTS {table}")
            conn.execute("DROP TABLE IF EXISTS sqlite_stat1")
            conn.commit()

            missing = [t for t in REQUIRED_TABLES if t not in table_names(conn)]
            if missing:
                sys.exit(f"asset is missing tables the importer needs: {missing}")

            distribution = add_derived_columns(conn)

            counts = {
                table: conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
                for table in REQUIRED_TABLES
            }
            version = conn.execute(
                "SELECT value FROM db_metadata WHERE key = 'dataset_version'"
            ).fetchone()

            conn.execute("VACUUM")
            conn.commit()
        finally:
            conn.close()

        after = os.path.getsize(staging)
        shutil.move(staging, dest)
        staging = None
    finally:
        if staging and os.path.exists(staging):
            os.remove(staging)

    print(f"dataset_version : {version[0] if version else '(unset)'}")
    for table, count in counts.items():
        print(f"  {table:<22} {count:>6}")
    print("raw_safety      :")
    for safety, count in sorted(distribution.items(), key=lambda kv: -kv[1]):
        print(f"  {safety:<22} {count:>6}")
    print(f"size            : {before / 1024:.0f} KB -> {after / 1024:.0f} KB")
    print(f"written         : {dest}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", help="raw dataset produced by the BEDCA pipeline")
    parser.add_argument(
        "-o",
        "--output",
        default=os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "app",
            "src",
            "main",
            "assets",
            "nutrinest_foods.db",
        ),
        help="where to write the asset (defaults to the app's assets directory)",
    )
    args = parser.parse_args()
    build(args.source, args.output)


if __name__ == "__main__":
    main()
