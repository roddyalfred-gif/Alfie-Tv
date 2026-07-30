import { describe, it, expect } from 'vitest';
import { HistoryStore } from '../history-store';

describe('HistoryStore', () => {
  it('returns the most recent entries first and clears them', () => {
    const store = new HistoryStore();
    store.add({ channelId: 'one', watchedAt: 100, positionSeconds: 1 });
    store.add({ channelId: 'two', watchedAt: 200, positionSeconds: 2 });

    expect(store.getRecent(1).map((entry) => entry.channelId)).toEqual(['two']);

    store.clear();
    expect(store.load()).toEqual([]);
  });
});
