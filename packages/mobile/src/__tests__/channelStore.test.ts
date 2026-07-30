import { describe, expect, it } from 'vitest';
import { getMobileChannels } from '../channelStore';

describe('getMobileChannels', () => {
  it('returns featured channels for the mobile experience', () => {
    const channels = getMobileChannels();
    expect(channels.some((channel) => channel.featured)).toBe(true);
    expect(channels[0].name).toBe('News HD');
  });
});
