import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { MatchingService } from './matching-service.mjs';
import { cases, comparison } from './fixtures/matching.simulated.mjs';
import { labelDigest } from './matching-evidence.mjs';
import { fixedProduct } from './price-provider.mjs';

test('labelled decisions via the service, including abstentions', t => {
    const matrix = {};
    for (const row of cases) {
        const service = new MatchingService(':memory:');
        const [result] = service.reconcile(comparison(row));
        const d = result.decision;
        const truth = row[2];
        const expectedStatus = truth === 'INCOMPATIBLE' ? 'REJECTED' :
            ['PENDING', 'ALLOWED_SUBSTITUTE'].includes(truth) ? 'PENDING' : 'ACCEPTED';
        assert.equal(d.status, expectedStatus, row[1]);
        assert.equal(d.kind, truth === 'PENDING' ? 'AMBIGUOUS' : truth, row[1]);
        assert.equal(service.acceptedMatches().length, Number(expectedStatus === 'ACCEPTED'));
        (matrix[truth] ??= {})[d.status] = (matrix[truth]?.[d.status] ?? 0) + 1;
        service.close();
    }
    t.diagnostic(JSON.stringify(matrix));
});

test('bare provider row, stale label and invalid package fail closed even with zero threshold', () => {
    const s = new MatchingService(':memory:');
    const batch = comparison(cases[0]);
    batch.threshold = 0;
    batch.catalog = [fixedProduct('fixture', { externalId: 'rice', gtin: '0036000291452',
        name: 'ARROZ', netContent: { amount: '500', unit: 'G' }, unitsPerPackage: 1 })];
    assert.equal(s.reconcile(batch)[0].decision.status, 'PENDING');
    const clean = comparison(cases[0]);
    clean.catalog[0].product.variant = 'precocinado';
    assert.ok(s.reconcile(clean).find(r => r.packageId === 'pack.arroz').decision.missing.includes('labelReview'));
    clean.catalog[0].package.netContent.amount = '0';
    clean.catalog[0].facts.labelDigest = labelDigest(clean.catalog[0]);
    assert.ok(s.reconcile(clean).find(r => r.packageId === 'pack.arroz').decision.missing.includes('unitOrPackage'));
    s.close();
});

test('competing canonical records, manual rejection, optimistic review and durable withdrawal', t => {
    const dir = mkdtempSync(join(tmpdir(), 'matching-'));
    t.after(() => rmSync(dir, { recursive: true, force: true }));
    const path = join(dir, 'audit.sqlite');
    let s = new MatchingService(path);
    const batch = comparison(cases[0]);
    batch.ingredients.push({ ...batch.ingredients[0], key: 'other' });
    assert.ok(s.reconcile(batch).every(row => row.decision.status === 'PENDING'));
    const review = { ingredientKey: 'arroz', packageId: 'pack.arroz', version: 1,
        actor: 'editor', reason: 'Ficha contrastada', status: 'ACCEPTED' };
    assert.throws(() => s.review(review), /competing/);
    s.review({ ...review, ingredientKey: 'other', status: 'REJECTED' });
    s.review(review);
    assert.throws(() => s.review(review), /Stale/);
    s.close();
    s = new MatchingService(path);
    assert.equal(s.reconcile(batch)[0].version, 2);
    assert.equal(s.acceptedMatches()[0].mappingVersion, 2);
    batch.ingredients[0].revision = 'fixture-2';
    assert.equal(s.reconcile(batch)[0].decision.status, 'PENDING');
    s.reconcile({ ingredients: [], catalog: [] });
    assert.equal(s.history('arroz', 'pack.arroz').length, 4);
    assert.equal(s.acceptedMatches().length, 0);
    s.close();
});

test('review cannot override unit, unknown allergen, or conflicting preparation', () => {
    const s = new MatchingService(':memory:');
    for (const index of [1,12,17]) {
        const batch = comparison(cases[index]);
        s.reconcile(batch);
        const [i] = batch.ingredients;
        assert.throws(() => s.review({ ingredientKey: i.key, packageId: batch.catalog[0].package.id,
            version: 1, actor: 'editor', reason: 'test', status: 'ACCEPTED' }), /Evidence required/);
    }
    s.close();
});

test('duplicate GTIN across packages, brand conflict and invalid target cannot certify identity', () => {
    const s = new MatchingService(':memory:');
    const batch = comparison(cases[7]);
    const copy = structuredClone(batch.catalog[0]);
    copy.package.id += '.second';
    copy.facts.labelDigest = labelDigest(copy);
    batch.catalog.push(copy);
    assert.ok(s.reconcile(batch).every(r => r.decision.missing.includes('duplicateGtin')));
    batch.catalog.pop();
    batch.ingredients[0].target.brand = 'Marca B';
    assert.equal(s.reconcile(batch)[0].decision.kind, 'INCOMPATIBLE');
    batch.ingredients[0].target.brand = 'Marca A';
    batch.ingredients[0].target.gtin = '00000000000000';
    assert.equal(s.reconcile(batch)[0].decision.status, 'PENDING');
    s.close();
});

test('calibration and reviewed substitute projection', () => {
    const s = new MatchingService(':memory:');
    const batch = comparison(cases[0]);
    batch.catalog[0].product.name = 'Marca Sol';
    batch.catalog[0].facts.labelDigest = labelDigest(batch.catalog[0]);
    batch.threshold = 90;
    assert.equal(s.reconcile(batch)[0].decision.status, 'PENDING');
    batch.threshold = 80;
    assert.equal(s.reconcile(batch)[0].decision.status, 'ACCEPTED');
    const alt = comparison(cases[20]);
    s.reconcile(alt);
    s.review({ ingredientKey: 'pollo', packageId: 'pack.pollo', version: 1,
        status: 'ACCEPTED', actor: 'editor', reason: 'Alternativa culinaria revisada' });
    assert.equal(s.acceptedMatches()[0].kind, 'ALTERNATIVE');
    s.close();
});

test('BEDCA review status and reopened rival suspend automatic eligibility', () => {
    const s = new MatchingService(':memory:');
    const batch = comparison(cases[0]);
    batch.ingredients[0].catalogState = 'REVIEW_REQUIRED';
    assert.equal(s.reconcile(batch)[0].decision.status, 'PENDING');
    batch.ingredients[0].catalogState = 'VISIBLE';
    batch.ingredients.push({ ...batch.ingredients[0], key: 'rival' });
    s.reconcile(batch);
    const review = { packageId: 'pack.arroz', actor: 'editor', reason: 'Verificación de identidad' };
    s.review({ ...review, ingredientKey: 'rival', status: 'REJECTED', version: 1 });
    s.review({ ...review, ingredientKey: 'arroz', status: 'ACCEPTED', version: 2 });
    s.review({ ...review, ingredientKey: 'rival', status: 'PENDING', version: 2 });
    assert.equal(s.acceptedMatches().length, 0);
    s.close();
});
