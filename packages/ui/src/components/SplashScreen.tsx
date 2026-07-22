import React, { useEffect, useState } from 'react';
import { SplashScreenConfig, DEFAULT_SPLASH_SCREEN_CONFIG } from '@alfie-tv/core';

interface SplashScreenProps {
  config?: Partial<SplashScreenConfig>;
  onComplete?: () => void;
}

export const SplashScreen: React.FC<SplashScreenProps> = ({
  config,
  onComplete,
}) => {
  const [isVisible, setIsVisible] = useState(true);
  const finalConfig = { ...DEFAULT_SPLASH_SCREEN_CONFIG, ...config };

  useEffect(() => {
    if (!finalConfig.enabled) {
      setIsVisible(false);
      onComplete?.();
      return;
    }

    const timer = setTimeout(() => {
      setIsVisible(false);
      onComplete?.();
    }, finalConfig.duration);

    return () => clearTimeout(timer);
  }, [finalConfig, onComplete]);

  if (!isVisible) return null;

  const animationClass =
    finalConfig.animation === 'fade'
      ? 'animate-fade'
      : finalConfig.animation === 'slide'
        ? 'animate-slide'
        : finalConfig.animation === 'zoom'
          ? 'animate-zoom'
          : '';

  return (
    <div
      className={`fixed inset-0 flex flex-col items-center justify-center ${animationClass}`}
      style={{
        backgroundColor: finalConfig.backgroundColor,
        animationDuration: `${finalConfig.animationDuration}ms`,
      }}
    >
      {finalConfig.backgroundImage && (
        <div
          className="absolute inset-0"
          style={{
            backgroundImage: `url(${finalConfig.backgroundImage})`,
            backgroundSize: 'cover',
            backgroundPosition: 'center',
            opacity: 0.1,
          }}
        />
      )}

      <div className="relative z-10 flex flex-col items-center gap-6">
        {finalConfig.logo && (
          <img
            src={finalConfig.logo}
            alt="Alfie TV"
            className={`
              ${finalConfig.logoSize === 'small' ? 'w-24' : ''}
              ${finalConfig.logoSize === 'medium' ? 'w-32' : ''}
              ${finalConfig.logoSize === 'large' ? 'w-48' : ''}
            `}
          />
        )}

        {finalConfig.text && (
          <h1
            className={`
              font-bold text-center
              ${finalConfig.textSize === 'small' ? 'text-2xl' : ''}
              ${finalConfig.textSize === 'medium' ? 'text-4xl' : ''}
              ${finalConfig.textSize === 'large' ? 'text-6xl' : ''}
            `}
            style={{ color: finalConfig.textColor }}
          >
            {finalConfig.text}
          </h1>
        )}

        {finalConfig.showSpinner && (
          <div className="flex gap-2">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="w-3 h-3 rounded-full animate-bounce"
                style={{
                  backgroundColor: finalConfig.spinnerColor,
                  animationDelay: `${i * 0.1}s`,
                }}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
