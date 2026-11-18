import { ProviderError } from './provider-error.mjs';

const HTTP_CODES = new Map([
    [401, 'UNAUTHENTICATED'], [403, 'FORBIDDEN'], [404, 'NOT_FOUND'],
    [408, 'TIMEOUT'], [429, 'RATE_LIMITED'], [503, 'UNAVAILABLE'], [504, 'TIMEOUT'],
]);

function retryAt(value, now) {
    if (!value) return undefined;
    const date = /^\d+$/.test(value) ? now + Number(value) * 1000 : Date.parse(value);
    if (!Number.isFinite(date) || Math.abs(date) > 8.64e15) return undefined;
    return new Date(Math.max(now, date)).toISOString();
}

/** The adapter owns the URL and headers. The deadline includes reading the body. */
export async function download(url, { fetchImpl = fetch, userAgent, timeoutMs, maxBytes, now = Date.now }) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
        const response = await fetchImpl(url, {
            method: 'GET', redirect: 'manual', signal: controller.signal,
            headers: { 'User-Agent': userAgent, 'Accept-Encoding': 'identity' },
        });
        if (response.status !== 200) {
            const { status } = response;
            throw new ProviderError(HTTP_CODES.get(status) ?? (status >= 500 ? 'UPSTREAM_ERROR' : 'HTTP_ERROR'),
                'Provider rejected the download', {
                    status,
                    retryable: status === 408 || status === 429 || status >= 500,
                    retryAt: [429, 503].includes(status) ? retryAt(response.headers.get('retry-after'), now()) : undefined,
                });
        }
        const declaredLength = Number(response.headers.get('content-length'));
        if (declaredLength > maxBytes) throw new ProviderError('LIMIT_EXCEEDED', 'Compressed feed exceeds byte limit');
        if (!response.body) throw new ProviderError('MALFORMED_RESPONSE', 'Feed has no body');
        const chunks = [];
        let length = 0;
        for await (const chunk of response.body) {
            length += chunk.byteLength;
            if (length > maxBytes) throw new ProviderError('LIMIT_EXCEEDED', 'Compressed feed exceeds byte limit');
            chunks.push(chunk);
        }
        return { bytes: Buffer.concat(chunks, length), lastModified: response.headers.get('last-modified') };
    } catch (error) {
        if (error instanceof ProviderError) throw error;
        if (controller.signal.aborted) throw new ProviderError('TIMEOUT', 'Download timed out', { retryable: true });
        throw new ProviderError('NETWORK', 'Download connection failed', { retryable: true });
    } finally {
        clearTimeout(timer);
        controller.abort();
    }
}
