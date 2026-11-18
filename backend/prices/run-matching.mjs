import { readFileSync } from 'node:fs';
import { MatchingService } from './matching-service.mjs';

const [command, path, input, pack] = process.argv.slice(2);
if (!path || !['reconcile', 'review', 'history', 'current', 'accepted'].includes(command))
    throw Error('Usage: run-matching.mjs reconcile|review|history|current|accepted DATABASE [input.json | ingredient package]');
const service = new MatchingService(path);
try {
    const result = command === 'accepted' ? service.acceptedMatches() : command === 'current' ? service.current() : command === 'history' ? service.history(input, pack)
        : service[command](JSON.parse(readFileSync(input, 'utf8').replace(/^\uFEFF/, '')));
    console.log(JSON.stringify(result, null, 2));
} finally {
    service.close();
}
