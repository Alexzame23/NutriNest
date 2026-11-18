import { readFile } from 'node:fs/promises';
import { FakePriceProvider } from './fake-price-provider.mjs';
import { OpenPricesProvider } from './open-prices-provider.mjs';
import { ProviderError } from './provider-error.mjs';

async function loadProvider(args) {
    if (args.length === 1 && args[0] === 'fake') return FakePriceProvider.load();
    if (args.length === 2 && args[0] === 'open-prices') {
        let plan;
        try {
            plan = JSON.parse(await readFile(args[1], 'utf8'));
        } catch {
            throw new ProviderError('INVALID_CONFIGURATION', 'Cannot read the pilot plan as JSON');
        }
        return OpenPricesProvider.load(plan, { userAgent: process.env.NUTRINEST_PRICE_USER_AGENT });
    }
    throw new ProviderError('INVALID_CONFIGURATION', 'Usage: node backend/prices/run-pilot.mjs fake | open-prices <plan.json>');
}

try {
    const provider = await loadProvider(process.argv.slice(2));
    const metadata = provider.getMetadata();
    console.log(JSON.stringify({
        ...metadata,
        catalogProducts: provider.listCatalog({ limit: 100 }).items.length,
        priceCounts: metadata.stores.map(({ id }) => ({ storeId: id, count: provider.listPrices(id, { limit: 100 }).items.length })),
    }, null, 2));
} catch (error) {
    const { code, message, status, retryable, retryAt } = error instanceof ProviderError
        ? error
        : new ProviderError('INTERNAL_ERROR', 'Unable to load the provider batch');
    console.error(JSON.stringify({ code, message, status, retryable, retryAt }));
    process.exitCode = retryable ? 75 : 1;
}
