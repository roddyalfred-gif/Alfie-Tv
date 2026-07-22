export interface SplashScreenConfig {
  enabled: boolean;
  duration: number;
  backgroundColor: string;
  backgroundImage?: string;
  logo?: string;
  logoSize: 'small' | 'medium' | 'large';
  text?: string;
  textColor: string;
  textSize: 'small' | 'medium' | 'large';
  showSpinner: boolean;
  spinnerColor: string;
  animation?: 'fade' | 'slide' | 'zoom' | 'none';
  animationDuration: number;
  theme: 'light' | 'dark';
}

export const DEFAULT_SPLASH_SCREEN_CONFIG: SplashScreenConfig = {
  enabled: true,
  duration: 3000,
  backgroundColor: '#1a1a1a',
  logo: undefined,
  logoSize: 'large',
  text: 'Alfie TV',
  textColor: '#ffffff',
  textSize: 'large',
  showSpinner: true,
  spinnerColor: '#00a8ff',
  animation: 'fade',
  animationDuration: 1000,
  theme: 'dark',
};

export const SPLASH_SCREEN_PRESETS = {
  dark: {
    ...DEFAULT_SPLASH_SCREEN_CONFIG,
    backgroundColor: '#000000',
    theme: 'dark',
  } as SplashScreenConfig,
  light: {
    ...DEFAULT_SPLASH_SCREEN_CONFIG,
    backgroundColor: '#ffffff',
    textColor: '#000000',
    spinnerColor: '#0066cc',
    theme: 'light',
  } as SplashScreenConfig,
  branded: {
    ...DEFAULT_SPLASH_SCREEN_CONFIG,
    backgroundColor: '#1a1a2e',
    spinnerColor: '#00d4ff',
    animation: 'zoom',
    theme: 'dark',
  } as SplashScreenConfig,
};
