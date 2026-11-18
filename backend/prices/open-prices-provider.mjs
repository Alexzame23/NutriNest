import { createHash } from 'node:crypto';
import { Readable } from 'node:stream';
import { createGunzip } from 'node:zlib';
import { download } from './http-download.mjs';
import {
    PriceProvider, fixedProduct, gtinKey, isDate, isId, isLabel, regularStorePrice,
} from './price-provider.mjs';
import { ProviderError, requireField } from './provider-error.mjs';

const SOURCE = {
    id: 'open-prices', name: 'Open Prices', kind: 'OPEN_DATA',
    licenseRef: 'https://opendatacommons.org/licenses/odbl/1-0/',
    attribution: 'Open Prices contributors (https://prices.openfoodfacts.org/)',
};
const EXPORT_ROOT = 'https://prices.openfoodfacts.org/data/';
const MAX_COMPRESSED = 64 * 1024 * 1024;
const MAX_EXPANDED = 512 * 1024 * 1024;
const MAX_LINE_CHARS = 64 * 1024;
const positiveId = value => Number.isSafeInteger(value) && value > 0;

async function scanExport(file, visit) {
    const stream = Readable.from([file.bytes]).pipe(createGunzip());
    const decoder = new TextDecoder('utf-8', { fatal: true });
    let tail = '';
    let bytes = 0;
    let rows = 0;

    function readLine(line) {
        requireField(line.length <= MAX_LINE_CHARS, 'JSONL row size', 'LIMIT_EXCEEDED');
        // Keep the exact decimal when the upstream serializer emits a JSON number.
        const row = JSON.parse(line, (key, value, context) =>
            key === 'price' && typeof value === 'number' ? context.source : value);
        requireField(row && !Array.isArray(row) && positiveId(row.id), 'export row ID');
        visit(row);
        rows++;
    }

    try {
        for await (const chunk of stream) {
            bytes += chunk.length;
            requireField(bytes <= MAX_EXPANDED, 'expanded feed size', 'LIMIT_EXCEEDED');
            const lines = (tail + decoder.decode(chunk, { stream: true })).split('\n');
            tail = lines.pop();
            requireField(tail.length <= MAX_LINE_CHARS, 'JSONL row size', 'LIMIT_EXCEEDED');
            for (const line of lines) readLine(line);
        }
        tail += decoder.decode();
        if (tail) readLine(tail);
        requireField(rows > 0, 'empty export');
    } catch (error) {
        if (error instanceof ProviderError) throw error;
        if (error instanceof SyntaxError || error.code === 'ERR_ENCODING_INVALID_ENCODED_DATA' ||
            error.code?.startsWith('Z_')) {
            throw new ProviderError('MALFORMED_RESPONSE', 'Invalid gzip, UTF-8 or JSONL');
        }
        throw error;
    }
}

function pilotConfiguration(plan) {
    const { from, through, stores, products } = plan ?? {};
    const check = (condition, field) => requireField(condition, field, 'INVALID_CONFIGURATION');
    check(isDate(from) && isDate(through) && from <= through &&
        Date.parse(through) - Date.parse(from) < 90 * 86_400_000, 'pilot date window');
    check(Array.isArray(stores) && stores.length >= 1 && stores.length <= 3, 'pilot stores');
    check(Array.isArray(products) && products.length >= 1 && products.length <= 20, 'pilot products');
    const locations = new Map();
    const retailers = new Map();
    for (const row of stores) {
        check(row && positiveId(row.locationId) && positiveId(row.osmId) &&
            ['NODE', 'WAY', 'RELATION'].includes(row.osmType), 'store external identity');
        check(isId(row.retailerKey) && row.retailerKey.length <= 80 &&
            isLabel(row.name) && isLabel(row.retailerName), 'store labels');
        check(!locations.has(row.locationId), 'duplicate location ID');
        const retailerId = `${SOURCE.id}.retailer.${row.retailerKey}`;
        check(!retailers.has(retailerId) || retailers.get(retailerId).name === row.retailerName, 'retailer name');
        retailers.set(retailerId, {
            id: retailerId, name: row.retailerName, origin: { sourceId: SOURCE.id, recordKey: row.retailerKey },
        });
        locations.set(row.locationId, { ...row, retailerId });
    }
    const catalog = new Map();
    try {
        for (const row of products) {
            const item = fixedProduct(SOURCE.id, row);
            const key = gtinKey(item.package.gtin);
            check(!catalog.has(key), 'duplicate pilot GTIN');
            catalog.set(key, item);
        }
    } catch (error) {
        if (error.code === 'MALFORMED_RESPONSE') throw new ProviderError('INVALID_CONFIGURATION', error.message);
        throw error;
    }
    check(new Set([...catalog.values()].map(item => item.product.id)).size === catalog.size, 'duplicate pilot product ID');
    return { from, through, locations, catalog, retailers };
}

async function loadStores(file, locations) {
    const stores = new Map();
    await scanExport(file, row => {
        const configured = locations.get(row.id);
        if (!configured) return;
        requireField(!stores.has(row.id) && row.type === 'OSM' &&
            row.osm_address_country_code === 'ES' && row.osm_id === configured.osmId &&
            row.osm_type === configured.osmType, 'pilot store reference');
        const recordKey = String(row.id);
        const postalCode = row.osm_address_postcode;
        stores.set(row.id, {
            id: `${SOURCE.id}.store.${recordKey}`, origin: { sourceId: SOURCE.id, recordKey },
            retailerId: configured.retailerId, name: configured.name,
            postalCode: typeof postalCode === 'string' && /^\d{5}$/.test(postalCode) ? postalCode : null,
        });
    });
    requireField(stores.size === locations.size, 'missing pilot store');
    return stores;
}

async function loadPrices(file, stores, { from, through, catalog }) {
    const latest = new Map();
    const seen = new Set();
    await scanExport(file, row => {
        requireField(row.location_id === null || positiveId(row.location_id), 'price.location_id');
        const store = stores.get(row.location_id);
        if (!store) return;
        requireField(typeof row.type === 'string' &&
            (row.product_code === null || typeof row.product_code === 'string'), 'price product reference');
        if (row.type !== 'PRODUCT') return;
        const product = catalog.get(gtinKey(row.product_code));
        if (!product) return;

        requireField(row.date === null || isDate(row.date), 'price.date');
        requireField(typeof row.price_is_discounted === 'boolean' &&
            (row.duplicate_of === null || positiveId(row.duplicate_of)), 'price discount or duplicate');
        requireField((row.currency === null || (typeof row.currency === 'string' && /^[A-Z]{3}$/.test(row.currency))) &&
            (row.price_per === null || typeof row.price_per === 'string'), 'price unit or currency');
        if (row.date === null || row.date < from || row.date > through || row.currency !== 'EUR' ||
            (row.price_per !== null && row.price_per !== '') || row.price_is_discounted || row.duplicate_of !== null) return;

        requireField(!seen.has(row.id), 'duplicate price ID');
        requireField(seen.size < 10_000, 'pilot price rows', 'LIMIT_EXCEEDED');
        seen.add(row.id);
        requireField(typeof row.price === 'string' && /^(0|[1-9][0-9]{0,6})(\.[0-9]{1,3})?$/.test(row.price),
            'Open Prices decimal amount');
        const price = regularStorePrice(SOURCE.id, String(row.id), store, product, row.date, row.price, 'MEDIUM');
        const key = `${store.id}:${product.package.id}`;
        const previous = latest.get(key);
        if (!previous || row.date > previous.price.record.observedOn ||
            (row.date === previous.price.record.observedOn && row.id > previous.id)) {
            latest.set(key, { id: row.id, price });
        }
    });
    return [...latest.values()].sort((a, b) => a.id - b.id).map(row => row.price);
}

/** Internal pilot only. Uses official daily exports and reviewed package/location configuration. */
export class OpenPricesProvider extends PriceProvider {
    static async load(plan, { fetchImpl, userAgent } = {}) {
        const config = pilotConfiguration(plan);
        requireField(isLabel(userAgent) && /^[\x20-\x7e]{1,200}$/.test(userAgent),
            'backend User-Agent', 'INVALID_CONFIGURATION');
        const downloadOptions = { fetchImpl, userAgent, timeoutMs: 60_000, maxBytes: MAX_COMPRESSED };
        const inputs = [];

        async function readFile(name, parse) {
            const url = new URL(`${name}.jsonl.gz`, EXPORT_ROOT).href;
            const file = await download(url, downloadOptions);
            inputs.push({
                url, sha256: createHash('sha256').update(file.bytes).digest('hex'),
                byteLength: file.bytes.length, lastModified: file.lastModified,
            });
            return parse(file);
        }

        const stores = await readFile('locations', file => loadStores(file, config.locations));
        const prices = await readFile('prices', file => loadPrices(file, stores, config));
        return new OpenPricesProvider({
            source: SOURCE, simulated: false, usage: 'INTERNAL_PILOT',
            capabilities: ['CATALOG', 'PRODUCT', 'STORE_PRICES', 'METADATA'],
            coverage: { from: config.from, through: config.through, country: 'ES', currency: 'EUR' },
            retailers: [...config.retailers.values()], stores: [...stores.values()],
            retrievedAt: new Date().toISOString(), inputs,
        }, [...config.catalog.values()], prices);
    }
}
