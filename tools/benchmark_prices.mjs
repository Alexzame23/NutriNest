import assert from 'node:assert/strict';
import { mkdtempSync, readdirSync, rmdirSync, statSync, unlinkSync } from 'node:fs';
import { tmpdir, cpus, platform, release } from 'node:os';
import { join } from 'node:path';
import { performance } from 'node:perf_hooks';
import { DatabaseSync } from 'node:sqlite';
import { FakePriceProvider } from '../backend/prices/fake-price-provider.mjs';
import { cases, comparison } from '../backend/prices/fixtures/matching.simulated.mjs';
import { labelDigest } from '../backend/prices/matching-evidence.mjs';
import { MatchingService } from '../backend/prices/matching-service.mjs';
import { PriceProvider, fixedProduct, regularStorePrice } from '../backend/prices/price-provider.mjs';
import { syncPrices } from '../backend/prices/price-sync.mjs';
import { HOUR, syncPolicy } from '../backend/prices/sync-policy.mjs';
import { PriceSyncStore } from '../backend/prices/sync-store.mjs';

const directory = mkdtempSync(join(tmpdir(), 'nutrinest-perf-'));
const start = Date.parse('2026-11-18T12:00:00Z');
const metadata = (await FakePriceProvider.load()).getMetadata();
const samples = 21;
const warmup = 5;

function summary(values) {
    const ordered = values.toSorted((a, b) => a - b);
    return {
        median_ms: ordered[Math.floor(ordered.length / 2)],
        p95_ms: ordered[Math.ceil(ordered.length * 0.95) - 1],
        samples_ms: values,
    };
}

function fixture(sourceId, products, stores) {
    const catalog = Array.from({ length: products }, (_, i) => {
        const digits = String(200000000000 + i);
        const sum = [...digits].reduce((total, digit, pos) => total + Number(digit) * (pos % 2 ? 3 : 1), 0);
        return fixedProduct(sourceId, {
            externalId: `product-${i}`, name: `Product ${i}`, gtin: digits + (10 - sum % 10) % 10,
            netContent: { amount: '500', unit: 'G' }, unitsPerPackage: 1,
        });
    });
    const retailer = { ...metadata.retailers[0], origin: { sourceId, recordKey: 'fixture-retailer' } };
    const shops = Array.from({ length: stores }, (_, i) => ({
        id: `${sourceId}.store.${i}`, name: `Store ${i}`, retailerId: retailer.id,
        origin: { sourceId, recordKey: `store-${i}` },
    }));
    return cycle => new PriceProvider({
        ...metadata, source: { ...metadata.source, id: sourceId }, retailers: [retailer], stores: shops,
    }, catalog, shops.flatMap((shop, i) => catalog.map((product, j) =>
        regularStorePrice(sourceId, `offer-${i}-${j}`, shop, product, '2026-11-17',
            cycle % 2 ? '1.23' : '1.25', 'HIGH'))));
}

async function syncScenario(products, stores, regions, providers) {
    const active = [];
    const timings = [];
    const before = process.memoryUsage().rss;
    try {
        for (let area = 0; area < regions; area++) {
            for (const source of ['fake', 'open-prices'].slice(0, providers)) {
                const regionId = `r-${area}`;
                const policy = syncPolicy({
                    source, regionId, cadenceHours: 24, approvedPilot: true,
                    rightsUntil: '2027-11-18T00:00:00Z', plan: { stores: [], products: [] },
                }, 'NutriNest offline benchmark');
                const path = join(directory, `sync-${products}-${stores}-${regions}-${providers}-${area}-${source}.db`);
                const provider = fixture(policy.sourceId, products, stores);
                active.push({ store: new PriceSyncStore(path, policy.sourceId, regionId), policy, path, provider });
            }
        }
        for (let cycle = 0; cycle < warmup + samples; cycle++) {
            const now = () => start + cycle * 24 * HOUR;
            const time = performance.now();
            for (const { store, policy, provider } of active) {
                // The loader is injected and all input rows are generated locally.
                assert.equal((await syncPrices(store, policy, { now, load: () => provider(cycle) })).status, 'SUCCEEDED');
            }
            if (cycle >= warmup) timings.push(performance.now() - time);
        }
        const count = active.reduce((total, { store }) => total + store.history().length, 0);
        assert.equal(count, products * stores * active.length * (warmup + samples));
        const publicationBytes = active.reduce((total, { store }) =>
            total + Buffer.byteLength(store.publication().snapshot), 0);
        const rss = process.memoryUsage().rss;
        for (const { store } of active) store.close();
        const bytes = active.reduce((total, { path }) => total + statSync(path).size, 0);
        active.length = 0;
        return { products, stores, regions, providers, history_rows: count, publication_bytes: publicationBytes,
            bytes_after_close: bytes, rss_delta_bytes: rss - before, ...summary(timings) };
    } finally {
        for (const { store } of active) store.close();
    }
}

function matchingScenario() {
    const base = comparison(cases[0]);
    const ingredients = Array.from({ length: 925 }, (_, i) => ({
        ...base.ingredients[0], key: `ingredient-${i}`, name: `Ingredient ${i}`,
        category: `category-${i % 200}`, concept: `concept-${i % 200}`,
    }));
    const catalog = Array.from({ length: 200 }, (_, i) => {
        const item = base.catalog[0];
        const entry = {
            product: { ...item.product, id: `product-${i}`, name: `Product ${i}` },
            package: { ...item.package, id: `pack-${i}`, productId: `product-${i}` },
        };
        return { ...entry, facts: { ...item.facts, labelDigest: labelDigest(entry),
            category: `category-${i}`, concept: `concept-${i}` } };
    });
    const service = new MatchingService(join(directory, 'matching.db'));
    const times = [];
    try {
        for (let i = 0; i < warmup + samples; i++) {
            const start = performance.now();
            assert.equal(service.reconcile({ ingredients, catalog }).length, ingredients.length);
            if (i >= warmup) times.push(performance.now() - start);
        }
        return { ingredients: ingredients.length, packages: catalog.length, ...summary(times) };
    } finally {
        service.close();
    }
}

function historyIndexTrial(indexed) {
    const path = join(directory, `history-${indexed}.db`);
    const db = new DatabaseSync(path);
    try {
        db.exec('CREATE TABLE price_history (id INTEGER PRIMARY KEY, fetched_at INTEGER NOT NULL, observation TEXT NOT NULL)');
        if (indexed) db.exec('CREATE INDEX price_history_fetched_at_idx ON price_history(fetched_at)');
        const insert = db.prepare('INSERT INTO price_history(fetched_at, observation) VALUES (?, ?)');
        const prune = 'DELETE FROM price_history WHERE fetched_at < ?';
        const plan = db.prepare(`EXPLAIN QUERY PLAN ${prune}`).all(0).map(row => row.detail);
        const writes = [];
        const deletes = [];
        for (let i = 0; i < warmup + samples; i++) {
            db.exec('BEGIN');
            const start = performance.now();
            for (let j = 0; j < 1000; j++) insert.run(i * 1000 + j, 'x'.repeat(384));
            const elapsed = performance.now() - start;
            db.exec('COMMIT');
            if (i >= warmup) writes.push(elapsed);
        }
        for (let i = 0; i < warmup + samples; i++) {
            db.exec('BEGIN');
            const start = performance.now();
            assert.equal(db.prepare(prune).run(60).changes, 60);
            const elapsed = performance.now() - start;
            db.exec('ROLLBACK');
            if (i >= warmup) deletes.push(elapsed);
        }
        return { indexed, plan, bytes: statSync(path).size, insert_1000: summary(writes), prune_60: summary(deletes) };
    } finally {
        db.close();
    }
}

try {
    const sync = [];
    for (const dimensions of [[5, 1, 1, 1], [20, 1, 1, 1], [20, 3, 1, 1], [20, 3, 4, 1], [20, 3, 4, 2]]) {
        sync.push(await syncScenario(...dimensions));
    }
    console.log(JSON.stringify({
        node: process.version, sqlite: process.versions.sqlite, platform: platform(), release: release(),
        cpu: cpus()[0].model, sync, matching: matchingScenario(), history_index: [false, true].map(historyIndexTrial),
    }, null, 2));
} finally {
    try {
        for (const file of readdirSync(directory)) unlinkSync(join(directory, file));
        rmdirSync(directory);
    } catch (error) {
        console.error(`Temporary fixture cleanup failed: ${error.message}`);
    }
}
