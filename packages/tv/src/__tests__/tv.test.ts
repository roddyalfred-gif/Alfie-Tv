import { createRequire } from 'node:module';
import { describe, expect, it } from 'vitest';

const require = createRequire(import.meta.url);
const { App } = require('../../index.js');

describe('smart tv scaffold', () => {
  it('exports a tv shell component', () => {
    expect(typeof App).toBe('function');
  });
});
