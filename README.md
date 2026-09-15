# Alfie TV - Cross-Platform IPTV Player

Alfie TV is a monorepo for a cross-platform IPTV experience spanning web, mobile, desktop, Android TV, ChromeOS, Samsung Tizen TV, and LG webOS TV shells.

## Current platform targets

- Android / Android TV — native Android build
- Windows / macOS — Electron desktop builds
- ChromeOS — responsive web/PWA build through Chrome
- Samsung Smart TV — dedicated Tizen Web App shell under `apps/tizen`
- LG Smart TV — dedicated webOS Web App shell under `apps/webos`

The smart-TV shells reuse the web application build so the provider interface, Live TV, TV Guide/EPG, Movies, Series, favorites, history, and shared playback UI remain aligned across platforms. Platform-specific remote-key handling is injected for Samsung and LG TV controls.

## Smart TV packaging

Run:

```bash
npm ci
npm run build -w @alfie-tv/web
node scripts/package-smart-tvs.mjs
```

This produces development/CI artifacts in `dist/smart-tv`:

- `alfie-tv-tizen-unsigned.wgt`
- `alfie-tv-webos-unsigned.ipk`

Samsung distribution requires a valid Tizen certificate/signing profile before installation or Seller Office submission. LG webOS testing uses the Developer Mode app and the webOS CLI. The CI artifacts are therefore explicitly marked unsigned and are not production-store packages.

## Getting started

```bash
git clone https://github.com/roddyalfred-gif/alfie-tv.git
cd alfie-tv
npm install
npm test
```

## Project structure

```text
apps/
├── android/   # Android / Android TV
├── tizen/     # Samsung Tizen TV shell
└── webos/     # LG webOS TV shell

packages/
├── backend/   # persistence, auth, and storage helpers
├── core/      # shared platform logic and channel models
├── ui/        # reusable UI components
├── web/       # React web/PWA app
├── mobile/    # React Native app shell
├── desktop/   # Electron desktop shell
└── tv/        # shared smart-TV logic
```

## Tech stack

- TypeScript
- React and React Native
- Vite for the web app
- Vitest for tests
- npm workspaces
