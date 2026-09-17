package com.alfietv.player

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var handoffStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val handoff = Runnable {
            if (isFinishing || handoffStarted) return@Runnable
            handoffStarted = true

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
                putExtra("channel_urls", intent.getStringArrayListExtra("channel_urls"))
                putExtra("channel_titles", intent.getStringArrayListExtra("channel_titles"))
                putExtra("channel_ids", intent.getStringArrayListExtra("channel_ids"))
                putExtra("channel_numbers", intent.getStringArrayListExtra("channel_numbers"))
                putExtra("channel_index", intent.getIntExtra("channel_index", 0))
            }

            runCatching {
                startActivity(target)
                overridePendingTransition(0, 0)
                finish()
                overridePendingTransition(0, 0)
            }.onFailure { error ->
                handoffStarted = false
                Toast.makeText(this, "Unable to open fullscreen player: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        // TV remotes can deliver the same OK event during an Activity handoff,
        // so retain the short delay on Leanback devices. Phones/tablets use the
        // immediate path to avoid the extra delay and touch/focus race.
        if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)) {
            Handler(Looper.getMainLooper()).postDelayed(handoff, 120L)
        } else {
            handoff.run()
        }
    }
}
