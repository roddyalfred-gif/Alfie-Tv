import { describe, expect, it } from 'vitest';
import { createTvViewModel } from '../tvApp';

describe('createTvViewModel', () => {
  it('creates a shared smart-tv view model with richer state', () => {
    const viewModel = createTvViewModel();
    expect(viewModel.title).toBe('Alfie TV Smart TV');
    expect(viewModel.preferences.theme).toBe('dark');
    expect(viewModel.navigation.currentScreen).toBe('home');
    expect(viewModel.playback.channelId).toBe('news-hd');
    expect(viewModel.playback.isPlaying).toBe(true);
    expect(viewModel.deviceProfile.profile).toBe('tv');
    expect(viewModel.deviceProfile.supportsRemoteControl).toBe(true);
    expect(viewModel.quickActions).toContain('Resume');
  });
});
