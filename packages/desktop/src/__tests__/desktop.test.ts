import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const require = createRequire(import.meta.url);

describe('desktop scaffold', () => {
  it('exposes a desktop shell entry without launching Electron', () => {
    const packageJson = require('../../package.json');
    const entry = readFileSync(fileURLToPath(new URL('../../index.js', import.meta.url)), 'utf8');

    expect(packageJson.main).toBe('index.js');
    expect(entry).toContain('module.exports');
    expect(entry).toContain('createWindow');
  });
});
