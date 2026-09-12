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

/** Legacy Live TV target that bridges the old url/title extras into MainActivity. */
class VideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = Intent(this, MainActivity::class.java).apply {
            putExtra("stream_url", intent.getStringExtra("url") ?: intent.getStringExtra("stream_url") ?: "")
            putExtra("title", intent.getStringExtra("title") ?: "Alfie TV")
            putExtra("content_id", intent.getStringExtra("content_id"))
            putExtra("content_type", intent.getStringExtra("content_type") ?: UserLibraryStore.Type.LIVE.name)
            putExtra("server", intent.getStringExtra("server"))
            putExtra("username", intent.getStringExtra("username"))
            putExtra("password", intent.getStringExtra("password"))
            putExtra("channel_id", intent.getStringExtra("channel_id"))
            putExtra("channel_number", intent.getStringExtra("channel_number"))
        }
        startActivity(target)
        finish()
    }
}
