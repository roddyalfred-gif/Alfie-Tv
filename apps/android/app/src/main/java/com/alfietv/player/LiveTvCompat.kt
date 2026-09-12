package com.alfietv.player

/** Compatibility helpers for the TV-first Live TV screen. */
private fun IptvChannel.toLibraryItem(): UserLibraryStore.Item =
    UserLibraryStore.Item(
        id = id,
        type = UserLibraryStore.Type.LIVE,
        title = name,
        streamUrl = streamUrl,
        categoryId = categoryId,
        posterUrl = logoUrl
    )

/** The playback screen is MainActivity; keep the legacy Live TV name source-compatible. */
typealias VideoPlayerActivity = MainActivity
