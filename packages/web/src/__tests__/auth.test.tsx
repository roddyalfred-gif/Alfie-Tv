import { describe, expect, it } from 'vitest';
import { loginUser } from '../auth';

describe('loginUser', () => {
  it('throws when the auth endpoint returns a non-ok response', async () => {
    const originalFetch = global.fetch;
    global.fetch = (() => Promise.resolve({ ok: false } as Response)) as typeof fetch;

    await expect(loginUser('user', 'pass')).rejects.toThrow('Unable to sign in');

    global.fetch = originalFetch;
  });
});
