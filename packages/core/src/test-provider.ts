import { parseM3UPlaylist } from './playlists';

/** Public streams intended only for playback/integration testing. */
export const TEST_PROVIDER_NAME = 'Alfie TV Test Streams';

export const TEST_PROVIDER_PLAYLIST = `#EXTM3U
#EXTINF:-1 tvg-id="alfie-test-bipbop-4x3" tvg-name="BipBop 4:3" group-title="Test Streams",BipBop 4:3
https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8
#EXTINF:-1 tvg-id="alfie-test-bipbop-16x9" tvg-name="BipBop 16:9" group-title="Test Streams",BipBop 16:9
https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8
#EXTINF:-1 tvg-id="alfie-test-bipbop-fmp4" tvg-name="BipBop Advanced fMP4" group-title="Test Streams",BipBop Advanced fMP4
https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8
#EXTINF:-1 tvg-id="alfie-test-mux" tvg-name="Mux HLS Test" group-title="Test Streams",Mux HLS Test
https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`;

export function getTestProviderChannels() {
  return parseM3UPlaylist(TEST_PROVIDER_PLAYLIST);
}
