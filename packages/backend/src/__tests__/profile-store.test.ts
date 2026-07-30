import { mkdtempSync, rmSync } from 'fs';
import os from 'os';
import path from 'path';
import { afterEach, describe, expect, it } from 'vitest';
import { ProfileStore } from '../profile-store';

describe('ProfileStore', () => {
  const tempDirs: string[] = [];

  afterEach(() => {
    tempDirs.splice(0).forEach((dir) => rmSync(dir, { recursive: true, force: true }));
  });

  it('stores and retrieves a user profile', () => {
    const dir = mkdtempSync(path.join(os.tmpdir(), 'alfie-profile-'));
    tempDirs.push(dir);
    const store = new ProfileStore(path.join(dir, 'profiles.json'));

    const profile = store.saveProfile({ id: 'u1', username: 'demo', email: 'demo@example.com', theme: 'dark', language: 'en', createdAt: 1, updatedAt: 2 });

    expect(store.getProfile('u1')).toEqual(profile);
  });
});
