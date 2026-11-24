import { readFile, realpath } from 'node:fs/promises';
import { basename, dirname, join, resolve } from 'node:path';
import { syncPrices } from './price-sync.mjs';
import { priceServer } from './sync-http.mjs';
import { POLL_MS, syncPolicy } from './sync-policy.mjs';
import { PriceSyncStore } from './sync-store.mjs';

async function main([configPath, mode, ...extra]) {
    if (!configPath || (mode && mode !== '--once') || extra.length) {
        throw new Error('Usage: node backend/prices/run-sync.mjs <config.json> [--once]');
    }
    const config = JSON.parse(await readFile(configPath, 'utf8'));
    const port = config.port ?? 8787;
    if (typeof config.dbPath !== 'string' || !config.dbPath.trim() ||
        !Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid dbPath or port');
    const policy = syncPolicy(config, process.env.NUTRINEST_PRICE_USER_AGENT);
    const path = resolve(dirname(configPath), config.dbPath);
    const dbPath = await realpath(path).catch(async error => {
        if (error.code !== 'ENOENT') throw error;
        return join(await realpath(dirname(path)), basename(path));
    });
    const store = new PriceSyncStore(dbPath, policy.sourceId, policy.regionId);
    if (mode === '--once') {
        try {
            const result = await syncPrices(store, policy);
            console.log(JSON.stringify(result));
            if (result.status === 'FAILED') process.exitCode = 1;
        } finally {
            store.close();
        }
        return;
    }

    const tls = config.tls && {
        key: await readFile(resolve(dirname(configPath), config.tls.key)),
        cert: await readFile(resolve(dirname(configPath), config.tls.cert)),
    };
    const server = priceServer(store, policy, tls);
    let timer;
    let work;
    let stopped = false;
    function tick() {
        work = syncPrices(store, policy)
            .then(result => {
                if (['SUCCEEDED', 'FAILED'].includes(result.status)) console.log(JSON.stringify(result));
            })
            .catch(() => console.error('Price synchronization storage error'))
            .finally(() => {
                if (!stopped) timer = setTimeout(tick, POLL_MS);
            });
    }
    function stop() {
        if (stopped) return;
        stopped = true;
        clearTimeout(timer);
        server.close(async () => {
            await work;
            store.close();
        });
    }
    server.once('error', error => {
        console.error(error.code ?? 'Price server startup error');
        process.exitCode = 1;
        if (server.listening) stop();
        else store.close();
    });
    server.listen(port, '127.0.0.1', () => {
        console.log(`Price service listening on 127.0.0.1:${port}`);
        tick();
    });
    process.once('SIGINT', stop);
    process.once('SIGTERM', stop);
}

try {
    await main(process.argv.slice(2));
} catch (error) {
    console.error(error.message);
    process.exitCode = 1;
}
