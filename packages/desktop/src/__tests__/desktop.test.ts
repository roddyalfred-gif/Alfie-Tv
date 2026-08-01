import { createRequire } from 'node:module';
import { describe, expect, it } from 'vitest';

const require = createRequire(import.meta.url);

describe('desktop scaffold', () => {
  it('exposes a desktop shell entry', () => {
    expect(typeof require('../../index.js')).toBe('object');
  });
});
