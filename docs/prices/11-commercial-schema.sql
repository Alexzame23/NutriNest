BEGIN;

CREATE SCHEMA nutrinest_commercial;
SET LOCAL search_path TO nutrinest_commercial, pg_catalog;

CREATE DOMAIN commercial_id AS text
    CHECK (VALUE ~ '^[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$');
CREATE DOMAIN provider_key AS text
    CHECK (VALUE ~ '[^[:space:]]' AND char_length(VALUE) <= 256);
CREATE DOMAIN label AS text CHECK (VALUE ~ '[^[:space:]]');
CREATE DOMAIN confidence AS text CHECK (VALUE IN ('LOW', 'MEDIUM', 'HIGH'));
CREATE DOMAIN eur_amount AS numeric CHECK (VALUE >= 0 AND VALUE < 1000000000 AND scale(VALUE) <= 4);
CREATE DOMAIN content_amount AS numeric CHECK (VALUE > 0 AND VALUE < 1000000000 AND scale(VALUE) <= 3);

CREATE FUNCTION valid_gtin(code text) RETURNS boolean
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
    SELECT CASE WHEN length(code) IN (8, 12, 13, 14) AND code ~ '^[0-9]+$' AND code ~ '[1-9]'
        THEN (SELECT sum(substring(reverse(code), pos, 1)::integer
                         * CASE WHEN pos % 2 = 0 THEN 3 ELSE 1 END)
                     FROM generate_series(1, length(code)) AS digit(pos)) % 10 = 0
        ELSE false END
$$;

CREATE TABLE price_source (
    id commercial_id PRIMARY KEY,
    name label NOT NULL,
    kind text NOT NULL CHECK (kind IN ('OPEN_DATA', 'LICENSED_FEED', 'AUTHORIZED_IMPORT')),
    license_ref label NOT NULL,
    attribution label NOT NULL
);

CREATE TABLE retailer (
    id commercial_id PRIMARY KEY,
    source_id commercial_id NOT NULL REFERENCES price_source,
    record_key provider_key NOT NULL,
    name label NOT NULL,
    UNIQUE (source_id, record_key)
);

CREATE TABLE store (
    id commercial_id PRIMARY KEY,
    source_id commercial_id NOT NULL REFERENCES price_source,
    record_key provider_key NOT NULL,
    retailer_id commercial_id NOT NULL REFERENCES retailer,
    name label NOT NULL,
    postal_code text CHECK (postal_code ~ '^[0-9]{5}$'),
    UNIQUE (source_id, record_key),
    UNIQUE (retailer_id, id)
);

CREATE TABLE price_zone (
    id commercial_id PRIMARY KEY,
    source_id commercial_id NOT NULL REFERENCES price_source,
    record_key provider_key NOT NULL,
    retailer_id commercial_id NOT NULL REFERENCES retailer,
    name label NOT NULL,
    UNIQUE (source_id, record_key),
    UNIQUE (retailer_id, id)
);

CREATE FUNCTION valid_product_attributes(attrs jsonb) RETURNS boolean
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
    SELECT CASE WHEN jsonb_typeof(attrs) = 'object'
        THEN NOT EXISTS (SELECT 1 FROM jsonb_each(attrs) AS attr
                         WHERE attr.key !~ '[^[:space:]]' OR jsonb_typeof(attr.value) <> 'string')
        ELSE false END
$$;

CREATE TABLE commercial_product (
    id commercial_id PRIMARY KEY,
    source_id commercial_id NOT NULL REFERENCES price_source,
    record_key provider_key NOT NULL,
    name label NOT NULL,
    brand text,
    variant text,
    attributes jsonb NOT NULL DEFAULT '{}' CHECK (valid_product_attributes(attributes)),
    UNIQUE (source_id, record_key)
);

CREATE TABLE product_package (
    id commercial_id PRIMARY KEY,
    source_id commercial_id NOT NULL REFERENCES price_source,
    record_key provider_key NOT NULL,
    product_id commercial_id NOT NULL REFERENCES commercial_product,
    gtin text CHECK (valid_gtin(gtin)),
    gtin_normalized text GENERATED ALWAYS AS (lpad(gtin, 14, '0')) STORED,
    weight_mode text NOT NULL,
    sale_unit text NOT NULL,
    net_amount content_amount,
    net_unit text CHECK (net_unit IN ('G', 'ML', 'UNIT')),
    units_per_package integer CHECK (units_per_package > 0),
    CHECK (net_unit IS DISTINCT FROM 'UNIT' OR net_amount = trunc(net_amount)),
    CHECK ((weight_mode = 'FIXED' AND sale_unit = 'PACKAGE' AND net_amount IS NOT NULL
            AND net_unit IS NOT NULL AND units_per_package IS NOT NULL)
        OR (weight_mode = 'VARIABLE' AND sale_unit = 'KG' AND net_amount IS NULL
            AND net_unit IS NULL AND units_per_package IS NULL)),
    UNIQUE (source_id, record_key),
    UNIQUE (id, sale_unit)
);
CREATE INDEX product_package_product_idx ON product_package (product_id);
CREATE INDEX product_package_gtin_idx ON product_package (gtin_normalized) WHERE gtin_normalized IS NOT NULL;

CREATE TABLE editorial_ingredient_key (
    id commercial_id PRIMARY KEY
);

CREATE TABLE ingredient_product_match (
    source_id commercial_id NOT NULL REFERENCES price_source,
    record_key provider_key NOT NULL,
    mapping_version integer NOT NULL CHECK (mapping_version > 0),
    ingredient_key commercial_id NOT NULL REFERENCES editorial_ingredient_key,
    package_id commercial_id NOT NULL REFERENCES product_package,
    kind text NOT NULL CHECK (kind IN ('EXACT', 'ALTERNATIVE')),
    confidence confidence NOT NULL,
    PRIMARY KEY (source_id, record_key, mapping_version)
);
CREATE INDEX ingredient_product_match_key_idx ON ingredient_product_match (ingredient_key, mapping_version DESC);
CREATE INDEX ingredient_product_match_package_idx ON ingredient_product_match (package_id);

CREATE TABLE observation_scope (
    id commercial_id PRIMARY KEY,
    retailer_id commercial_id NOT NULL REFERENCES retailer,
    kind text NOT NULL,
    store_id commercial_id,
    zone_id commercial_id,
    FOREIGN KEY (retailer_id, store_id) REFERENCES store (retailer_id, id),
    FOREIGN KEY (retailer_id, zone_id) REFERENCES price_zone (retailer_id, id),
    CHECK ((kind = 'STORE' AND store_id IS NOT NULL AND zone_id IS NULL)
        OR (kind = 'ZONE' AND store_id IS NULL AND zone_id IS NOT NULL)
        OR (kind = 'ONLINE' AND store_id IS NULL)
        OR (kind = 'UNKNOWN' AND store_id IS NULL AND zone_id IS NULL)),
    UNIQUE NULLS NOT DISTINCT (retailer_id, kind, store_id, zone_id)
);
CREATE INDEX observation_scope_store_idx ON observation_scope (retailer_id, store_id);
CREATE INDEX observation_scope_zone_idx ON observation_scope (retailer_id, zone_id);

CREATE TABLE price_observation (
    source_id commercial_id NOT NULL REFERENCES price_source,
    record_key provider_key NOT NULL,
    revision integer NOT NULL CHECK (revision > 0),
    package_id commercial_id NOT NULL,
    scope_id commercial_id NOT NULL REFERENCES observation_scope,
    observed_on date NOT NULL,
    observed_at timestamptz,
    ingested_at timestamptz NOT NULL DEFAULT now(),
    confidence confidence NOT NULL,
    amount eur_amount NOT NULL,
    currency text NOT NULL CHECK (currency = 'EUR'),
    sale_unit text NOT NULL,
    kind text NOT NULL CHECK (kind IN ('REGULAR', 'PROMOTIONAL')),
    eligibility text CHECK (eligibility IN ('PUBLIC', 'LOYALTY', 'CONDITIONAL', 'UNKNOWN')),
    valid_until date,
    CHECK ((kind = 'PROMOTIONAL') = (eligibility IS NOT NULL)),
    CHECK (valid_until IS NULL OR (kind = 'PROMOTIONAL' AND valid_until >= observed_on)),
    FOREIGN KEY (package_id, sale_unit) REFERENCES product_package (id, sale_unit),
    PRIMARY KEY (source_id, record_key, revision)
);
CREATE INDEX price_observation_lookup_idx ON price_observation (package_id, scope_id, observed_on DESC);
CREATE INDEX price_observation_scope_idx ON price_observation (scope_id);

CREATE TABLE availability_observation (
    source_id commercial_id NOT NULL REFERENCES price_source,
    record_key provider_key NOT NULL,
    revision integer NOT NULL CHECK (revision > 0),
    package_id commercial_id NOT NULL REFERENCES product_package,
    scope_id commercial_id NOT NULL REFERENCES observation_scope,
    observed_on date NOT NULL,
    observed_at timestamptz,
    ingested_at timestamptz NOT NULL DEFAULT now(),
    confidence confidence NOT NULL,
    state text NOT NULL CHECK (state IN ('IN_STOCK', 'OUT_OF_STOCK', 'LIMITED', 'UNKNOWN')),
    PRIMARY KEY (source_id, record_key, revision)
);
CREATE INDEX availability_observation_lookup_idx ON availability_observation (package_id, scope_id, observed_on DESC);
CREATE INDEX availability_observation_scope_idx ON availability_observation (scope_id);

CREATE TABLE price_region (
    id commercial_id PRIMARY KEY,
    name label NOT NULL
);

CREATE TABLE price_sync_status (
    source_id commercial_id NOT NULL REFERENCES price_source,
    region_id commercial_id NOT NULL REFERENCES price_region,
    state text NOT NULL CHECK (state IN ('NEVER_SYNCED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    last_attempt_at timestamptz,
    last_success_at timestamptz,
    CHECK ((state = 'NEVER_SYNCED') = (last_attempt_at IS NULL)),
    CHECK (last_success_at IS NULL OR (last_attempt_at IS NOT NULL AND last_success_at <= last_attempt_at)),
    CHECK (state <> 'SUCCEEDED' OR last_success_at IS NOT DISTINCT FROM last_attempt_at),
    PRIMARY KEY (source_id, region_id)
);
CREATE INDEX price_sync_region_idx ON price_sync_status (region_id);

CREATE TABLE price_publication (
    region_id commercial_id NOT NULL REFERENCES price_region,
    revision bigint NOT NULL CHECK (revision BETWEEN 1 AND 9007199254740991),
    contract_version integer NOT NULL CHECK (contract_version = 1),
    generated_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > generated_at AND expires_at = date_trunc('milliseconds', expires_at)),
    sha256 text NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    byte_length integer NOT NULL CHECK (byte_length BETWEEN 1 AND 1048576),
    withdrawn_at timestamptz CHECK (withdrawn_at >= generated_at),
    PRIMARY KEY (region_id, revision)
);

CREATE TABLE price_publication_source (
    region_id commercial_id NOT NULL,
    revision bigint NOT NULL,
    source_id commercial_id NOT NULL REFERENCES price_source,
    FOREIGN KEY (region_id, revision) REFERENCES price_publication,
    PRIMARY KEY (region_id, revision, source_id)
);
CREATE INDEX price_publication_source_idx ON price_publication_source (source_id);

COMMIT;
