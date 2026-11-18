BEGIN;
SET LOCAL search_path TO nutrinest_commercial, pg_catalog;

CREATE FUNCTION pg_temp.expect_error(command text, expected_state text) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    EXECUTE command;
    RAISE EXCEPTION 'Statement should have failed: %', command;
EXCEPTION WHEN OTHERS THEN
    IF SQLSTATE <> expected_state THEN RAISE; END IF;
END
$$;

INSERT INTO price_source VALUES
    ('fixture-source', 'Fixture', 'AUTHORIZED_IMPORT', 'fixture-license', 'Fixture only'),
    ('other-source', 'Other fixture', 'OPEN_DATA', 'fixture-license', 'Fixture only');
INSERT INTO retailer VALUES
    ('ra', 'fixture-source', 'external-a', 'Retailer fixture A'),
    ('rb', 'other-source', 'external-a', 'Retailer fixture B');
INSERT INTO store VALUES ('store-a', 'fixture-source', 'external-a', 'ra', 'Store fixture', '08001');
INSERT INTO price_zone VALUES ('zone-a', 'fixture-source', 'external-a', 'ra', 'Zone fixture');
INSERT INTO commercial_product (id, source_id, record_key, name, attributes) VALUES
    ('product-a', 'fixture-source', 'external-a', 'Product fixture', '{"format":"pasta"}');
INSERT INTO product_package (id, source_id, record_key, product_id, gtin, weight_mode, sale_unit,
                             net_amount, net_unit, units_per_package)
VALUES ('pack-a', 'fixture-source', 'external-a', 'product-a', '036000291452', 'FIXED', 'PACKAGE', 500, 'G', 2),
       ('pack-b', 'fixture-source', 'external-b', 'product-a', NULL, 'VARIABLE', 'KG', NULL, NULL, NULL);
INSERT INTO editorial_ingredient_key VALUES ('fixture-ingredient');
INSERT INTO ingredient_product_match VALUES
    ('fixture-source', 'match-a', 1, 'fixture-ingredient', 'pack-a', 'ALTERNATIVE', 'MEDIUM'),
    ('fixture-source', 'match-a', 2, 'fixture-ingredient', 'pack-a', 'EXACT', 'HIGH');
INSERT INTO observation_scope VALUES
    ('in-store', 'ra', 'STORE', 'store-a', NULL),
    ('zone-only', 'ra', 'ZONE', NULL, 'zone-a'),
    ('online-zone', 'ra', 'ONLINE', NULL, 'zone-a'),
    ('online-general', 'ra', 'ONLINE', NULL, NULL),
    ('unknown-scope', 'ra', 'UNKNOWN', NULL, NULL);
INSERT INTO price_observation (source_id, record_key, revision, package_id, scope_id, observed_on,
                              confidence, amount, currency, sale_unit, kind, eligibility, valid_until)
VALUES ('fixture-source', 'offer-a', 1, 'pack-a', 'in-store', DATE '2026-11-18',
        'HIGH', 1.2345, 'EUR', 'PACKAGE', 'REGULAR', NULL, NULL),
       ('fixture-source', 'offer-a', 2, 'pack-a', 'in-store', DATE '2026-11-18',
        'HIGH', 1.2346, 'EUR', 'PACKAGE', 'REGULAR', NULL, NULL),
       ('other-source', 'offer-a', 1, 'pack-b', 'online-zone', DATE '2026-11-18',
        'MEDIUM', 0.99, 'EUR', 'KG', 'PROMOTIONAL', 'LOYALTY', DATE '2026-11-20');
INSERT INTO availability_observation (source_id, record_key, revision, package_id, scope_id, observed_on,
                                     confidence, state)
VALUES ('fixture-source', 'offer-a', 1, 'pack-a', 'zone-only', DATE '2026-11-18', 'LOW', 'OUT_OF_STOCK'),
       ('fixture-source', 'stock-only', 1, 'pack-b', 'zone-only', DATE '2026-11-18', 'LOW', 'UNKNOWN');
INSERT INTO price_region VALUES ('fixture-region', 'Region fixture');
INSERT INTO price_sync_status VALUES ('fixture-source', 'fixture-region', 'NEVER_SYNCED', NULL, NULL);
INSERT INTO price_publication VALUES ('fixture-region', 1, 1, '2026-11-18T12:00Z', '2026-11-20T12:00Z', repeat('a', 64), 123);
INSERT INTO price_publication_source VALUES ('fixture-region', 1, 'fixture-source');

SELECT pg_temp.expect_error($q$INSERT INTO retailer VALUES ('rc','fixture-source','external-a','Duplicate')$q$, '23505');
SELECT pg_temp.expect_error($q$INSERT INTO observation_scope VALUES ('wrong-chain','rb','STORE','store-a',NULL)$q$, '23503');
SELECT pg_temp.expect_error($q$INSERT INTO observation_scope VALUES ('wrong-zone','rb','ONLINE',NULL,'zone-a')$q$, '23503');
SELECT pg_temp.expect_error($q$INSERT INTO observation_scope VALUES ('ambiguous','ra','STORE','store-a','zone-a')$q$, '23514');
SELECT pg_temp.expect_error($q$INSERT INTO observation_scope VALUES ('same-online','ra','ONLINE',NULL,NULL)$q$, '23505');
SELECT pg_temp.expect_error($q$UPDATE commercial_product SET attributes='{"size":500}'$q$, '23514');
SELECT pg_temp.expect_error($q$UPDATE product_package SET gtin='036000291453' WHERE id='pack-a'$q$, '23514');
SELECT pg_temp.expect_error($q$UPDATE product_package SET net_amount=10, net_unit='G' WHERE id='pack-b'$q$, '23514');
SELECT pg_temp.expect_error($q$UPDATE product_package SET net_amount=1.5, net_unit='UNIT' WHERE id='pack-a'$q$, '23514');
SELECT pg_temp.expect_error($q$UPDATE ingredient_product_match SET ingredient_key='missing'$q$, '23503');
SELECT pg_temp.expect_error($q$INSERT INTO price_observation SELECT * FROM price_observation WHERE source_id='fixture-source' AND revision=1$q$, '23505');
SELECT pg_temp.expect_error($q$UPDATE price_observation SET currency='USD' WHERE revision=2$q$, '23514');
SELECT pg_temp.expect_error($q$UPDATE price_observation SET amount=1.00001 WHERE revision=2$q$, '23514');
SELECT pg_temp.expect_error($q$UPDATE price_observation SET amount='NaN' WHERE revision=2$q$, '23514');
SELECT pg_temp.expect_error($q$UPDATE price_observation SET sale_unit='KG' WHERE revision=2$q$, '23503');
SELECT pg_temp.expect_error($q$UPDATE price_observation SET valid_until='2026-11-17' WHERE kind='PROMOTIONAL'$q$, '23514');
SELECT pg_temp.expect_error($q$UPDATE price_observation SET eligibility=NULL WHERE kind='PROMOTIONAL'$q$, '23514');
SELECT pg_temp.expect_error($q$UPDATE availability_observation SET package_id='missing' WHERE record_key='stock-only'$q$, '23503');
SELECT pg_temp.expect_error($q$UPDATE price_sync_status SET state='SUCCEEDED', last_attempt_at=now()$q$, '23514');
SELECT pg_temp.expect_error($q$UPDATE price_publication SET expires_at=generated_at$q$, '23514');
SELECT pg_temp.expect_error($q$UPDATE price_publication SET byte_length=1048577$q$, '23514');
SELECT pg_temp.expect_error($q$UPDATE price_publication SET withdrawn_at='2026-11-17T12:00Z'$q$, '23514');
SELECT pg_temp.expect_error($q$INSERT INTO price_publication_source VALUES ('fixture-region',2,'fixture-source')$q$, '23503');

SET LOCAL enable_seqscan TO off;
DO $$
DECLARE
    plan json;
BEGIN
    IF (SELECT gtin_normalized FROM product_package WHERE id='pack-a') <> '00036000291452' THEN
        RAISE EXCEPTION 'GTIN normalization differs from Android';
    END IF;
    IF (SELECT amount::text FROM price_observation WHERE source_id='fixture-source' AND revision=1) <> '1.2345' THEN
        RAISE EXCEPTION 'Exact decimal data did not round-trip';
    END IF;
    EXECUTE 'EXPLAIN (FORMAT JSON) SELECT id FROM product_package WHERE gtin_normalized=''00036000291452''' INTO plan;
    IF plan::text NOT LIKE '%product_package_gtin_idx%' THEN RAISE EXCEPTION 'GTIN index not found in plan'; END IF;
    EXECUTE 'EXPLAIN (FORMAT JSON) SELECT * FROM price_observation WHERE package_id=''pack-a''
             AND scope_id=''in-store'' ORDER BY observed_on DESC LIMIT 1' INTO plan;
    IF plan::text NOT LIKE '%price_observation_lookup_idx%' THEN RAISE EXCEPTION 'Price lookup index not found in plan'; END IF;
END
$$;

ROLLBACK;
