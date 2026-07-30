export interface RecentHistoryItem {
  channelId: string;
  label: string;
  watchedAt: number;
}

export function formatRecentHistory(items: RecentHistoryItem[]): string[] {
  return items.map((item) => {
    const timestamp = new Date(item.watchedAt).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
    });

    return `${item.label} • ${timestamp}`;
  });
}
