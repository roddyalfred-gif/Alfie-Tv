import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { PersistedFavoritesStore } from '../persisted-favorites';

describe('PersistedFavoritesStore', () => {
  beforeEach(() => {
    const storage = new Map<string, string>();
    const mockStorage = {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    };

    Object.defineProperty(globalThis, 'window', {
      value: { localStorage: mockStorage },
      configurable: true,
    });
  });

  afterEach(() => {
    delete (globalThis as { window?: Window }).window;
  });

  it('persists favorite ids and restores them across instances', () => {
    const firstStore = new PersistedFavoritesStore('alfie-tv-favorites');
    firstStore.toggle('channel-a');

    const secondStore = new PersistedFavoritesStore('alfie-tv-favorites');

    expect(secondStore.has('channel-a')).toBe(true);
    expect(secondStore.getAll()).toEqual(['channel-a']);
  });
});
