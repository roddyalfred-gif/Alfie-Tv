import { describe, expect, it } from 'vitest';
import { getOfflineStatusMessage } from '../offline';

describe('getOfflineStatusMessage', () => {
  it('reports online status', () => {
    expect(typeof getOfflineStatusMessage()).toBe('string');
  });

  it('supports explicit offline overrides', () => {
    expect(getOfflineStatusMessage(false)).toBe('Offline');
  });
});
