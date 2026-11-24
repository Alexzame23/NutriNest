import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { once } from 'node:events';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { promisify } from 'node:util';
import { syncPrices } from './price-sync.mjs';
import { priceServer } from './sync-http.mjs';
import { syncPolicy } from './sync-policy.mjs';
import { PriceSyncStore } from './sync-store.mjs';

const configPath = process.env.NUTRINEST_LIVE_PUBLISHER_CONFIG;

test('real Open Prices → regional HTTPS → Android provider → empty Room database', {
    skip: !configPath, timeout: 120_000,
}, async t => {
    const config = JSON.parse(await readFile(configPath, 'utf8'));
    const local = path => resolve(dirname(configPath), path);
    const policy = syncPolicy(config, process.env.NUTRINEST_PRICE_USER_AGENT);
    assert.equal(policy.source, 'open-prices');
    assert.equal(config.plan.transport, 'location-api');
    assert.ok(process.env.ANDROID_SERIAL, 'Select the test emulator with ANDROID_SERIAL');
    const adb = (...args) => promisify(execFile)(process.env.ADB || 'adb',
        ['-s', process.env.ANDROID_SERIAL, ...args], { timeout: 80_000 });
    const store = new PriceSyncStore(local(config.dbPath), policy.sourceId, policy.regionId);
    try {
        const sync = await syncPrices(store, policy);
        assert.ok(['SUCCEEDED', 'NOT_DUE'].includes(sync.status), JSON.stringify(sync));
        const publication = store.publication();
        assert.ok(publication?.snapshot, 'No current regional snapshot');
        const snapshot = JSON.parse(publication.snapshot);
        const manifest = JSON.parse(publication.manifest);
        assert.equal(snapshot.dataKind, 'live', 'No verified current observations for this plan');
        assert.ok(snapshot.prices.length > 0);
        const server = priceServer(store, policy, {
            cert: await readFile(local(config.tls.cert)), key: await readFile(local(config.tls.key)),
        });
        server.listen(0, '127.0.0.1');
        await once(server, 'listening');
        try {
            const devicePort = 'tcp:' + config.port;
            await adb('reverse', '--no-rebind', devicePort, 'tcp:' + server.address().port);
            try {
                const { stdout } = await adb('shell', 'am', 'instrument', '-w',
                    '-e', 'livePublisher', 'true', '-e', 'snapshotSha256', manifest.sha256,
                    '-e', 'class', 'com.zame.nutrinest.data.prices.LivePricePublisherTest',
                    'com.zame.nutrinest.test/androidx.test.runner.AndroidJUnitRunner');
                assert.match(stdout, /OK \(1 test\)/, stdout);
                t.diagnostic(JSON.stringify({ sync: sync.status, ...manifest, coverage: snapshot.coverage,
                    inputs: store.read().metadata.inputs }));
            } finally {
                await adb('reverse', '--remove', devicePort);
            }
        } finally {
            await new Promise(resolve => server.close(resolve));
        }
    } finally {
        store.close();
    }
});
