import { describe, expect, it } from 'vitest';
import { ChannelManager } from '../channels/manager';
import { createPlatformStatus } from '../platform-status';
import { createNavigationState, navigateTo } from '../navigation';
import { createDefaultPreferences, updatePreference } from '../preferences';
import { createPlaybackState, updatePlaybackState } from '../playback-state';
import { createDeviceProfile } from '../device-profile';
import { resolveVpnStatus } from '../vpn';
import type { ClientPreferences } from '../preferences';
import type { PlaybackState } from '../playback-state';

describe('ChannelManager favorites', () => {
  it('updates channel favorite state when toggled', () => {
    const manager = new ChannelManager();
    manager.addChannel({
      id: 'news',
      name: 'News',
      number: 1,
      logo: '',
      streamUrl: 'https://example.com/stream.m3u8',
      category: 'News',
      isFavorite: false,
      quality: '1080p',
    });

    manager.toggleFavorite('news');

    expect(manager.getChannel('news')?.isFavorite).toBe(true);
    expect(manager.getFavorites().map((channel) => channel.id)).toEqual(['news']);
  });

  it('filters to favorite-only channels when requested', () => {
    const manager = new ChannelManager();
    manager.addChannel({
      id: 'news',
      name: 'News',
      number: 1,
      logo: '',
      streamUrl: 'https://example.com/stream.m3u8',
      category: 'News',
      isFavorite: false,
      quality: '1080p',
    });
    manager.addChannel({
      id: 'sports',
      name: 'Sports',
      number: 2,
      logo: '',
      streamUrl: 'https://example.com/stream.m3u8',
      category: 'Sports',
      isFavorite: false,
      quality: '1080p',
    });

    manager.toggleFavorite('news');

    expect(manager.filterChannels({ favoriteOnly: true }).map((channel) => channel.id)).toEqual(['news']);
  });

  it('refreshes cached favorite filters after a favorite changes', () => {
    const manager = new ChannelManager();
    manager.addChannel({
      id: 'news',
      name: 'News',
      number: 1,
      logo: '',
      streamUrl: 'https://example.com/stream.m3u8',
      category: 'News',
      isFavorite: false,
      quality: '1080p',
    });

    expect(manager.filterChannels({ favoriteOnly: true }).map((channel) => channel.id)).toEqual([]);

    manager.toggleFavorite('news');

    expect(manager.filterChannels({ favoriteOnly: true }).map((channel) => channel.id)).toEqual(['news']);
  });

  it('imports channel names from M3U metadata and skips duplicate stream URLs', () => {
    const manager = new ChannelManager();

    const imported = manager.importFromM3U(`
#EXTM3U
#EXTINF:-1,News Channel
https://example.com/news.m3u8
#EXTINF:-1,Sports Channel
https://example.com/sports.m3u8
https://example.com/news.m3u8
`);

    expect(imported).toHaveLength(2);
    expect(imported[0].name).toBe('News Channel');
    expect(imported[1].name).toBe('Sports Channel');
    expect(manager.getAllChannels()).toHaveLength(2);
  });

  it('creates a shared platform status message for clients', () => {
    expect(createPlatformStatus('mobile')).toEqual({
      mode: 'mobile',
      connected: true,
      message: 'MOBILE client ready',
    });
  });

  it('supports shared navigation transitions between screens', () => {
    const state = createNavigationState('home');
    const nextState = navigateTo(state, 'channels');

    expect(nextState.currentScreen).toBe('channels');
    expect(nextState.canGoBack).toBe(true);
  });

  it('creates shared client preferences with update support', () => {
    const preferences = createDefaultPreferences();
    const nextPreferences = updatePreference(preferences, 'quality', 'hd');

    expect(nextPreferences.quality).toBe('hd');
    expect(nextPreferences.autoplay).toBe(true);
  });

  it('normalizes invalid preference values to safe defaults', () => {
    const preferences = createDefaultPreferences();
    const nextPreferences = updatePreference(preferences, 'quality', '4k' as unknown as ClientPreferences['quality']);

    expect(nextPreferences.quality).toBe('auto');
    expect(updatePreference(preferences, 'autoplay', undefined as unknown as ClientPreferences['autoplay']).autoplay).toBe(false);
    expect(updatePreference(preferences, 'theme', 'blue' as unknown as ClientPreferences['theme']).theme).toBe('dark');
  });

  it('supports vpn preferences with safe defaults', () => {
    const preferences = createDefaultPreferences();
    const nextPreferences = updatePreference(preferences, 'vpnEnabled', true);
    const vpnModePreferences = updatePreference(nextPreferences, 'vpnMode', 'auto');
    const invalidModePreferences = updatePreference(preferences, 'vpnMode', 'boost' as unknown as ClientPreferences['vpnMode']);

    expect(nextPreferences.vpnEnabled).toBe(true);
    expect(vpnModePreferences.vpnMode).toBe('auto');
    expect(invalidModePreferences.vpnMode).toBe('off');
    expect(updatePreference(preferences, 'vpnProvider', 'NordVPN').vpnProvider).toBe('NordVPN');
  });

  it('resolves vpn status from shared preferences and connectivity', () => {
    const preferences = createDefaultPreferences();
    const disabledState = resolveVpnStatus({ ...preferences, vpnEnabled: false, vpnMode: 'off' }, true);
    const activeState = resolveVpnStatus({ ...preferences, vpnEnabled: true, vpnMode: 'on', vpnProvider: 'NordVPN' }, true);
    const standbyState = resolveVpnStatus({ ...preferences, vpnEnabled: true, vpnMode: 'auto', vpnProvider: 'SurfShark' }, false);

    expect(disabledState.state).toBe('disabled');
    expect(activeState.state).toBe('active');
    expect(standbyState.state).toBe('standby');
    expect(activeState.message).toContain('NordVPN');
  });

  it('creates shared playback state for all clients', () => {
    const state = createPlaybackState('news');
    const nextState = updatePlaybackState(state, { isPlaying: true, positionSeconds: 42 });

    expect(nextState.channelId).toBe('news');
    expect(nextState.isPlaying).toBe(true);
    expect(nextState.positionSeconds).toBe(42);
  });

  it('clamps invalid playback positions and normalizes playback flags', () => {
    const state = createPlaybackState('news');
    const nextState = updatePlaybackState(state, { isPlaying: false, positionSeconds: -10 as unknown as PlaybackState['positionSeconds'] });

    expect(nextState.positionSeconds).toBe(0);
    expect(nextState.isPlaying).toBe(false);
  });

  it('creates shared device profiles for each client shell', () => {
    const profile = createDeviceProfile('tv');

    expect(profile.profile).toBe('tv');
    expect(profile.optimizedForTouch).toBe(false);
    expect(profile.supportsRemoteControl).toBe(true);
  });
});
