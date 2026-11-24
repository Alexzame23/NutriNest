import { DatabaseSync } from 'node:sqlite';
import { digest, stableJson as json } from './matching-evidence.mjs';
import { assess, candidate, rulesVersion } from './matching-rules.mjs';
import { gtinKey, isId, isLabel } from './price-provider.mjs';

function uniqueIds(rows, key) {
    const ids = rows.map(key);
    if (!ids.every(isId) || new Set(ids).size !== ids.length) throw Error('Invalid or duplicate catalog key');
}

export class MatchingService {
    #db;
    #ownsDatabase;
    constructor(path) {
        this.#ownsDatabase = !(path instanceof DatabaseSync);
        this.#db = this.#ownsDatabase ? new DatabaseSync(path) : path;
        this.#db.exec(`PRAGMA busy_timeout=5000;
            CREATE TABLE IF NOT EXISTS match_revision (
                ingredient TEXT NOT NULL, package TEXT NOT NULL, version INTEGER NOT NULL,
                event TEXT NOT NULL, PRIMARY KEY(ingredient,package,version));
            CREATE TRIGGER IF NOT EXISTS immutable_match_update BEFORE UPDATE ON match_revision
                BEGIN SELECT RAISE(ABORT,'immutable revision'); END;
            CREATE TRIGGER IF NOT EXISTS immutable_match_delete BEFORE DELETE ON match_revision
                BEGIN SELECT RAISE(ABORT,'immutable revision'); END;`);
    }
    close() { if (this.#ownsDatabase) this.#db.close(); }
    history(ingredient, pack) {
        return this.#db.prepare('SELECT event FROM match_revision WHERE ingredient=? AND package=? ORDER BY version')
            .all(ingredient, pack).map(row => JSON.parse(row.event));
    }
    current() {
        return this.#db.prepare(`SELECT event FROM match_revision m WHERE version=(
            SELECT max(version) FROM match_revision WHERE ingredient=m.ingredient AND package=m.package)
            ORDER BY ingredient,package`).all().map(row => JSON.parse(row.event));
    }
    acceptedMatches() {
        return this.current().filter(row => row.decision.status === 'ACCEPTED').map(row => ({
            origin: { sourceId: 'nutrinest-editorial', recordKey: digest([row.ingredientKey, row.packageId]) },
            ingredientKey: row.ingredientKey, packageId: row.packageId, mappingVersion: row.version,
            ...(row.canonicalIngredientId && {
                ingredientKey: row.canonicalIngredientId, canonicalIngredientId: row.canonicalIngredientId,
                commercialProductId: row.evidence.item.product.id, classification: row.classification,
                evidenceDigest: row.fingerprint,
            }),
            kind: ['ALLOWED_SUBSTITUTE', 'SUSTITUTO'].includes(row.decision.kind) ? 'ALTERNATIVE' : 'EXACT',
            confidence: row.review || row.decision.score >= 90 ? 'HIGH' : 'MEDIUM',
        }));
    }
    #transaction(action) {
        if (this.#db.isTransaction) return action();
        this.#db.exec('BEGIN IMMEDIATE');
        try {
            const result = action();
            this.#db.exec('COMMIT');
            return result;
        } catch (error) {
            this.#db.exec('ROLLBACK');
            throw error;
        }
    }
    #append(data, previous) {
        const event = { ...data, version: (previous?.version ?? 0) + 1, at: new Date().toISOString() };
        const d = event.decision;
        event.classification = d.status === 'PENDING' && d.kind !== 'ALLOWED_SUBSTITUTE' ? 'AMBIGUOUS' : d.kind;
        event.confidence = d.status === 'ACCEPTED' ? (event.review || d.score >= 90 ? 'HIGH' : 'MEDIUM') : 'LOW';
        this.#db.prepare('INSERT INTO match_revision VALUES (?,?,?,?)')
            .run(event.ingredientKey, event.packageId, event.version, json(event));
        return event;
    }

    /** Replace the entire editorial scope of this database from one consistent input batch. */
    reconcile({ ingredients, catalog, threshold = 80 }) {
        if (!Number.isInteger(threshold) || threshold < 0 || threshold > 100) throw Error('Invalid threshold');
        uniqueIds(ingredients, i => i.key ?? i.canonicalIngredientId);
        uniqueIds(ingredients.filter(i => i.canonicalIngredientId != null), i => i.canonicalIngredientId);
        if (ingredients.some(i => i.canonicalIngredientId != null &&
            (gtinKey(i.canonicalIngredientId) || i.canonicalIngredientId.startsWith('bedca.'))))
            throw Error('Expected a canonical ingredient identity');
        uniqueIds(catalog, row => row.package.id);
        const gtins = new Set(), duplicateGtins = new Set(), skus = new Set(), duplicateSkus = new Set();
        for (const { product, package: pack } of catalog) {
            if (!isId(product.id) || pack.productId !== product.id || !isLabel(product.name) ||
                ![product, pack].every(x => isId(x.origin?.sourceId) && isLabel(x.origin?.recordKey)))
                throw Error('Invalid provider linkage');
            const gtin = gtinKey(pack.gtin);
            if (gtin && gtins.has(gtin)) duplicateGtins.add(gtin);
            gtins.add(gtin);
            if (pack.retailerSku != null) {
                if (!isId(pack.retailerSku.retailerId) || !isLabel(pack.retailerSku.value)) throw Error('Invalid retailer SKU');
                const sku = json([pack.retailerSku.retailerId, pack.retailerSku.value]);
                if (skus.has(sku)) duplicateSkus.add(sku);
                skus.add(sku);
            }
        }
        const batch = [];
        for (const item of catalog) {
            const rows = ingredients.filter(i => candidate(i, item)).map(i => ({
                ingredientKey: i.key ?? i.canonicalIngredientId, canonicalIngredientId: i.canonicalIngredientId ?? i.canonicalId,
                packageId: item.package.id, rulesVersion,
                evidence: { ingredient: i, item }, decision: assess(i, item, threshold),
            }));
            const possible = rows.filter(row => !['INCOMPATIBLE', 'ALLOWED_SUBSTITUTE'].includes(row.decision.kind));
            const competing = possible.map(row => [row.ingredientKey, digest(row.evidence.ingredient)]).sort();
            for (const row of rows) {
                if (possible.length > 1 && possible.includes(row)) {
                    row.competitors = competing;
                    row.decision.missing.push('competingIngredients');
                    row.decision.status = 'PENDING';
                }
                if (row.decision.status !== 'REJECTED' && duplicateGtins.has(gtinKey(item.package.gtin))) {
                    row.decision.missing.push('duplicateGtin');
                    row.decision.status = 'PENDING';
                }
                const sku = item.package.retailerSku;
                if (row.decision.status !== 'REJECTED' && sku && duplicateSkus.has(json([sku.retailerId, sku.value]))) {
                    row.decision.missing.push('duplicateRetailerSku');
                    row.decision.status = 'PENDING';
                }
            }
            batch.push(...rows);
        }
        return this.#transaction(() => {
            const key = row => json([row.ingredientKey, row.packageId]);
            const prior = new Map(this.current().map(row => [key(row), row]));
            for (const row of batch) {
                const previous = prior.get(key(row));
                prior.delete(key(row));
                const fingerprint = digest(row);
                if (previous?.fingerprint === fingerprint && previous.decision.status !== 'WITHDRAWN') continue;
                if (previous?.review || previous?.requiresReview) {
                    row.requiresReview = true;
                    if (row.decision.status !== 'REJECTED') {
                        row.decision.status = 'PENDING';
                        row.decision.missing.push('recheckReview');
                    }
                }
                this.#append({ ...row, fingerprint }, previous);
            }
            for (const previous of prior.values()) {
                if (previous.decision.status !== 'WITHDRAWN')
                    this.#append({ ...previous, decision: { ...previous.decision, status: 'WITHDRAWN' } }, previous);
            }
            return this.current();
        });
    }
    review({ ingredientKey, packageId, version, status, actor, reason }) {
        if (!isLabel(actor) || !isLabel(reason) || !['ACCEPTED', 'REJECTED', 'PENDING'].includes(status))
            throw Error('Invalid review');
        return this.#transaction(() => {
            const old = this.history(ingredientKey, packageId).at(-1);
            if (!old || old.version !== version || old.decision.status === 'WITHDRAWN') throw Error('Stale review');
            const d = old.decision;
            if (status === 'ACCEPTED' && (!d.kind || ['INCOMPATIBLE', 'AMBIGUOUS', 'NO_COMPATIBLE'].includes(d.kind) ||
                d.missing.some(r => !['competingIngredients', 'recheckReview'].includes(r)))) throw Error('Evidence required');
            if (status === 'ACCEPTED' && this.current().some(r => r.packageId === packageId &&
                r.ingredientKey !== ingredientKey && r.decision.status !== 'REJECTED' && r.decision.status !== 'WITHDRAWN'))
                throw Error('Resolve competing candidates first');
            if (status === 'PENDING') {
                for (const other of this.current().filter(r => r.packageId === packageId &&
                    r.ingredientKey !== ingredientKey && r.decision.status === 'ACCEPTED')) {
                    this.#append({ ...other, requiresReview: true, review: { actor, reason },
                        decision: { ...other.decision, status: 'PENDING',
                            missing: [...new Set([...other.decision.missing, 'competingIngredients'])] } }, other);
                }
            }
            return this.#append({ ...old, review: { actor, reason }, decision: { ...d, status } }, old);
        });
    }
}
