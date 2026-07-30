export interface WatchHistoryEntry {
  channelId: string;
  watchedAt: number;
  positionSeconds: number;
}

export function createWatchHistorySummary(entries: WatchHistoryEntry[]): Array<{ channelId: string; count: number }> {
  return Object.entries(
    entries.reduce<Record<string, number>>((acc, entry) => {
      acc[entry.channelId] = (acc[entry.channelId] || 0) + 1;
      return acc;
    }, {})
  ).map(([channelId, count]) => ({ channelId, count }));
}
