import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { createDefaultPreferences } from '@alfie-tv/core';
import { readStoredPreferences, writeStoredPreferences } from '../preferencesStorage';

describe('preferencesStorage', () => {
  const originalLocalStorage = window.localStorage;

  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    window.localStorage.clear();
    Object.defineProperty(window, 'localStorage', { value: originalLocalStorage });
  });

  it('falls back to defaults when storage is unavailable', () => {
    Object.defineProperty(window, 'localStorage', {
      value: {
        getItem: () => {
          throw new Error('blocked');
        },
        setItem: () => {
          throw new Error('blocked');
        },
        removeItem: () => {
          throw new Error('blocked');
        },
        clear: () => undefined,
      },
    });

    const defaults = createDefaultPreferences();
    expect(readStoredPreferences(defaults)).toEqual(defaults);
    expect(() => writeStoredPreferences(defaults)).not.toThrow();
  });

  it('reads and writes preferences through storage', () => {
    const defaults = createDefaultPreferences();
    const next = { ...defaults, vpnEnabled: true, vpnMode: 'auto' as const, vpnProvider: 'NordVPN' };

    writeStoredPreferences(next);

    expect(readStoredPreferences(defaults)).toEqual(next);
  });
});
