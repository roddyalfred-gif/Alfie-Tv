import { describe, expect, it } from 'vitest';
import { getDesktopGreeting } from '../desktopApp';

describe('getDesktopGreeting', () => {
  it('returns the desktop app greeting', () => {
    expect(getDesktopGreeting()).toBe('Alfie TV Desktop shell ready');
  });
});
