# Alfie TV - Quick Start Guide

## What is Alfie TV?

Alfie TV is a modern, open-source IPTV player built from scratch with support for all devices. It features:

✨ **4K Support** - Full 4K (3840×2160) and 8K (7680×4320) streaming  
🎨 **Customizable Splash Screens** - Fully branded startup screens  
🌍 **Cross-Platform** - Web, Mobile, Desktop, and Smart TV  
⚡ **Modern Stack** - React, TypeScript, Express, Tailwind CSS  
🎯 **All Core Features** - Channels, EPG, Favorites, Quality Selection  

## Quick Setup

### 1. Clone & Install

```bash
git clone https://github.com/roddyalfred-gif/alfie-tv.git
cd alfie-tv
npm install
```

### 2. Start Development

```bash
# Terminal 1: Start Web App
cd packages/web
npm run dev

# Terminal 2: Start Backend API
cd packages/backend
npm run dev
```

### 3. Open in Browser

- **Web App**: http://localhost:5173
- **API Health**: http://localhost:3000/api/health

## Key Features

### Streaming Engine

```typescript
import { StreamingEngine, StreamFormat, StreamQuality } from '@alfie-tv/core';

const engine = new StreamingEngine({
  url: 'https://example.com/stream.m3u8',
  format: StreamFormat.HLS,
  quality: StreamQuality.ULTRA_HD,  // 4K
  autoQuality: true,
  bufferSize: 10,
  timeout: 30000,
  retryAttempts: 5,
});

await engine.initialize();
await engine.play();
```

### Channel Management

```typescript
import { ChannelManager } from '@alfie-tv/core';

const manager = new ChannelManager();
manager.addChannel({
  id: 'ch-1',
  name: 'Example Channel',
  number: 1,
  logo: 'https://example.com/logo.png',
  streamUrl: 'https://example.com/stream.m3u8',
  category: 'General',
  isFavorite: false,
  quality: '4K',
});

const channels = manager.searchChannels('example');
const favorites = manager.getFavorites();
```

### Customizable Splash Screens

```typescript
import { SplashScreen, SPLASH_SCREEN_PRESETS } from '@alfie-tv/ui';

// Use preset
<SplashScreen config={SPLASH_SCREEN_PRESETS.branded} />

// Or customize
<SplashScreen
  config={{
    duration: 3000,
    backgroundColor: '#1a1a1a',
    logo: '/logo.png',
    text: 'Alfie TV',
    animation: 'zoom',
  }}
  onComplete={() => console.log('Ready!')}
/>
```

### Theme System

```typescript
import { DARK_THEME, LIGHT_THEME } from '@alfie-tv/core';

// Use built-in themes
const primaryColor = DARK_THEME.colors.primary; // '#00a8ff'

// Or create custom
const customTheme: Theme = {
  id: 'custom',
  name: 'My Theme',
  isDark: true,
  colors: { /* ... */ },
  spacing: { /* ... */ },
  typography: { /* ... */ },
  borderRadius: { /* ... */ },
};
```

## Project Structure

```
alfie-tv/
├── packages/
│   ├── core/       # Streaming engine & managers
│   ├── ui/         # React components
│   ├── web/        # Web app (React + Vite)
│   └── backend/    # Express API
├── docs/           # Documentation
└── .github/        # GitHub Actions
```

## Available Commands

```bash
# Development
npm run dev        # Start all dev servers
npm run build      # Build all packages

# Quality
npm run lint       # Lint code
npm run format     # Format code
npm run test       # Run tests

# Cleanup
npm run clean      # Remove node_modules and dist
```

## 4K Streaming

Alfie TV supports multiple resolutions:

| Quality | Resolution | Bitrate |
|---------|------------|---------|
| LOW | 640×360 | 500 kbps |
| SD | 854×480 | 1 Mbps |
| HD | 1280×720 | 2.5 Mbps |
| FULL_HD | 1920×1080 | 5 Mbps |
| ULTRA_HD | 3840×2160 | 15 Mbps |
| FULL_4K | 7680×4320 | 25 Mbps |

Enable 4K:

```typescript
const config: StreamConfig = {
  url: 'https://example.com/4k-stream.m3u8',
  quality: StreamQuality.ULTRA_HD,  // 3840×2160
  autoQuality: true,                 // Auto-select based on bandwidth
};
```

## API Endpoints

```
GET  /api/health              # Server status
GET  /api/channels            # List channels
GET  /api/epg/:channelId      # Get EPG schedule
POST /api/users               # Create user
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md) - System design
- [Getting Started](docs/GETTING_STARTED.md) - Setup guide
- [Customization](docs/CUSTOMIZATION.md) - How to customize
- [API Reference](docs/API.md) - Backend endpoints
- [Roadmap](ROADMAP.md) - Future plans

## License

MIT License - See [LICENSE](LICENSE) file

## Support

- 📖 [Documentation](docs/)
- 🐛 [Issues](https://github.com/roddyalfred-gif/alfie-tv/issues)
- 💬 [Discussions](https://github.com/roddyalfred-gif/alfie-tv/discussions)

---

**Built with ❤️ by Roddy Alfred**
