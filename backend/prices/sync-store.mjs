import { createHash } from 'node:crypto';
import { DatabaseSync } from 'node:sqlite';

/** A local volume shared by the worker and its readers on one host. */
export class PriceSyncStore {
    #db;
    #lock;
    #running = false;

    constructor(path, sourceId, regionId) {
        this.#db = new DatabaseSync(path, { timeout: 5000 });
        try {
            this.#db.exec(`
                PRAGMA journal_mode = WAL;
                PRAGMA synchronous = FULL;
                CREATE TABLE IF NOT EXISTS sync_state (
                    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
                    source_id TEXT NOT NULL, region_id TEXT NOT NULL, data TEXT NOT NULL
                ) STRICT;
                CREATE TABLE IF NOT EXISTS evidence (
                    record_key TEXT PRIMARY KEY, revision INTEGER NOT NULL, digest TEXT NOT NULL
                ) STRICT;
                CREATE TABLE IF NOT EXISTS price_history (
                    id INTEGER PRIMARY KEY, fetched_at INTEGER NOT NULL, observation TEXT NOT NULL
                ) STRICT;
                CREATE TABLE IF NOT EXISTS publications (
                    revision INTEGER PRIMARY KEY, expires_at INTEGER NOT NULL,
                    contents_hash TEXT NOT NULL, manifest TEXT NOT NULL, snapshot TEXT
                ) STRICT;
            `);
            this.#db.prepare('INSERT OR IGNORE INTO sync_state VALUES (1, ?, ?, ?)').run(
                sourceId, regionId, JSON.stringify({
                    nextAt: 0, cycleAt: null, attempts: 0, outcome: 'NEVER_SYNCED',
                    requestTimes: [], counts: { attempts: 0, successes: 0, failures: 0, requests: 0, notModified: 0, changes: 0 },
                    quotes: [], catalog: [], metadata: null, revalidation: null,
                }),
            );
            const row = this.#db.prepare('SELECT source_id, region_id FROM sync_state WHERE singleton = 1').get();
            if (row.source_id !== sourceId || row.region_id !== regionId) {
                throw new Error('This database belongs to a different source or region');
            }
            this.#lock = new DatabaseSync(path + '.worker-lock', { timeout: 0 });
        } catch (error) {
            this.#db.close();
            throw error;
        }
    }

    async exclusive(run) {
        if (this.#running) return { status: 'BUSY' };
        try {
            this.#lock.exec('BEGIN IMMEDIATE');
        } catch (error) {
            if (error.errcode === 5) return { status: 'BUSY' };
            throw error;
        }
        this.#running = true;
        try {
            return await run();
        } finally {
            this.#running = false;
            this.#lock.exec('ROLLBACK');
        }
    }

    transaction(run) {
        this.#db.exec('BEGIN IMMEDIATE');
        try {
            const result = run();
            this.#db.exec('COMMIT');
            return result;
        } catch (error) {
            if (this.#db.isTransaction) this.#db.exec('ROLLBACK');
            throw error;
        }
    }

    read() {
        return JSON.parse(this.#db.prepare('SELECT data FROM sync_state WHERE singleton = 1').get().data);
    }

    save(state) {
        this.#db.prepare('UPDATE sync_state SET data = ? WHERE singleton = 1').run(JSON.stringify(state));
    }

    registerEvidence(price) {
        const key = price.record.origin.recordKey;
        const digest = createHash('sha256').update(JSON.stringify(price)).digest('hex');
        const previous = this.#db.prepare('SELECT revision, digest FROM evidence WHERE record_key = ?').get(key);
        if (previous?.digest === digest) return previous.revision;
        const revision = (previous?.revision ?? 0) + 1;
        if (revision > 2147483647) throw new Error('Observation revision overflow');
        this.#db.prepare('INSERT OR REPLACE INTO evidence VALUES (?, ?, ?)').run(key, revision, digest);
        return revision;
    }

    addHistory(fetchedAt, observation) {
        this.#db.prepare('INSERT INTO price_history (fetched_at, observation) VALUES (?, ?)')
            .run(fetchedAt, JSON.stringify(observation));
    }

    history() {
        return this.#db.prepare('SELECT observation FROM price_history ORDER BY id').all()
            .map(row => JSON.parse(row.observation));
    }

    retainHistory(since) {
        this.#db.prepare('DELETE FROM price_history WHERE fetched_at < ?').run(since);
    }

    publication(revision) {
        return revision === undefined
            ? this.#db.prepare('SELECT * FROM publications ORDER BY revision DESC LIMIT 1').get()
            : this.#db.prepare('SELECT * FROM publications WHERE revision = ?').get(revision);
    }

    publicationView(revision) {
        return this.#db.prepare(`
            WITH latest AS (SELECT MAX(revision) AS revision FROM publications)
            SELECT state.data, latest.revision AS latest_revision, publication.*
            FROM sync_state AS state CROSS JOIN latest
            LEFT JOIN publications AS publication ON publication.revision = COALESCE(?, latest.revision)
            WHERE state.singleton = 1
        `).get(revision ?? null);
    }

    publish(revision, expiresAt, hash, manifest, snapshot) {
        this.#db.prepare('INSERT INTO publications VALUES (?, ?, ?, ?, ?)')
            .run(revision, expiresAt, hash, manifest, snapshot);
    }

    withdrawPrevious(revision) {
        this.#db.prepare('DELETE FROM publications WHERE revision < ?').run(revision);
    }

    retireExpired(now) {
        this.#db.prepare('DELETE FROM publications WHERE expires_at <= ? AND revision < (SELECT MAX(revision) FROM publications)')
            .run(now);
        this.#db.prepare('UPDATE publications SET snapshot = NULL WHERE expires_at <= ? AND snapshot IS NOT NULL').run(now);
    }

    close() {
        this.#lock.close();
        this.#db.close();
    }
}
