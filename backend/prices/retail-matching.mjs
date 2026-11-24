import registry from '../../app/src/main/assets/retail-canonical-profiles.json' with { type: 'json' };
import labels from './reviewed-labels.json' with { type: 'json' };
import { digest } from './matching-evidence.mjs';
import { rulesVersion } from './matching-rules.mjs';

export const matchingRevision = digest([rulesVersion, registry, labels]);

export const editorialSource = {
    id: 'nutrinest-editorial', name: 'NutriNest', kind: 'AUTHORIZED_IMPORT',
    licenseRef: 'NutriNest editorial matching', attribution: 'NutriNest · equivalencias culinarias',
};

export function retailMatchingBatch(catalog) {
    return {
        ingredients: registry.profiles.map(profile => ({
            ...profile, reviewed: true, revision: registry.revision, sourceSha256: registry.sourceSha256,
        })),
        catalog: catalog.map(({ product, package: pack }) => ({
            product, package: pack,
            facts: labels.find(label => label.providerId === product.origin.sourceId &&
                label.providerProductId === product.origin.recordKey),
        })),
    };
}

// Count observations, independently of how many canonical candidates a pack generated.
export function matchingCoverage(prices, rows) {
    const byPackage = Map.groupBy(rows.filter(row => row.decision.status !== 'WITHDRAWN'), row => row.packageId);
    const counts = { received: prices.length, candidates: 0, accepted: 0, ambiguous: 0, incompatible: 0, noCandidate: 0 };
    for (const { record } of prices) {
        const candidates = byPackage.get(record.packageId) ?? [];
        if (!candidates.length) { counts.noCandidate++; continue; }
        counts.candidates++;
        if (candidates.some(row => row.decision.status === 'ACCEPTED')) counts.accepted++;
        else if (candidates.some(row => row.decision.status === 'PENDING')) counts.ambiguous++;
        else counts.incompatible++;
    }
    return counts;
}
