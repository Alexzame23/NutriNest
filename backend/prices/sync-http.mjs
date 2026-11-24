import { createServer } from 'node:http';
import { createServer as createTlsServer } from 'node:https';
import { cachedQuotes } from './price-sync.mjs';
import { readPublication } from './sync-publication.mjs';
import { DAY, hasRights } from './sync-policy.mjs';

const json = (status, data) => ({
    status, body: JSON.stringify(data), headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
});

export function priceResponse(store, policy, { method, path, ifNoneMatch }, now) {
    if (method !== 'GET') return { status: 405, headers: { Allow: 'GET' } };
    if (path === '/healthz') {
        const state = store.read();
        const active = hasRights(policy, now);
        const ready = active && state.scopeKey === policy.scopeKey && state.outcome !== 'FAILED' &&
            !!state.lastSuccessAt && now - state.lastSuccessAt < policy.cadenceMs + 15 * 60_000;
        return json(200, { status: active ? (ready ? 'ok' : 'degraded') : 'disabled' });
    }
    if (path === '/metrics') {
        const state = store.read();
        const statuses = { CURRENT: 0, STALE: 0, UNVERIFIED: 0 };
        for (const quote of cachedQuotes(state, policy, now)) statuses[quote.status]++;
        return json(200, {
            source: policy.sourceId, region: policy.regionId, outcome: state.outcome,
            ...state.counts, quotes: statuses,
            requestsInLast24Hours: state.requestTimes.filter(at => at > now - DAY).length,
            requestBudget: policy.requestsPer24Hours,
            lastAttemptAt: state.lastAttemptAt ?? null, lastSuccessAt: state.lastSuccessAt ?? null,
            nextAt: state.nextAt || null, lastError: state.lastError ?? null,
        });
    }
    if (path === `/internal/price-regions/${policy.regionId}/quotes` && hasRights(policy, now)) {
        return json(200, { regionId: policy.regionId, quotes: cachedQuotes(store.read(), policy, now) });
    }
    const manifest = path === `/v1/price-regions/${policy.regionId}/manifest`;
    const match = /^\/v1\/price-regions\/([^/]+)\/snapshots\/([1-9][0-9]{0,15})$/.exec(path);
    const revision = Number(match?.[2]);
    if (!manifest && (match?.[1] !== policy.regionId || !Number.isSafeInteger(revision))) return { status: 404 };
    const response = readPublication(store, policy, now, manifest ? undefined : revision);
    if (response.status !== 200) return { status: response.status, headers: { 'Cache-Control': 'no-store' } };
    const headers = {
        ETag: response.etag, 'Cache-Control': 'private, no-cache', 'Content-Type': 'application/json',
        'X-NutriNest-Price-Data': response.dataKind,
    };
    const validators = ifNoneMatch?.split(',').map(tag => tag.trim().replace(/^W\//, '')) ?? [];
    if (validators.includes('*') || validators.includes(response.etag)) return { status: 304, headers };
    return { status: 200, headers, body: response.body };
}

export function priceServer(store, policy, tls) {
    const handle = (request, response) => {
        let result;
        try {
            result = priceResponse(store, policy, {
                method: request.method, path: new URL(request.url, 'http://localhost').pathname,
                ifNoneMatch: request.headers['if-none-match'],
            }, Date.now());
        } catch {
            result = json(503, { status: 'unavailable' });
            result.headers['Retry-After'] = '60';
        }
        response.writeHead(result.status, result.headers ?? {});
        response.end(result.body);
    };
    return tls ? createTlsServer(tls, handle) : createServer(handle);
}
