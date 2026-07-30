# Alfie TV - Cross-Platform IPTV Player

Alfie TV is a monorepo for a cross-platform IPTV experience spanning web, mobile, desktop, and smart TV shells. The current codebase includes shared core logic, backend persistence helpers, UI components, and platform-specific entry points.

## ✅ Current Status

The repository is in a working state with:
- shared channel and navigation state in the core package
- persistence and profile helpers in the backend package
- web, mobile, desktop, and TV shell scaffolds
- verified Vitest coverage for core, backend, web, mobile, and desktop behavior
- recent hardening for backend storage safety and M3U playlist import reliability
- Docker-based build support and CI workflow scaffolding for repeatable validation

## 🚀 Getting Started

```bash
git clone https://github.com/roddyalfred-gif/alfie-tv.git
cd alfie-tv
npm install
npm test
```

## 📁 Project Structure

```text
packages/
├── backend/   # persistence, auth, and storage helpers
├── core/      # shared platform logic and channel models
├── ui/        # reusable UI components
├── web/       # React web app shell
├── mobile/    # React Native app shell
├── desktop/   # Electron desktop shell
└── tv/        # smart TV shell
```

## 🛠️ Tech Stack

- TypeScript
- React and React Native
- Vite for the web app
- Vitest for tests
- npm workspaces for package management

## 📝 License

MIT License