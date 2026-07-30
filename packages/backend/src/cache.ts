export class InMemoryCache<T> {
  private readonly values = new Map<string, { expiresAt: number; value: T }>();

  constructor(private readonly ttlMs: number) {}

  set(key: string, value: T): void {
    this.values.set(key, { expiresAt: Date.now() + this.ttlMs, value });
  }

  get(key: string): T | undefined {
    const entry = this.values.get(key);
    if (!entry) {
      return undefined;
    }

    if (entry.expiresAt < Date.now()) {
      this.values.delete(key);
      return undefined;
    }

    return entry.value;
  }

  delete(key: string): void {
    this.values.delete(key);
  }
}
