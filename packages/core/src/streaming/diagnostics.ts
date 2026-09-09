export type PlaybackErrorCategory =
  | 'network'
  | 'decoder'
  | 'buffering'
  | 'stall'
  | 'format'
  | 'unknown';

export interface PlaybackDiagnostics {
  startupLatencyMs: number | null;
  bitrate: number | null;
  resolution: string | null;
  bufferedSeconds: number | null;
  rebufferCount: number;
  recoveryCount: number;
  droppedFrames: number | null;
  lastErrorCategory: PlaybackErrorCategory | null;
  lastErrorAt: number | null;
}

export function createPlaybackDiagnostics(): PlaybackDiagnostics {
  return {
    startupLatencyMs: null,
    bitrate: null,
    resolution: null,
    bufferedSeconds: null,
    rebufferCount: 0,
    recoveryCount: 0,
    droppedFrames: null,
    lastErrorCategory: null,
    lastErrorAt: null,
  };
}
