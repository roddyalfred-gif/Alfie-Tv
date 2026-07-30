import { describe, expect, it } from 'vitest';
import { createToken, verifyToken } from '../auth';

describe('auth helpers', () => {
  it('creates and verifies a signed token', () => {
    const token = createToken({ sub: 'user-1' }, 'secret');

    expect(verifyToken(token, 'secret')).toMatchObject({ sub: 'user-1' });
  });

  it('rejects a token signed with a different secret', () => {
    const token = createToken({ sub: 'user-2' }, 'secret');

    expect(verifyToken(token, 'other')).toBeNull();
  });
});
