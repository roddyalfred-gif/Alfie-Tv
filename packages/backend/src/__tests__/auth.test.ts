import crypto from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { createToken, verifyToken } from '../auth';

describe('auth helpers', () => {
  it('creates and verifies a signed token with expiry claims', () => {
    const token = createToken({ sub: 'user-1' }, 'secret');
    const payload = verifyToken(token, 'secret');

    expect(payload).toMatchObject({ sub: 'user-1' });
    expect(typeof payload?.iat).toBe('number');
    expect(typeof payload?.exp).toBe('number');
  });

  it('rejects a token signed with a different secret', () => {
    const token = createToken({ sub: 'user-2' }, 'secret');
    expect(verifyToken(token, 'other')).toBeNull();
  });

  it('rejects an expired token', () => {
    const now = Math.floor(Date.now() / 1000);
    const token = createToken({ sub: 'user-3', exp: now - 1 }, 'secret');
    expect(verifyToken(token, 'secret')).toBeNull();
  });

  it('rejects an unsupported JWT algorithm', () => {
    const token = createToken({ sub: 'user-4' }, 'secret');
    const [, body, signature] = token.split('.');
    const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
    expect(verifyToken(`${header}.${body}.${signature}`, 'secret')).toBeNull();
  });

  it('rejects a token without an expiry claim', () => {
    const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
    const body = Buffer.from(JSON.stringify({ sub: 'user-5' })).toString('base64url');
    const signature = crypto.createHmac('sha256', 'secret').update(`${header}.${body}`).digest('base64url');
    expect(verifyToken(`${header}.${body}.${signature}`, 'secret')).toBeNull();
  });

  it('rejects a token with a future issued timestamp', () => {
    const now = Math.floor(Date.now() / 1000);
    const token = createToken({ sub: 'user-6', iat: now + 60, exp: now + 3600 }, 'secret');
    expect(verifyToken(token, 'secret')).toBeNull();
  });

  it('rejects a token whose expiry is not after its issued timestamp', () => {
    const now = Math.floor(Date.now() / 1000);
    const token = createToken({ sub: 'user-7', iat: now - 60, exp: now - 120 }, 'secret');
    expect(verifyToken(token, 'secret')).toBeNull();
  });
});
