import React, { useCallback, useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import { StreamQuality } from '@alfie-tv/core';
import type { StreamConfig } from '@alfie-tv/core';

interface VideoPlayerProps {
  streamConfig: StreamConfig;
  onError?: (error: Error) => void;
  onReady?: () => void;
  className?: string;
}

const MAX_RECOVERY_ATTEMPTS = 4;
const STALL_TIMEOUT_MS = 8_000;

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  streamConfig,
  onError,
  onReady,
  className = '',
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const stallTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const recoveryAttemptsRef = useRef(0);
  const onErrorRef = useRef(onError);
  const onReadyRef = useRef(onReady);

  const [quality, setQuality] = useState<StreamQuality>(streamConfig.quality ?? StreamQuality.HD);
  const [isPlaying, setIsPlaying] = useState(false);
  const [status, setStatus] = useState('Preparing stream…');
  const [levels, setLevels] = useState<Array<{ index: number; label: string }>>([]);

  useEffect(() => {
    onErrorRef.current = onError;
  }, [onError]);

  useEffect(() => {
    onReadyRef.current = onReady;
  }, [onReady]);

  const clearStallTimer = useCallback(() => {
    if (stallTimerRef.current) {
      clearTimeout(stallTimerRef.current);
      stallTimerRef.current = null;
    }
  }, []);

  const reportError = useCallback((message: string, cause?: unknown) => {
    const error = cause instanceof Error ? cause : new Error(message);
    setStatus(message);
    onErrorRef.current?.(error);
  }, []);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    let cancelled = false;
    recoveryAttemptsRef.current = 0;
    clearStallTimer();
    setIsPlaying(false);
    setStatus('Preparing stream…');
    setLevels([]);
    setQuality(streamConfig.quality ?? StreamQuality.HD);

    const url = streamConfig.url;
    const isHls = streamConfig.format === 'hls';

    const destroy = () => {
      clearStallTimer();
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
      video.pause();
      video.removeAttribute('src');
      video.load();
    };

    const recover = (kind: 'network' | 'media' | 'stall') => {
      if (cancelled) return;
      recoveryAttemptsRef.current += 1;
      const attempt = recoveryAttemptsRef.current;
      if (attempt > MAX_RECOVERY_ATTEMPTS) {
        reportError('Stream recovery failed after several attempts.');
        return;
      }

      setStatus(`Recovering stream… (${attempt}/${MAX_RECOVERY_ATTEMPTS})`);
      clearStallTimer();

      const hls = hlsRef.current;
      if (hls && kind === 'network') {
        hls.startLoad(-1);
        return;
      }
      if (hls && kind === 'media') {
        hls.recoverMediaError();
        return;
      }

      const wasPlaying = !video.paused;
      const position = Number.isFinite(video.currentTime) ? video.currentTime : 0;
      video.load();
      video.currentTime = position;
      if (wasPlaying) {
        void video.play().catch(() => undefined);
      }
    };

    const armStallRecovery = () => {
      clearStallTimer();
      stallTimerRef.current = setTimeout(() => recover('stall'), STALL_TIMEOUT_MS);
    };

    const onPlaying = () => {
      clearStallTimer();
      recoveryAttemptsRef.current = 0;
      setIsPlaying(true);
      setStatus('Playing');
    };
    const onWaiting = () => {
      setIsPlaying(false);
      setStatus('Buffering…');
      armStallRecovery();
    };
    const onStalled = () => {
      setStatus('Stream stalled…');
      armStallRecovery();
    };
    const onCanPlay = () => {
      setStatus('Ready');
      onReadyRef.current?.();
    };
    const onPause = () => {
      clearStallTimer();
      setIsPlaying(false);
      if (!video.ended) setStatus('Paused');
    };
    const onEnded = () => {
      clearStallTimer();
      setIsPlaying(false);
      setStatus('Stream ended');
    };
    const onVideoError = () => {
      if (!cancelled) recover('media');
    };

    video.addEventListener('playing', onPlaying);
    video.addEventListener('waiting', onWaiting);
    video.addEventListener('stalled', onStalled);
    video.addEventListener('canplay', onCanPlay);
    video.addEventListener('pause', onPause);
    video.addEventListener('ended', onEnded);
    video.addEventListener('error', onVideoError);

    if (isHls && Hls.isSupported()) {
      const hls = new Hls({
        autoStartLoad: true,
        enableWorker: true,
        lowLatencyMode: false,
        backBufferLength: 30,
        maxBufferLength: 20,
        maxMaxBufferLength: 60,
        startLevel: -1,
        capLevelToPlayerSize: true,
        manifestLoadingMaxRetry: Math.max(1, streamConfig.retryAttempts ?? 3),
        levelLoadingMaxRetry: Math.max(1, streamConfig.retryAttempts ?? 3),
        fragLoadingMaxRetry: Math.max(1, streamConfig.retryAttempts ?? 3),
        manifestLoadingTimeOut: Math.max(5_000, streamConfig.timeout ?? 15_000),
        levelLoadingTimeOut: Math.max(5_000, streamConfig.timeout ?? 15_000),
        fragLoadingTimeOut: Math.max(5_000, streamConfig.timeout ?? 15_000),
        xhrSetup: (xhr) => {
          Object.entries(streamConfig.headers ?? {}).forEach(([key, value]) => {
            xhr.setRequestHeader(key, value);
          });
        },
      });

      hlsRef.current = hls;
      hls.attachMedia(video);

      hls.on(Hls.Events.MEDIA_ATTACHED, () => {
        if (!cancelled) hls.loadSource(url);
      });
      hls.on(Hls.Events.MANIFEST_PARSED, (_event, data) => {
        if (cancelled) return;
        const mapped = data.levels.map((level, index) => ({
          index,
          label: level.height ? `${level.height}p` : `${Math.round(level.bitrate / 1000)} kbps`,
        }));
        setLevels(mapped);
        setStatus('Ready');
        onReadyRef.current?.();
      });
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (cancelled || !data.fatal) return;
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) recover('network');
        else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) recover('media');
        else recover('stall');
      });
    } else {
      if (isHls && !video.canPlayType('application/vnd.apple.mpegurl')) {
        reportError('This device/browser does not support HLS playback.');
      } else {
        video.src = url;
        video.load();
      }
    }

    return () => {
      cancelled = true;
      video.removeEventListener('playing', onPlaying);
      video.removeEventListener('waiting', onWaiting);
      video.removeEventListener('stalled', onStalled);
      video.removeEventListener('canplay', onCanPlay);
      video.removeEventListener('pause', onPause);
      video.removeEventListener('ended', onEnded);
      video.removeEventListener('error', onVideoError);
      destroy();
    };
  }, [clearStallTimer, reportError, streamConfig]);

  const handlePlay = async () => {
    const video = videoRef.current;
    if (!video) return;
    try {
      await video.play();
    } catch (error) {
      reportError('Playback could not start.', error);
    }
  };

  const handlePause = () => videoRef.current?.pause();

  const handleQualityChange = (value: string) => {
    const level = Number(value);
    const hls = hlsRef.current;
    if (!hls || !Number.isInteger(level)) return;
    hls.currentLevel = level;
    const selected = levels.find((item) => item.index === level);
    if (selected) setQuality(selected.label as StreamQuality);
    setStatus(`Quality: ${selected?.label ?? 'Auto'}`);
  };

  return (
    <div className={`relative w-full bg-black ${className}`}>
      <video
        ref={videoRef}
        className="h-full w-full"
        controls
        playsInline
        preload="auto"
        style={{ aspectRatio: '16 / 9' }}
      />

      <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black to-transparent">
        <div className="mb-3 flex items-center justify-between rounded-full bg-black/60 px-3 py-2 text-sm text-gray-200">
          <span>{status}</span>
          <span className="text-blue-300">{quality}</span>
        </div>
        <div className="flex items-center justify-between gap-3">
          <button
            onClick={isPlaying ? handlePause : handlePlay}
            className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
          >
            {isPlaying ? 'Pause' : 'Play'}
          </button>
          {levels.length > 0 && (
            <select
              value={hlsRef.current?.currentLevel ?? -1}
              onChange={(event) => handleQualityChange(event.target.value)}
              className="rounded bg-gray-800 px-3 py-2 text-white"
              aria-label="Video quality"
            >
              <option value={-1}>Auto</option>
              {levels.map((level) => (
                <option key={level.index} value={level.index}>{level.label}</option>
              ))}
            </select>
          )}
        </div>
      </div>
    </div>
  );
};
