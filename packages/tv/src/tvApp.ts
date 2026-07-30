import { createDefaultPreferences } from '@alfie-tv/core';
import { createDeviceProfile } from '@alfie-tv/core';
import { createNavigationState } from '@alfie-tv/core';
import { createPlaybackState, updatePlaybackState } from '@alfie-tv/core';
import { createPlatformStatus } from '@alfie-tv/core';

export function createTvViewModel() {
  const navigation = createNavigationState('home');
  const preferences = createDefaultPreferences();
  const platform = createPlatformStatus('desktop');
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
  };
}
