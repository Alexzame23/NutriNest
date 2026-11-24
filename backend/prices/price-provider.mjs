import { ProviderError, requireField } from './provider-error.mjs';

export const isId = value => typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$/.test(value);
export const isLabel = value => typeof value === 'string' && /\S/.test(value);
export const isAmount = value => typeof value === 'string' && /^(0|[1-9][0-9]{0,8})(\.[0-9]{1,4})?$/.test(value);

export function isDate(value) {
    if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value) || value.startsWith('0000')) {
        return false;
    }
    const time = Date.parse(value);
    return Number.isFinite(time) && new Date(time).toISOString().slice(0, 10) === value;
}

export function gtinKey(value) {
    if (typeof value !== 'string' || !/^(\d{8}|\d{12,14})$/.test(value) || !/[1-9]/.test(value)) return null;
    const sum = [...value].reverse().reduce((total, digit, i) => total + Number(digit) * (i % 2 ? 3 : 1), 0);
    return sum % 10 === 0 ? value.padStart(14, '0') : null;
}

export function fixedProduct(sourceId, row) {
    const { externalId, gtin, name, brand = null, netContent, unitsPerPackage } = row ?? {};
    requireField(isId(externalId) && sourceId.length + externalId.length + 9 <= 128, 'product.externalId');
    requireField((gtin == null || gtinKey(gtin)) && isLabel(name) && (brand === null || isLabel(brand)), 'product identity');
    const sku = row.retailerSku;
    requireField(sku == null || (isId(sku.retailerId) && isLabel(sku.value)), 'retailer SKU');
    const { amount, unit } = netContent ?? {};
    requireField(isAmount(amount) && Number(amount) > 0 && !/\.\d{4}$/.test(amount), 'netContent.amount');
    requireField(['G', 'ML', 'UNIT'].includes(unit) &&
        (unit !== 'UNIT' || Number.isInteger(Number(amount))), 'netContent.unit');
    requireField(Number.isInteger(unitsPerPackage) && unitsPerPackage > 0 &&
        unitsPerPackage <= 2147483647, 'unitsPerPackage');
    const productId = `${sourceId}.product.${externalId}`;
    return {
        product: { id: productId, origin: { sourceId, recordKey: externalId }, name, brand, attributes: {} },
        package: {
            id: `${sourceId}.pack.${externalId}`, origin: { sourceId, recordKey: externalId },
            productId, gtin: gtin ?? null, ...(sku == null ? {} : { retailerSku: { retailerId: sku.retailerId, value: sku.value } }),
            weightMode: 'FIXED', saleUnit: 'PACKAGE', netContent: { amount, unit }, unitsPerPackage,
        },
    };
}

export function regularStorePrice(sourceId, externalId, store, product, observedOn, amount, confidence) {
    requireField(isId(externalId), 'price external ID');
    requireField(isDate(observedOn), 'price date');
    requireField(isAmount(amount), 'price decimal amount');
    return {
        record: {
            origin: { sourceId, recordKey: externalId }, retailerId: store.retailerId,
            packageId: product.package.id, scope: { type: 'STORE', storeId: store.id }, observedOn, confidence,
        },
        amount, currency: 'EUR', per: 'PACKAGE', kind: 'REGULAR',
    };
}

function page(items, pagination = {}) {
    requireField(pagination && typeof pagination === 'object' && !Array.isArray(pagination),
        'pagination', 'INVALID_QUERY');
    const { limit = 25, offset = 0 } = pagination;
    requireField(Number.isInteger(limit) && limit >= 1 && limit <= 100 &&
        Number.isSafeInteger(offset) && offset >= 0 && offset <= items.length, 'pagination', 'INVALID_QUERY');
    const end = offset + limit;
    return { items: structuredClone(items.slice(offset, end)), nextOffset: end < items.length ? end : null };
}

/** Read contract for one validated, immutable ingestion batch. Queries perform no I/O. */
export class PriceProvider {
    #metadata;
    #catalog;
    #products;
    #prices;

    constructor(metadata, catalog, prices) {
        this.#metadata = structuredClone(metadata);
        this.#catalog = structuredClone(catalog);
        this.#products = new Map(this.#catalog.map(item => [item.product.origin.recordKey, item]));
        this.#prices = structuredClone(prices);
        requireField(this.#products.size === this.#catalog.length, 'duplicate product ID');
    }

    getMetadata() {
        return structuredClone(this.#metadata);
    }

    listCatalog(pagination) {
        return page(this.#catalog, pagination);
    }

    getProduct(externalId) {
        requireField(isId(externalId), 'external product ID', 'INVALID_QUERY');
        const item = this.#products.get(externalId);
        if (!item) throw new ProviderError('NOT_FOUND', 'Product is absent from this batch');
        return structuredClone(item);
    }

    listPrices(storeId, pagination) {
        requireField(isId(storeId), 'store ID', 'INVALID_QUERY');
        if (!this.#metadata.stores.some(store => store.id === storeId)) {
            throw new ProviderError('NOT_FOUND', 'Store is outside this batch');
        }
        return page(this.#prices.filter(price => price.record.scope.storeId === storeId), pagination);
    }
}
