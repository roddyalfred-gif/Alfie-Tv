import { mkdtempSync, rmSync } from 'fs';
import path from 'path';
import os from 'os';
import { describe, expect, it, afterEach } from 'vitest';
import { FileStore } from '../store';
import { InMemoryCache } from '../cache';

describe('FileStore', () => {
  const tempDirs: string[] = [];

  afterEach(() => {
    tempDirs.splice(0).forEach((dir) => rmSync(dir, { recursive: true, force: true }));
  });

  it('persists channels to disk and reloads them', () => {
    const dir = mkdtempSync(path.join(os.tmpdir(), 'alfie-tv-store-'));
    tempDirs.push(dir);
    const store = new FileStore(path.join(dir, 'data.json'));

    store.setChannels([{ id: 'c1', name: 'Test', category: 'News', streamUrl: 'https://example.com/stream.m3u8', isFavorite: false }]);

    const reloaded = new FileStore(path.join(dir, 'data.json'));
    expect(reloaded.getChannels()).toHaveLength(1);
    expect(reloaded.getChannels()[0].name).toBe('Test');
  });

  it('drops malformed channels and normalizes valid ones when persisting', () => {
    const dir = mkdtempSync(path.join(os.tmpdir(), 'alfie-tv-store-'));
    tempDirs.push(dir);
    const store = new FileStore(path.join(dir, 'data.json'));

    store.setChannels([
      { id: 'c1', name: 'Valid', category: 'News', streamUrl: 'https://example.com/stream.m3u8', isFavorite: true },
      { id: '', name: 'Missing Fields', category: 'News', streamUrl: '' },
      { id: 'c2', name: 'Missing Category', streamUrl: 'https://example.com/stream2.m3u8' } as unknown as Parameters<FileStore['setChannels']>[0][number],
    ]);

    const reloaded = new FileStore(path.join(dir, 'data.json'));
    const channels = reloaded.getChannels();

    expect(channels).toHaveLength(2);
    expect(channels[0]).toMatchObject({ id: 'c1', name: 'Valid', category: 'News', isFavorite: true });
    expect(channels[1]).toMatchObject({ id: 'c2', name: 'Missing Category', category: 'General', isFavorite: false });
  });
});

describe('InMemoryCache', () => {
  it('returns cached values until they expire', () => {
    const cache = new InMemoryCache<string>(10);
    cache.set('greeting', 'hello');

    expect(cache.get('greeting')).toBe('hello');
    cache.delete('greeting');
    expect(cache.get('greeting')).toBeUndefined();
  });
});
