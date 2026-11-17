#!/usr/bin/env python3
"""Read-only, deterministic audit of the shipped catalog (Python >= 3.10).

Run from any directory: python tools/audit_food_catalog.py
No external packages, network, Kotlin execution, or source-data writes.
Unknown recipe DSL expressions fail closed instead of silently losing IDs.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import itertools
import json
import math
from collections import Counter, defaultdict
from pathlib import Path
import re
import sqlite3
import statistics
import sys
import unicodedata

from ksql import extract_copy_steps

ROOT = Path(__file__).resolve().parents[1]
LOCAL = Path("app/src/main/java/com/zame/nutrinest/data/local")
DEFAULT_ASSET = ROOT / "app/src/main/assets/nutrinest_foods.db"
COOKED = {"BOILED", "COOKED", "FRIED", "GRILLED", "ROASTED", "STEAMED"}
MACRO_KEYS = ("kcal", "protein_g", "fat_g", "carbs_g", "fiber_g", "alcohol_g")
ATTR_KEYS = ("category_id", "prep_state", "is_liquid", "density_g_ml")
THRESHOLDS = {
    "macro_equality_abs": 0.000001, "energy_difference_kcal": 20,
    "energy_difference_relative": 0.20, "mass_balance_min_g": 95,
    "mass_balance_max_g": 105, "density_review_min": 0.5,
    "density_review_max": 2.0, "portion_review_min_g": 0.1,
    "portion_review_max_g": 2000, "portion_volume_relative": 0.05,
    "nutrient_coverage_low_pct": 50,
}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def norm(value):
    text = "".join(c for c in unicodedata.normalize("NFD", value or "")
                   if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def stable(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, allow_nan=False)


def close(a, b, tolerance=1e-6):
    return a is not None and b is not None and abs(a - b) <= tolerance


def positive(x):
    return isinstance(x, (int, float)) and math.isfinite(x) and x > 0


def without_comments(text):
    # Preserve quoted strings, including punctuation in recipe instructions.
    token = re.compile(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*.*?\*/', re.S)
    return token.sub(lambda m: m[0] if m[0].startswith('"') else
                     "\n" * m[0].count("\n"), text)


def call_at(text, start):
    """Return call name, top-level argument texts, and end position."""
    match = re.match(r"(\w+)\s*\(", text[start:])
    if not match:
        raise ValueError("Expected literal Kotlin call: " + text[start:start + 60])
    begin = pos = start + match.end()
    depth, quoted, escaped, args = 1, False, False, []
    while pos < len(text):
        char = text[pos]
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
            if depth == 0:
                if text[begin:pos].strip():
                    args.append(text[begin:pos].strip())
                return match[1], args, pos + 1
        elif char == "," and depth == 1:
            if text[begin:pos].strip():
                args.append(text[begin:pos].strip())
            begin = pos + 1
        pos += 1
    raise ValueError("Unclosed Kotlin call")


def full_call(text, expected):
    name, args, end = call_at(text, 0)
    if name not in expected or text[end:].strip():
        raise ValueError("Unsupported Kotlin expression: " + text[:100])
    return name, args


def literal(text):
    value = json.loads(text)
    if not isinstance(value, str) or "$" in value:
        raise ValueError("Expected non-interpolated literal")
    return value


def read_recipes():
    """Follow the actual listOf + Expansion.recipes graph, not a file glob."""
    recipes, sources, visited = [], {}, set()

    def visit(object_name):
        if object_name in visited:
            raise ValueError("Repeated/cyclic recipe expansion " + object_name)
        visited.add(object_name)
        path = LOCAL / (object_name + ".kt")
        sources[path.as_posix()] = sha(ROOT / path)
        text = without_comments((ROOT / path).read_text(encoding="utf-8"))
        declaration = re.search(r"\bval recipes(?:\s*:\s*List<BuiltInRecipe>)?\s*=\s*", text)
        if not declaration:
            raise ValueError("Unsupported recipes declaration in " + str(path))
        name, entries, end = call_at(text, declaration.end())
        if name != "listOf":
            raise ValueError("Recipes must be an explicit listOf")
        ingredient_calls = 0
        for entry in entries:
            helper, args = full_call(entry, {"r", "expandedRecipe"})
            ingredient_index = 4 if helper == "r" else 5
            _, ingredients = full_call(args[ingredient_index], {"listOf"})
            recipe = {"recipe_key": literal(args[0]), "recipe_name": literal(args[1]),
                      "source_file": path.as_posix(), "ingredients": []}
            for expr in ingredients:
                _, values = full_call(expr, {"i", "bedca"})
                if len(values) not in (2, 3) or not re.fullmatch(r"\d+", values[0]) \
                        or not re.fullmatch(r"\d+(?:\.\d+)?", values[1]):
                    raise ValueError("Nonliteral ingredient: " + expr)
                recipe["ingredients"].append({"food_id": int(values[0]),
                    "grams": float(values[1]),
                    "quantity_unit": literal(values[2]) if len(values) == 3 else "GRAM"})
                ingredient_calls += 1
            if not ingredients or len({i["food_id"] for i in recipe["ingredients"]}) != len(ingredients):
                raise ValueError("Empty/duplicate ingredient list: " + recipe["recipe_key"])
            recipes.append(recipe)
        # Independent call-count guard within the parsed initializer.
        initializer = text[declaration.end():end]
        if len(re.findall(r"\b(?:i|bedca)\s*\(", initializer)) != ingredient_calls:
            raise ValueError("Unaccounted ingredient calls")
        tail = text[end:]
        while (expansion := re.match(r"\s*\+\s*(\w+)\.recipes", tail)):
            visit(expansion[1])
            tail = tail[expansion.end():]
        if not re.match(r"\s*(?:private fun|}\s*$)", tail):
            raise ValueError("Unsupported recipe list transformation in " + str(path))

    visit("BuiltInRecipeLibrary")
    if len({r["recipe_key"] for r in recipes}) != len(recipes):
        raise ValueError("Duplicate recipe keys")
    # Audited helpers forward identity/grams without transformations.
    for filename in ("BuiltInRecipeLibrary.kt", "BuiltInRecipeExpansionSupport.kt"):
        path = LOCAL / filename
        text = (ROOT / path).read_text(encoding="utf-8")
        if "BuiltInIngredient(foodId, grams.toDouble(), unit)" not in text or \
                "ingredients = ingredients" not in text:
            raise ValueError("Recipe helper semantics changed; audit parser must be reviewed")
        sources[path.as_posix()] = sha(ROOT / path)
    test = ROOT / "app/src/test/java/com/zame/nutrinest/data/local/BuiltInRecipeLibraryTest.kt"
    expected = re.search(r"assertEquals\((\d+), recipes.size\)", test.read_text(encoding="utf-8"))
    if not expected or int(expected[1]) != len(recipes):
        raise ValueError("Extracted recipe count disagrees with the library test contract")
    sources[test.relative_to(ROOT).as_posix()] = sha(test)
    return sorted(recipes, key=lambda r: r["recipe_key"]), sources


def distribution(rows, fields):
    counts = Counter(tuple(r.get(k) for k in fields) for r in rows)
    return [dict(zip(fields, key), count=count)
            for key, count in sorted(counts.items(), key=lambda kv: stable(kv[0]))]


def stats(values):
    values = sorted(v for v in values if v is not None and math.isfinite(v))
    if not values:
        return {"count": 0}
    return {"count": len(values), "min": values[0], "median": statistics.median(values),
            "p10": values[int((len(values)-1)*.1)], "p90": values[int((len(values)-1)*.9)],
            "max": values[-1]}


def audit(asset):
    before = sha(asset)
    recipes, sources = read_recipes()
    for rel in [LOCAL / "FoodDatabaseImporter.kt", LOCAL / "BuiltInRecipeSeeder.kt",
                LOCAL / "entity/FoodDataEntities.kt", LOCAL / "entity/IngredientEntity.kt",
                LOCAL / "dao/IngredientDao.kt", Path("tools/build_food_asset.py"),
                Path("tools/ksql.py"), Path("tools/audit_food_catalog.py")]:
        sources[rel.as_posix()] = sha(ROOT / rel)
    with sqlite3.connect(asset.as_uri() + "?mode=ro", uri=True) as db:
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA query_only=ON")
        integrity = [r[0] for r in db.execute("PRAGMA integrity_check")]
        foreign_keys = [list(r) for r in db.execute("PRAGMA foreign_key_check")]
        schema = [dict(r) for r in db.execute(
            "SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name")]
        tables = {}
        for table in ("foods", "food_macros", "food_nutrients", "food_portions", "food_aliases",
                      "food_quality_issues", "food_categories", "shopping_aisles", "nutrient_defs",
                      "db_metadata"):
            tables[table] = sorted([dict(r) for r in db.execute('SELECT * FROM "' + table + '"')], key=stable)
        importer = (ROOT / LOCAL / "FoodDatabaseImporter.kt").read_text(encoding="utf-8")
        constants = {name: int(value.replace("_", "")) for name, value in
                     re.findall(r"const val (\w+) = ([\d_]+)L", importer)}
        steps = extract_copy_steps(importer, {"$" + k: str(v) for k, v in constants.items()})
        query = next(s["read"] for s in steps if s["label"] == "ingredients")
        if "$" in query:
            raise ValueError("Unresolved importer interpolation")
        importable = {r[0] - constants["IMPORTED_ID_OFFSET"] for r in db.execute(query, (0, 0))}
    after = sha(asset)
    if before != after:
        raise ValueError("Source hash changed during audit")
    if integrity != ["ok"] or foreign_keys:
        raise ValueError("Source integrity/FK failure: " + stable([integrity, foreign_keys]))

    foods = {f["id"]: f for f in tables["foods"]}
    macros = {m["food_id"]: m for m in tables["food_macros"]}
    categories = {c["id"]: c["key"] for c in tables["food_categories"]}
    nutrient_defs = {n["tag"]: n for n in tables["nutrient_defs"]}
    nutrients, portions, aliases, usage = (defaultdict(list) for _ in range(4))
    for table, target in (("food_nutrients", nutrients), ("food_portions", portions), ("food_aliases", aliases)):
        for row in tables[table]:
            target[row["food_id"]].append(row)
    recipe_links = []
    for recipe in recipes:
        for ingredient in recipe["ingredients"]:
            food_id = ingredient["food_id"]
            usage[food_id].append(recipe["recipe_key"])
            recipe_links.append({k: v for k, v in recipe.items() if k != "ingredients"} | ingredient)

    def identity(food_id):
        f = foods.get(food_id, {})
        return {"food_id": food_id, "source_key": "BEDCA:" + str(food_id),
            "source": f.get("source", "BEDCA"), "source_ref": f.get("source_ref"),
            "name": f.get("name_es", "AUSENTE"), "name_en": f.get("name_en"),
            "status": "ABSENT" if not f else "DEPRECATED" if f["is_deprecated"] else "ACTIVE",
            "importable": food_id in importable, "prep_state": f.get("prep_state"),
            "category": categories.get(f.get("category_id")),
            "recipe_count": len(set(usage.get(food_id, [])))}

    findings = []

    def finding(food_id, code, cause, recommendation, evidence=None, severity="REVIEW", related=()):
        findings.append(identity(food_id) | {"code": code, "severity": severity,
            "cause": cause, "recommendation": recommendation,
            "related_ids": sorted(related), "evidence": evidence or {}})

    for food_id, f in sorted(foods.items()):
        m = macros.get(food_id, {})
        panel = {n["tag"]: n for n in nutrients[food_id]}
        valid_portions = [p for p in portions[food_id] if positive(p["grams"])]
        f["has_countable_unit"] = any(p["portion_type"] == "UNIT" for p in valid_portions)
        f["has_household_measure"] = any(p["portion_type"] != "PORTION" for p in valid_portions)
        if not f["has_countable_unit"]:
            finding(food_id, "NO_COUNTABLE_UNIT", "No hay porción UNIT con gramos finitos positivos.",
                    "Conservar uso en gramos; no inferir peso de pieza ni envase.", severity="INFO")
        if not f["has_household_measure"]:
            finding(food_id, "NO_HOUSEHOLD_MEASURE", "Solo ración genérica o ninguna medida doméstica utilizable.",
                    "Solicitar medida y fuente si el producto necesita unidades domésticas.", severity="INFO")
        if not valid_portions:
            finding(food_id, "NO_VALID_PORTION", "Sin porción válida.", "Mantener gramos como entrada.")
        defaults = [p["id"] for p in portions[food_id] if p["is_default"]]
        if len(defaults) != 1:
            finding(food_id, "DEFAULT_PORTION_COUNT", "El importador toma un default con LIMIT 1.",
                    "Revisar ausencia/ambigüedad del default.", {"portion_ids": defaults})
        if f["is_deprecated"]:
            target = f["replaced_by_id"]
            finding(food_id, "EXISTING_DEPRECATION", "Obsolescencia declarada por el dataset, no por esta auditoría.",
                    "Conservar fila y trazabilidad; revisar equivalencia antes de migrar referencias.",
                    {"replaced_by_id": target}, "INFO", [target] if target else [])
        elif food_id not in importable:
            finding(food_id, "EXCLUDED_BY_IMPORTER", "Activo en asset pero ausente del SELECT real de importación.",
                    "Mantener exclusión de interfaz existente; conservar registro fuente.")
        if f["completeness_pct"] < THRESHOLDS["nutrient_coverage_low_pct"] or f["quality"] != "OK":
            finding(food_id, "LOW_COVERAGE_OR_QUALITY", "Flag del dataset o completitud <50%.",
                    "Revisar fuente y ausencias; no convertir faltantes en ceros.",
                    {"quality": f["quality"], "completeness_pct": f["completeness_pct"]})
        preparation_words = sorted(set(re.findall(
            r"\b(?:crudo|cruda|raw|cocido|cocida|cooked|hervido|hervida|boiled|frito|frita|fried|"
            r"asado|asada|roasted|tostado|tostada|toasted|escalfado|escalfada|poached|"
            r"desecado|desecada|dried|congelado|congelada|frozen|powder)\b",
            norm(f['name_es']+' '+(f['name_en'] or '')))))
        if f['prep_state'] == 'UNSPECIFIED' and preparation_words:
            finding(food_id, "PREPARATION_TEXT_UNSPECIFIED", "Nombre con preparación explícita y prep_state sin especificar.",
                    "Revisar clasificación; no inferir conversiones ni unir automáticamente estados.",
                    {"name_tokens": preparation_words})
        for column, expected in (("name_norm", norm(f["name_es"])),
                                 ("name_en_norm", norm(f["name_en"] or f["name_es"]))):
            if f[column] != expected:
                finding(food_id, "NORMALIZATION_MISMATCH", "Nombre normalizado no coincide con la regla actual.",
                        "Revisar índice de búsqueda sin cambiar identidad.", {"column": column, "stored": f[column], "expected": expected})
        density = f["density_g_ml"]
        if f["is_liquid"] and not positive(density):
            finding(food_id, "MISSING_LIQUID_DENSITY", "Líquido sin densidad válida.", "No inventar densidad; pedir fuente.")
        if density is not None and (not positive(density) or not .5 <= density <= 2):
            finding(food_id, "DENSITY_SCREEN", "Densidad fuera de banda exploratoria 0.5–2 g/ml.",
                    "Revisar unidad/temperatura/estado; la banda no prueba error.", {"density_g_ml": density})
        if not m:
            finding(food_id, "MISSING_MACROS", "Sin fila food_macros.", "Revisar importabilidad.", severity="HIGH")
            continue
        for key, value in m.items():
            if key != "food_id" and isinstance(value, (int, float)) and (not math.isfinite(value) or value < 0):
                finding(food_id, "INVALID_MACRO_VALUE", "Valor negativo o no finito.", "Revisar registro fuente.", {key: value}, "HIGH")
            if key.endswith("_g") and key != "mass_balance_g" and value is not None and value > 100.1:
                finding(food_id, "MASS_COMPONENT_OVER_100", "Componente >100 g por 100 g.", "Comprobar unidad y base.", {key: value}, "HIGH")
        if m["kcal"] > 1000:
            finding(food_id, "ENERGY_RANGE", "Energía >1000 kcal/100 g.", "Comprobar unidad y base.", {"kcal": m["kcal"]}, "HIGH")
        energy = panel.get("ENERC", {}).get("value")
        if energy is not None and not close(energy, m["kcal"], .1):
            matches_kj = close(energy, m["kj"], .1)
            finding(food_id, "ENERGY_PANEL_UNIT", "ENERC etiquetado " + nutrient_defs["ENERC"]["unit"] +
                    (" coincide numéricamente con kj, no con kcal." if matches_kj else " difiere de kcal del resumen."),
                    "Revisar unidad de definición y valor antes de mostrar energía; no corregir automáticamente.",
                    {"panel": energy, "macro_kcal": m["kcal"], "macro_kj": m["kj"], "matches_kj": matches_kj}, "HIGH")
        absent = [tag for tag in ("ENERC", "PROT", "FAT", "CHO", "FIBT", "ALC") if tag not in panel]
        if absent:
            finding(food_id, "MISSING_SOURCE_NUTRIENTS", "Panel fuente sin algunos campos principales; resumen puede derivarlos o usar cero.",
                    "Distinguir ausente, cero lógico y dato derivado.", {"missing_tags": absent, "energy_source": m["energy_source"]})
        calculated = 4*m["protein_g"] + 9*m["fat_g"] + 4*m["carbs_g"] + 2*m["fiber_g"] + 7*m["alcohol_g"]
        if abs(calculated-m["kcal"]) > max(20, .2*max(m["kcal"], calculated)):
            finding(food_id, "ENERGY_SCREEN", "Diferencia >20 kcal y >20% frente a estimación 4/9/4/2/7.",
                    "Revisar componentes/factores omitidos; cribado, no corrección nutricional.",
                    {"kcal": m["kcal"], "estimate": round(calculated, 6)})
        balance = m.get("mass_balance_g")
        if balance is not None and not 95 <= balance <= 105:
            finding(food_id, "MASS_BALANCE_SCREEN", "Balance almacenado fuera de 95–105 g.",
                    "Revisar base y componentes; la suma puede omitir cenizas u otros constituyentes.", {"mass_balance_g": balance})
        for tag, column in (("FASAT", "sat_fat_g"), ("FAMS", "mono_fat_g"),
                            ("FAPU", "poly_fat_g"), ("SUGAR", "sugars_g"), ("STARCH", "starch_g"), ("CHO", "carbs_g")):
            value = panel.get(tag, {}).get("value")
            if value is not None and m[column] is not None and not close(value, m[column]):
                finding(food_id, "PANEL_SUMMARY_DIVERGENCE", "Panel fuente y resumen usan cifras distintas.",
                        "Revisar clamp o base de carbohidrato; no intercambiar ambos valores sin procedencia.",
                        {"tag": tag, "panel": value, "column": column, "summary": m[column]})
        for tag, parent in (("FASAT", "FAT"), ("FAMS", "FAT"), ("FAPU", "FAT"), ("SUGAR", "CHO"), ("STARCH", "CHO")):
            if tag in panel and parent in panel and panel[tag]["value"] > panel[parent]["value"] + .01:
                finding(food_id, "SOURCE_CHILD_EXCEEDS_PARENT", "Subcomponente fuente mayor que su total.",
                        "Revisión humana del panel original; un clamp del resumen no lo resuelve.",
                        {"tag": tag, "value": panel[tag]["value"], "parent": parent, "total": panel[parent]["value"]}, "HIGH")
        fats = [m[k] for k in ("sat_fat_g", "mono_fat_g", "poly_fat_g")]
        if all(v is not None for v in fats) and sum(fats) > m["fat_g"] + max(.1, .05*m["fat_g"]):
            finding(food_id, "FAT_SUM_EXCEEDS_TOTAL", "Suma saturada/mono/poli del resumen supera grasa total con tolerancia.",
                    "Revisar coherencia conjunta después de clamps individuales.", {"sum_g": round(sum(fats), 6), "fat_g": m["fat_g"]})
        for n in nutrients[food_id]:
            if n["value"] < 0 or not math.isfinite(n["value"]):
                finding(food_id, "INVALID_NUTRIENT", "Nutriente negativo/no finito.", "Revisar fuente.", n, "HIGH")
        for p in portions[food_id]:
            checked_labels = set()
            if not positive(p["grams"]) or not .1 <= p["grams"] <= 2000:
                finding(food_id, "PORTION_RANGE", "Masa de porción fuera de banda exploratoria 0.1–2000 g.",
                        "Revisar unidad/envase; no sustituir por una masa estimada.", p)
            for label_key in ("label_es", "label_en"):
                for amount, unit in re.findall(r"(\d+(?:[.,]\d+)?)\s*(kg|g|ml|cl|l)\b", p[label_key], re.I):
                    amount = float(amount.replace(",", ".")); unit = unit.lower()
                    if (amount, unit) in checked_labels:
                        continue
                    checked_labels.add((amount, unit))
                    expected = amount * (1000 if unit == "kg" else 1) if unit in {"g", "kg"} else None
                    if unit in {"ml", "cl", "l"} and positive(density):
                        expected = amount * {"ml": 1, "cl": 10, "l": 1000}[unit] * density
                    if expected is not None and abs(expected-p["grams"]) > max(1, .05*expected):
                        finding(food_id, "PORTION_LABEL_MISMATCH", "Masa de porción no concuerda con cantidad explícita de etiqueta/densidad.",
                                "Verificar significado y redondeo de etiqueta.", {"portion": p, "expected_from_label": expected})
            if p["portion_type"] == "UNIT" and re.search(r"desecad|deshidrat|polvo|dried|powder", norm(f["name_es"]+' '+(f['name_en'] or ''))):
                finding(food_id, "DRIED_UNIT_REVIEW", "Alimento seco/polvo con medida de unidad posiblemente heredada del fresco.",
                        "Validar que gramos corresponden al estado seco, no a unidad fresca ni peso con cáscara.", p)
    for q in tables["food_quality_issues"]:
        finding(q["food_id"], "EXISTING_" + q["code"], "Incidencia preexistente: " + (q["detail"] or q["code"]),
                "Conservar trazabilidad; comprobar si sigue vigente o describe transformación previa.", q,
                "HIGH" if q["severity"] == "ERROR" else "REVIEW" if q["severity"] == "WARN" else "INFO")

    # Candidate generation uses exact lexical keys, base attributes and complete
    # nutrient signatures; classification below always checks non-text evidence.
    pair_reasons = defaultdict(set)
    collisions = []

    def group_candidates(kind, groups):
        for key, ids in sorted(groups.items(), key=lambda kv: stable(kv[0])):
            ids = sorted(set(ids))
            if len(ids) < 2:
                continue
            collisions.append({"kind": kind, "key": key, "food_ids": ids})
            for a, b in itertools.combinations(ids, 2):
                pair_reasons[a, b].add(kind)

    for field in ("name_norm", "name_en_norm", "base_name_norm", "langual"):
        groups = defaultdict(list)
        for f in foods.values():
            if f[field]:
                key = (f["category_id"], f[field]) if field in {"base_name_norm", "langual"} else f[field]
                groups[key].append(f["id"])
        group_candidates(field, groups)
    groups = defaultdict(list)
    for a in tables["food_aliases"]:
        if a["alias_norm"]:
            groups[(a["lang"], a["alias_norm"])].append(a["food_id"])
    group_candidates("alias", groups)
    signatures = {}
    groups = defaultdict(list)
    for food_id, f in foods.items():
        signature = stable([[n[k] for k in ("tag", "value", "value_type", "is_logical_zero")]
                           for n in sorted(nutrients[food_id], key=lambda n: n["tag"])])
        signatures[food_id] = signature
        if len(nutrients[food_id]) >= 10:
            groups[stable([*[f[k] for k in ATTR_KEYS], signature])].append(food_id)
        if f["replaced_by_id"] in foods:
            pair_reasons[tuple(sorted((food_id, f["replaced_by_id"])))].add("replaced_by_id")
    group_candidates("exact_attributes_and_panel", groups)
    pairs = []
    for (a, b), reasons in sorted(pair_reasons.items()):
        fa, fb, ma, mb = foods[a], foods[b], macros.get(a, {}), macros.get(b, {})
        attrs_equal = all(fa[k] == fb[k] for k in ATTR_KEYS)
        deltas = {k: round(abs(ma[k]-mb[k]), 6) for k in MACRO_KEYS if k in ma and k in mb}
        macro_equal = len(deltas) == len(MACRO_KEYS) and all(v <= 1e-6 for v in deltas.values())
        panel_equal = signatures[a] == signatures[b] and len(nutrients[a]) >= 10
        # Full quantitative summary incl. null coverage, not merely 4 macros.
        summary_equal = {k:v for k,v in ma.items() if k != 'food_id'} == {k:v for k,v in mb.items() if k != 'food_id'}
        provenance_equal = bool(fa["langual"] and fa["source_ref"]) and all(fa[k] == fb[k] for k in ("source", "source_ref", "langual"))
        strong_name = bool(reasons & {"name_norm", "name_en_norm"})
        if "replaced_by_id" in reasons:
            classification = "DECLARED_REPLACEMENT"
        elif attrs_equal and macro_equal and panel_equal and summary_equal and provenance_equal and strong_name:
            classification = "EXACT_RECORD_DUPLICATE"
        elif fa["prep_state"] != fb["prep_state"] or fa["category_id"] != fb["category_id"] or not macro_equal:
            classification = "DISTINCT_OR_NUTRITIONAL_VARIANT"
        else:
            classification = "AMBIGUOUS_IDENTITY"
        recommendation = ("Candidato a consolidación lógica tras validar identidad; preservar ambos IDs y referencias."
            if classification == "EXACT_RECORD_DUPLICATE" else
            "Conservar ambas filas; reemplazo declarado no demuestra equivalencia nutricional."
            if classification == "DECLARED_REPLACEMENT" else
            "Conservar variante/composición separada; no fusionar por texto."
            if classification == "DISTINCT_OR_NUTRITIONAL_VARIANT" else
            "Revisión humana de identidad, cobertura y procedencia; conservar mientras tanto.")
        evidence = {"triggers": sorted(reasons), "attributes_equal": attrs_equal,
                    "primary_macros_equal": macro_equal, "full_summary_equal": summary_equal,
                    "panel_equal": panel_equal, "provenance_equal": provenance_equal, "macro_abs_deltas": deltas}
        pair_id = f"PAIR:{a}:{b}"
        pairs.append({"pair_id": pair_id, "a": identity(a), "b": identity(b),
                      "classification": classification, "evidence": evidence, "recommendation": recommendation})
        for food_id, other in ((a,b), (b,a)):
            finding(food_id, classification, "Candidato comparado con atributos y composición, además de claves textuales.",
                    recommendation, {"pair_id": pair_id, **evidence},
                    "INFO" if classification == "DISTINCT_OR_NUTRITIONAL_VARIANT" else "REVIEW", [other])

    raw_cooked = []
    bases = defaultdict(list)
    for f in foods.values():
        bases[(f["category_id"], f["base_name_norm"])].append(f["id"])
    for (category, base), ids in sorted(bases.items()):
        raw = [i for i in ids if foods[i]["prep_state"] == "RAW"]
        cooked = [i for i in ids if foods[i]["prep_state"] in COOKED]
        if raw and cooked:
            members = sorted(raw+cooked)
            raw_cooked.append({"group_id": "RAW_COOKED:"+str(category)+":"+base,
                "base_name_norm": base, "category": categories[category],
                "raw_ids": sorted(raw), "cooked_ids": sorted(cooked),
                "members": [identity(i) | {"macros": macros.get(i)} for i in members],
                "cause": "Misma base normalizada y categoría con estados RAW y térmicos explícitos.",
                "recommendation": "Conservar estados distintos. No inferir rendimiento ni conversión crudo/cocido."})
    recipe_coverage = []
    for food_id in sorted(usage):
        status = "ABSENT" if food_id not in foods else "IMPORTABLE" if food_id in importable else "PRESENT_NOT_IMPORTABLE"
        recipe_coverage.append(identity(food_id) | {"coverage_status": status, "recipes": sorted(set(usage[food_id]))})
        if status != "IMPORTABLE":
            finding(food_id, "RECIPE_REFERENCE_UNAVAILABLE", "ID requerido por biblioteca: " + status,
                    "Revisar fuente y referencias; no sustituir silenciosamente.", {"recipes": sorted(set(usage[food_id]))}, "HIGH")
    # Broad aliases are navigation aids, not identity edges. Cross-category
    # collisions are individually traceable even when nutrient differences exist.
    for group in collisions:
        if group["kind"] == "alias" and len({foods[i]["category_id"] for i in group["food_ids"]}) > 1:
            for i in group["food_ids"]:
                finding(i, "ALIAS_CROSS_CATEGORY", "Alias compartido entre categorías distintas.",
                        "Revisar alcance del alias; no tratarlo como equivalencia alimentaria.",
                        {"alias_key": group["key"]}, related=[j for j in group["food_ids"] if j != i])

    findings.sort(key=lambda x: (x["food_id"], x["code"], stable(x["evidence"]), stable(x["related_ids"])))
    for index, row in enumerate(findings, 1):
        row["finding_id"] = f"F{index:06d}"
    by_food = defaultdict(list)
    for row in findings:
        by_food[row["food_id"]].append(row)
    catalog = []
    for food_id, f in sorted(foods.items()):
        codes = sorted({r["code"] for r in by_food[food_id]})
        recommendation = "CONSERVAR"
        if f["is_deprecated"] or food_id not in importable:
            recommendation = "CONSERVAR_OCULTACION_EXISTENTE"
        elif "EXACT_RECORD_DUPLICATE" in codes:
            recommendation = "REVISAR_CONSOLIDACION_LOGICA"
        elif any(r["severity"] in {"HIGH", "REVIEW"} for r in by_food[food_id]):
            recommendation = "CONSERVAR_Y_REVISAR"
        catalog.append(identity(food_id) | {"quality": f["quality"], "completeness_pct": f["completeness_pct"],
            "density_g_ml": f["density_g_ml"], "has_countable_unit": f["has_countable_unit"],
            "has_household_measure": f["has_household_measure"], "portion_count": len(portions[food_id]),
            "nutrient_count": len(nutrients[food_id]), "missing_nutrient_tags": sorted(set(nutrient_defs)-{n['tag'] for n in nutrients[food_id]}),
            "logical_zero_tags": sorted(n['tag'] for n in nutrients[food_id] if n['is_logical_zero']),
            "recipes": sorted(set(usage.get(food_id, []))), "finding_codes": codes,
            "cause": "Clasificación conservadora según evidencias enlazadas; no usado en biblioteca no significa inútil.",
            "recommendation": recommendation})
    distributions = {}
    for scope, rows in (("all", list(foods.values())), ("active", [f for f in foods.values() if not f['is_deprecated']]),
                        ("importable", [foods[i] for i in sorted(importable)])):
        labeled = [f | {"category": categories[f['category_id']]} for f in rows]
        distributions[scope] = {"category": distribution(labeled, ["category"]),
            "prep_state": distribution(rows, ["prep_state"]), "category_prep": distribution(labeled, ["category", "prep_state"]),
            "quality": distribution(rows, ["quality"]), "density_values": distribution(rows, ["is_liquid", "density_g_ml"]),
            "density_stats": stats([f['density_g_ml'] for f in rows]),
            "completeness_stats": stats([f['completeness_pct'] for f in rows])}
        distributions[scope]['no_countable_unit'] = sum(not f['has_countable_unit'] for f in rows)
        distributions[scope]['no_household_measure'] = sum(not f['has_household_measure'] for f in rows)
        scope_ids = {f['id'] for f in rows}
        distributions[scope]['collision_group_counts'] = dict(sorted(Counter(
            g['kind'] for g in collisions if len(scope_ids.intersection(g['food_ids'])) > 1).items()))
    portion_export = [identity(p["food_id"]) | p for p in sorted(tables["food_portions"], key=lambda p:p["id"])]
    for row in recipe_links:
        row.update(identity(row["food_id"]))
        row["coverage_status"] = "ABSENT" if row["food_id"] not in foods else "IMPORTABLE" if row["food_id"] in importable else "PRESENT_NOT_IMPORTABLE"
    summary = {"foods": len(foods), "active": sum(not f['is_deprecated'] for f in foods.values()),
        "deprecated": sum(bool(f['is_deprecated']) for f in foods.values()), "importable": len(importable),
        "recipes": len(recipes), "recipe_links": len(recipe_links), "recipe_unique_food_ids": len(usage),
        "recipe_absent_ids": [r['food_id'] for r in recipe_coverage if r['coverage_status']=='ABSENT'],
        "recipe_not_importable_ids": [r['food_id'] for r in recipe_coverage if r['coverage_status']=='PRESENT_NOT_IMPORTABLE'],
        "pair_classifications": dict(sorted(Counter(p['classification'] for p in pairs).items())),
        "exact_record_duplicate_pairs": sum(p['classification']=='EXACT_RECORD_DUPLICATE' for p in pairs),
        "collision_group_counts": dict(sorted(Counter(g['kind'] for g in collisions).items())),
        "declared_replacements_with_macro_differences": sum(p['classification']=='DECLARED_REPLACEMENT' and not p['evidence']['primary_macros_equal'] for p in pairs),
        "raw_cooked_groups": len(raw_cooked), "findings": len(findings),
        "finding_counts": dict(sorted(Counter(f['code'] for f in findings).items())),
        "finding_food_counts": {code:len({f['food_id'] for f in findings if f['code']==code}) for code in sorted({f['code'] for f in findings})},
        "finding_used_food_counts": {code:len({f['food_id'] for f in findings if f['code']==code and f['recipe_count']}) for code in sorted({f['code'] for f in findings})},
        "no_countable_unit": sum(not f['has_countable_unit'] for f in foods.values()),
        "no_household_measure": sum(not f['has_household_measure'] for f in foods.values()),
        "recommendations": dict(sorted(Counter(f['recommendation'] for f in catalog).items())),
        "existing_issue_count": len(tables['food_quality_issues']),
        "existing_issue_distribution": distribution(tables['food_quality_issues'], ['code','severity']),
        "portion_types": distribution(tables['food_portions'], ['portion_type']),
        "portion_sources": distribution(tables['food_portions'], ['source']),
        "portion_grams": stats([p['grams'] for p in tables['food_portions']]),
        "energy_sources": distribution(tables['food_macros'], ['energy_source']),
        "table_counts": {k:len(v) for k,v in sorted(tables.items())}}
    return {"audit_version": 1, "source_sha256_before": before, "source_sha256_after": after,
        "source_bytes": asset.stat().st_size, "input_sha256": dict(sorted(sources.items())),
        "metadata": {r['key']:r['value'] for r in tables['db_metadata']}, "schema": schema,
        "integrity_check": integrity, "foreign_key_check": foreign_keys, "thresholds": THRESHOLDS,
        "summary": summary, "distributions": distributions, "foods": catalog, "findings": findings,
        "pairs": pairs, "collision_groups": collisions, "raw_cooked_groups": raw_cooked,
        "recipe_coverage": recipe_coverage, "recipe_links": recipe_links, "portions": portion_export}


def write_csv(path, rows, fields):
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore", lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({k: stable(v) if isinstance(v, (list, dict, tuple)) else v for k,v in row.items() if k in fields})


def markdown(report):
    s, d = report['summary'], report['distributions']
    lines = ["# 02 — Auditoría del catálogo alimentario", "",
        "Informe generado por `tools/audit_food_catalog.py`. Solo lectura; ninguna clasificación autoriza eliminación o modificación de datos.", "",
        f"Asset SHA-256 antes y después: `{report['source_sha256_before']}`. Tamaño: {report['source_bytes']} bytes. Dataset: `{report['metadata'].get('dataset_version')}`.", "",
        "## Resultados", "", "| Indicador | Valor |", "| --- | --- |"]
    for key in ('foods','active','deprecated','importable','recipes','recipe_links','recipe_unique_food_ids',
                'recipe_absent_ids','recipe_not_importable_ids','raw_cooked_groups','no_countable_unit','no_household_measure','existing_issue_count','findings'):
        lines.append(f"| {key} | {s[key]} |")
    lines += ["", "## Distribuciones", "", "Categorías: total / activos / importables. Activo significa is_deprecated=0; importable usa el SELECT real del importador con sus JOIN y exclusión, sin ejecutar INSERT.", "",
              "| Categoría | Total | Activos | Importables |", "| --- | --- | --- | --- |"]
    maps = [{r['category']:r['count'] for r in d[scope]['category']} for scope in ('all','active','importable')]
    for category in sorted(maps[0]):
        lines.append(f"| {category} | {maps[0].get(category,0)} | {maps[1].get(category,0)} | {maps[2].get(category,0)} |")
    lines += ["", "| prep_state | Total | Activos | Importables |", "| --- | --- | --- | --- |"]
    maps = [{r['prep_state']:r['count'] for r in d[scope]['prep_state']} for scope in ('all','active','importable')]
    for state in sorted(maps[0]):
        lines.append(f"| {state} | {maps[0].get(state,0)} | {maps[1].get(state,0)} | {maps[2].get(state,0)} |")
    lines += ["", "El cruce completo categoría × preparación y distribuciones de calidad, completitud y densidades están en JSON (all/active/importable).", "",
        "### Densidades y medidas", "", f"Densidades presentes: `{stable(d['all']['density_stats'])}`. Gramos de porciones: `{stable(s['portion_grams'])}`.", "",
        "| Tipo de porción | Filas |", "| --- | --- |"]
    lines += [f"| {r['portion_type']} | {r['count']} |" for r in s['portion_types']]
    lines += ["", f"Fuentes de porciones: `{stable(s['portion_sources'])}`.", "",
        f"Entre los importables, {d['importable']['no_countable_unit']} no tienen UNIT y {d['importable']['no_household_measure']} no tienen medida doméstica no genérica. Sin ninguna porción válida: {sum(f['code']=='NO_VALID_PORTION' for f in report['findings'])} alimentos; importables afectados: {sum(f['code']=='NO_VALID_PORTION' and f['importable'] for f in report['findings'])}.", "",
        "UNIT significa pieza/unidad según catálogo (incluye envases). Ausencia de UNIT no es error: aceite o harina pueden usarse por masa o volumen. Medida doméstica significa cualquier tipo válido distinto de PORTION; una ración genérica no acredita peso de pieza, neto comercial, escurrido ni fracción comestible. No se inventan densidades, rendimientos o pesos. Las densidades no tienen procedencia individual/temperatura en el esquema; las porciones son adiciones NutriNest según metadata, no mediciones BEDCA acreditadas.", "",
        "## Duplicados, variantes y preparación", "",
        "Candidatos por igualdad de nombre ES/EN normalizado, alias por idioma, base+categoría, LanguaL+categoría y panel completo+atributos; también replaced_by_id. Se evalúan categoría, preparación, estado líquido/densidad, seis macros, resumen completo (incluidos NULL), panel con valor/tipo/cero lógico, fuente y LanguaL. No se usa distancia textual como decisión.", "",
        "| Clasificación de pares | Cantidad |", "| --- | --- |"]
    lines += [f"| {k} | {v} |" for k,v in s['pair_classifications'].items()]
    lines += ["", f"Duplicados exactos demostrados con el criterio estricto: **{s['exact_record_duplicate_pairs']}**. De los {s['pair_classifications'].get('DECLARED_REPLACEMENT',0)} reemplazos ya declarados en este asset, {s['declared_replacements_with_macro_differences']} tienen alguna diferencia en los seis macros comparados: no son intercambiables sin decidir qué composición conservar.", "",
              "| Origen del grupo candidato | Grupos (pueden solaparse) |", "| --- | --- |"]
    lines += [f"| {k} | {v} |" for k,v in s['collision_group_counts'].items()]
    lines += ["", f"Grupos con al menos dos alimentos importables: `{stable(d['importable']['collision_group_counts'])}`. Los recuentos anteriores incluyen obsoletos; un grupo puede aparecer bajo varias claves y no equivale a un duplicado confirmado."]
    lines += ["", "EXACT_RECORD_DUPLICATE exige igualdad cuantitativa completa, cobertura >=10 nutrientes, atributos, procedencia y nombre ES o EN exacto. Es duplicación del registro representado, no prueba de identidad biológica/comercial. DECLARED_REPLACEMENT refleja una decisión anterior del dataset, no la revalida. DISTINCT_OR_NUTRITIONAL_VARIANT conserva filas con preparación/categoría/macros diferentes; AMBIGUOUS_IDENTITY exige revisión. Diferencias mínimas pueden ser redondeo y producir candidatos conservadores, nunca fusión automática.", "",
        "Los grupos crudo/cocido exigen misma base normalizada y categoría, RAW y al menos un estado térmico explícito. No incluyen UNSPECIFIED como crudo, ni consideran CANNED/FROZEN equivalentes a cocción. No son pares de muestras equivalentes ni permiten deducir rendimiento a partir de kcal o agua.", "",
        "## Hallazgos y revisión", "", "Cada fila de findings.csv/JSON contiene finding_id, food_id, fuente, referencia, nombre, estado, preparación, causa, recomendación y evidencia; los pares identifican ambos extremos. Códigos y recuentos (hallazgos, no alimentos únicos):", "",
        "| Código | Hallazgos | Alimentos únicos | Usados en recetas |", "| --- | --- | --- | --- |"]
    lines += [f"| {k} | {v} | {s['finding_food_counts'][k]} | {s['finding_used_food_counts'][k]} |" for k,v in s['finding_counts'].items()]
    lines += ["", "### Casos ilustrativos trazables", "", "| ID | Fuente / nombre / estado | Causa | Recomendación |", "| --- | --- | --- | --- |"]
    examples = []
    for code in ('ENERGY_PANEL_UNIT','SOURCE_CHILD_EXCEEDS_PARENT','PANEL_SUMMARY_DIVERGENCE','DRIED_UNIT_REVIEW','ALIAS_CROSS_CATEGORY','EXCLUDED_BY_IMPORTER','LOW_COVERAGE_OR_QUALITY'):
        matches = [f for f in report['findings'] if f['code']==code]
        examples.extend(matches[:2])
    for f in examples:
        clean = lambda x: str(x).replace('|','/').replace('\n',' ')
        lines.append(f"| {f['finding_id']} / BEDCA:{f['food_id']} | {clean(f['source_ref'])}: {clean(f['name'])}; {f['status']}/{f['prep_state']} | {clean(f['cause'])} | {clean(f['recommendation'])} |")
    lines += ["", "### Pares que evitan falsos positivos", "", "| Par | Nombres | Clasificación y evidencia |", "| --- | --- | --- |"]
    for p in report['pairs']:
        if p['classification']=='AMBIGUOUS_IDENTITY' or (p['a']['food_id'],p['b']['food_id']) in {(71,540),(481,2423),(788,1267),(861,1282),(1216,2132)}:
            lines.append(f"| {p['pair_id']} | {p['a']['name']} / {p['b']['name']} | {p['classification']}; diferencias absolutas por 100 g: `{stable(p['evidence']['macro_abs_deltas'])}`; panel igual: {p['evidence']['panel_equal']} |")
    lines += ["", "### Calidad nutricional: interpretación", "",
        f"La discrepancia ENERGY_PANEL_UNIT afecta a {s['finding_food_counts'].get('ENERGY_PANEL_UNIT',0)} alimentos ({s['finding_used_food_counts'].get('ENERGY_PANEL_UNIT',0)} usados por recetas). En {sum(f['code']=='ENERGY_PANEL_UNIT' and f['evidence']['matches_kj'] for f in report['findings'])} coincide con kj. No se ha aplicado conversión alguna.", "",
        "Se contrastan panel fuente y food_macros por separado. ENERC se compara con kcal y kj: coincidencia con kj bajo definición kcal se reporta como inconsistencia interna observable, sin asumir que todos los valores requieran una misma conversión. El importador copia ambos conjuntos sin reconciliarlos. CHO puede usar una base diferente; clamps en macros no alteran necesariamente food_nutrients. Las incidencias EXISTING_* pueden describir correcciones históricas y se conservan separadas del cribado actual.", "",
        "Se distinguen ausencias de tags, NULL y ceros lógicos; completeness_pct es metadata heredada, no porcentaje recalculado de 81 tags. La cobertura completa por alimento está en foods.csv. El cribado 4/9/4/2/7 considera proteína/grasa/carbohidratos/fibra/alcohol; diferencia >20 kcal y >20% es motivo de revisión, no diagnóstico. Puede omitir ácidos orgánicos, polialcoholes y factores específicos. Balance 95–105 g, densidad 0.5–2 y porción 0.1–2000 g son bandas explícitas de auditoría, no límites clínicos ni prohibiciones. No se suman indiscriminadamente nutrientes solapados ni se penaliza un cero por sí mismo.", "",
        "## Uso en recetas y decisiones propuestas", "",
        "El parser sigue únicamente listOf de BuiltInRecipeLibrary y las expansiones .recipes alcanzables. Acepta llamadas literales r/expandedRecipe e i/bedca, verifica helpers, rechaza expresiones desconocidas, cuenta llamadas independientemente y contrasta el total con BuiltInRecipeLibraryTest. No ejecuta Kotlin; cualquier cambio del DSL exige revisar el extractor. recipe_usage.csv conserva clave/nombre/archivo de receta, ID, gramos y unidad; recipe_coverage en JSON enumera cada ID requerido, incluidos ausentes/no importables.", "",
        "| Recomendación principal por alimento | Cantidad |", "| --- | --- |"]
    lines += [f"| {k} | {v} |" for k,v in s['recommendations'].items()]
    lines += ["", "Consolidación solo lógica y pendiente de evidencia; no se selecciona automáticamente un ID ganador. Ocultación solo conserva la política existente (deprecated/excluido por importador); no se recomienda ocultar alimentos por no aparecer en las 250 recetas, por ser poco comunes o por categoría. Sin telemetría ni inventario del usuario no puede medirse utilidad real. Todo alimento permanece conservado y exportado.", "",
        "## Reproducción y garantías", "", "```powershell", "python tools/audit_food_catalog.py",
        "python tools/audit_food_catalog.py --output-dir build/food-audit-repeat", "```", "",
        "Python >=3.10, biblioteca estándar y tools/ksql.py. Conexión URI mode=ro, query_only=ON; PRAGMA integrity_check y foreign_key_check; SHA-256 antes/después. Los resultados no contienen reloj, rutas absolutas ni identificadores aleatorios. Orden explícito, UTF-8, LF, JSON ordenado y manifest de hashes de salidas. Comprobar igualdad binaria de los archivos indicados por exports/manifest.json entre ambas carpetas. Las huellas de scripts/Kotlin se incluyen como procedencia; cambios en esas entradas cambian el informe aunque el asset sea igual. Código 0 indica auditoría completada, no ausencia de hallazgos; código 1 indica error de integridad/lectura/DSL/salida.", "",
        "## Archivos de revisión", "", "- `02-food-audit.md`: este informe reproducible.",
        "- `02-verification.json`: evidencia de las dos ejecuciones y comprobaciones de esta fase; se registra por separado de las salidas deterministas del script.",
        "- `exports/02-food-audit.json`: schema real, entradas/hashes, distribuciones, catálogo y evidencias completas.",
        f"- `exports/foods.csv`: {s['foods']} registros, cobertura, uso, medidas y recomendación principal.",
        "- `exports/human_review.csv`: alimentos con hallazgos HIGH/REVIEW, códigos y uso en recetas; prioridades clínicas no inferidas.",
        "- `exports/findings.csv`: hallazgos individuales, incluidos motivos de conservación.",
        "- `exports/duplicate_pairs.csv`: comparación de ambos alimentos y evidencia cuantitativa.",
        "- `exports/raw_cooked.csv`: grupos de preparación y IDs; no factores de conversión.",
        "- `exports/recipe_usage.csv`: todos los enlaces de la biblioteca.",
        "- `exports/portions.csv`: medidas y procedencia originales, con identidad del alimento.",
        "- `exports/manifest.json`: SHA-256 de cada salida (excepto del propio manifest).", "",
        "## Límites y decisiones para fase 03", "",
        "No se ha contrastado cada estudio fuente con BEDCA externo ni se ha certificado el significado de todos los códigos LanguaL/value_type. Se audita el asset derivado entregado: el origen crudo no está en Git. Traducciones, aliases genéricos, etiquetas de piezas secas y pares con diferencias por redondeo requieren revisión humana; compartir LanguaL no implica idéntica composición. No se han ejecutado Android/Room ni modificado catálogos, recetas, identificadores o macros.", "",
        "Fase 03 debe acordar identidad canónica frente a variantes, política de aliases y traducciones, contrato energético/CHO entre panel y resumen, procedencia y estado comestible de las porciones, y preservación de IDs/referencias históricas. Revisar primero HIGH y casos usados en recetas, antes de cualquier consolidación. El listado completo evita que un ejemplo favorable o una coincidencia textual se convierta en decisión global.", "",
        "Checkpoint recomendado, no creado: `audit: classify existing food catalog`.", ""]
    return '\n'.join(lines)


def export(report, output, asset):
    output = output.resolve()
    if output == asset.parent or asset.parent in output.parents:
        raise ValueError("Output must not be inside the source asset directory")
    exports = output / "exports"
    names = ['02-food-audit.md', *['exports/'+n for n in ('02-food-audit.json','foods.csv','findings.csv',
             'duplicate_pairs.csv','raw_cooked.csv','recipe_usage.csv','portions.csv','human_review.csv','manifest.json')]]
    for name in names:
        target = output / name
        if target.resolve() == asset or (target.exists() and target.samefile(asset)) or target.is_symlink() or not target.resolve().is_relative_to(output):
            raise ValueError("Unsafe output path " + str(target))
    exports.mkdir(parents=True, exist_ok=True)
    (output / '02-food-audit.md').write_text(markdown(report), encoding='utf-8', newline='\n')
    (exports / '02-food-audit.json').write_text(json.dumps(report, ensure_ascii=False, sort_keys=True,
        indent=2, allow_nan=False)+'\n', encoding='utf-8', newline='\n')
    for key, name in [('foods','foods.csv'), ('findings','findings.csv'), ('portions','portions.csv'), ('recipe_links','recipe_usage.csv')]:
        rows = report[key]
        fields = list(dict.fromkeys(k for row in rows for k in row)) or ['food_id']
        write_csv(exports/name, rows, fields)
    review_ids = {f['food_id'] for f in report['findings'] if f['severity'] in {'HIGH','REVIEW'}}
    review = [f for f in report['foods'] if f['food_id'] in review_ids]
    write_csv(exports/'human_review.csv', review, list(report['foods'][0]))
    pairs = []
    for p in report['pairs']:
        pairs.append({'pair_id':p['pair_id'], **{'a_'+k:v for k,v in p['a'].items()},
            **{'b_'+k:v for k,v in p['b'].items()}, 'classification':p['classification'],
            'cause':'Cruce de claves, atributos, composición y procedencia.',
            'evidence':p['evidence'], 'recommendation':p['recommendation']})
    write_csv(exports/'duplicate_pairs.csv', pairs, list(pairs[0]) if pairs else ['pair_id'])
    groups = report['raw_cooked_groups']
    write_csv(exports/'raw_cooked.csv', groups, list(groups[0]) if groups else ['group_id'])
    manifest = {name:sha(output/name) for name in sorted(names) if name != 'exports/manifest.json'}
    (exports/'manifest.json').write_text(json.dumps(manifest, indent=2, sort_keys=True)+'\n', encoding='utf-8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--asset', type=Path, default=DEFAULT_ASSET)
    parser.add_argument('--output-dir', type=Path, default=ROOT/'docs/catalog')
    args = parser.parse_args()
    asset = args.asset.resolve(strict=True)
    original = sha(asset)
    # This audit targets a standalone shipped asset, never a live WAL database.
    for suffix in ('-wal', '-journal'):
        if Path(str(asset)+suffix).exists():
            raise ValueError("Refusing live/sidecar database " + str(asset))
    try:
        report = audit(asset)
        export(report, args.output_dir, asset)
    finally:
        if sha(asset) != original:
            raise RuntimeError('SOURCE HASH CHANGED')
    print(stable(report['summary']))
    print('Source SHA-256 unchanged: ' + original)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, sqlite3.Error, KeyError, StopIteration) as error:
        print('Audit failed: ' + str(error), file=sys.stderr)
        sys.exit(1)
