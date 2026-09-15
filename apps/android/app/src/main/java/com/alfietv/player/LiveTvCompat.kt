package com.alfietv.player

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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

/** Live TV target that starts an isolated MainActivity playback session. */
class VideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Always create a fresh player Activity for the second OK press. Do not use
        // CLEAR_TOP/SINGLE_TOP here: reusing an older MainActivity can leave it with
        // stale playback state and can make the TV app appear to exit after the preview.
        val streamUrl = intent.getStringExtra("url") ?: intent.getStringExtra("stream_url") ?: ""
        if (streamUrl.isBlank()) {
            Toast.makeText(this, "Unable to play this channel", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val target = Intent(this, MainActivity::class.java).apply {
            putExtra("stream_url", streamUrl)
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

        try {
            startActivity(target)
            finish()
        } catch (exception: Exception) {
            Toast.makeText(this, "Unable to open fullscreen player", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
