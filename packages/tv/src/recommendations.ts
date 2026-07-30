export interface TvRecommendation {
  title: string;
  reason: string;
}

export function getTvRecommendations(): TvRecommendation[] {
  return [
    { title: 'News HD', reason: 'Trending in your favorites' },
    { title: 'Sports Live', reason: 'Recently resumed' },
  ];
}
