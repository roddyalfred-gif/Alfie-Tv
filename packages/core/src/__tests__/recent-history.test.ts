import { describe, it, expect } from 'vitest';
import { formatRecentHistory } from '../recent-history';

describe('formatRecentHistory', () => {
  it('formats recent history items with labels and times', () => {
    const formatted = formatRecentHistory([
      { channelId: 'demo-channel', label: 'Demo Channel', watchedAt: Date.parse('2024-01-01T12:00:00Z') },
    ]);

    expect(formatted[0]).toContain('Demo Channel');
    expect(formatted[0]).toContain('12:00');
  });
});
