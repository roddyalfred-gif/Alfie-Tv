import { HistoryEntry, HistoryStore } from './history-store';

export class PersistedHistoryStore extends HistoryStore {
  constructor(private readonly storageKey: string) {
    super();
    this.loadFromStorage();
  }

  private loadFromStorage(): void {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }

    const raw = window.localStorage.getItem(this.storageKey);
    if (!raw) {
      return;
    }

    try {
      const parsed = JSON.parse(raw) as HistoryEntry[];
      this.entries = parsed;
    } catch {
      this.entries = [];
    }
  }

  override add(entry: HistoryEntry): void {
    super.add(entry);
    this.persist();
  }

  override clear(): void {
    super.clear();
    this.persist();
  }

  private persist(): void {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }

    window.localStorage.setItem(this.storageKey, JSON.stringify(this.load()));
  }
}
