export interface ClientPreferences {
  theme: 'dark' | 'light';
  autoplay: boolean;
  quality: 'auto' | 'hd' | 'sd';
  vpnEnabled: boolean;
  vpnMode: 'off' | 'auto' | 'on';
  vpnProvider: string;
}

const DEFAULT_PREFERENCES: ClientPreferences = {
  theme: 'dark',
  autoplay: true,
  quality: 'auto',
  vpnEnabled: false,
  vpnMode: 'off',
  vpnProvider: 'default',
};

function sanitizePreference<T extends keyof ClientPreferences>(
  key: T,
  value: ClientPreferences[T]
): ClientPreferences[T] {
  if (key === 'theme') {
    return (value === 'light' ? 'light' : 'dark') as ClientPreferences[T];
  }

  if (key === 'autoplay') {
    return Boolean(value) as ClientPreferences[T];
  }

  if (key === 'vpnEnabled') {
    return Boolean(value) as ClientPreferences[T];
  }

  if (key === 'vpnMode') {
    return ((value === 'auto' || value === 'on') ? value : 'off') as ClientPreferences[T];
  }

  if (key === 'vpnProvider') {
    return (typeof value === 'string' && value.trim().length > 0 ? value.trim() : 'default') as ClientPreferences[T];
  }

  return (value === 'hd' || value === 'sd' ? value : 'auto') as ClientPreferences[T];
}

export function createDefaultPreferences(): ClientPreferences {
  return { ...DEFAULT_PREFERENCES };
}

export function updatePreference<T extends keyof ClientPreferences>(
  preferences: ClientPreferences,
  key: T,
  value: ClientPreferences[T]
): ClientPreferences {
  return {
    ...preferences,
    [key]: sanitizePreference(key, value),
  };
}
