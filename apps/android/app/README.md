# Alfie TV — Native Android / Android TV

This module is the native playback foundation for Alfie TV.

## Included

- Android + Android TV launcher activity
- Media3/ExoPlayer playback
- HLS and DASH playback through Media3
- Adaptive bitrate playback
- Bounded automatic recovery for stalls and player errors
- Playback diagnostics
- Xtream Codes API authentication, live categories, and live streams
- TV/D-pad compatible PlayerView

## Package

`com.alfietv.player`

## Provider compatibility

The Xtream client expects a provider endpoint that implements the standard Xtream Codes API. Use only streams and provider accounts you are authorized to access.

## Build

Open `apps/android` as a Gradle project in Android Studio with a current Android SDK. The root repository remains a JavaScript/TypeScript workspace; this native module is intentionally isolated so the web/mobile code can share the same domain models without forcing Android tooling onto the JS build.
