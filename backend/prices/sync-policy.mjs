import { createHash } from 'node:crypto';
import { isId, isLabel } from './price-provider.mjs';
import { requireField } from './provider-error.mjs';

export const HOUR = 3_600_000;
export const DAY = 24 * HOUR;
export const POLL_MS = 60_000;

export function syncPolicy(config, userAgent) {
    const check = (condition, field) => requireField(condition, field, 'INVALID_CONFIGURATION');
    const { source = 'fake', regionId, enabled = true, freshDays = 30, retainDays = 90,
        historyDays = 365, cadenceHours = 24, snapshotHours = 36, requestsPer24Hours = 6 } = config ?? {};
    const integerIn = (value, min, max) => Number.isInteger(value) && value >= min && value <= max;
    check(['fake', 'open-prices'].includes(source) && isId(regionId) && typeof enabled === 'boolean', 'source and region');
    check(integerIn(freshDays, 1, 30) && integerIn(retainDays, freshDays, 90) &&
        integerIn(historyDays, retainDays, 365), 'price retention');
    check(integerIn(cadenceHours, source === 'fake' ? 1 : 24, 168) && integerIn(snapshotHours, 1, 48),
        'ingestion cadence and snapshot TTL');
    check(integerIn(requestsPer24Hours, 2, 6), 'request budget');
    const rightsUntil = source === 'fake' ? Infinity : Date.parse(config.rightsUntil);
    if (source === 'open-prices' && enabled) {
        const zonedExpiry = typeof config.rightsUntil === 'string' &&
            /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?(?:Z|[+-]\d{2}:\d{2})$/.test(config.rightsUntil);
        check(config.approvedPilot === true && zonedExpiry && Number.isFinite(rightsUntil) &&
            Array.isArray(config.plan?.stores) && Array.isArray(config.plan?.products), 'approved internal pilot plan and expiry');
        check(isLabel(userAgent) && /^[\x20-\x7e]{1,200}$/.test(userAgent), 'backend User-Agent');
    }
    return {
        source, sourceId: source === 'fake' ? 'fake-prices' : source, regionId, enabled, rightsUntil,
        cadenceMs: cadenceHours * HOUR, freshMs: freshDays * DAY, retainMs: retainDays * DAY,
        historyMs: historyDays * DAY, snapshotMs: snapshotHours * HOUR, requestsPer24Hours,
        scopeKey: createHash('sha256').update(JSON.stringify(config.plan ?? null)).digest('hex'),
        maxAttempts: 3, baseRetryMs: 5 * 60_000, plan: config.plan, userAgent,
    };
}

export function hasRights(policy, now) {
    return policy.enabled && now < policy.rightsUntil;
}

export function priceTime(record) {
    return Date.parse(record.observedAt ?? record.observedOn);
}

export function priceStatus(quote, policy, now) {
    if (!quote || !hasRights(policy, now) || priceTime(quote.price.record) + policy.retainMs <= now) return 'UNVERIFIED';
    if (quote.missing || priceTime(quote.price.record) + policy.freshMs <= now) return 'STALE';
    return 'CURRENT';
}
