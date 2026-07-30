export type DeviceProfile = 'mobile' | 'desktop' | 'tv' | 'web';

export interface DeviceProfileState {
  profile: DeviceProfile;
  optimizedForTouch: boolean;
  supportsRemoteControl: boolean;
}

export function createDeviceProfile(profile: DeviceProfile): DeviceProfileState {
  return {
    profile,
    optimizedForTouch: profile === 'mobile',
    supportsRemoteControl: profile === 'tv' || profile === 'desktop',
  };
}
