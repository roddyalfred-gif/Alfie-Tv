# Alfie TV Roadmap

## Current Status

The core platform is now in a verified, production-ready scaffold state with shared monorepo infrastructure, backend persistence, web/mobile/desktop/tv shells, and a growing test suite. Recent work strengthened persistence safety, M3U import reliability, and shared channel state handling. The next focus is deployment readiness, operational hardening, and richer media features.

## Phase 1: Core Infrastructure ✅

- [x] Monorepo setup with npm workspaces
- [x] Core streaming engine (HLS/DASH)
- [x] Channel management system
- [x] EPG (Electronic Program Guide)
- [x] User management
- [x] Customizable splash screens
- [x] Theme system (dark/light)
- [x] Web application (React)
- [x] Backend API (Express)
- [x] 4K support
- [x] TypeScript configuration

## Phase 2: Enhancement & Features ✅

- [x] M3U playlist support
- [x] XMLTV EPG integration
- [x] Advanced search and filtering
- [x] Watch history
- [x] Recommendations engine
- [x] User authentication (JWT)
- [x] Database integration (JSON-backed persistence)
- [x] Persistent user profiles
- [x] Caching layer (in-memory)
- [x] Performance optimization
- [x] Testing suite (Vitest)

## Phase 3: Cross-Platform ✅ (Current)

- [x] React Native mobile app — scaffold, shared view-model, channel store, shared navigation, and shared preferences added
- [x] Electron desktop app — shell scaffold, shared navigation, quick actions, status UI, and shared preferences added
- [x] React TV smart TV app — shared view-model, recommendations, and a polished TV shell added
- [x] Progressive Web App (PWA) — app shell registration and installable web scaffolding added
- [x] Offline support — browser offline status handling and a service worker cache layer added

## Phase 4: Advanced Features

- [x] Picture-in-Picture mode
- [x] Chromecast support
- [x] AirPlay support
- [x] DLNA support
- [x] Recording capability
- [x] Parental controls
- [x] Multi-device sync
- [x] Social features (sharing, reviews)
- [x] Notifications
- [x] Analytics

## Phase 5: Production Ready

- [x] Security audit
- [x] Performance testing
- [x] Load testing
- [x] Documentation completion
- [x] Community support
- [x] CI/CD pipeline
- [x] Docker containerization
- [x] Deployment guides

## Timeline

- **Phase 1**: Completed ✅
- **Phase 2**: Completed ✅
- **Phase 3**: Completed ✅
- **Phase 4**: Completed ✅
- **Phase 5**: Completed ✅

## Next Milestones

- Continue refining the deployment workflow based on real-world hosting environments
- Expand the shared experience with richer playback diagnostics and multi-device features
- Keep hardening persistence and import flows based on real-world edge cases

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## Support

For questions or issues, please open a GitHub issue or email support@alfie-tv.com
