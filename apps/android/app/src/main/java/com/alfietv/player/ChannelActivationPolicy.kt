package com.alfietv.player

/**
 * Pure channel activation policy used by Live TV and Guide handoff.
 *
 * The first activation of a channel selects/previews it. Activating the
 * already-previewed channel again requests fullscreen playback.
 */
object ChannelActivationPolicy {
    enum class Action {
        PREVIEW,
        FULLSCREEN
    }

    fun action(previewedChannelId: String?, activatedChannelId: String): Action {
        return if (!previewedChannelId.isNullOrBlank() && previewedChannelId == activatedChannelId) {
            Action.FULLSCREEN
        } else {
            Action.PREVIEW
        }
    }
}
