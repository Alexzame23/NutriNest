import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { gzipSync } from 'node:zlib';
import { FakePriceProvider } from './fake-price-provider.mjs';
import { download } from './http-download.mjs';
import { OpenPricesProvider } from './open-prices-provider.mjs';

const fixture = JSON.parse(readFileSync(new URL('./fixtures/open-prices-export.simulated.json', import.meta.url)));
assert.equal(fixture.simulated, true);
const ROOT = 'https://prices.openfoodfacts.org/data/';
const USER_AGENT = 'NutriNest-Contract-Test/1.0';
const exportBytes = rows => gzipSync(rows.map(row => JSON.stringify(row)).join('\n') + '\n');

function loadOpen({ plan = fixture.plan, locations = fixture.locations, prices = fixture.prices, requests = [] } = {}) {
    return OpenPricesProvider.load(plan, {
        userAgent: USER_AGENT,
        fetchImpl: async (url, options) => {
            requests.push(url);
            assert.equal(options.headers['User-Agent'], USER_AGENT);
            assert.equal(options.redirect, 'manual');
            assert.match(url, /^https:\/\/prices\.openfoodfacts\.org\/data\/(locations|prices)\.jsonl\.gz$/);
            const feed = url === ROOT + 'locations.jsonl.gz' ? locations : prices;
            return new Response(Array.isArray(feed) ? exportBytes(feed) : feed, {
                headers: { 'Last-Modified': 'Wed, 18 Nov 2026 00:00:00 GMT' },
            });
        },
    });
}

test('simulated provider satisfies the complete read contract offline', async () => {
    const provider = await FakePriceProvider.load();
    const metadata = provider.getMetadata();
    assert.equal(metadata.simulated, true);
    assert.equal(metadata.usage, 'TEST_ONLY');
    const [store] = metadata.stores;
    const first = provider.listCatalog({ limit: 1 });
    assert.equal(first.nextOffset, 1);
    const last = provider.listCatalog({ limit: 1, offset: first.nextOffset });
    assert.equal(last.nextOffset, null);
    assert.notEqual(last.items[0].product.id, first.items[0].product.id);
    assert.deepEqual(provider.getProduct('pasta'), first.items[0]);
    first.items[0].package.netContent.amount = '0';
    metadata.stores.length = 0;
    assert.equal(provider.getProduct('pasta').package.netContent.amount, '500');
    const prices = provider.listPrices(store.id, { limit: 1 });
    assert.equal(prices.items[0].amount, '1.2345');
    assert.equal(prices.items[0].record.packageId, first.items[0].package.id);
    assert.equal(provider.listPrices(store.id, { offset: prices.nextOffset }).items.length, 1);
    assert.throws(() => provider.listCatalog({ limit: 101 }), { code: 'INVALID_QUERY' });
    assert.throws(() => provider.listCatalog(null), { code: 'INVALID_QUERY' });
    assert.throws(() => provider.listCatalog({ offset: -1 }), { code: 'INVALID_QUERY' });
    assert.throws(() => provider.getProduct('unknown'), { code: 'NOT_FOUND' });
    assert.throws(() => provider.listPrices('unknown'), { code: 'NOT_FOUND' });
});

test('Open Prices joins approved exports and maps latest regular EUR prices without extra calls', async () => {
    const requests = [];
    const [base] = fixture.prices;
    const provider = await loadOpen({ requests, prices: [
        ...fixture.prices,
        { ...base, id: 503, date: '2026-11-17', price: '1.125' },
        { ...base, id: 510, price_is_discounted: true }, { ...base, id: 511, duplicate_of: 502 },
        { ...base, id: 512, price_per: 'KILOGRAM' }, { ...base, id: 513, currency: 'USD' },
        { ...base, id: 514, date: '2026-11-20' }, { ...base, id: 515, location_id: 102 },
        { ...base, id: 516, product_code: '0036000291453' }, { ...base, id: 517, date: '2026-10-31' },
    ] });
    const metadata = provider.getMetadata();
    assert.equal(metadata.simulated, false);
    assert.equal(metadata.usage, 'INTERNAL_PILOT');
    assert.equal(metadata.source.kind, 'OPEN_DATA');
    assert.equal(metadata.inputs.length, 2);
    assert.match(metadata.inputs[1].sha256, /^[0-9a-f]{64}$/);
    assert.ok(metadata.inputs[1].lastModified);
    const [store] = metadata.stores;
    assert.equal(store.postalCode, '08001');
    assert.equal(store.osmRef, 'node/99101');
    assert.equal(store.origin.recordKey, '101');
    const entry = provider.getProduct('pack-v1');
    assert.equal(entry.package.gtin, '0036000291452');
    assert.deepEqual(provider.listCatalog().items, [entry]);
    const { items } = provider.listPrices(store.id);
    assert.equal(items.length, 1);
    const [price] = items;
    assert.equal(price.record.origin.recordKey, '503');
    assert.equal(price.record.observedOn, '2026-11-17');
    assert.equal(price.record.packageId, entry.package.id);
    assert.equal(price.amount, '1.125');
    assert.deepEqual(requests, [ROOT + 'locations.jsonl.gz', ROOT + 'prices.jsonl.gz']);
    const initialRows = await loadOpen();
    assert.doesNotMatch(JSON.stringify(initialRows.listPrices(store.id)), /test-private-input|proof_id/);
});

test('Open Prices distinguishes empty coverage from an invalid batch', async () => {
    const [base] = fixture.prices;
    const provider = await loadOpen({ prices: [{ ...base, location_id: 102 }] });
    assert.deepEqual(provider.listPrices(provider.getMetadata().stores[0].id), { items: [], nextOffset: null });
    for (const prices of [
        Buffer.from('not gzip'), gzipSync('broken json\n'), gzipSync(Buffer.from([0xff])),
        gzipSync(''), [{ ...base, price: true }], [{ ...base, price: '0.00001' }],
        [{ ...base, date: '2026-11-31' }], [{ ...base, location_id: '101' }], [base, base],
    ]) {
        await assert.rejects(loadOpen({ prices }), { code: 'MALFORMED_RESPONSE' });
    }
    await assert.rejects(loadOpen({ prices: gzipSync('a'.repeat(70_000)) }), { code: 'LIMIT_EXCEEDED' });
    await assert.rejects(loadOpen({ locations: fixture.locations.slice(1) }), { code: 'MALFORMED_RESPONSE' });
    const wrongCountry = [{ ...fixture.locations[0], osm_address_country_code: 'FR' }];
    await assert.rejects(loadOpen({ locations: wrongCountry }), { code: 'MALFORMED_RESPONSE' });
});

test('Open Prices preserves the decimal lexeme for JSON numbers', async () => {
    const row = JSON.stringify({ ...fixture.prices[0], price_per: '', price: '1.070' });
    const provider = await loadOpen({ prices: gzipSync(row.replace('"1.070"', '1.070')) });
    assert.equal(provider.listPrices(provider.getMetadata().stores[0].id).items[0].amount, '1.070');
});

test('Open Prices validates pilot limits and GTIN before network access', async () => {
    const requests = [];
    const plan = structuredClone(fixture.plan);
    plan.products[0].gtin = '0036000291453';
    await assert.rejects(loadOpen({ plan, requests }), { code: 'INVALID_CONFIGURATION' });
    await assert.rejects(loadOpen({ plan: { ...fixture.plan, from: '2025-01-01' }, requests }),
        { code: 'INVALID_CONFIGURATION' });
    assert.deepEqual(requests, []);
});

test('HTTP error states preserve bounded retry guidance without retries or redirects', async t => {
    const now = Date.parse('2026-11-18T12:00:00Z');
    for (const [status, code, retryable, header, expected] of [
        [401, 'UNAUTHENTICATED', false], [403, 'FORBIDDEN', false], [404, 'NOT_FOUND', false],
        [429, 'RATE_LIMITED', true, '90', '2026-11-18T12:01:30.000Z'],
        [500, 'UPSTREAM_ERROR', true],
        [503, 'UNAVAILABLE', true, 'Wed, 18 Nov 2026 12:10:00 GMT', '2026-11-18T12:10:00.000Z'],
        [302, 'HTTP_ERROR', false],
    ]) {
        await t.test(String(status), async () => {
            let attempts = 0;
            const fetchImpl = async () => {
                attempts++;
                return new Response(null, { status, headers: header ? { 'Retry-After': header } : {} });
            };
            await assert.rejects(download(ROOT, { fetchImpl, userAgent: USER_AGENT,
                timeoutMs: 1000, maxBytes: 100, now: () => now }), { code, status, retryable, retryAt: expected });
            assert.equal(attempts, 1);
        });
    }
});

test('transport handles streamed success, timeout, interrupted download and byte caps', async () => {
    const options = { userAgent: USER_AGENT, timeoutMs: 1000, maxBytes: 5 };
    const body = text => async () => new Response(text);
    assert.equal((await download(ROOT, { ...options, fetchImpl: body('ok') })).bytes.toString(), 'ok');
    await assert.rejects(download(ROOT, { ...options, fetchImpl: body('123456') }), { code: 'LIMIT_EXCEEDED' });
    await assert.rejects(download(ROOT, {
        ...options, fetchImpl: async () => new Response('ok', { headers: { 'Content-Length': '6' } }),
    }), { code: 'LIMIT_EXCEEDED' });

    const encoder = new TextEncoder();
    const interrupted = async () => new Response(new ReadableStream({
        start(controller) { controller.enqueue(encoder.encode('good')); },
        pull(controller) { controller.error(new TypeError('socket closed')); },
    }));
    await assert.rejects(download(ROOT, { ...options, fetchImpl: interrupted }), { code: 'NETWORK', retryable: true });

    const stalled = async (_url, { signal }) => new Response(new ReadableStream({
        start(controller) {
            controller.enqueue(encoder.encode('good'));
            signal.addEventListener('abort', () => controller.error(signal.reason), { once: true });
        },
    }));
    await assert.rejects(download(ROOT, { ...options, fetchImpl: stalled, timeoutMs: 30 }),
        { code: 'TIMEOUT', retryable: true });
});
