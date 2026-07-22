import React, { useRef, useEffect, useState } from 'react';
import { StreamingEngine, StreamConfig, StreamQuality } from '@alfie-tv/core';

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

  useEffect(() => {
    const initializeEngine = async () => {
      try {
        const streamingEngine = new StreamingEngine(streamConfig);
        await streamingEngine.initialize();
        setEngine(streamingEngine);
        onReady?.();
      } catch (error) {
        onError?.(error as Error);
      }
    };

    initializeEngine();

    return () => {
      engine?.stop();
    };
  }, [streamConfig, onError, onReady]);

  const handlePlay = async () => {
    if (engine) {
      await engine.play();
      setIsPlaying(true);
    }
  };

  const handlePause = () => {
    engine?.pause();
    setIsPlaying(false);
  };

  const handleQualityChange = async (newQuality: StreamQuality) => {
    if (engine) {
      await engine.changeQuality(newQuality);
      setQuality(newQuality);
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
