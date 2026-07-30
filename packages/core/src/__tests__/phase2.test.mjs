import test from 'node:test';
import assert from 'node:assert/strict';
import { parseXMLTVGuide } from '../epg/xmltv.ts';
import { generateRecommendations } from '../recommendations.ts';

test('parseXMLTVGuide reads channel programs from XMLTV content', () => {
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
  assert.equal(guide['ch-1'].length, 1);
  assert.equal(guide['ch-1'][0].title, 'Evening News');
});

test('generateRecommendations prioritizes watched and favorite channels', () => {
  const channels = [
    { id: 'news', category: 'News', isFavorite: true },
    { id: 'sports', category: 'Sports', isFavorite: false },
    { id: 'movies', category: 'Movies', isFavorite: false },
  ];

  const recommendations = generateRecommendations(channels, ['sports'], ['news'], 2);
  assert.deepEqual(recommendations.map((item) => item.id), ['news', 'sports']);
});
