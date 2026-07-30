import { describe, expect, it } from 'vitest';

describe('desktop scaffold', () => {
  it('exposes a desktop shell entry', () => {
    expect(typeof require('../../index.js')).toBe('object');
  });
});
