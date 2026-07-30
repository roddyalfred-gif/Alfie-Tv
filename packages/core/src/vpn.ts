import { ClientPreferences } from './preferences';

export type VpnStatusState = 'disabled' | 'active' | 'standby';

export interface VpnStatus {
  state: VpnStatusState;
  enabled: boolean;
  mode: ClientPreferences['vpnMode'];
  provider: string;
  message: string;
}

export function resolveVpnStatus(preferences: ClientPreferences, isOnline: boolean): VpnStatus {
  const mode = preferences.vpnMode;
  const provider = preferences.vpnProvider || 'default';

  if (!preferences.vpnEnabled) {
    return {
      state: 'disabled',
      enabled: false,
      mode,
      provider,
      message: 'VPN disabled',
    };
  }

  if (mode === 'on') {
    return {
      state: 'active',
      enabled: true,
      mode,
      provider,
      message: `VPN active via ${provider}`,
    };
  }

  if (mode === 'auto') {
    const state = isOnline ? 'active' : 'standby';

    return {
      state,
      enabled: true,
      mode,
      provider,
      message: isOnline ? `VPN auto mode active via ${provider}` : `VPN auto mode standby via ${provider}`,
    };
  }

  return {
    state: 'disabled',
    enabled: false,
    mode: 'off',
    provider,
    message: 'VPN disabled',
  };
}
