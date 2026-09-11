import { createDefaultPreferences, createDeviceProfile, createNavigationState, createPlaybackState, updatePlaybackState, createPlatformStatus } from '@alfie-tv/core';

export function createTvViewModel() {
  const navigation = createNavigationState('home');
  const preferences = createDefaultPreferences();
  const platform = createPlatformStatus('tv');
  const playback = updatePlaybackState(createPlaybackState('news-hd'), {
    isPlaying: true,
    positionSeconds: 1280,
  });
  const deviceProfile = createDeviceProfile('tv');

  return {
    navigation,
    preferences,
    platform,
    playback,
    deviceProfile,
    quickActions: ['Resume', 'Favorites', 'Guide'],
    title: 'Alfie TV Smart TV',
    interfaceSections: ['Live TV', 'Movies', 'Series', 'Favorites', 'Guide', 'Settings'],
  };
}
