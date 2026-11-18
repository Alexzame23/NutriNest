import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { gzipSync } from 'node:zlib';
import { FakePriceProvider } from './fake-price-provider.mjs';
import { OpenPricesProvider } from './open-prices-provider.mjs';
import { PriceProvider } from './price-provider.mjs';
import { cachedQuotes, syncPrices } from './price-sync.mjs';
import { ProviderError } from './provider-error.mjs';
import { priceResponse } from './sync-http.mjs';
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
