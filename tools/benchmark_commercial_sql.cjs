// Run against the same local PGlite dependency used by verify_commercial_contract.cjs.
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { createRequire } = require('node:module');
const { resolve } = require('node:path');

const deps = createRequire(resolve(process.argv[2], 'package.json'));
const { PGlite } = deps('@electric-sql/pglite');

const fixture = `
    SET search_path TO nutrinest_commercial, pg_catalog;
    INSERT INTO price_source VALUES
      ('p-1', 'Provider one', 'AUTHORIZED_IMPORT', 'fixture', 'fixture'),
      ('p-2', 'Provider two', 'AUTHORIZED_IMPORT', 'fixture', 'fixture');
    INSERT INTO retailer
      SELECT 'r-' || i, 'p-' || i, 'retailer-' || i, 'Retailer ' || i FROM generate_series(1, 2) i;
    INSERT INTO price_zone
      SELECT 'z-' || i, 'p-' || CASE WHEN i <= 2 THEN 1 ELSE 2 END, 'zone-' || i,
             'r-' || CASE WHEN i <= 2 THEN 1 ELSE 2 END, 'Zone ' || i FROM generate_series(1, 4) i;
    INSERT INTO store
      SELECT 's-' || i, 'p-' || CASE WHEN i <= 2 THEN 1 ELSE 2 END, 'store-' || i,
             'r-' || CASE WHEN i <= 2 THEN 1 ELSE 2 END, 'Store ' || i, '08001' FROM generate_series(1, 4) i;
    INSERT INTO observation_scope
      SELECT 'zone-' || substring(id, 3), retailer_id, 'ZONE', NULL, id FROM price_zone
      UNION ALL
      SELECT 'store-' || substring(id, 3), retailer_id, 'STORE', id, NULL FROM store;
    INSERT INTO commercial_product (id, source_id, record_key, name)
      SELECT 'product-' || i, 'p-1', 'product-' || i, 'Product ' || i FROM generate_series(1, 300) i;
    INSERT INTO product_package
      (id, source_id, record_key, product_id, weight_mode, sale_unit, net_amount, net_unit, units_per_package)
      SELECT 'pack-' || i, 'p-1', 'pack-' || i, 'product-' || i, 'FIXED', 'PACKAGE', 500, 'G', 1
      FROM generate_series(1, 300) i;
    INSERT INTO editorial_ingredient_key SELECT 'ingredient-' || i FROM generate_series(1, 300) i;
    INSERT INTO ingredient_product_match
      SELECT 'p-1', 'match-' || i, 1, 'ingredient-' || i, 'pack-' || i, 'EXACT', 'HIGH'
      FROM generate_series(1, 300) i;
`;

const observations = (packs, days, firstDay) => `
    INSERT INTO price_observation
      (source_id, record_key, revision, package_id, scope_id, observed_on, confidence, amount, currency, sale_unit, kind)
    SELECT p.id, i || '/' || s.id || '/' || d, 1, 'pack-' || i, s.id,
           DATE '2026-11-01' + d, 'HIGH', 1 + (i % 200) * 0.01, 'EUR', 'PACKAGE', 'REGULAR'
    FROM generate_series(1, ${packs}) i CROSS JOIN observation_scope s
    CROSS JOIN generate_series(${firstDay}, ${firstDay + days - 1}) d CROSS JOIN price_source p
`;

const latest = `
    SELECT o.amount
    FROM price_observation o
    WHERE o.package_id = $1 AND o.scope_id = $2 AND observed_on <= DATE '2026-11-14'
    ORDER BY o.observed_on DESC, o.source_id
    LIMIT 1
`;

const offers = `
    SELECT m.ingredient_key, s.id, recent.amount
    FROM ingredient_product_match m
    CROSS JOIN observation_scope s
    CROSS JOIN LATERAL (
      SELECT o.amount
      FROM price_observation o
      WHERE o.package_id = m.package_id AND o.scope_id = s.id AND observed_on <= DATE '2026-11-14'
      ORDER BY o.observed_on DESC, o.source_id
      LIMIT 1
    ) recent
    WHERE m.ingredient_key = ANY($1::text[])
      AND m.kind = 'EXACT' AND m.mapping_version = 1
      AND s.id = ANY($2::text[])
`;

async function inspect(db, query, params, write = false) {
    const measurements = [];
    let plan;
    for (let i = 0; i < 16; i++) {
        if (write) await db.exec('BEGIN');
        try {
            const result = await db.query(`EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) ${query}`, params);
            const inspected = result.rows[0]['QUERY PLAN'][0];
            plan ??= inspected.Plan;
            if (i >= 5) measurements.push(inspected['Execution Time']);
        } finally {
            if (write) await db.exec('ROLLBACK');
        }
    }
    const ordered = measurements.toSorted((a, b) => a - b);
    return { median_ms: ordered[5], p95_ms: ordered[10], samples_ms: measurements, plan };
}

async function main() {
    const db = new PGlite();
    try {
        await db.exec(readFileSync(resolve(__dirname, '../docs/prices/11-commercial-schema.sql'), 'utf8'));
        await db.exec(fixture);
        await db.exec(observations(300, 14, 0));
        await db.exec('ANALYZE');
        const { rows: [{ count }] } = await db.query('SELECT count(*)::int AS count FROM price_observation');
        assert.equal(count, 300 * 8 * 14 * 2);
        const point = await inspect(db, latest, ['pack-42', 'store-1']);
        const baskets = [];
        for (const lines of [10, 50, 100]) {
            const ingredients = Array.from({ length: lines }, (_, i) => `ingredient-${i + 1}`);
            const scopes = ['store-1', 'store-2', 'zone-3', 'zone-4'];
            assert.equal((await db.query(offers, [ingredients, scopes])).rows.length, lines * scopes.length);
            baskets.push({ lines, ...await inspect(db, offers, [ingredients, scopes]) });
        }
        const insert = observations(40, 1, 14);
        const indexBytes = 'SELECT pg_relation_size(\'price_observation_lookup_idx\')::int AS bytes';
        const { rows: [{ bytes }] } = await db.query(indexBytes);
        const withIndex = await inspect(db, insert, [], true);
        await db.exec('DROP INDEX price_observation_lookup_idx');
        await db.exec('VACUUM ANALYZE price_observation');
        const withoutIndex = await inspect(db, insert, [], true);
        const pointWithoutIndex = await inspect(db, latest, ['pack-42', 'store-1']);
        const { rows: [{ version }] } = await db.query('SELECT version()');
        console.log(JSON.stringify({ version, observations: count, products: 300, stores: 4, zones: 4,
            providers: 2, point, pointWithoutIndex, baskets, indexBytes: bytes,
            insert: { observations: 640, withIndex, withoutIndex } }, null, 2));
    } finally {
        await db.close();
    }
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
