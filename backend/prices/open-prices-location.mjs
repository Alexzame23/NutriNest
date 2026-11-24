import { createHash } from 'node:crypto';
import { download } from './http-download.mjs';
import { PriceProvider, fixedProduct, gtinKey, isDate, isId, isLabel, regularStorePrice } from './price-provider.mjs';
import { ProviderError, requireField } from './provider-error.mjs';

// Server-side port of R2's Location query. Only reviewed packages at the configured OSM shop qualify.
export async function loadLocationPage(plan, { beforeRequest, now, userAgent, fetchImpl }) {
    requireField(plan.stores.length === 1 && plan.products.length > 0 && plan.products.length <= 20,
        'location pilot scope', 'INVALID_CONFIGURATION');
    const [shop] = plan.stores;
    requireField(Number.isSafeInteger(shop.locationId) && shop.locationId > 0 && Number.isSafeInteger(shop.osmId) &&
        shop.osmId > 0 && ['NODE', 'WAY', 'RELATION'].includes(shop.osmType) && isId(shop.retailerKey) &&
        shop.retailerKey.length <= 80 && isLabel(shop.name) && isLabel(shop.retailerName) &&
        /^\d{5}$/.test(shop.postalCode), 'reviewed shop identity', 'INVALID_CONFIGURATION');
    const sourceId = 'open-prices';
    const catalog = plan.products.map(row => fixedProduct(sourceId, row));
    const byCode = new Map(catalog.map(item => [item.package.gtin, item]));
    requireField(catalog.every(item => gtinKey(item.package.gtin)) &&
        byCode.size === catalog.length && new Set(catalog.map(item => item.product.id)).size === catalog.length,
        'duplicate reviewed product', 'INVALID_CONFIGURATION');
    const url = new URL('https://prices.openfoodfacts.org/api/v1/prices');
    url.search = new URLSearchParams({ location_id: shop.locationId, currency: 'EUR', order_by: '-date,-id', size: 10 });
    const file = await download(url.href, { beforeRequest, now, userAgent, fetchImpl, maxBytes: 512 * 1024, timeoutMs: 5000 });
    let page;
    try {
        page = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(file.bytes),
            (key, value, context) => key === 'price' && typeof value === 'number' ? context.source : value);
    } catch {
        throw new ProviderError('MALFORMED_RESPONSE', 'Invalid Open Prices JSON');
    }
    requireField(Array.isArray(page?.items) && page.items.length <= 10 && page.page === 1 &&
        Number.isSafeInteger(page.total) && page.total >= page.items.length, 'Open Prices page');

    const origin = key => ({ sourceId, recordKey: String(key) });
    const retailer = { id: sourceId + '.retailer.' + shop.retailerKey, name: shop.retailerName, origin: origin(shop.retailerKey) };
    const store = { id: sourceId + '.store.' + shop.locationId, name: shop.name, origin: origin(shop.locationId),
        retailerId: retailer.id, postalCode: shop.postalCode, osmRef: shop.osmType.toLowerCase() + '/' + shop.osmId };
    const prices = [];
    for (const row of page.items) {
        const location = row.location;
        requireField(row.location_id === shop.locationId && location?.id === shop.locationId &&
            location.type === 'OSM' && location.osm_type === shop.osmType && location.osm_id === shop.osmId &&
            location.osm_tag_key === 'shop' && location.osm_brand === shop.retailerName &&
            location.osm_address_country_code === 'ES' && location.osm_address_postcode === shop.postalCode,
            'Open Prices location');
        const item = byCode.get(row.product_code);
        if (!item || row.type !== 'PRODUCT' || row.currency !== 'EUR' || row.price_is_discounted !== false ||
            row.duplicate_of !== null || (row.price_per !== null && row.price_per !== '') ||
            !isDate(row.date) || row.date < plan.from || row.date > plan.through) continue;
        const { product, package: pack } = item;
        const details = row.product;
        requireField(Number.isSafeInteger(row.id) && row.id > 0 && details?.code === pack.gtin &&
            details.product_name === product.name && details.brands === product.brand &&
            String(details.product_quantity) === pack.netContent.amount &&
            details.product_quantity_unit?.toUpperCase() === pack.netContent.unit, 'reviewed product metadata');
        const price = regularStorePrice(sourceId, String(row.id), store, item, row.date, row.price, 'LOW');
        if (Number.isSafeInteger(row.proof_id) && row.proof_id > 0) price.record.proofId = row.proof_id;
        prices.push(price);
    }
    return new PriceProvider({
        source: { id: sourceId, name: 'Open Prices', kind: 'OPEN_DATA',
            licenseRef: 'https://opendatacommons.org/licenses/odbl/1-0/',
            attribution: 'Open Prices and Open Food Facts contributors; location data © OpenStreetMap contributors (ODbL 1.0)' },
        simulated: fetchImpl !== undefined, verifiedLocationPage: fetchImpl === undefined,
        retailers: [retailer], stores: [store],
        inputs: [{ url: url.href, sha256: createHash('sha256').update(file.bytes).digest('hex'), byteLength: file.bytes.length }],
    }, catalog, prices);
}
