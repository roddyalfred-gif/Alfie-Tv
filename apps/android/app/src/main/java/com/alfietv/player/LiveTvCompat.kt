package com.alfietv.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

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

/** Live TV target that starts a fresh MainActivity playback session. */
class VideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = Intent(this, MainActivity::class.java).apply {
            // Do not use SINGLE_TOP here. MainActivity reads the stream extras in onCreate;
            // reusing an existing instance would deliver onNewIntent instead and leave the
            // previous Live TV session stopped when a channel is selected from the TV Guide.
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("stream_url", intent.getStringExtra("url") ?: intent.getStringExtra("stream_url") ?: "")
            putExtra("title", intent.getStringExtra("title") ?: "Alfie TV")
            putExtra("content_id", intent.getStringExtra("content_id"))
            putExtra("content_type", intent.getStringExtra("content_type") ?: UserLibraryStore.Type.LIVE.name)
            putExtra("server", intent.getStringExtra("server"))
            putExtra("username", intent.getStringExtra("username"))
            putExtra("password", intent.getStringExtra("password"))
            putExtra("channel_id", intent.getStringExtra("channel_id"))
            putExtra("channel_number", intent.getStringExtra("channel_number"))
            putExtra("channel_urls", intent.getStringArrayListExtra("channel_urls"))
            putExtra("channel_titles", intent.getStringArrayListExtra("channel_titles"))
            putExtra("channel_ids", intent.getStringArrayListExtra("channel_ids"))
            putExtra("channel_numbers", intent.getStringArrayListExtra("channel_numbers"))
            putExtra("channel_index", intent.getIntExtra("channel_index", 0))
        }
        startActivity(target)
        finish()
    }
}
