// Run with a separate npm tool prefix. This verifier does not install application dependencies.
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const { mkdtempSync, readFileSync, rmSync } = require('node:fs');
const { createRequire } = require('node:module');
const { tmpdir } = require('node:os');
const { join, resolve } = require('node:path');

async function main() {
    const deps = createRequire(resolve(process.argv[2], 'package.json'));
    const { PGlite } = deps('@electric-sql/pglite');
    const swagger = deps('@apidevtools/swagger-parser');
    const Ajv = deps('ajv/dist/2020').default;
    const addFormats = deps('ajv-formats').default;
    const contractPath = 'docs/prices/11-price-snapshots.openapi.yaml';
    await swagger.validate(contractPath, { dereference: { circular: false } });
    const contract = await swagger.parse(contractPath);
    const ajv = new Ajv({ strict: false, allErrors: true });
    addFormats(ajv);
    const validateSnapshot = ajv.compile({
        $ref: '#/components/schemas/Snapshot',
        components: contract.components,
    });
    const validateManifest = ajv.compile({
        $ref: '#/components/schemas/Manifest',
        components: contract.components,
    });
    const bytes = readFileSync('app/src/androidTest/assets/price-snapshot-v1.json');
    const payload = JSON.parse(bytes);
    assert(validateSnapshot(payload), JSON.stringify(validateSnapshot.errors));
    const { contractVersion, regionId, revision, generatedAt, expiresAt } = payload;
    const manifest = {
        contractVersion, regionId, revision, generatedAt, expiresAt,
        sha256: createHash('sha256').update(bytes).digest('hex'), byteLength: bytes.length,
    };
    assert(validateManifest(manifest), JSON.stringify(validateManifest.errors));
    assert(!validateManifest({ ...manifest, byteLength: 1_048_577 }));
    const invalidSnapshots = [
        example => { example.prices[0].amount = 1.2345; },
        example => { example.prices[0].currency = 'USD'; },
        example => { delete example.prices[1].eligibility; },
        example => { example.prices[0].record.scope.zoneId = 'another-zone'; },
        example => { example.packages[1].netContent = { amount: '500', unit: 'G' }; },
        example => { example.packages[0].netContent = { amount: '1.5', unit: 'UNIT' }; },
    ];
    for (const mutate of invalidSnapshots) {
        const example = structuredClone(payload);
        mutate(example);
        assert(!validateSnapshot(example), 'Invalid snapshot accepted by the wire schema');
    }
    console.log('OpenAPI, manifest, shared JSON fixture and 7 negative wire cases passed');

    const { FakePriceProvider } = await import('../backend/prices/fake-price-provider.mjs');
    const provider = await FakePriceProvider.load();
    const { source, retailers, stores } = provider.getMetadata();
    const { items } = provider.listCatalog();
    // The publisher will assign revisions when it persists price candidates.
    const candidateSnapshot = {
        contractVersion, regionId, revision, generatedAt, expiresAt,
        sources: [source], retailers, stores, zones: [],
        products: items.map(item => item.product), packages: items.map(item => item.package),
        matches: [], availability: [],
        prices: provider.listPrices(stores[0].id).items.map(price => ({
            ...price, record: { ...price.record, revision: 1 },
        })),
    };
    assert(validateSnapshot(candidateSnapshot), JSON.stringify(validateSnapshot.errors));
    console.log('Normalized simulated batch maps to the Phase 11 snapshot schema');

    const { syncPrices } = await import('../backend/prices/price-sync.mjs');
    const { syncPolicy } = await import('../backend/prices/sync-policy.mjs');
    const { PriceSyncStore } = await import('../backend/prices/sync-store.mjs');
    const directory = mkdtempSync(join(tmpdir(), 'nutrinest-publication-'));
    const policy = syncPolicy({ source: 'fake', regionId });
    const store = new PriceSyncStore(join(directory, 'prices.sqlite'), policy.sourceId, regionId);
    try {
        const result = await syncPrices(store, policy, { now: () => Date.parse(generatedAt) });
        assert.equal(result.status, 'SUCCEEDED');
        const published = store.publication();
        const publicManifest = JSON.parse(published.manifest);
        const publicSnapshot = JSON.parse(published.snapshot);
        assert(validateManifest(publicManifest), JSON.stringify(validateManifest.errors));
        assert(validateSnapshot(publicSnapshot), JSON.stringify(validateSnapshot.errors));
        assert.equal(publicManifest.sha256, createHash('sha256').update(published.snapshot).digest('hex'));
        assert.equal(publicManifest.byteLength, Buffer.byteLength(published.snapshot));
        console.log('Scheduled publication passes the existing manifest and snapshot wire schemas');
    } finally {
        store.close();
        rmSync(directory, { recursive: true, force: true });
    }

    const db = new PGlite();
    try {
        await db.exec(readFileSync('docs/prices/11-commercial-schema.sql', 'utf8'));
        await db.exec(readFileSync('docs/prices/tests/11-commercial-constraints.sql', 'utf8'));
        const { rows: [{ version }] } = await db.query('SELECT version()');
        console.log(`${version.split(' on ')[0]}: exact decimals, 22 constraint cases and 2 index plans passed`);
    } finally {
        await db.close();
    }
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
