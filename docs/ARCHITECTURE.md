# Alfie TV - Architecture Overview

## Project Structure

Alfie TV is built as a monorepo using npm workspaces with the following structure:

```
alfie-tv/
├── packages/
│   ├── core/           # Core streaming and business logic (TypeScript)
│   ├── ui/             # Shared UI components (React)
│   ├── web/            # Web application (React + Vite)
│   ├── mobile/         # Mobile app (React Native) - Coming Soon
│   ├── desktop/        # Desktop app (Electron) - Coming Soon
│   ├── tv/             # Smart TV app (React TV) - Coming Soon
│   └── backend/        # Backend API (Express.js)
├── docs/               # Documentation
└── .github/            # GitHub configuration
```

## Technology Stack

### Core & Shared Logic
- **TypeScript** - Type-safe development
- **HLS.js** - HLS streaming protocol support
- **Dash.js** - DASH streaming protocol support

### Frontend
- **React 18** - UI framework
- **Tailwind CSS** - Utility-first CSS framework
- **Redux Toolkit** - State management
- **React Query** - Server state management
- **Vite** - Build tool and dev server

### Backend
- **Node.js** - Runtime environment
- **Express.js** - Web framework
- **CORS** - Cross-origin resource sharing
- **JWT** - Authentication

### Cross-Platform (Coming Soon)
- **React Native** - iOS and Android apps
- **Electron** - Desktop apps (Windows, macOS, Linux)
- **React TV** - Smart TV apps

## Core Features Architecture

### 1. Streaming Engine (`packages/core/src/streaming`)
- **StreamingEngine** class for HLS/DASH playback
- Support for 4K resolution (3840x2160, 7680x4320)
- Adaptive bitrate selection
- Quality switching
- Event system for playback control

### 2. Channel Management (`packages/core/src/channels`)
- **ChannelManager** for channel organization
- Search and filter capabilities
- Favorite channel management
- Channel grouping

### 3. EPG System (`packages/core/src/epg`)
- **EPGManager** for program schedules
- Current/next program queries
- Program search
- Date-range queries

### 4. User Management (`packages/core/src/user`)
- **UserManager** for user profiles
- User preferences and settings
- Playback history
- Multi-user support

### 5. Splash Screen (`packages/core/src/splash-screen`)
- Fully customizable splash screens
- Multiple animation styles (fade, slide, zoom)
- Theme presets (dark, light, branded)
- Logo and text customization

### 6. Theme System (`packages/core/src/themes`)
- Dark and light theme presets
- Customizable color schemes
- Typography configuration
- Spacing and border radius

## UI Components (`packages/ui/src/components`)

### SplashScreen Component
- Customizable appearance and duration
- Multiple animation options
- Theme support
- Spinner and progress indication

### VideoPlayer Component
- HLS/DASH streaming support
- Quality selector
- Play/pause controls
- 4K ready

### ChannelList Component
- Channel browsing
- Search and filter
- Favorite toggle
- Selection state

## 4K & Scalability Support

### Resolution Support
- 360p (LOW)
- 480p (SD)
- 720p (HD)
- 1080p (FULL_HD)
- 3840x2160 (ULTRA_HD / 4K)
- 7680x4320 (FULL_4K / 8K)

### Adaptive Streaming
- Automatic quality selection based on bandwidth
- Manual quality override
- Bitrate optimization
- Buffer management

### Responsive Design
- Mobile-first approach
- Tailwind CSS responsive utilities
- Flexible layouts
- Touch-friendly controls

## API Endpoints

### Health Check
- `GET /api/health` - Server status

### Channels
- `GET /api/channels` - List all channels
- `POST /api/channels` - Create channel (admin)
- `PUT /api/channels/:id` - Update channel (admin)
- `DELETE /api/channels/:id` - Delete channel (admin)

### EPG
- `GET /api/epg/:channelId` - Get schedule for channel
- `GET /api/epg/date/:date` - Get programs for date

### Users
- `POST /api/users` - Create user
- `GET /api/users/:id` - Get user profile
- `PUT /api/users/:id` - Update user
- `GET /api/users/:id/preferences` - Get preferences

## Data Flow

```
User Input
    ↓
UI Components (React)
    ↓
Core Managers (Streaming, Channels, EPG, User)
    ↓
Backend API (Express)
    ↓
Stream Source / Database
```

## State Management

- **Redux Toolkit** for global app state
- **React Query** for server state and caching
- **Component State** for local UI state

## Performance Optimizations

- Code splitting per package
- Lazy loading of components
- Image optimization
- Stream buffer optimization
- EPG data caching
- Service worker for PWA (planned)

## Security Considerations

- JWT authentication
- HTTPS for all communications
- Content verification
- Rate limiting on API endpoints
- CORS configuration
- Input validation

## Future Enhancements

- Progressive Web App (PWA)
- Picture-in-Picture mode
- Chromecast support
- AirPlay support
- DLNA support
- Offline mode
- Recording capability
- Parental controls
