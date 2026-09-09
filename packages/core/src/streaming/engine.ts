import { StreamConfig, StreamEvent, StreamFormat, StreamMetadata, StreamQuality } from './types';

export type StreamingStatus = 'idle' | 'buffering' | 'ready' | 'playing' | 'paused' | 'recovering' | 'stopped' | 'error';

export interface StreamingDiagnostics {
  status: StreamingStatus;
  recoveryAttempts: number;
  lastError: Error | null;
  lastErrorAt: number | null;
  initialized: boolean;
}

const QUALITY_BITRATES: Record<StreamQuality, number> = {
  [StreamQuality.LOW]: 500,
  [StreamQuality.SD]: 1000,
  [StreamQuality.HD]: 2500,
  [StreamQuality.FULL_HD]: 5000,
  [StreamQuality.ULTRA_HD]: 15000,
  [StreamQuality.FULL_4K]: 25000,
};

const QUALITY_RESOLUTIONS: Record<StreamQuality, string> = {
  [StreamQuality.LOW]: '640x360',
  [StreamQuality.SD]: '854x480',
  [StreamQuality.HD]: '1280x720',
  [StreamQuality.FULL_HD]: '1920x1080',
  [StreamQuality.ULTRA_HD]: '3840x2160',
  [StreamQuality.FULL_4K]: '7680x4320',
};

export class StreamingEngine {
  private readonly config: StreamConfig;
  private metadata: StreamMetadata | null = null;
  private readonly listeners = new Map<StreamEvent['type'], Set<(event: StreamEvent) => void>>();
  private isPlaying = false;
  private initialized = false;
  private currentQuality: StreamQuality;
  private status: StreamingStatus = 'idle';
  private recoveryAttempts = 0;
  private lastError: Error | null = null;
  private lastErrorAt: number | null = null;

  constructor(config: StreamConfig) {
    this.config = {
      ...config,
      headers: config.headers ? { ...config.headers } : undefined,
      retryAttempts: Math.max(0, Math.floor(config.retryAttempts ?? 3)),
      timeout: Math.max(1000, Math.floor(config.timeout ?? 15000)),
      bufferSize: Math.max(0, Math.floor(config.bufferSize ?? 0)),
    };
    this.currentQuality = config.quality ?? StreamQuality.HD;
    this.validateConfig();
  }

  private validateConfig(): void {
    if (!this.config.url?.trim()) {
      throw new Error('Stream URL is required');
    }

    try {
      const parsed = new URL(this.config.url);
      if (!['http:', 'https:'].includes(parsed.protocol)) {
        throw new Error('Only HTTP and HTTPS stream URLs are supported');
      }
    } catch {
      throw new Error('Invalid stream URL');
    }
  }

  async initialize(): Promise<void> {
    this.status = 'buffering';
    this.lastError = null;
    this.lastErrorAt = null;
    this.emit('buffering', { timestamp: Date.now() });

    try {
      this.metadata = this.createInitialMetadata();
      this.initialized = true;
      this.status = 'ready';
      this.recoveryAttempts = 0;
    } catch (error) {
      this.handleError(error);
      throw error;
    }
  }

  private createInitialMetadata(): StreamMetadata {
    return {
      duration: 0,
      bitrate: QUALITY_BITRATES[this.currentQuality],
      resolution: QUALITY_RESOLUTIONS[this.currentQuality],
      codec: this.config.format === StreamFormat.HLS ? 'h264' : 'unknown',
      fps: 0,
    };
  }

  async play(): Promise<void> {
    if (!this.initialized) await this.initialize();
    if (this.isPlaying) return;

    this.isPlaying = true;
    this.status = 'playing';
    this.emit('play', { timestamp: Date.now() });
  }

  pause(): void {
    if (!this.isPlaying) return;
    this.isPlaying = false;
    this.status = 'paused';
    this.emit('pause', { timestamp: Date.now() });
  }

  stop(): void {
    this.isPlaying = false;
    this.status = 'stopped';
    this.recoveryAttempts = 0;
    this.emit('stop', { timestamp: Date.now() });
  }

  async changeQuality(quality: StreamQuality): Promise<void> {
    if (!Object.values(StreamQuality).includes(quality)) {
      throw new Error(`Unsupported stream quality: ${String(quality)}`);
    }

    const previousQuality = this.currentQuality;
    if (previousQuality === quality) return;

    this.currentQuality = quality;
    if (this.metadata) {
      this.metadata = {
        ...this.metadata,
        bitrate: QUALITY_BITRATES[quality],
        resolution: QUALITY_RESOLUTIONS[quality],
      };
    }

    this.emit('quality_change', {
      timestamp: Date.now(),
      data: { from: previousQuality, to: quality, autoQuality: this.config.autoQuality },
    });
  }

  markBuffering(): void {
    if (this.status === 'stopped') return;
    this.status = 'buffering';
    this.emit('buffering', { timestamp: Date.now() });
  }

  markRecovered(): void {
    this.recoveryAttempts = 0;
    this.lastError = null;
    this.lastErrorAt = null;
    this.status = this.isPlaying ? 'playing' : 'ready';
  }

  async recover(reason: 'network' | 'media' | 'stall' = 'stall'): Promise<boolean> {
    if (!this.initialized || this.status === 'stopped') return false;

    if (this.recoveryAttempts >= this.config.retryAttempts) {
      this.status = 'error';
      this.handleError(new Error(`Stream recovery exhausted after ${this.recoveryAttempts} attempts (${reason})`));
      return false;
    }

    this.recoveryAttempts += 1;
    this.status = 'recovering';
    this.emit('buffering', {
      timestamp: Date.now(),
      data: { recovery: true, attempt: this.recoveryAttempts, maxAttempts: this.config.retryAttempts, reason },
    });
    return true;
  }

  reportError(error: unknown): void {
    this.handleError(error);
  }

  getConfig(): StreamConfig {
    return {
      ...this.config,
      headers: this.config.headers ? { ...this.config.headers } : undefined,
    };
  }

  getMetadata(): StreamMetadata | null {
    return this.metadata ? { ...this.metadata } : null;
  }

  getCurrentQuality(): StreamQuality {
    return this.currentQuality;
  }

  isInitialized(): boolean {
    return this.initialized;
  }

  getStatus(): StreamingStatus {
    return this.status;
  }

  getDiagnostics(): StreamingDiagnostics {
    return {
      status: this.status,
      recoveryAttempts: this.recoveryAttempts,
      lastError: this.lastError,
      lastErrorAt: this.lastErrorAt,
      initialized: this.initialized,
    };
  }

  on(eventType: StreamEvent['type'], callback: (event: StreamEvent) => void): () => void {
    let callbacks = this.listeners.get(eventType);
    if (!callbacks) {
      callbacks = new Set();
      this.listeners.set(eventType, callbacks);
    }
    callbacks.add(callback);
    return () => callbacks?.delete(callback);
  }

  private handleError(error: unknown): void {
    this.lastError = error instanceof Error ? error : new Error(String(error));
    this.lastErrorAt = Date.now();
    this.status = 'error';
    this.isPlaying = false;
    this.emit('error', { timestamp: this.lastErrorAt, data: this.lastError });
  }

  private emit(eventType: StreamEvent['type'], eventData: Omit<StreamEvent, 'type'>): void {
    const callbacks = this.listeners.get(eventType);
    callbacks?.forEach((callback) => {
      try {
        callback({ type: eventType, ...eventData });
      } catch {
        // Listener failures must not break playback lifecycle management.
      }
    });
  }
}
