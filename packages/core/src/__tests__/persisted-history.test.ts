import { describe, it, expect, beforeEach } from 'vitest';
import { PersistedHistoryStore } from '../persisted-history';
import { installLocalStorageMock } from './helpers/local-storage';

describe('PersistedHistoryStore', () => {
  beforeEach(() => {
    Object.defineProperty(globalThis, 'window', {
      value: { localStorage: installLocalStorageMock() },
      configurable: true,
    });
  });

  it('persists entries to local storage and reloads them', () => {
    const storage = new PersistedHistoryStore('phase2-test');
    storage.clear();
    storage.add({ channelId: 'news', watchedAt: 1, positionSeconds: 2 });

    const reloaded = new PersistedHistoryStore('phase2-test');
    expect(reloaded.load()).toHaveLength(1);
    expect(reloaded.load()[0].channelId).toBe('news');
  });
});
