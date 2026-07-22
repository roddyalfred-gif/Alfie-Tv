# Alfie TV - Cross-Platform IPTV Player

A modern, feature-rich IPTV player application built from scratch with support for all devices including web, mobile, desktop, and smart TVs. Built with 4K scalability and customizable features.

## 🎯 Features

### Core Features
- **Live TV Streaming** - HLS/DASH streaming support
- **4K Ready** - Full 4K resolution support with adaptive bitrate
- **Customizable Splash Screen** - Branded splash screens with custom themes
- **Channel Management** - Browse, search, and organize channels
- **EPG** - Program listings and scheduling
- **Favorites** - Save favorite channels
- **Playback Controls** - Play, pause, seek, volume, quality selection
- **Multi-User Support** - User profiles with preferences
- **Dark/Light Themes** - Complete theme customization

## 🚀 Getting Started

```bash
git clone https://github.com/roddyalfred-gif/alfie-tv.git
cd alfie-tv
npm install
npm run dev
```

## 📁 Project Structure

```
packages/
├── core/        # Streaming & business logic
├── ui/          # Shared UI components
├── web/         # Web application
├── mobile/      # React Native app
├── desktop/     # Electron app
├── tv/          # Smart TV app
└── backend/     # Express API
```

## 📋 Tech Stack

- React 18, TypeScript, Tailwind CSS
- HLS.js, Dash.js for streaming
- Redux Toolkit for state management
- Express.js backend
- React Native, Electron, React TV

## 📝 License

MIT License