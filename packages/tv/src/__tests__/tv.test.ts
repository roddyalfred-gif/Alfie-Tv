import { describe, expect, it } from 'vitest';
import { createTvViewModel } from '../tvApp';

describe('smart tv scaffold', () => {
  it('creates a valid TV shell view model', () => {
    const viewModel = createTvViewModel();

    expect(viewModel.title).toBe('Alfie TV Smart TV');
    expect(viewModel.quickActions).toEqual(['Resume', 'Favorites', 'Guide']);
    expect(viewModel.deviceProfile.supportsRemoteControl).toBe(true);
    expect(viewModel.playback.isPlaying).toBe(true);
    expect(viewModel.playback.positionSeconds).toBe(1280);
  });
});
