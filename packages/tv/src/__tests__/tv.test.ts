import { describe, expect, it } from 'vitest';

const { App } = require('../../index.js');

describe('smart tv scaffold', () => {
  it('exports a tv shell component', () => {
    expect(typeof App).toBe('function');
  });
});
