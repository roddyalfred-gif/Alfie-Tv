package com.alfietv.player

/** Compatibility helper for Activity lifecycle code that needs to re-prepare the current Media3 item. */
fun AlfiePlayer.prepare() {
    player.prepare()
}
