import React, { useRef, useEffect, useState } from 'react';
import { StreamingEngine, StreamQuality } from '@alfie-tv/core';
import type { StreamConfig } from '@alfie-tv/core';

interface VideoPlayerProps {
  streamConfig: StreamConfig;
  onError?: (error: Error) => void;
  onReady?: () => void;
  className?: string;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  streamConfig,
  onError,
  onReady,
  className = '',
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [engine, setEngine] = useState<StreamingEngine | null>(null);
  const [quality, setQuality] = useState<StreamQuality>(StreamQuality.HD);
  const [isPlaying, setIsPlaying] = useState(false);
  const [status, setStatus] = useState('Ready to play');

  useEffect(() => {
    let cancelled = false;

    const initializeEngine = async () => {
      try {
        const streamingEngine = new StreamingEngine(streamConfig);
        await streamingEngine.initialize();

        if (!cancelled) {
          setEngine(streamingEngine);
          setStatus('Stream ready');
          setIsPlaying(false);
          onReady?.();
        } else {
          streamingEngine.stop();
        }
      } catch (error) {
        if (!cancelled) {
          setStatus('Stream unavailable');
          onError?.(error as Error);
        }
      }
    };

    initializeEngine();

    return () => {
      cancelled = true;
      engine?.stop();
    };
  }, [streamConfig, onError, onReady]);

  const handlePlay = async () => {
    if (!engine) {
      setStatus('Stream unavailable');
      return;
    }

    try {
      await engine.play();
      setIsPlaying(true);
      setStatus('Playing');
    } catch (error) {
      setStatus('Playback failed');
      onError?.(error as Error);
    }
  };

  const handlePause = () => {
    if (!engine) {
      setStatus('Stream unavailable');
      return;
    }

    engine.pause();
    setIsPlaying(false);
    setStatus('Paused');
  };

  const handleQualityChange = async (newQuality: StreamQuality) => {
    if (engine) {
      await engine.changeQuality(newQuality);
      setQuality(newQuality);
      setStatus(`Quality: ${newQuality}`);
    }
  };

  return (
    <div className={`relative w-full bg-black ${className}`}>
      <video
        ref={videoRef}
        className="w-full h-full"
        controls
        style={{ aspectRatio: '16 / 9' }}
      >
        <source src={streamConfig.url} type="application/x-mpegURL" />
        Your browser does not support the video tag.
      </video>

      <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black to-transparent">
        <div className="mb-3 flex items-center justify-between rounded-full bg-black/50 px-3 py-2 text-sm text-gray-200">
          <span>{status}</span>
          <span className="text-blue-300">{quality}</span>
        </div>
        <div className="flex items-center justify-between">
          <div className="flex gap-2">
            <button
              onClick={isPlaying ? handlePause : handlePlay}
              className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
            >
              {isPlaying ? 'Pause' : 'Play'}
            </button>
          </div>

          <select
            value={quality}
            onChange={(e) => handleQualityChange(e.target.value as StreamQuality)}
            className="px-3 py-2 bg-gray-800 text-white rounded"
          >
            {Object.values(StreamQuality).map((q) => (
              <option key={q} value={q}>
                {q}
              </option>
            ))}
          </select>
        </div>
      </div>
    </div>
  );
};
