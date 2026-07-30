import { ClientPreferences, createDefaultPreferences } from '@alfie-tv/core';

const PREFERENCES_STORAGE_KEY = 'alfie-tv-preferences';

function safeStorage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

export function readStoredPreferences(defaults: ClientPreferences = createDefaultPreferences()): ClientPreferences {
  const storage = safeStorage();

  if (!storage) {
    return defaults;
  }

  try {
    const storedPreferences = storage.getItem(PREFERENCES_STORAGE_KEY);

    if (!storedPreferences) {
      return defaults;
    }

    const parsed = JSON.parse(storedPreferences) as Partial<ClientPreferences>;
    return {
      ...defaults,
      ...parsed,
    };
  } catch {
    return defaults;
  }
}

export function writeStoredPreferences(preferences: ClientPreferences): void {
  const storage = safeStorage();

  if (!storage) {
    return;
  }

  try {
    storage.setItem(PREFERENCES_STORAGE_KEY, JSON.stringify(preferences));
  } catch {
    // Ignore storage failures and keep the in-memory state intact.
  }
}
