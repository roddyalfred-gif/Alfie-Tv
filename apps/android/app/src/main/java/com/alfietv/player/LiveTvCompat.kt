package com.alfietv.player

/** Shared conversion used by the TV-first Live TV screen. */
fun IptvChannel.toLibraryItem(): UserLibraryStore.Item =
    UserLibraryStore.Item(
        id = id,
        type = UserLibraryStore.Type.LIVE,
        title = name,
        streamUrl = streamUrl,
        categoryId = categoryId,
        posterUrl = logoUrl
    )

/**
 * Full-screen Live TV player.
 *
 * Reuse the proven MainActivity player implementation directly instead of
 * creating a second Activity and immediately replacing it. This keeps the
 * Android activity stack as LiveTvActivity -> VideoPlayerActivity, so Back
 * reliably returns to Live TV and the second OK press cannot race an Activity
 * handoff that leaves the user at the main menu/launcher.
 *
 * LiveTvActivity already supplies the stream URL, title, provider credentials,
 * and channel-zap arrays as intent extras, which MainActivity consumes.
 */
class VideoPlayerActivity : MainActivity()
