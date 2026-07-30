import { describe, expect, it } from 'vitest';
import { buildChannelSummary } from '../channelViewModel';

describe('buildChannelSummary', () => {
  it('creates a readable summary for favorite channels', () => {
    const summary = buildChannelSummary({
      id: 'news',
      name: 'News HD',
      category: 'News',
      quality: '1080p',
      isFavorite: true,
    });

    expect(summary.title).toBe('News HD');
    expect(summary.subtitle).toBe('News • 1080p');
    expect(summary.status).toBe('Favorite');
  });

  it('falls back to a default quality label', () => {
    const summary = buildChannelSummary({
      id: 'sports',
      name: 'Sports Live',
      category: 'Sports',
      isFavorite: false,
    });

    expect(summary.subtitle).toBe('Sports • HD');
    expect(summary.status).toBe('Live');
  });
});
