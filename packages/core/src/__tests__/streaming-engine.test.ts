import { describe, expect, it, vi } from 'vitest';
import { StreamingEngine } from '../streaming/engine';
import { StreamFormat, StreamQuality } from '../streaming/types';

const config = {
  url: 'https://example.com/live.m3u8',
  format: StreamFormat.HLS,
  quality: StreamQuality.HD,
  autoQuality: true,
  bufferSize: 20,
  timeout: 15000,
  retryAttempts: 3,
};

describe('StreamingEngine', () => {
  it('validates and initializes an HTTP stream', async () => {
    const engine = new StreamingEngine(config);
    await engine.initialize();

    expect(engine.isInitialized()).toBe(true);
    expect(engine.getStatus()).toBe('ready');
    expect(engine.getMetadata()).toMatchObject({ resolution: '1280x720' });
  });

  it('rejects invalid protocols', () => {
    expect(() => new StreamingEngine({ ...config, url: 'ftp://example.com/live.m3u8' })).toThrow();
  });

  it('changes quality and emits an event', async () => {
    const engine = new StreamingEngine(config);
    const listener = vi.fn();
    engine.on('quality_change', listener);

    await engine.initialize();
    await engine.changeQuality(StreamQuality.FULL_HD);

    expect(engine.getCurrentQuality()).toBe(StreamQuality.FULL_HD);
    expect(engine.getMetadata()?.resolution).toBe('1920x1080');
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('limits recovery attempts', async () => {
    const engine = new StreamingEngine({ ...config, retryAttempts: 2 });
    await engine.initialize();

    expect(await engine.recover('network')).toBe(true);
    expect(await engine.recover('media')).toBe(true);
    expect(await engine.recover('stall')).toBe(false);
    expect(engine.getStatus()).toBe('error');
  });
});
