import { describe, expect, it } from 'vitest';
import { parseXMLTVGuide } from '../epg/xmltv';
import { generateRecommendations } from '../recommendations';

describe('phase 2 coverage', () => {
  it('parseXMLTVGuide reads channel programs from XMLTV content', () => {
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
  <tv>
    <channel id="ch-1">
      <display-name>Demo News</display-name>
    </channel>
    <programme channel="ch-1" start="20240101000000 +0000" stop="20240101010000 +0000">
      <title lang="en">Evening News</title>
      <desc lang="en">A detailed report</desc>
    </programme>
  </tv>`;

    const guide = parseXMLTVGuide(xml);
    expect(guide['ch-1']).toHaveLength(1);
    expect(guide['ch-1'][0].title).toBe('Evening News');
  });

  it('generateRecommendations prioritizes watched and favorite channels', () => {
    const channels = [
      { id: 'news', category: 'News', isFavorite: true },
      { id: 'sports', category: 'Sports', isFavorite: false },
      { id: 'movies', category: 'Movies', isFavorite: false },
    ];

    const recommendations = generateRecommendations(channels, ['sports'], ['news'], 2);
    expect(recommendations.map((item) => item.id)).toEqual(['news', 'sports']);
  });
});