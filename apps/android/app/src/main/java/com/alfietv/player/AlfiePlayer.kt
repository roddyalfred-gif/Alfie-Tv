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

/** Native Media3 playback engine optimized for long-running live TV. */
class AlfiePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recoveryAttempts = 0
    private var audioRecoveryAttempts = 0
    private var lastRecoveryAt = 0L
    private var lastAudioRecoveryAt = 0L
    private var lastPosition = 0L
    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var recovering = false
    private var lastPlayingPosition = C.TIME_UNSET

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

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        recoveryAttempts = 0
                        audioRecoveryAttempts = 0
                    }
                }
            })
        }

    private val healthCheck = object : Runnable {
        override fun run() {
            checkPlaybackHealth()
            handler.postDelayed(this, 4_000)
        }
    }

    fun attach(view: PlayerView) {
        view.player = player
        view.useController = true
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        view.requestFocus()
        handler.removeCallbacks(healthCheck)
        handler.postDelayed(healthCheck, 4_000)
    }

    fun play(url: String, title: String? = null, positionMs: Long = C.TIME_UNSET) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Unsupported stream URL" }

        currentUrl = url
        currentTitle = title
        recoveryAttempts = 0
        audioRecoveryAttempts = 0
        recovering = false
        lastPosition = if (positionMs == C.TIME_UNSET) 0L else positionMs
        lastPlayingPosition = C.TIME_UNSET

        val builder = MediaItem.Builder()
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
            url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
        }

        player.setMediaItem(builder.build(), if (positionMs == C.TIME_UNSET) C.TIME_UNSET else positionMs)
        player.prepare()
        player.playWhenReady = true
    }

    /** Fast channel change using the same player/decoder pipeline. */
    fun switchChannel(url: String, title: String? = null) = play(url, title)

    fun playLastPosition() {
        if (player.currentMediaItem == null) return
        if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
        else player.seekTo(lastPosition.coerceAtLeast(0L))
        player.playWhenReady = true
    }

    fun stop() {
        lastPosition = player.currentPosition.coerceAtLeast(0L)
        player.stop()
    }

    fun release() {
        handler.removeCallbacks(healthCheck)
        lastPosition = player.currentPosition.coerceAtLeast(0L)
        currentUrl = null
        player.release()
    }

    private fun checkPlaybackHealth() {
        val p = player
        if (!p.playWhenReady) return

        val videoPlaying = p.isPlaying && diagnostics.videoTrackAvailable
        val audioAvailable = diagnostics.audioTrackAvailable

        if (videoPlaying && audioAvailable) {
            val position = p.currentPosition
            if (lastPlayingPosition != C.TIME_UNSET && position == lastPlayingPosition &&
                System.currentTimeMillis() - lastAudioRecoveryAt > 5_000) {
                recoverAudio()
            }
            lastPlayingPosition = position
            return
        }

        if (p.playbackState == Player.STATE_BUFFERING && p.playbackException == null) {
            recover("stall")
        } else if (videoPlaying && !audioAvailable) {
            recoverAudio()
        }
    }

    /**
     * Resets the audio renderer without destroying the Activity or changing
     * channels. This targets the failure mode where video continues while
     * audio silently stops decoding.
     */
    private fun recoverAudio() {
        if (recovering || player.currentMediaItem == null) return
        val now = System.currentTimeMillis()
        if (now - lastAudioRecoveryAt < 8_000 || audioRecoveryAttempts >= 3) return

        lastAudioRecoveryAt = now
        audioRecoveryAttempts++
        diagnostics.audioRecoveryCount++
        val wasPlaying = player.isPlaying || player.playWhenReady
        val position = player.currentPosition.coerceAtLeast(0L)
        val live = player.isCurrentMediaItemLive

        player.stop()
        if (live) {
            player.setMediaItem(player.currentMediaItem!!)
            player.prepare()
            player.seekToDefaultPosition()
        } else {
            player.setMediaItem(player.currentMediaItem!!, position)
            player.prepare()
            player.seekTo(position)
        }
        player.playWhenReady = wasPlaying
    }

    private fun recover(reason: String) {
        val url = currentUrl ?: return
        if (recovering) return
        val now = System.currentTimeMillis()
        if (now - lastRecoveryAt < 2_000 || recoveryAttempts >= 4) return

        recovering = true
        recoveryAttempts++
        diagnostics.recoveryCount++
        lastRecoveryAt = now
        val live = player.isCurrentMediaItemLive
        val position = player.currentPosition.coerceAtLeast(0L)

        handler.postDelayed({
            if (currentUrl != url) {
                recovering = false
                return@postDelayed
            }
            val item = player.currentMediaItem ?: MediaItem.Builder().setUri(url).setMediaId(currentTitle ?: "alfie-tv").build()
            player.setMediaItem(item, if (live) C.TIME_UNSET else position)
            player.prepare()
            if (live) player.seekToDefaultPosition()
            player.playWhenReady = true
            recovering = false
        }, 350L)
    }
}
