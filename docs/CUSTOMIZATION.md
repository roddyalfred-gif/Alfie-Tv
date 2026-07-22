# Alfie TV Customization Guide

## Splash Screen Customization

### Using Presets

```typescript
import { SplashScreen, SPLASH_SCREEN_PRESETS } from '@alfie-tv/ui';

// Dark theme
<SplashScreen config={SPLASH_SCREEN_PRESETS.dark} />

// Light theme
<SplashScreen config={SPLASH_SCREEN_PRESETS.light} />

// Branded theme
<SplashScreen config={SPLASH_SCREEN_PRESETS.branded} />
```

### Custom Configuration

```typescript
<SplashScreen
  config={{
    enabled: true,
    duration: 3000,
    backgroundColor: '#000000',
    backgroundImage: '/path/to/bg.png',
    logo: '/path/to/logo.png',
    logoSize: 'large',
    text: 'Welcome to Alfie TV',
    textColor: '#ffffff',
    textSize: 'large',
    showSpinner: true,
    spinnerColor: '#00d4ff',
    animation: 'zoom',
    animationDuration: 1000,
    theme: 'dark',
  }}
  onComplete={() => console.log('Splash screen done')}
/>
```

## Theme Customization

### Using Built-in Themes

```typescript
import { DARK_THEME, LIGHT_THEME } from '@alfie-tv/core';

const theme = DARK_THEME;
console.log(theme.colors.primary); // '#00a8ff'
```

### Creating Custom Themes

```typescript
import { Theme } from '@alfie-tv/core';

const customTheme: Theme = {
  id: 'my-theme',
  name: 'My Custom Theme',
  isDark: true,
  colors: {
    primary: '#ff00ff',
    secondary: '#00ffff',
    background: '#0a0a0a',
    surface: '#1a1a1a',
    text: '#ffffff',
    textSecondary: '#cccccc',
    border: '#333333',
    accent: '#ff00ff',
    success: '#00ff00',
    warning: '#ffff00',
    error: '#ff0000',
  },
  spacing: {
    xs: 4,
    sm: 8,
    md: 16,
    lg: 24,
    xl: 32,
  },
  typography: {
    fontFamily: 'Arial, sans-serif',
    sizes: {
      xs: 12,
      sm: 14,
      md: 16,
      lg: 18,
      xl: 24,
      '2xl': 32,
    },
  },
  borderRadius: {
    sm: 4,
    md: 8,
    lg: 12,
    full: 9999,
  },
};
```

## Streaming Configuration

### Configure Quality Defaults

```typescript
import { StreamConfig, StreamFormat, StreamQuality } from '@alfie-tv/core';

const streamConfig: StreamConfig = {
  url: 'https://example.com/stream.m3u8',
  format: StreamFormat.HLS,
  quality: StreamQuality.ULTRA_HD,  // 4K
  autoQuality: true,
  bufferSize: 10,
  timeout: 30000,
  retryAttempts: 5,
  headers: {
    'User-Agent': 'Alfie TV',
    'Referer': 'https://example.com',
  },
};
```

## Channel Management

### Load Channels from M3U

```typescript
import { ChannelManager } from '@alfie-tv/core';

const channelManager = new ChannelManager();

// Parse M3U and add channels
const channels = parseM3U(m3uContent);
channels.forEach(channel => {
  channelManager.addChannel(channel);
});
```

### Filter and Search

```typescript
// Search channels
const results = channelManager.searchChannels('news');

// Filter by category
const sports = channelManager.filterChannels({
  category: 'Sports',
  favoriteOnly: false,
});

// Get favorites
const favorites = channelManager.getFavorites();
```

## 4K Support

The player supports the following resolutions:

```typescript
enum StreamQuality {
  LOW = '360p',
  SD = '480p',
  HD = '720p',
  FULL_HD = '1080p',
  ULTRA_HD = '4K',        // 3840x2160
  FULL_4K = '8K',         // 7680x4320
}
```

### Enable 4K Streaming

```typescript
const streamConfig: StreamConfig = {
  url: 'https://example.com/4k-stream.m3u8',
  format: StreamFormat.HLS,
  quality: StreamQuality.ULTRA_HD,  // Enable 4K
  autoQuality: true,                 // Auto-select based on bandwidth
  bufferSize: 15,                    // Larger buffer for 4K
  timeout: 30000,
  retryAttempts: 5,
};
```

## Best Practices

1. **Use presets first** - Start with built-in themes and splash screens
2. **Test on multiple devices** - Ensure 4K and responsive design work
3. **Monitor performance** - Check streaming quality and buffer times
4. **Accessibility** - Include proper ARIA labels and keyboard navigation
5. **Documentation** - Document custom configurations for your team
