export class PersistedFavoritesStore {
  private favorites: Set<string> = new Set();

  constructor(private readonly storageKey: string) {
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
      const parsed = JSON.parse(raw) as string[];
      this.favorites = new Set(parsed);
    } catch {
      this.favorites = new Set();
    }
  }

  toggle(channelId: string): boolean {
    if (this.favorites.has(channelId)) {
      this.favorites.delete(channelId);
      this.persist();
      return false;
    }

    this.favorites.add(channelId);
    this.persist();
    return true;
  }

  has(channelId: string): boolean {
    return this.favorites.has(channelId);
  }

  getAll(): string[] {
    return Array.from(this.favorites);
  }

  private persist(): void {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }

    window.localStorage.setItem(this.storageKey, JSON.stringify(this.getAll()));
  }
}
