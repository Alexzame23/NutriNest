import { createHash } from 'node:crypto';

// Snapshot the exact provider record inspected by the editor (including variant and pack).
export const stableJson = value => JSON.stringify(value, (_, v) => v && typeof v === 'object' && !Array.isArray(v)
    ? Object.fromEntries(Object.keys(v).sort().map(k => [k, v[k]])) : v);
export const digest = value => createHash('sha256').update(stableJson(value)).digest('hex');
export const labelDigest = ({ product, package: pack }) => digest({ product, package: pack });
