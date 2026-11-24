import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import validation from './fixtures/madrid-r4.validation.json' with { type: 'json' };
import { cases, comparison } from './fixtures/matching.simulated.mjs';
import { labelDigest } from './matching-evidence.mjs';
import { MatchingService } from './matching-service.mjs';
import { matchingCoverage, retailMatchingBatch } from './retail-matching.mjs';
import { PriceSyncStore } from './sync-store.mjs';
import { updatePublication } from './sync-publication.mjs';
import { syncPolicy } from './sync-policy.mjs';

const bytes = readFileSync(new URL('./fixtures/madrid-r4.snapshot.json', import.meta.url));
const snapshot = JSON.parse(bytes);
const catalog = snapshot.packages.map(pack => ({
    product: snapshot.products.find(product => product.id === pack.productId), package: pack,
}));

test('R4 recorded observations and independent labels against the shipped catalog', t => {
    assert.equal(createHash('sha256').update(bytes).digest('hex'), validation.snapshotSha256);
    assert.deepEqual(snapshot.prices.map(p => p.record.origin.recordKey), validation.observations);
    const service = new MatchingService(':memory:');
    t.after(() => service.close());
    const batch = retailMatchingBatch(catalog);
    const curated = JSON.parse(readFileSync(new URL('../../app/src/main/assets/catalog-curation-v1.json', import.meta.url)));
    for (const i of batch.ingredients) {
        const canonical = curated.entries.find(row => row.conceptKey === i.conceptKey);
        assert.equal(i.revision, curated.version);
        assert.equal(i.sourceSha256, curated.sourceSha256);
        assert.equal(i.name, canonical.nameEs);
        assert.equal(i.catalogState, canonical.state);
        assert.equal(i.category, canonical.evidence.category);
    }
    for (const evidence of ['snapshot-only', 'reviewed-label']) {
        const rows = service.reconcile({ ...batch, catalog: evidence === 'snapshot-only' ? catalog : batch.catalog });
        const active = rows.filter(row => row.decision.status !== 'WITHDRAWN');
        const expected = validation.comparisons.filter(c => c.evidence === evidence);
        assert.equal(active.length, expected.length);
        for (const c of expected) {
            const price = snapshot.prices.find(p => p.record.origin.recordKey === c.recordKey);
            const row = active.find(r => r.canonicalIngredientId === c.canonicalIngredientId && r.packageId === price.record.packageId);
            assert.equal(row?.classification, c.expected);
        }
        const coverage = matchingCoverage(snapshot.prices, rows);
        assert.deepEqual(coverage, { received: 1, candidates: 1, accepted: Number(evidence === 'reviewed-label'),
            ambiguous: Number(evidence === 'snapshot-only'), incompatible: 0, noCandidate: 0 });
        t.diagnostic(JSON.stringify({ evidence, ...coverage }));
    }
    const [match] = service.acceptedMatches();
    const event = service.history(match.canonicalIngredientId, match.packageId).at(-1);
    assert.equal(event.fingerprint, match.evidenceDigest);
    assert.equal(event.confidence, match.confidence);
    assert.equal(event.decision.score, 81);
    assert.equal(event.evidence.item.facts.labelDigest, labelDigest(catalog[0]));
});

test('regional publisher retains original price IDs and withdraws an invalidated label', t => {
    const dir = mkdtempSync(join(tmpdir(), 'r4-matching-'));
    const store = new PriceSyncStore(join(dir, 'prices.sqlite'), 'open-prices', snapshot.regionId);
    t.after(() => { store.close(); rmSync(dir, { recursive: true, force: true }); });
    const now = Date.parse(snapshot.generatedAt);
    const policy = syncPolicy({ source: 'fake', regionId: snapshot.regionId });
    const state = { catalog: structuredClone(catalog), lastSuccessAt: now, scopeKey: policy.scopeKey,
        metadata: { source: snapshot.sources[0], retailers: snapshot.retailers, stores: snapshot.stores },
        quotes: snapshot.prices.map(price => ({ price, fetchedAt: price.record.fetchedAt })) };
    const publish = () => store.transaction(() => updatePublication(store, state, policy, now));
    publish();
    const first = JSON.parse(store.publication().snapshot);
    assert.deepEqual(first.prices, snapshot.prices);
    assert.equal(first.matches.length, 1);
    assert.equal(first.matches[0].commercialProductId, first.products[0].id);
    assert.equal(first.packages[0].id, first.prices[0].record.packageId);
    assert.equal(first.sources[1].id, first.matches[0].origin.sourceId);
    publish();
    assert.equal(store.publication().revision, first.revision);
    state.catalog[0].product.brand = 'changed';
    publish();
    const second = JSON.parse(store.publication().snapshot);
    assert.equal(second.matches.length, 0);
    assert.equal(second.coverage.matching.ambiguous, 1);
    assert.equal(store.matching.history(first.matches[0].canonicalIngredientId, first.packages[0].id).length, 2);
});

test('retailer SKU without GTIN proves only the same reviewed retailer and pack', t => {
    const service = new MatchingService(':memory:');
    t.after(() => service.close());
    const batch = comparison(cases[7]);
    const item = batch.catalog[0];
    const target = batch.ingredients[0].target = { ...batch.ingredients[0].target };
    const sku = { retailerId: 'retailer-A', value: '00007' };
    delete target.gtin;
    target.retailerSku = sku;
    item.package.gtin = null;
    item.package.retailerSku = sku;
    item.facts.labelDigest = labelDigest(item);
    assert.equal(service.reconcile(batch)[0].classification, 'EXACT_SKU');
    target.retailerSku = { ...sku, retailerId: 'retailer-B' };
    assert.equal(service.reconcile(batch)[0].classification, 'INCOMPATIBLE');
    target.retailerSku = sku;
    item.package.netContent.amount = '500';
    item.facts.labelDigest = labelDigest(item);
    assert.equal(service.reconcile(batch)[0].classification, 'INCOMPATIBLE');
});
