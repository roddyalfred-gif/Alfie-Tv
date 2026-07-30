import { mkdtempSync, rmSync } from 'fs';
import os from 'os';
import path from 'path';
import { afterEach, describe, expect, it } from 'vitest';
import { JsonDatabase } from '../database';

describe('JsonDatabase', () => {
  const tempDirs: string[] = [];

  afterEach(() => {
    tempDirs.splice(0).forEach((dir) => rmSync(dir, { recursive: true, force: true }));
  });

  it('persists and reloads channels from disk', () => {
    const dir = mkdtempSync(path.join(os.tmpdir(), 'alfie-db-'));
    tempDirs.push(dir);
    const db = new JsonDatabase(path.join(dir, 'channels.json'));

    db.addChannel({ id: 'db-1', name: 'DB Channel', category: 'News', streamUrl: 'https://example.com/live.m3u8' });

    expect(db.listChannels()).toHaveLength(1);
    expect(db.listChannels()[0].name).toBe('DB Channel');
  });
});
