package com.alfietv.player

/** Compatibility helper for Activity lifecycle code that safely re-prepares the current Media3 item. */
fun AlfiePlayer.prepare() {
    if (player.currentMediaItem != null && player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
        player.prepare()
    }
}
