package com.alfietv.player

/**
 * Monotonically increasing token used to invalidate delayed playback work.
 *
 * A delayed recovery/zap callback must only act when its captured token is still current;
 * otherwise an old channel can be restarted after the viewer has already moved on.
 */
internal class PlaybackGeneration {
    private var value = 0L

    fun next(): Long {
        value += 1L
        return value
    }

    fun current(): Long = value

    fun isCurrent(token: Long): Boolean = token == value
}
