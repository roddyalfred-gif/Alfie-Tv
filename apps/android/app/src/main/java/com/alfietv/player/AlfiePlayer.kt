package com.alfietv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView

/**
 * Native Media3 playback engine used by Alfie TV.
 *
 * Designed for long-running live-TV sessions: one ExoPlayer instance is reused,
 * live streams get an explicit target offset, buffering is bounded, and
 * recovery is rate-limited so a bad provider stream cannot cause a restart loop.
 */
class AlfiePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recoveryAttempts = 0
    private var lastRecoveryAt = 0L
    private var lastPosition = 0L
    private var currentUrl: String? = null
    private var recovering = false

    val diagnostics = PlaybackDiagnostics()

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            5_000,  // min buffer
            30_000, // max buffer
            1_500,  // buffer for playback
            3_000   // buffer after rebuffer
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(20_000)
        .setAllowCrossProtocolRedirects(true)

    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setLoadControl(loadControl)
        .setMediaSourceFactory(mediaSourceFactory)
        .build().apply {
            setHandleAudioBecomingNoisy(true)
            setSeekBackIncrementMs(10_000)
            setSeekForwardIncrementMs(10_000)
            addListener(DiagnosticsListener(diagnostics))
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    recover("player-error")
                }
            })
        }

    private val stallCheck = object : Runnable {
        override fun run() {
            val p = player
            val stalled = p.playWhenReady &&
                p.playbackState == Player.STATE_BUFFERING &&
                p.playbackException == null

            if (stalled) {
                recover("stall")
            }

            handler.postDelayed(this, 8_000)
        }
    }

    fun attach(view: PlayerView) {
        view.player = player
        view.useController = true
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        view.requestFocus()
        handler.removeCallbacks(stallCheck)
        handler.postDelayed(stallCheck, 8_000)
    }

    fun play(url: String, title: String? = null, positionMs: Long = C.TIME_UNSET) {
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Unsupported stream URL"
        }

        currentUrl = url
        recoveryAttempts = 0
        recovering = false
        lastPosition = if (positionMs == C.TIME_UNSET) 0L else positionMs

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(title ?: "alfie-tv")
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(2_000)
                    .setMinPlaybackSpeed(0.98f)
                    .setMaxPlaybackSpeed(1.02f)
                    .build()
            )

        when {
            url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ->
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            url.substringBefore('?').endsWith(".mpd", ignoreCase = true) ->
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
        }

        // Reuse the same player instance for channel changes instead of
        // creating a new ExoPlayer, which keeps decoder/session state stable.
        val mediaItem = mediaItemBuilder.build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun playLastPosition() {
        if (player.currentMediaItem == null) return
        if (player.isCurrentMediaItemLive) {
            player.seekToDefaultPosition()
        } else {
            player.seekTo(lastPosition.coerceAtLeast(0L))
        }
        player.playWhenReady = true
    }

    fun stop() {
        lastPosition = player.currentPosition.coerceAtLeast(0L)
        player.stop()
    }

    fun release() {
        handler.removeCallbacks(stallCheck)
        lastPosition = player.currentPosition.coerceAtLeast(0L)
        currentUrl = null
        player.release()
    }

    private fun recover(reason: String) {
        val url = currentUrl ?: return
        if (recovering) return

        val now = System.currentTimeMillis()
        if (now - lastRecoveryAt < 2_000) return
        if (recoveryAttempts >= 4) return

        recovering = true
        recoveryAttempts++
        diagnostics.recoveryCount++
        lastRecoveryAt = now

        val live = player.isCurrentMediaItemLive
        val resumePosition = player.currentPosition.coerceAtLeast(0L)

        handler.postDelayed({
            if (currentUrl != url) {
                recovering = false
                return@postDelayed
            }

            // A prolonged live-TV stall is better recovered at the live edge
            // than by replaying an expired media position.
            if (live) {
                player.seekToDefaultPosition()
                player.prepare()
                player.playWhenReady = true
            } else {
                player.setMediaItem(MediaItem.fromUri(url), resumePosition)
                player.prepare()
                player.playWhenReady = true
                lastPosition = resumePosition
            }

            recovering = false
        }, 350L)
    }
}
