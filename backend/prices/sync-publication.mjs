import { createHash } from 'node:crypto';
import { hasRights, priceStatus, priceTime } from './sync-policy.mjs';

const sha256 = data => createHash('sha256').update(data).digest('hex');

export function updatePublication(store, state, policy, now) {
    const { source, retailers, stores } = state.metadata;
    const current = state.quotes.filter(quote => priceStatus(quote, policy, now) === 'CURRENT');
    const content = {
        sources: [source], retailers, stores, zones: [],
        products: state.catalog.map(entry => entry.product),
        packages: state.catalog.map(entry => entry.package),
        matches: [], availability: [], prices: current.map(({ price, fetchedAt }) => ({
            ...price, record: { ...price.record, fetchedAt },
        })),
        dataKind: state.metadata.simulated ? 'simulated' :
            state.metadata.simulated === false && source.id === 'open-prices' &&
                state.metadata.verifiedLocationPage === true && current.length > 0
                ? 'live' : 'unverified',
        coverage: {
            regionId: policy.regionId, observations: current.length,
            storesWithPrices: new Set(current.map(q => q.price.record.scope.storeId)).size,
            packagesWithPrices: new Set(current.map(q => q.price.record.packageId)).size,
            plannedStores: stores.length, plannedPackages: state.catalog.length,
        },
    };
    const contentsHash = sha256(JSON.stringify({ scopeKey: state.scopeKey, content }));
    const expiresAt = Math.min(state.lastSuccessAt + policy.snapshotMs, policy.rightsUntil,
        ...current.map(quote => priceTime(quote.price.record) + policy.freshMs));
    if (expiresAt <= now) return;
    const previous = store.publication();
    if (previous?.contents_hash === contentsHash && previous.expires_at > now &&
        (previous.expires_at === expiresAt ||
        (previous.expires_at < expiresAt && previous.expires_at - now >= policy.cadenceMs))) return;

    const revision = (previous?.revision ?? 0) + 1;
    if (!Number.isSafeInteger(revision)) throw new Error('Publication revision overflow');
    const header = {
        contractVersion: 1, regionId: policy.regionId, revision,
        generatedAt: new Date(now).toISOString(), expiresAt: new Date(expiresAt).toISOString(),
    };
    const snapshot = JSON.stringify({ ...header, ...content });
    const byteLength = Buffer.byteLength(snapshot);
    if (byteLength > 1_048_576) throw new Error('Publication exceeds the phase 11 size limit');
    const manifest = JSON.stringify({ ...header, sha256: sha256(snapshot), byteLength });
    store.publish(revision, expiresAt, contentsHash, manifest, snapshot);
    if (previous && (previous.contents_hash !== contentsHash || previous.expires_at > expiresAt)) {
        store.withdrawPrevious(revision);
    }
    store.retireExpired(now);
}

export function readPublication(store, policy, now, revision) {
    const item = store.publicationView(revision);
    if (item.latest_revision === null || (revision !== undefined && revision > item.latest_revision)) return { status: 404 };
    const inactive = !item.snapshot || item.expires_at <= now || !hasRights(policy, now) ||
        JSON.parse(item.data).scopeKey !== policy.scopeKey;
    if (inactive) return { status: revision === undefined ? 404 : 410 };
    const snapshot = JSON.parse(item.snapshot);
    const allowedUntil = Math.min(Date.parse(snapshot.generatedAt) + policy.snapshotMs, policy.rightsUntil,
        ...snapshot.prices.map(price => priceTime(price.record) + policy.freshMs));
    if (item.expires_at > allowedUntil) {
        return { status: revision === undefined ? 404 : 410 };
    }
    const body = revision === undefined ? item.manifest : item.snapshot;
    return { status: 200, body, dataKind: snapshot.dataKind ?? 'unverified', etag: `"${sha256(body)}"` };
}
