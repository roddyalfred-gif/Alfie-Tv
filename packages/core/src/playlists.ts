import { Channel } from './channels/types';

export function parseM3UPlaylist(content: string): Channel[] {
  const channels: Channel[] = [];
  const lines = content.split(/\r?\n/);
  let current: Partial<Channel> | null = null;

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    if (trimmed.startsWith('#EXTINF')) {
      const categoryMatch = trimmed.match(/group-title="([^"]+)"/i);
      current = {
        id: `m3u-${channels.length + 1}`,
        name: trimmed.split(',').pop()?.trim() || 'Untitled Channel',
        number: channels.length + 1,
        logo: '',
        streamUrl: '',
        category: categoryMatch?.[1] || 'General',
        isFavorite: false,
        quality: '1080p',
      };
      continue;
    }

    if (trimmed.startsWith('#')) {
      continue;
    }

    if (current) {
      current.streamUrl = trimmed;
      channels.push({
        ...current,
        id: current.id || `m3u-${channels.length + 1}`,
        name: current.name || 'Untitled Channel',
        category: current.category || 'General',
        streamUrl: trimmed,
      } as Channel);
      current = null;
    }
  }

  return channels;
}
