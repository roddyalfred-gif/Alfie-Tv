import { describe, expect, it } from 'vitest';
import { getTvRecommendations } from '../recommendations';

describe('getTvRecommendations', () => {
  it('returns tv recommendations for the smart tv shell', () => {
    const recommendations = getTvRecommendations();
    expect(recommendations.length).toBe(2);
    expect(recommendations[0].title).toBe('News HD');
  });
});
