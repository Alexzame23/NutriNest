export class ProviderError extends Error {
    constructor(code, message, { status, retryAt, retryable = false } = {}) {
        super(message);
        this.name = 'ProviderError';
        this.code = code;
        this.status = status;
        this.retryAt = retryAt;
        this.retryable = retryable;
    }
}

export function requireField(condition, field, code = 'MALFORMED_RESPONSE') {
    if (!condition) throw new ProviderError(code, `Invalid ${field}`);
}
