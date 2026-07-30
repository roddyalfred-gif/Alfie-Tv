import { describe, expect, it } from 'vitest';
import { buildSeedChannels, buildSeedPrograms } from '../index';

describe('backend seed data', () => {
  it('creates realistic seed channels without placeholder logos', () => {
    const channels = buildSeedChannels();

    expect(channels).toHaveLength(3);
    expect(channels.every((channel) => channel.logo === '')).toBe(true);
    expect(channels[0]).toMatchObject({ name: 'Alfie News', category: 'News' });
  });

  it('creates realistic seed programs for a channel', () => {
    const programs = buildSeedPrograms('ch-1');

    expect(programs).toHaveLength(2);
    expect(programs[0]).toMatchObject({ channelId: 'ch-1', genre: 'News' });
  });
});
