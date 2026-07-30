export interface HistoryEntry {
  channelId: string;
  watchedAt: number;
  positionSeconds: number;
}

export class HistoryStore {
  protected entries: HistoryEntry[] = [];

  load(): HistoryEntry[] {
    return this.entries;
  }

  add(entry: HistoryEntry): void {
    this.entries = [entry, ...this.entries].slice(0, 20);
  }

  getRecent(limit = 5): HistoryEntry[] {
    return this.entries.slice(0, limit);
  }

  clear(): void {
    this.entries = [];
  }
}
