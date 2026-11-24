import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { once } from 'node:events';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { gzipSync } from 'node:zlib';
import { FakePriceProvider } from './fake-price-provider.mjs';
import { OpenPricesProvider } from './open-prices-provider.mjs';
import { loadLocationPage } from './open-prices-location.mjs';
import { PriceProvider } from './price-provider.mjs';
import { cachedQuotes, syncPrices } from './price-sync.mjs';
import { ProviderError } from './provider-error.mjs';
import { priceResponse, priceServer } from './sync-http.mjs';
import { DAY, HOUR, syncPolicy } from './sync-policy.mjs';
import { PriceSyncStore } from './sync-store.mjs';

const START = Date.parse('2026-11-18T12:00:00Z');
const MINUTE = 60_000;
const fakePolicy = options => syncPolicy({ source: 'fake', regionId: 'test-region', ...options });
const exportFixture = JSON.parse(readFileSync(
    new URL('./fixtures/open-prices-export.simulated.json', import.meta.url), 'utf8',
));
const userAgent = 'NutriNest-Contract-Test/1.0';
const realConfig = {
    source: 'open-prices', regionId: 'test-region', approvedPilot: true,
    rightsUntil: '2026-12-15T00:00:00Z', plan: exportFixture.plan,
};

function openTestDatabase(t, policy) {
    const dir = mkdtempSync(join(tmpdir(), 'nutrinest-prices-'));
    const path = join(dir, 'test.sqlite');
    const active = new Set();
    const open = () => {
        const store = new PriceSyncStore(path, policy.sourceId, policy.regionId);
        active.add(store);
        return store;
    };
    t.after(() => {
        for (const store of active) store.close();
        rmSync(dir, { recursive: true, force: true });
    });
    return { open, close: store => { active.delete(store); store.close(); } };
}

async function fakeWithPrices(transform) {
    const fake = await FakePriceProvider.load();
    const metadata = fake.getMetadata();
    const { items } = fake.listPrices(metadata.stores[0].id);
    return new PriceProvider(metadata, fake.listCatalog().items, transform(items));
}

test('persists a deduplicated schedule and records genuine changes with lineage', async t => {
    const policy = fakePolicy({ cadenceHours: 1 });
    const database = openTestDatabase(t, policy);
    let store = database.open();
    let time = START;
    const now = () => time;
    const load = () => fakeWithPrices(items => [...items, items[0]]);
    assert.equal((await syncPrices(store, policy, { load, now })).status, 'SUCCEEDED');
    const first = store.read().quotes[0];
    assert.equal(store.history().length, 2);
    assert.equal(first.regionId, policy.regionId);
    assert.equal(first.price.record.observedAt, null);
    assert.equal(first.price.record.observedOn, '2026-11-17');
    assert.equal(Date.parse(first.fetchedAt), START);
    assert.equal(first.price.record.origin.sourceId, 'fake-prices');
    assert.equal(first.price.record.scope.type, 'STORE');
    assert.match(first.inputs[0].sha256, /^[a-f0-9]{64}$/);

    database.close(store);
    store = database.open();
    assert.equal((await syncPrices(store, policy, { now, load })).status, 'NOT_DUE');
    time += HOUR;
    assert.equal((await syncPrices(store, policy, { now, load })).status, 'SUCCEEDED');
    assert.deepEqual(store.read().quotes[0], first);
    assert.equal(store.history().length, 2);
    assert.equal(store.publication().revision, 1);

    time += HOUR;
    const correction = () => fakeWithPrices(items => [{ ...items[0], amount: '1.300' }, items[1]]);
    await syncPrices(store, policy, { now, load: correction });
    assert.equal(store.read().quotes[0].price.record.revision, 2);
    assert.equal(store.history()[2].price.amount, '1.300');
    assert.equal(store.publication().revision, 2);

    time += HOUR;
    await syncPrices(store, policy, { now, load: () => fakeWithPrices(items => [{
        ...items[0], amount: '1.30',
        record: { ...items[0].record, observedOn: '2026-11-18',
            origin: { ...items[0].record.origin, recordKey: 'offer-3' } },
    }, items[1]]) });
    assert.equal(store.read().quotes[0].price.record.origin.recordKey, 'offer-3');
    assert.equal(store.read().quotes[0].price.record.revision, 1);
    assert.equal(store.history().length, 3);
});

test('excludes overlapping executions across SQLite connections and releases the lock', async t => {
    const policy = fakePolicy();
    const database = openTestDatabase(t, policy);
    const first = database.open();
    const second = database.open();
    const { promise, resolve } = Promise.withResolvers();
    const running = syncPrices(first, policy, { now: () => START, load: () => promise });
    assert.equal((await syncPrices(second, policy, { now: () => START })).status, 'BUSY');
    assert.equal((await syncPrices(first, policy, { now: () => START })).status, 'BUSY');
    resolve(await FakePriceProvider.load());
    assert.equal((await running).status, 'SUCCEEDED');
    assert.equal((await syncPrices(second, policy, { now: () => START })).status, 'NOT_DUE');
    assert.equal(second.read().counts.attempts, 1);
});

test('honors Retry-After and caps consecutive attempts after network failure and provider outage', async t => {
    const policy = fakePolicy({ cadenceHours: 1 });
    const database = openTestDatabase(t, policy);
    let store = database.open();
    let time = START;
    const now = () => time;
    await syncPrices(store, policy, { now });
    const previous = store.publication();
    time += HOUR;
    const cycle = time;
    const errors = [
        new ProviderError('NETWORK', 'socket closed', { retryable: true }),
        new ProviderError('RATE_LIMITED', '429', { status: 429, retryable: true,
            retryAt: new Date(cycle + 22 * MINUTE).toISOString() }),
        new ProviderError('UNAVAILABLE', '503', { status: 503, retryable: true }),
    ];
    const load = async () => { throw errors.shift(); };
    assert.equal((await syncPrices(store, policy, { now, load })).nextAt, cycle + 5 * MINUTE);
    database.close(store);
    store = database.open();
    time += 5 * MINUTE;
    assert.equal((await syncPrices(store, policy, { now, load })).nextAt, cycle + 22 * MINUTE);
    time = cycle + 21 * MINUTE;
    assert.equal((await syncPrices(store, policy, { now, load })).status, 'NOT_DUE');
    time += MINUTE;
    assert.equal((await syncPrices(store, policy, { now, load })).nextAt, cycle + HOUR);
    assert.equal((await syncPrices(store, policy, { now, load })).status, 'NOT_DUE');
    assert.equal(errors.length, 0);
    assert.deepEqual(store.publication(), previous);
    assert.equal(store.read().counts.failures, 3);
    assert.equal(store.history().length, 2);
    assert.deepEqual(JSON.parse(priceResponse(store, policy, { method: 'GET', path: '/healthz' }, time).body),
        { status: 'degraded' });
});

test('reserves each real request durably and defers when the rolling budget is exhausted', async t => {
    const policy = syncPolicy({ ...realConfig, requestsPer24Hours: 2 }, userAgent);
    const store = openTestDatabase(t, policy).open();
    let time = START;
    const now = () => time;
    let sent = 0;
    const load = async ({ beforeRequest }) => {
        for (let i = 0; i < 2; i++) {
            beforeRequest();
            sent++;
        }
        throw new ProviderError('NETWORK', 'interrupted', { retryable: true });
    };
    await syncPrices(store, policy, { now, load });
    time += 5 * MINUTE;
    const result = await syncPrices(store, policy, { now, load });
    assert.equal(result.error, 'BUDGET_EXHAUSTED');
    assert.equal(result.nextAt, START + DAY);
    assert.equal(sent, 2);
    assert.equal(store.read().counts.requests, 2);
    assert.equal((await syncPrices(store, { ...policy, enabled: false }, { now, load })).status, 'DISABLED');
    assert.throws(() => syncPolicy({ ...realConfig, cadenceHours: 1 }, userAgent), { code: 'INVALID_CONFIGURATION' });
});

test('revalidates official exports independently and preserves cached normalized records on 304', async t => {
    const policy = syncPolicy(realConfig, userAgent);
    const store = openTestDatabase(t, policy).open();
    let time = START;
    const now = () => time;
    const requests = [];
    const load = ({ previous, beforeRequest }) => OpenPricesProvider.load({
        ...exportFixture.plan, through: new Date(time).toISOString().slice(0, 10),
    }, {
        userAgent, now, previous, beforeRequest,
        fetchImpl: async (url, { headers }) => {
            const rows = url.endsWith('/locations.jsonl.gz') ? exportFixture.locations : exportFixture.prices;
            const tag = rows === exportFixture.locations ? '"locations"' : '"prices"';
            requests.push(headers['If-None-Match']);
            if (headers['If-None-Match'] === tag) return new Response(null, { status: 304 });
            return new Response(gzipSync(rows.map(row => JSON.stringify(row)).join('\n')), { headers: { ETag: tag } });
        },
    });
    assert.equal((await syncPrices(store, policy, { now, load })).status, 'SUCCEEDED');
    const first = store.read().quotes[0];
    time += DAY;
    assert.equal((await syncPrices(store, policy, { now, load })).status, 'SUCCEEDED');
    assert.deepEqual(requests, [undefined, undefined, '"locations"', '"prices"']);
    assert.deepEqual(store.read().quotes[0], first);
    assert.equal(store.read().counts.notModified, 2);
    assert.equal(store.history().length, 1);
});

test('R2 location page binds the reviewed product and store and keeps injected data simulated', async t => {
    const config = JSON.parse(readFileSync(new URL('./madrid-pilot.json', import.meta.url)));
    const policy = syncPolicy(config, userAgent);
    const store = openTestDatabase(t, policy).open();
    const [shop] = config.plan.stores;
    const [pack] = config.plan.products;
    const row = {
        id: 1, location_id: shop.locationId, type: 'PRODUCT', product_code: pack.gtin,
        currency: 'EUR', date: '2026-11-17', price: '1.230', price_is_discounted: false,
        duplicate_of: null, price_per: null, proof_id: 100,
        product: { code: pack.gtin, product_name: pack.name, brands: pack.brand,
            product_quantity: 900, product_quantity_unit: 'g' },
        location: { id: shop.locationId, type: 'OSM', osm_type: shop.osmType, osm_id: shop.osmId,
            osm_tag_key: 'shop', osm_brand: shop.retailerName, osm_address_country_code: 'ES',
            osm_address_postcode: shop.postalCode },
    };
    let time = START;
    const load = ({ beforeRequest }) => loadLocationPage({
        ...config.plan, from: '2026-11-01', through: '2026-11-30',
    }, { userAgent, now: () => time, beforeRequest, fetchImpl: async url => {
        assert.equal(new URL(url).searchParams.get('location_id'), String(shop.locationId));
        return Response.json({ page: 1, total: 2, items: [row, { ...row, id: 2, price_is_discounted: true }] });
    } });
    assert.equal((await syncPrices(store, policy, { now: () => time, load })).status, 'SUCCEEDED');
    const previous = store.publication();
    const snapshot = JSON.parse(previous.snapshot);
    assert.equal(snapshot.prices.length, 1);
    assert.equal(snapshot.prices[0].amount, '1.230');
    assert.equal(snapshot.prices[0].record.proofId, 100);
    assert.equal(snapshot.prices[0].record.observedAt, null);
    assert.equal(snapshot.dataKind, 'simulated');
    row.location.osm_id++;
    time += DAY;
    assert.equal((await syncPrices(store, policy, { now: () => time, load })).error, 'MALFORMED_RESPONSE');
    assert.deepEqual(store.publication(), previous);
    assert.equal(priceResponse(store, policy, {
        method: 'GET', path: '/v1/price-regions/' + policy.regionId + '/manifest',
    }, time).headers['X-NutriNest-Price-Data'], 'simulated');
});

test('exposes freshness, prevents expired conditional responses and omits missing prices from v1', async t => {
    const policy = fakePolicy({ freshDays: 2, retainDays: 3, cadenceHours: 1 });
    const store = openTestDatabase(t, policy).open();
    let time = START;
    const now = () => time;
    const region = '/v1/price-regions/test-region';
    const get = (path, ifNoneMatch) => priceResponse(store, policy, { method: 'GET', path, ifNoneMatch }, time);
    await syncPrices(store, policy, { now });
    const manifestResponse = get(region + '/manifest');
    const manifest = JSON.parse(manifestResponse.body);
    const snapshotPath = region + '/snapshots/' + manifest.revision;
    const snapshot = get(snapshotPath);
    assert.equal(Buffer.byteLength(snapshot.body), manifest.byteLength);
    assert.equal(createHash('sha256').update(snapshot.body).digest('hex'), manifest.sha256);
    assert.equal(get(region + '/manifest', manifestResponse.headers.ETag).status, 304);
    assert.equal(Date.parse(manifest.expiresAt), Date.parse('2026-11-19T00:00:00Z'));
    assert.equal(cachedQuotes(store.read(), policy, time)[0].status, 'CURRENT');
    time = Date.parse(manifest.expiresAt);
    assert.equal(get(region + '/manifest', manifestResponse.headers.ETag).status, 404);
    assert.equal(get(snapshotPath, snapshot.headers.ETag).status, 410);
    assert.equal(cachedQuotes(store.read(), policy, time)[0].status, 'STALE');
    await syncPrices(store, policy, { now, load: () => fakeWithPrices(() => []) });
    const newRevision = JSON.parse(get(region + '/manifest').body).revision;
    assert.equal(JSON.parse(get(region + '/snapshots/' + newRevision).body).prices.length, 0);
    assert.equal(cachedQuotes(store.read(), policy, time)[0].quote.price.amount, '1.2345');
    time += DAY;
    assert.deepEqual(cachedQuotes(store.read(), policy, time).map(({ status, quote }) => [status, quote]),
        [['UNVERIFIED', null], ['UNVERIFIED', null]]);
    assert.equal(JSON.parse(get('/metrics').body).quotes.UNVERIFIED, 2);
});

test('serves the v1 publication over HTTP with exact bytes and rejects expired validators', async t => {
    const policy = fakePolicy({ freshDays: 2, retainDays: 3 });
    const store = openTestDatabase(t, policy).open();
    let now = START;
    await syncPrices(store, policy, { now: () => now });
    const server = priceServer(store, policy);
    t.mock.method(Date, 'now', () => now);
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    t.after(() => new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())));
    const region = `http://127.0.0.1:${server.address().port}/v1/price-regions/test-region`;
    const request = (path, options) => fetch(region + path, { ...options, signal: AbortSignal.timeout(5000) });

    const manifest = await request('/manifest');
    assert.equal(manifest.status, 200);
    assert.equal(manifest.headers.get('content-type'), 'application/json');
    assert.equal(manifest.headers.get('x-nutrinest-price-data'), 'simulated');
    const manifestTag = manifest.headers.get('etag');
    assert.match(manifestTag, /^"[0-9a-f]{64}"$/);
    const { revision, byteLength, sha256, expiresAt } = await manifest.json();
    const snapshotPath = `/snapshots/${revision}`;
    const snapshot = await request(snapshotPath);
    assert.equal(snapshot.status, 200);
    assert.equal(snapshot.headers.get('x-nutrinest-price-data'), 'simulated');
    const snapshotTag = snapshot.headers.get('etag');
    assert.equal(snapshotTag, `"${sha256}"`);
    const bytes = Buffer.from(await snapshot.arrayBuffer());
    assert.equal(bytes.length, byteLength);
    assert.equal(createHash('sha256').update(bytes).digest('hex'), sha256);
    assert.equal(JSON.parse(bytes).sources[0].id, 'fake-prices');
    assert.equal(JSON.parse(bytes).coverage.observations, 2);
    assert.equal(Date.parse(JSON.parse(bytes).prices[0].record.fetchedAt), START);

    const cached = await request('/manifest', { headers: { 'If-None-Match': `W/${manifestTag}` } });
    assert.equal(cached.status, 304);
    assert.equal(cached.headers.get('x-nutrinest-price-data'), 'simulated');
    assert.equal(await cached.text(), '');
    const method = await request('/manifest', { method: 'POST' });
    assert.equal(method.status, 405);
    assert.equal(method.headers.get('allow'), 'GET');
    now = Date.parse(expiresAt);
    assert.equal((await request('/manifest', { headers: { 'If-None-Match': manifestTag } })).status, 404);
    assert.equal((await request(snapshotPath, { headers: { 'If-None-Match': snapshotTag } })).status, 410);
});

test('a publication failure rolls back observation revisions, history and active cache', async t => {
    const policy = fakePolicy({ cadenceHours: 1 });
    const store = openTestDatabase(t, policy).open();
    let time = START;
    const now = () => time;
    await syncPrices(store, policy, { now });
    const snapshot = store.publication();
    time += HOUR;
    const result = await syncPrices(store, policy, { now, load: async () => {
        const provider = await fakeWithPrices(items => [{ ...items[0], amount: '12.50' }, items[1]]);
        const metadata = provider.getMetadata();
        metadata.source.name = 'x'.repeat(1_048_576);
        return new PriceProvider(metadata, provider.listCatalog().items, provider.listPrices(metadata.stores[0].id).items);
    } });
    assert.equal(result.error, 'INTERNAL_ERROR');
    assert.equal(store.history().length, 2);
    assert.deepEqual(store.publication(), snapshot);
    time += HOUR;
    await syncPrices(store, policy, { now, load: () => fakeWithPrices(items => [{ ...items[0], amount: '2.75' }, items[1]]) });
    assert.equal(store.read().quotes[0].price.record.revision, 2);
});

test('republishes the still-fresh subset during backoff without extending the batch lifetime', async t => {
    const policy = fakePolicy({ freshDays: 2, cadenceHours: 1 });
    const store = openTestDatabase(t, policy).open();
    let time = Date.parse('2026-11-18T23:00:00Z');
    const now = () => time;
    await syncPrices(store, policy, { now, load: () => fakeWithPrices(items => [
        items[0], { ...items[1], record: { ...items[1].record, observedOn: '2026-11-18' } },
    ]) });
    const lastSuccess = time;
    time += HOUR;
    const outage = async () => { throw new ProviderError('UNAVAILABLE', '503', { retryable: true }); };
    assert.equal((await syncPrices(store, policy, { now, load: outage })).status, 'FAILED');
    time += MINUTE;
    assert.equal((await syncPrices(store, policy, { now, load: outage })).status, 'NOT_DUE');
    const snapshot = JSON.parse(store.publication().snapshot);
    assert.equal(snapshot.prices.length, 1);
    assert.equal(snapshot.prices[0].record.origin.recordKey, 'offer-2');
    assert.equal(store.read().lastSuccessAt, lastSuccess);
    assert.ok(Date.parse(snapshot.expiresAt) <= lastSuccess + policy.snapshotMs);
});
test('geo directory exposes only stores with physical observations in the active live revision', () => {
    const sample = JSON.parse(readFileSync(new URL('./fixtures/madrid-r4.snapshot.json', import.meta.url)));
    const shop = sample.stores[0];
    sample.stores.push({ ...shop, id: 'unobserved', postalCode: '15001' });
    sample.prices.push({ ...sample.prices[0], record: { ...sample.prices[0].record,
        scope: { type: 'ZONE', zoneId: 'regional' } } });
    const now = Date.parse(sample.generatedAt);
    const policy = syncPolicy({ ...realConfig, regionId: sample.regionId }, userAgent);
    const store = { publicationView: () => ({
        latest_revision: sample.revision, snapshot: JSON.stringify(sample),
        manifest: JSON.stringify({ revision: sample.revision }), expires_at: Date.parse(sample.expiresAt),
        data: JSON.stringify({ scopeKey: policy.scopeKey }),
    }) };
    const get = at => priceResponse(store, policy, { method: 'GET', path: '/v1/geo/directory' }, at);
    const response = get(now);
    assert.equal(response.status, 200);
    const result = JSON.parse(response.body);
    assert.equal(result.provider, 'open-prices');
    assert.equal(result.regionId, sample.regionId);
    assert.deepEqual(result.stores, [shop]);
    assert.equal(get(Date.parse(sample.expiresAt)).status, 404);
    sample.dataKind = 'simulated';
    assert.equal(get(now).status, 404);
});
