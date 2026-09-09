# Alfie TV — Native Android / Android TV Playback Architecture

## Goal

Provide a native playback layer for Android phones, tablets, Android TV, Google TV and Fire TV using Android Media3/ExoPlayer. This layer is independent of the web HLS.js player and is designed for lawful provider streams.

## Playback pipeline

1. Stream resolver determines HLS, DASH or progressive format.
2. Media3 creates the appropriate MediaSource.
3. Hardware decoding is preferred by the platform decoder stack.
4. Player monitoring observes buffering, playback position, decoder errors and audio/video state.
5. Recoverable network errors trigger controlled source/player recovery.
6. Decoder errors trigger renderer recovery or a clean player rebuild.
7. Channel changes release the previous media item and prepare the next item immediately.
8. Diagnostics record startup time, rebuffer count, recovery attempts and fatal errors.

## IPTV-specific defaults

- Live playback should favor low startup latency without making the buffer too small.
- Enable adaptive bitrate selection by default.
- Preserve the current channel when recoverable failures occur.
- Do not require the viewer to leave and reopen a channel to restore playback.
- Keep the last successful position for VOD; live channels use live-window recovery.
- Audio-track selection and subtitle selection must be exposed through the UI.
- TV controls must be D-pad friendly and avoid touch-only interactions.

## Recovery policy

- BUFFERING: show buffering state and wait for the player's normal load policy.
- NETWORK: retry with bounded backoff, then recreate the media source if needed.
- DECODER: attempt renderer recovery once, then rebuild the player.
- STALLED: verify the live position is advancing before declaring a stall.
- FATAL: surface a concise error and provide retry/channel-back actions.

## Diagnostics

Expose a `PlaybackDiagnostics` model containing:

- startup latency
- current bitrate
- video resolution
- dropped frames when available
- buffered duration
- rebuffer count
- recovery count
- last error category
- decoder information when available

## Security

Credentials and playlist URLs must not be logged. Provider authorization headers should be supplied only through the authenticated playback/session layer.
