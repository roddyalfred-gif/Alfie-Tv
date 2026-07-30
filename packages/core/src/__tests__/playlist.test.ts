import { describe, expect, it } from 'vitest';
import { parseM3UPlaylist } from '../playlists';

describe('parseM3UPlaylist', () => {
  it('parses a simple playlist into a single channel', () => {
    const playlist = `#EXTM3U
#EXTINF:-1 tvg-id="ch1" tvg-name="Demo Channel" group-title="News",Demo Channel
https://example.com/stream.m3u8`;

    const channels = parseM3UPlaylist(playlist);

    expect(channels).toHaveLength(1);
    expect(channels[0].name).toBe('Demo Channel');
    expect(channels[0].category).toBe('News');
    expect(channels[0].streamUrl).toBe('https://example.com/stream.m3u8');
  });
});
