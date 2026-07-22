import { StreamConfig, StreamEvent, StreamFormat, StreamMetadata, StreamQuality } from './types';

export class StreamingEngine {
  private config: StreamConfig;
  private metadata: StreamMetadata | null = null;
  private listeners: Map<string, Set<(event: StreamEvent) => void>> = new Map();
  private isPlaying = false;
  private currentQuality: StreamQuality = StreamQuality.HD;

  constructor(config: StreamConfig) {
    this.config = config;
    this.validateConfig();
  }

  private validateConfig(): void {
    if (!this.config.url) {
      throw new Error('Stream URL is required');
    }
  }

  async initialize(): Promise<void> {
    try {
      console.log(`Initializing ${this.config.format} stream`);
      this.emit('buffering', { timestamp: Date.now() });
      await this.fetchMetadata();
      this.emit('play', { timestamp: Date.now() });
    } catch (error) {
      console.error('Stream initialization failed:', error);
      this.emit('error', { timestamp: Date.now(), data: error });
      throw error;
    }
  }

  private async fetchMetadata(): Promise<void> {
    this.metadata = {
      duration: 0,
      bitrate: this.getQualityBitrate(this.currentQuality),
      resolution: this.getQualityResolution(this.currentQuality),
      codec: this.config.format === StreamFormat.HLS ? 'h264' : 'h265',
      fps: 60,
    };
  }

  private getQualityBitrate(quality: StreamQuality): number {
    const bitrateMap: Record<StreamQuality, number> = {
      [StreamQuality.LOW]: 500,
      [StreamQuality.SD]: 1000,
      [StreamQuality.HD]: 2500,
      [StreamQuality.FULL_HD]: 5000,
      [StreamQuality.ULTRA_HD]: 15000,
      [StreamQuality.FULL_4K]: 25000,
    };
    return bitrateMap[quality];
  }

  private getQualityResolution(quality: StreamQuality): string {
    const resolutionMap: Record<StreamQuality, string> = {
      [StreamQuality.LOW]: '640x360',
      [StreamQuality.SD]: '854x480',
      [StreamQuality.HD]: '1280x720',
      [StreamQuality.FULL_HD]: '1920x1080',
      [StreamQuality.ULTRA_HD]: '3840x2160',
      [StreamQuality.FULL_4K]: '7680x4320',
    };
    return resolutionMap[quality];
  }

  async changeQuality(quality: StreamQuality): Promise<void> {
    const previousQuality = this.currentQuality;
    this.currentQuality = quality;

    if (this.metadata) {
      this.metadata.bitrate = this.getQualityBitrate(quality);
      this.metadata.resolution = this.getQualityResolution(quality);
    }

    this.emit('quality_change', {
      timestamp: Date.now(),
      data: { from: previousQuality, to: quality },
    });
  }

  async play(): Promise<void> {
    if (!this.isPlaying) {
      this.isPlaying = true;
      this.emit('play', { timestamp: Date.now() });
    }
  }

  pause(): void {
    this.isPlaying = false;
    this.emit('pause', { timestamp: Date.now() });
  }

  stop(): void {
    this.isPlaying = false;
    this.emit('stop', { timestamp: Date.now() });
  }

  getMetadata(): StreamMetadata | null {
    return this.metadata;
  }

  getCurrentQuality(): StreamQuality {
    return this.currentQuality;
  }

  on(eventType: StreamEvent['type'], callback: (event: StreamEvent) => void): () => void {
    if (!this.listeners.has(eventType)) {
      this.listeners.set(eventType, new Set());
    }
    this.listeners.get(eventType)!.add(callback);

    return () => {
      this.listeners.get(eventType)?.delete(callback);
    };
  }

  private emit(eventType: StreamEvent['type'], eventData: Omit<StreamEvent, 'type'>): void {
    const callbacks = this.listeners.get(eventType);
    if (callbacks) {
      callbacks.forEach((callback) => {
        callback({ type: eventType, ...eventData });
      });
    }
  }
}
