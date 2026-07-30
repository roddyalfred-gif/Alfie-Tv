export interface RecommendationItem {
  id: string;
  category: string;
  score: number;
}

export function generateRecommendations(
  channels: Array<{ id: string; category: string; isFavorite?: boolean }>,
  watchedCategories: string[],
  favoriteCategories: string[],
  limit = 5
): RecommendationItem[] {
  const normalizedWatched = new Set(watchedCategories.map((category) => category.toLowerCase()));
  const normalizedFavorites = new Set(favoriteCategories.map((category) => category.toLowerCase()));
  const normalizedLimit = Math.max(1, Math.floor(limit || 5));

  const scored = channels.map((channel) => {
    let score = 0;
    const category = channel.category.toLowerCase();

    if (normalizedWatched.has(category)) {
      score += 3;
    }

    if (normalizedFavorites.has(category)) {
      score += 4;
    }

    if (channel.isFavorite) {
      score += 2;
    }

    return { id: channel.id, category: channel.category, score };
  });

  return scored
    .sort((a, b) => b.score - a.score)
    .slice(0, normalizedLimit)
    .map((item) => ({ ...item, score: Number(item.score.toFixed(2)) }));
}
