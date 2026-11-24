import { FakePriceProvider } from './fake-price-provider.mjs';
import { OpenPricesProvider } from './open-prices-provider.mjs';
import { loadLocationPage } from './open-prices-location.mjs';
import { ProviderError, requireField } from './provider-error.mjs';
import { DAY, hasRights, priceStatus, priceTime } from './sync-policy.mjs';
import { updatePublication } from './sync-publication.mjs';
import { matchingRevision } from './retail-matching.mjs';

const quoteKey = (storeId, packageId) => `${storeId}:${packageId}`;
const keyOf = price => quoteKey(price.record.scope.storeId, price.record.packageId);
const date = ms => new Date(ms).toISOString();
const commercialValue = price => JSON.stringify([
    price.amount.includes('.') ? price.amount.replace(/0+$/, '').replace(/\.$/, '') : price.amount,
    price.currency, price.per, price.kind, price.eligibility ?? null, price.validUntil ?? null,
]);

function newer(a, b) {
    const age = priceTime(a.record) - priceTime(b.record);
    if (age !== 0) return age > 0;
    const aKey = a.record.origin.recordKey;
    const bKey = b.record.origin.recordKey;
    return /^\d+$/.test(aKey) && /^\d+$/.test(bKey) ? BigInt(aKey) > BigInt(bKey) : aKey > bKey;
}

function catalogAndPrices(provider, policy, now) {
    const metadata = provider.getMetadata();
    const { items: catalog, nextOffset } = provider.listCatalog({ limit: 100 });
    requireField(metadata.source.id === policy.sourceId && catalog.length <= 20 && nextOffset === null &&
        metadata.stores.length <= 3, 'bounded provider batch');
    const packages = new Map(catalog.map(entry => [entry.package.id, entry.package]));
    const prices = new Map();
    const records = new Map();
    for (const store of metadata.stores) {
        const page = provider.listPrices(store.id, { limit: 100 });
        requireField(page.nextOffset === null && page.items.length <= 60, 'bounded store prices');
        for (const price of page.items) {
            const { record } = price;
            requireField(record.origin.sourceId === policy.sourceId && record.scope.type === 'STORE' &&
                record.scope.storeId === store.id && record.retailerId === store.retailerId &&
                packages.get(record.packageId)?.saleUnit === price.per && Number.isFinite(priceTime(record)) &&
                priceTime(record) <= now, 'price provenance');
            const recordKey = record.origin.recordKey;
            const serialized = JSON.stringify(price);
            requireField(!records.has(recordKey) || records.get(recordKey) === serialized, 'conflicting price ID');
            records.set(recordKey, serialized);
            const key = keyOf(price);
            const existing = prices.get(key);
            if (!existing || newer(price, existing)) prices.set(key, price);
        }
    }
    return { metadata, catalog, prices };
}

function mergeQuotes(store, state, batch, policy, now) {
    const old = new Map(state.quotes.map(quote => [keyOf(quote.price), quote]));
    const next = [];
    for (const shop of batch.metadata.stores) {
        for (const { package: pack } of batch.catalog) {
            const key = quoteKey(shop.id, pack.id);
            const previous = old.get(key);
            const candidate = batch.prices.get(key);
            const retained = previous && state.scopeKey === policy.scopeKey &&
                priceTime(previous.price.record) + policy.retainMs > now;
            if (!candidate || priceTime(candidate.record) + policy.retainMs <= now ||
                (retained && candidate.record.origin.recordKey !== previous.price.record.origin.recordKey &&
                newer(previous.price, candidate))) {
                if (retained) next.push({ ...previous, missing: true });
                continue;
            }
            const revision = store.registerEvidence(candidate);
            const sameEvidence = previous?.price.record.origin.recordKey === candidate.record.origin.recordKey &&
                previous.price.record.revision === revision;
            const quote = sameEvidence ? { ...previous, missing: false } : {
                price: { ...candidate, record: { ...candidate.record, observedAt: candidate.record.observedAt ?? null, revision } },
                regionId: policy.regionId, fetchedAt: date(now), missing: false,
                inputs: batch.metadata.inputs,
            };
            if (!previous || commercialValue(previous.price) !== commercialValue(candidate)) {
                store.addHistory(now, quote);
                state.counts.changes++;
            }
            next.push(quote);
        }
    }
    return next;
}

function nextAfterFailure(state, policy, now, retryable, retryAt = 0) {
    const nextCycle = state.cycleAt + policy.cadenceMs;
    const nextRetry = Math.max(now + policy.baseRetryMs * 2 ** (state.attempts - 1), retryAt);
    if (retryable && state.attempts < policy.maxAttempts && nextRetry < nextCycle) {
        return nextRetry;
    }
    const nextAt = Math.max(nextCycle, now + policy.baseRetryMs, retryAt);
    state.cycleAt = null;
    state.attempts = 0;
    return nextAt;
}

function loadScheduledProvider(policy, { now, cycleAt, previous, beforeRequest }) {
    if (policy.source === 'fake') return FakePriceProvider.load();
    const load = policy.plan.transport === 'location-api' ? loadLocationPage : OpenPricesProvider.load;
    return load({
        ...policy.plan,
        from: date(cycleAt - policy.retainMs + DAY).slice(0, 10),
        through: date(cycleAt).slice(0, 10),
    }, { userAgent: policy.userAgent, previous, beforeRequest, now });
}

export function syncPrices(store, policy, { now = Date.now, load = options => loadScheduledProvider(policy, options) } = {}) {
    return store.exclusive(async () => {
        const started = now();
        const state = store.read();
        if ((state.lastCleanupAt ?? 0) + DAY <= started) {
            store.transaction(() => {
                state.quotes = state.quotes.filter(quote => priceTime(quote.price.record) + policy.retainMs > started);
                if (state.revalidation) {
                    state.revalidation.result.prices = state.revalidation.result.prices
                        .filter(price => priceTime(price.record) + policy.retainMs > started);
                }
                state.lastCleanupAt = started;
                store.retainHistory(started - policy.historyMs);
                store.retireExpired(started);
                store.save(state);
            });
        }
        if (!hasRights(policy, started)) return { status: 'DISABLED' };
        if (state.nextAt > started) {
            if (state.metadata && state.scopeKey === policy.scopeKey && state.lastSuccessAt + policy.snapshotMs > started &&
                (state.matchingRevision !== matchingRevision || store.publication()?.expires_at <= started)) {
                store.transaction(() => {
                    updatePublication(store, state, policy, started);
                    state.matchingRevision = matchingRevision;
                    store.save(state);
                });
            }
            return { status: 'NOT_DUE', nextAt: state.nextAt };
        }
        state.cycleAt ??= started;
        const cycleAt = state.cycleAt;
        state.attempts++;
        state.counts.attempts++;
        state.lastAttemptAt = started;
        state.outcome = 'RUNNING';
        // Persist crash recovery and the attempt before any call leaves the process.
        const recovery = { ...state };
        state.nextAt = nextAfterFailure(recovery, policy, started + 3 * 60_000, true);
        const attempts = state.attempts;
        store.save({ ...state, cycleAt: recovery.cycleAt, attempts: recovery.attempts });
        const beforeRequest = () => {
            const time = now();
            if (!hasRights(policy, time)) throw new ProviderError('DISABLED', 'Source authorization is inactive');
            state.requestTimes = state.requestTimes.filter(at => at > time - DAY);
            if (state.requestTimes.length >= policy.requestsPer24Hours) {
                throw new ProviderError('BUDGET_EXHAUSTED', 'Provider request budget exhausted', {
                    retryable: true, retryAt: date(state.requestTimes[0] + DAY),
                });
            }
            state.requestTimes.push(time);
            state.counts.requests++;
            store.save({ ...state, cycleAt: recovery.cycleAt, attempts: recovery.attempts });
        };

        try {
            const provider = await load({ now, cycleAt, beforeRequest, previous: state.revalidation });
            const completed = now();
            if (!hasRights(policy, completed)) throw new ProviderError('DISABLED', 'Source authorization is inactive');
            const batch = catalogAndPrices(provider, policy, completed);
            const revalidation = provider.getRevalidation?.() ?? null;
            store.transaction(() => {
                state.quotes = mergeQuotes(store, state, batch, policy, completed);
                state.scopeKey = policy.scopeKey;
                state.metadata = batch.metadata;
                state.catalog = batch.catalog;
                state.revalidation = revalidation;
                state.lastSuccessAt = completed;
                state.outcome = 'SUCCEEDED';
                delete state.lastError;
                state.counts.successes++;
                state.counts.notModified += (batch.metadata.inputs ?? []).filter(input => input.status === 304).length;
                state.nextAt = cycleAt + policy.cadenceMs > completed
                    ? cycleAt + policy.cadenceMs : completed + policy.cadenceMs;
                state.cycleAt = null;
                state.attempts = 0;
                updatePublication(store, state, policy, completed);
                state.matchingRevision = matchingRevision;
                store.retainHistory(completed - policy.historyMs);
                store.save(state);
            });
            return { status: 'SUCCEEDED', nextAt: state.nextAt };
        } catch (error) {
            const failed = store.read();
            failed.cycleAt = cycleAt;
            failed.attempts = attempts;
            failed.outcome = 'FAILED';
            failed.lastError = error instanceof ProviderError ? error.code : 'INTERNAL_ERROR';
            failed.counts.failures++;
            failed.nextAt = nextAfterFailure(failed, policy, now(), error.retryable === true, Date.parse(error.retryAt) || 0);
            store.save(failed);
            return { status: 'FAILED', error: failed.lastError, nextAt: failed.nextAt };
        }
    });
}

export function cachedQuotes(state, policy, now) {
    if (!state.metadata || !hasRights(policy, now) || state.scopeKey !== policy.scopeKey) return [];
    const quotes = new Map(state.quotes.map(quote => [keyOf(quote.price), quote]));
    return state.metadata.stores.flatMap(shop => state.catalog.map(({ package: pack }) => {
        const quote = quotes.get(quoteKey(shop.id, pack.id));
        const status = priceStatus(quote, policy, now);
        return { storeId: shop.id, packageId: pack.id, status, quote: status === 'UNVERIFIED' ? null : quote };
    }));
}
