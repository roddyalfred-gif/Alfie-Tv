export enum StreamFormat {
  HLS = 'hls',
  DASH = 'dash',
  RTMP = 'rtmp',
  HTTP_PROGRESSIVE = 'http_progressive',
}

export enum StreamQuality {
  LOW = '360p',
  SD = '480p',
  HD = '720p',
  FULL_HD = '1080p',
  ULTRA_HD = '4K',
  FULL_4K = '8K',
}

export interface StreamConfig {
  url: string;
  format: StreamFormat;
  quality: StreamQuality;
  autoQuality: boolean;
  bufferSize: number;
  timeout: number;
  retryAttempts: number;
  headers?: Record<string, string>;
}

export interface StreamMetadata {
  duration: number;
  bitrate: number;
  resolution: string;
  codec: string;
  fps: number;
}

export interface StreamEvent {
  type: 'play' | 'pause' | 'stop' | 'seeking' | 'error' | 'buffering' | 'quality_change';
  timestamp: number;
  data?: unknown;
}
