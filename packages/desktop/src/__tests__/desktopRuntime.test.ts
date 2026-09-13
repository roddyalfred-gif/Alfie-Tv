import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';

describe('desktop runtime hardening', () => {
  it('keeps the Electron entrypoint and runtime patch syntactically valid', () => {
    const root = path.resolve(__dirname, '../..');
    const index = fs.readFileSync(path.join(root, 'index.js'), 'utf8');
    const runtime = fs.readFileSync(path.join(root, 'runtimePatch.js'), 'utf8');
    expect(() => new vm.Script(index)).not.toThrow();
    expect(() => new vm.Script(runtime)).not.toThrow();
  });

  it('contains provider, guide, HLS recovery, and session restore hooks', () => {
    const root = path.resolve(__dirname, '../..');
    const index = fs.readFileSync(path.join(root, 'index.js'), 'utf8');
    const runtime = fs.readFileSync(path.join(root, 'runtimePatch.js'), 'utf8');
    const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
    expect(index).toContain("ipcMain.handle('alfie:provider'");
    expect(index).toContain("get_short_epg");
    expect(index).toContain("require('hls.js')");
    expect(runtime).toContain("localStorage.getItem('alfie-provider')");
    expect(html).toContain('Connect Provider');
    expect(html).toContain('TV Guide');
    expect(html).toContain('Movies');
    expect(html).toContain('Series');
    expect(html).toContain('Favorites');
  });
});
