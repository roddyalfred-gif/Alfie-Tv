export interface PlatformStatus {
  mode: 'mobile' | 'desktop' | 'web';
  connected: boolean;
  message: string;
}

export function createPlatformStatus(mode: PlatformStatus['mode']): PlatformStatus {
  return {
    mode,
    connected: true,
    message: `${mode.toUpperCase()} client ready`,
  };
}
