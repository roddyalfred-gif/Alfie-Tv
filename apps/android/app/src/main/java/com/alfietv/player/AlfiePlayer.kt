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
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView

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
        .setBufferDurationsMs(5_000, 30_000, 1_500, 3_000)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(20_000)
        .setAllowCrossProtocolRedirects(true)

    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        .setLiveTargetOffsetMs(2_000)

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setLoadControl(loadControl)
        .setMediaSourceFactory(mediaSourceFactory)
        .build().apply {
            setHandleAudioBecomingNoisy(true)
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
            val stalled = p.playWhenReady && p.playbackState == Player.STATE_BUFFERING &&
                p.playbackException == null
            if (stalled) recover("stall")
            handler.postDelayed(this, 8_000)
        }
    }

    fun attach(view: PlayerView) {
        view.player = player
        view.useController = true
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        handler.removeCallbacks(stallCheck)
        handler.postDelayed(stallCheck, 8_000)
    }

    fun play(url: String, title: String? = null, positionMs: Long = 0L) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Unsupported stream URL" }
        currentUrl = url
        recoveryAttempts = 0
        recovering = false
        lastPosition = positionMs

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaId(title ?: "alfie-tv")
            .build()
        player.setMediaItem(mediaItem, positionMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun playLastPosition() {
        player.seekTo(lastPosition.coerceAtLeast(0L))
        player.playWhenReady = true
    }

    fun stop() {
        lastPosition = player.currentPosition
        player.stop()
        currentUrl = null
    }

    fun release() {
        handler.removeCallbacks(stallCheck)
        lastPosition = player.currentPosition
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
        val resumePosition = player.currentPosition.coerceAtLeast(lastPosition)

        handler.postDelayed({
            if (currentUrl != url) {
                recovering = false
                return@postDelayed
            }
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(url), resumePosition)
            player.prepare()
            player.playWhenReady = true
            lastPosition = resumePosition
            recovering = false
        }, 350L)
    }
}
