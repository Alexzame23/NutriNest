import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { PriceProvider, fixedProduct, isLabel, regularStorePrice } from './price-provider.mjs';
import { ProviderError, requireField } from './provider-error.mjs';

const SOURCE = {
    id: 'fake-prices', name: 'Precios simulados de NutriNest', kind: 'AUTHORIZED_IMPORT',
    licenseRef: 'NutriNest test fixture', attribution: 'NutriNest (datos simulados)',
};

export class FakePriceProvider extends PriceProvider {
    static async load() {
        const bytes = await readFile(new URL('./fixtures/fake-prices.simulated.json', import.meta.url));
        let fixture;
        try {
            fixture = JSON.parse(bytes);
        } catch (error) {
            if (!(error instanceof SyntaxError)) throw error;
            throw new ProviderError('MALFORMED_RESPONSE', 'Invalid simulated fixture JSON');
        }
        requireField(fixture?.simulated === true && isLabel(fixture.retailerName) && isLabel(fixture.storeName) &&
            Array.isArray(fixture.products) && Array.isArray(fixture.prices), 'simulated fixture');
        const retailer = {
            id: 'fake-prices.retailer.fixture', origin: { sourceId: SOURCE.id, recordKey: 'fixture-retailer' },
            name: fixture.retailerName,
        };
        const store = {
            id: 'fake-prices.store.fixture', origin: { sourceId: SOURCE.id, recordKey: 'fixture-store' },
            retailerId: retailer.id, name: fixture.storeName,
        };
        const catalog = fixture.products.map(row => fixedProduct(SOURCE.id, row));
        const products = new Map(catalog.map(item => [item.product.origin.recordKey, item]));
        const prices = fixture.prices.map(row => {
            const product = products.get(row?.productExternalId);
            requireField(product, 'simulated price product');
            return regularStorePrice(SOURCE.id, row.externalId, store, product, row.observedOn, row.amount, 'HIGH');
        });
        requireField(new Set(prices.map(price => price.record.origin.recordKey)).size === prices.length,
            'duplicate simulated price ID');
        return new FakePriceProvider({
            source: SOURCE, simulated: true, usage: 'TEST_ONLY',
            capabilities: ['CATALOG', 'PRODUCT', 'STORE_PRICES', 'METADATA'],
            retailers: [retailer], stores: [store],
            inputs: [{ sha256: createHash('sha256').update(bytes).digest('hex'), byteLength: bytes.length }],
        }, catalog, prices);
    }
}
