#!/usr/bin/env python3
"""Compile conservative, versioned editorial rules; never write the BEDCA database.

Classification uses explicit source IDs and declared preparation/category. Names
are labels, never evidence for merging. Every source record keeps its singleton.
"""
import hashlib
import json
import sqlite3
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / 'app/src/main/assets/nutrinest_foods.db'
OUTPUT = ROOT / 'app/src/main/assets/catalog-curation-v1.json'
VERSION = 'practical-catalog-v1'
EXPECTED_SOURCE = '66682591bb5ae18bd53f7b0d9f720718e3dd42abe81fc08a2d90e2141da90359'
THERMAL = {'BOILED', 'COOKED', 'FRIED', 'GRILLED', 'ROASTED', 'STEAMED'}
PRESERVATION = {'CANNED', 'FROZEN', 'DRIED', 'CURED', 'SMOKED'}
# Explicitly inspected records. These are display equivalences within ONE ID,
# not substitutions between foods or claims that unspecified means raw.
LABELS = {
    758: ('Huevo entero de gallina, escalfado', 'Whole chicken egg, poached'),
    760: ('Huevo entero de gallina, pasado por agua', 'Whole chicken egg, soft-boiled'),
    761: ('Huevo revuelto con mantequilla', 'Scrambled egg with butter'),
    762: ('Yema de huevo de gallina, desecada', 'Chicken egg yolk, dried'),
    1173: ('Clara de huevo de gallina, cruda', 'Chicken egg white, raw'),
    2125: ('Yema de huevo de gallina, cruda', 'Chicken egg yolk, raw'),
    2126: ('Huevo entero de gallina, cocido', 'Whole chicken egg, hard-boiled'),
    1319: ('Leche semidesnatada de vaca, pasteurizada', 'Semi-skimmed cow milk, pasteurised'),
    2163: ('Pan integral de trigo', 'Whole wheat bread'),
    2520: ('Yogur griego', 'Greek yogurt'),
    2543: ('Aceite de oliva', 'Olive oil'),
    2660: ('Arroz (preparación no especificada)', 'Rice (preparation unspecified)'),
}
EGG_PARTS = {1173: 'WHITE', 2125: 'YOLK', 762: 'YOLK'}
EGG_WHOLE = {757, 758, 759, 760, 763, 1461, 2126}
EGG_PREP = {758: 'POACHED', 760: 'SOFT_BOILED', 761: 'SCRAMBLED', 779: 'OMELETTE'}


def compile_rules():
    digest = hashlib.sha256(ASSET.read_bytes()).hexdigest()
    assert digest == EXPECTED_SOURCE, 'Changed source requires a new reviewed rules version'
    audit = json.loads((ROOT / 'docs/catalog/exports/02-food-audit.json').read_text(encoding='utf-8'))
    assert audit['source_sha256_before'] == digest
    ambiguous = {f['food_id']: f for f in audit['findings'] if f['code'] == 'AMBIGUOUS_IDENTITY'}
    with sqlite3.connect(ASSET.as_uri() + '?mode=ro', uri=True) as db:
        db.row_factory = sqlite3.Row
        foods = db.execute('SELECT f.*, c.key category FROM foods f JOIN food_categories c ON c.id=f.category_id ORDER BY f.id').fetchall()
        dataset = db.execute("SELECT value FROM db_metadata WHERE key='dataset_version'").fetchone()[0]
        snapshots = {}
        for f in foods:
            fid = f['id']
            m = db.execute('SELECT * FROM food_macros WHERE food_id=?', (fid,)).fetchone()
            default = db.execute('SELECT grams FROM food_portions WHERE food_id=? AND is_default=1 LIMIT 1', (fid,)).fetchone()
            attributes = dict(name=f['name_en'] or f['name_es'], nameEs=f['name_es'], nameNorm=f['name_en_norm'],
                nameEsNorm=f['name_norm'], baseName=f['base_name_es'], foodType=f['category'],
                aisleKey=db.execute('SELECT key FROM shopping_aisles WHERE id=?', (f['aisle_id'],)).fetchone()[0],
                kcal=m['kcal'], carbs=m['carbs_g'], fats=m['fat_g'], protein=m['protein_g'],
                prepState=f['prep_state'], rawSafety=f['raw_safety'], servingSizeG=default[0] if default else None,
                isLiquid=f['is_liquid'], densityGml=f['density_g_ml'], completenessPct=f['completeness_pct'],
                quality=f['quality'], sourceKey=f'BEDCA:{fid}', isCustom=0, notes=None)
            children = {}
            for table, sql, keys in [
                ('ingredient_nutrients', 'SELECT tag,value FROM food_nutrients WHERE food_id=?', None),
                ('ingredient_portions', 'SELECT label_en,label_es,grams,portion_type,is_default FROM food_portions WHERE food_id=?', (0, 2)),
                ('ingredient_aliases', 'SELECT alias,alias_norm,lang FROM food_aliases WHERE food_id=?', (1,)),
                ('ingredient_quality_issues', 'SELECT code,severity,detail FROM food_quality_issues WHERE food_id=?', (0,)),
            ]:
                rows = [list(row) for row in db.execute(sql, (fid,))]
                if keys:
                    unique = {}
                    for row in rows:
                        unique.setdefault(tuple(row[k] for k in keys), row)
                    rows = list(unique.values())
                children[table] = rows
            snapshots[fid] = dict(attributes=attributes, children=children)
    entries = []
    for food in foods:
        f = dict(food)
        fid, prep = f['id'], f['prep_state']
        state, rule = 'VISIBLE', 'C01_PRESERVE_PURCHASE_FORM'
        if prep == 'RAW':
            rule = 'C02_RAW_PRIORITY'
        # Roasted nuts are common purchase forms. Preserve that category rather
        # than classifying every roasted product as a kitchen preparation.
        if prep in THERMAL and not (f['category'] == 'LEGUMES' and prep == 'ROASTED'):
            state, rule = 'SECONDARY', 'C03_THERMAL_VARIANT'
        if fid in EGG_PARTS:
            state, rule = 'SECONDARY', 'C04_EGG_PART'
        if fid in {758, 760, 761, 779}:
            state, rule = 'SECONDARY', 'C05_EXPLICIT_EGG_PREPARATION'
        # No whole raw chicken egg exists. Whole poached is the first available
        # chicken-egg option, visibly labelled as poached, with its own nutrients.
        if fid == 758:
            state, rule = 'VISIBLE', 'C06_AVAILABLE_WHOLE_EGG'
        if fid in ambiguous:
            state, rule = 'REVIEW_REQUIRED', 'C07_AMBIGUOUS_IDENTITY'
        if f['is_deprecated']:
            state, rule = 'OBSOLETE', 'C08_DECLARED_OBSOLETE'
        if fid == 2494:
            state, rule = 'HIDDEN', 'C09_EXISTING_EXCLUSION'
        es, en = LABELS.get(fid, (f['name_es'], f['name_en']))
        assert es and en, f'Missing bilingual label: {fid}'
        entries.append(dict(
            foodId=fid, state=state, rule=rule, nameEs=es, nameEn=en,
            conceptKey=f'bedca:{fid}', variantKey=f'bedca:{fid}',
            partKey=EGG_PARTS.get(fid, 'WHOLE' if fid in EGG_WHOLE else 'UNSPECIFIED'),
            preparationKey=EGG_PREP.get(fid, 'UNSPECIFIED' if prep in PRESERVATION | {'POWDERED'} else prep),
            preservationKey='DRIED' if fid == 762 else prep if prep in PRESERVATION else 'UNSPECIFIED',
            formKey='POWDER' if prep == 'POWDERED' else 'UNSPECIFIED',
            formulationKey='WITH_BUTTER' if fid == 761 else 'UNSPECIFIED',
            snapshot=snapshots[fid],
            evidence=dict(source=f['source'], sourceRef=f['source_ref'], category=f['category'],
                          declaredPreparation=prep, originalEs=f['name_es'], originalEn=f['name_en'],
                          reviewFinding=ambiguous[fid]['finding_id'] if fid in ambiguous else None,
                          candidateIds=ambiguous[fid]['related_ids'] if fid in ambiguous else []),
        ))
    return dict(version=VERSION, sourceSha256=digest, datasetVersion=dataset,
                equivalencePolicy='SINGLETON_ONLY; labels are same-record translations; no food substitutions',
                coverageGap='No whole raw chicken egg in source; no composition invented', entries=entries)


if __name__ == '__main__':
    rules = compile_rules()
    # One entry per line makes source-controlled editorial decisions reviewable.
    header = {k: v for k, v in rules.items() if k != 'entries'}
    text = json.dumps(header, ensure_ascii=False, indent=2)[:-2] + ',\n  "entries": [\n'
    text += ',\n'.join('    ' + json.dumps(e, ensure_ascii=False, separators=(',', ':')) for e in rules['entries'])
    OUTPUT.write_text(text + '\n  ]\n}\n', encoding='utf-8', newline='\n')
    print(dict(Counter(e['state'] for e in rules['entries'])))
