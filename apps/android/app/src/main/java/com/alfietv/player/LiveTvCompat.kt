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
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
