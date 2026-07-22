# Getting Started with Alfie TV

## Prerequisites

- Node.js 18.0.0 or higher
- npm 9.0.0 or higher
- Git

## Installation

### 1. Clone the Repository

```bash
git clone https://github.com/roddyalfred-gif/alfie-tv.git
cd alfie-tv
```

### 2. Install Dependencies

```bash
# Install all dependencies for all packages
npm install
```

### 3. Build the Project

```bash
# Build all packages
npm run build
```

## Development

### Start Development Servers

```bash
# Start all dev servers
npm run dev
```

### Web Application

The web application is built with React and Vite.

```bash
cd packages/web
npm run dev
```

Access at: `http://localhost:5173`

### Backend API

The backend API is built with Express.js.

```bash
cd packages/backend
npm run dev
```

Access at: `http://localhost:3000`

Health check: `GET http://localhost:3000/api/health`

## Building for Production

```bash
# Build all packages for production
npm run build

# Or build a specific package
cd packages/web && npm run build
```

## Testing

```bash
# Run all tests
npm run test

# Run tests for specific package
cd packages/core && npm run test
```

## Linting and Formatting

```bash
# Lint all code
npm run lint

# Format all code
npm run format
```

## Project Structure

- `packages/core/` - Core streaming and business logic
- `packages/ui/` - Shared UI components
- `packages/web/` - Web application
- `packages/backend/` - Backend API
- `docs/` - Documentation

## Configuration

### Environment Variables

Create a `.env` file in `packages/backend/`:

```env
PORT=3000
NODE_ENV=development
```

### Splash Screen Configuration

Customize the splash screen in your app:

```typescript
import { SplashScreen, SPLASH_SCREEN_PRESETS } from '@alfie-tv/ui';

<SplashScreen config={SPLASH_SCREEN_PRESETS.dark} />
```

## Troubleshooting

### Port Already in Use

```bash
# Kill process on port 3000
lsof -ti:3000 | xargs kill -9
```

### Build Errors

```bash
# Clean and reinstall
npm run clean
npm install
npm run build
```

### Dependencies Issues

```bash
# Clear cache and reinstall
rm -rf node_modules package-lock.json
npm install
```

## Next Steps

1. **Explore Core Features** - Check `packages/core/src/` for streaming engine, channels, EPG
2. **Build Custom UI** - Extend components in `packages/ui/src/components/`
3. **Add Your Channels** - Load M3U playlists via the ChannelManager
4. **Deploy** - Setup CI/CD pipeline with GitHub Actions
5. **Build for Mobile/Desktop** - Setup React Native or Electron

## Support

For support:
- Open an issue on GitHub
- Check existing issues for solutions
- Read the documentation in `docs/`
