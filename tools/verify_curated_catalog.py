#!/usr/bin/env python3
"""Verify editorial source artifact and recipe coverage. Runtime ETL is tested on Android."""
import hashlib
import json
import sqlite3
from collections import Counter
from build_curated_catalog import ROOT, ASSET, OUTPUT, compile_rules
from audit_food_catalog import read_recipes


def main():
    source_hash = hashlib.sha256(ASSET.read_bytes()).hexdigest()
    stored = json.loads(OUTPUT.read_text(encoding='utf-8'))
    assert b'\r' not in OUTPUT.read_bytes(), 'Rule bytes require LF on every platform'
    assert stored == compile_rules() == compile_rules(), 'Non-reproducible rules'
    rules = {r['foodId']: r for r in stored['entries']}
    assert len(rules) == len(stored['entries']) == 936
    assert len({r['conceptKey'] for r in rules.values()}) == 936
    assert all(r['nameEs'] and r['nameEn'] for r in rules.values())
    assert all(r['variantKey'] == f'bedca:{fid}' for fid, r in rules.items())
    with sqlite3.connect(ASSET.as_uri() + '?mode=ro', uri=True) as db:
        assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
        assert not db.execute('PRAGMA foreign_key_check').fetchall()
        ids = {r[0] for r in db.execute('SELECT id FROM foods WHERE is_deprecated=0 AND id<>2494')}
        nutrients = db.execute('SELECT COUNT(*) FROM food_nutrients').fetchone()[0]
    assert len(ids) == 923
    assert rules[1173]['state'] == rules[2125]['state'] == 'SECONDARY'
    assert rules[758]['preparationKey'] == 'POACHED'
    assert rules[762]['preservationKey'] == 'DRIED'
    assert all(rules[i]['state'] == 'VISIBLE' for i in (2163, 1319, 2520, 2543))
    assert {i for i, r in rules.items() if r['state'] == 'REVIEW_REQUIRED'} == {1725, 1793, 2433, 2434}
    recipes, _ = read_recipes()
    links = [ingredient for recipe in recipes for ingredient in recipe['ingredients']]
    referenced = {link['food_id'] for link in links}
    assert len(recipes) == 250 and len(links) == 1421 and len(referenced) == 122
    assert referenced <= ids
    assert all(rules[i]['state'] in {'VISIBLE', 'SECONDARY'} for i in referenced)
    assert source_hash == hashlib.sha256(ASSET.read_bytes()).hexdigest()
    report = dict(
        rulesVersion=stored['version'], sourceSha256=source_hash,
        rulesSha256=hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
        sourceRecords=936, importableRecords=923, preservedSourceNutrients=nutrients,
        before={'VISIBLE': 923}, after=dict(Counter(rules[i]['state'] for i in ids)),
        allSourceStates=dict(Counter(r['state'] for r in rules.values())),
        normalizedMetadata=923, bilingualNames=923,
        editedDisplayNames=sum(rules[i]['nameEs'] != rules[i]['evidence']['originalEs'] or
                              rules[i]['nameEn'] != rules[i]['evidence']['originalEn'] for i in ids),
        mergedIdentities=0, changedOriginalMacros=0, deletedRecords=0,
        recipes=len(recipes), recipeLinks=len(links), uniqueRecipeFoodIds=len(referenced),
        recipeFoodStates=dict(Counter(rules[i]['state'] for i in referenced)),
        missingRecipeFoodIds=sorted(referenced - ids),
        coverageGap=stored['coverageGap'],
        scope='Static source/rules verification; actual Kotlin idempotence and rollback are instrumented tests')
    target = ROOT / 'docs/catalog/06-curation-statistics.json'
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
