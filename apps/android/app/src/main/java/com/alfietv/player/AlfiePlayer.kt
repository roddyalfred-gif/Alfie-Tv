package com.alfietv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView

/** Native Media3 playback engine optimized for long-running live TV and VOD. */
class AlfiePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val playbackGeneration = PlaybackGeneration()
    private var recoveryAttempts = 0
    private var audioRecoveryAttempts = 0
    private var lastRecoveryAt = 0L
    private var lastAudioRecoveryAt = 0L
    private var lastPlayingPosition = C.TIME_UNSET
    private var stagnantSince = 0L
    private var bufferingSince = 0L
    private var startupStartedAt = 0L
    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var currentChannelNumber: String? = null
    private var recovering = false
    private var pendingChannelUrl: String? = null
    private var pendingChannelTitle: String? = null
    private var pendingChannelNumber: String? = null
    private var pendingChannelGeneration = 0L

    /** Controlled by SettingsActivity/MainActivity; true enables automatic recovery after fatal errors. */
    var autoRetryEnabled: Boolean = true

    private val channelSwitchRunnable = Runnable {
        val url = pendingChannelUrl ?: return@Runnable
        val generation = pendingChannelGeneration
        if (!playbackGeneration.isCurrent(generation)) return@Runnable
        val title = pendingChannelTitle
        val number = pendingChannelNumber
        pendingChannelUrl = null
        pendingChannelTitle = null
        pendingChannelNumber = null
        play(url, title, C.TIME_UNSET, number)
    }
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
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            setSeekBackIncrementMs(10_000)
            setSeekForwardIncrementMs(10_000)
            addListener(DiagnosticsListener(diagnostics))
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_BUFFERING) {
                        if (bufferingSince == 0L) bufferingSince = System.currentTimeMillis()
                    } else if (playbackState == Player.STATE_READY) {
                        bufferingSince = 0L
                    }
                }
                override fun onRenderedFirstFrame() {
                    diagnostics.firstFrameRendered = true
                    if (startupStartedAt != 0L) {
                        diagnostics.startupLatencyMs = (System.currentTimeMillis() - startupStartedAt).coerceAtLeast(0L)
                        startupStartedAt = 0L
                    }
                    updateVideoDiagnostics()
                }
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    updateVideoDiagnostics()
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (autoRetryEnabled) recover()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        recoveryAttempts = 0
                        audioRecoveryAttempts = 0
                        stagnantSince = 0L
                        bufferingSince = 0L
                    }
                }
            })
        }

    /** MediaSession exposes the same player to Android/TV system media controls and remote transports. */
    private val mediaSession: MediaSession by lazy {
        MediaSession.Builder(appContext, player)
            .setId("alfie-tv")
            .build()
    }

    init { mediaSession }

    private val healthCheck = object : Runnable {
        override fun run() { checkPlaybackHealth(); handler.postDelayed(this, 4_000) }
    }

    fun attach(view: PlayerView) {
        view.player = player
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        view.requestFocus()
        handler.removeCallbacks(healthCheck)
        handler.postDelayed(healthCheck, 4_000)
    }

    fun play(url: String, title: String? = null, positionMs: Long = C.TIME_UNSET, channelNumber: String? = null) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Unsupported stream URL" }
        playbackGeneration.next()
        handler.removeCallbacks(channelSwitchRunnable)
        pendingChannelUrl = null
        pendingChannelTitle = null
        pendingChannelNumber = null
        currentUrl = url
        currentTitle = title
        currentChannelNumber = channelNumber
        recoveryAttempts = 0
        audioRecoveryAttempts = 0
        recovering = false
        bufferingSince = 0L
        stagnantSince = 0L
        lastPlayingPosition = C.TIME_UNSET
        startupStartedAt = System.currentTimeMillis()
        diagnostics.startupLatencyMs = null
        diagnostics.bitrate = null
        diagnostics.resolution = null
        diagnostics.bufferedSeconds = null
        diagnostics.lastErrorCategory = null
        diagnostics.lastErrorCode = null
        diagnostics.lastErrorCodeName = null
        diagnostics.lastErrorMessage = null
        diagnostics.audioTrackAvailable = false
        diagnostics.videoTrackAvailable = false
        diagnostics.firstFrameRendered = false
        diagnostics.audioSessionId = null
        diagnostics.lastAudioTrackChangeAt = null
        diagnostics.lastVideoTrackChangeAt = null

        val sourceType = inferSourceType(url)
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(title ?: "alfie-tv")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title ?: "Alfie TV")
                    .setArtist(channelNumber?.takeIf { it.isNotBlank() }?.let { "Channel $it" })
                    .build()
            )

        when (sourceType) {
            SourceType.HLS -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            SourceType.DASH -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            SourceType.MP4 -> builder.setMimeType(MimeTypes.VIDEO_MP4)
            SourceType.MKV -> builder.setMimeType("video/x-matroska")
            SourceType.WEBM -> builder.setMimeType(MimeTypes.VIDEO_WEBM)
            SourceType.MPEG_TS -> builder.setMimeType(MimeTypes.VIDEO_MP2T)
            SourceType.UNKNOWN -> Unit
        }

        player.setMediaItem(builder.build(), if (positionMs == C.TIME_UNSET) C.TIME_UNSET else positionMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun retryCurrent() {
        val url = currentUrl ?: return
        val wasLive = player.isCurrentMediaItemLive
        val position = player.currentPosition.coerceAtLeast(0L)
        play(url, currentTitle, if (wasLive) C.TIME_UNSET else position, currentChannelNumber)
    }

    fun switchChannel(url: String, title: String? = null, channelNumber: String? = null) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Unsupported stream URL" }
        handler.removeCallbacks(channelSwitchRunnable)
        pendingChannelGeneration = playbackGeneration.next()
        pendingChannelUrl = url
        pendingChannelTitle = title
        pendingChannelNumber = channelNumber
        handler.postDelayed(channelSwitchRunnable, 75L)
    }

    fun playLastPosition() {
        if (player.currentMediaItem == null) return
        if (player.isCurrentMediaItemLive) player.seekToDefaultPosition() else player.seekTo(lastPositionMs.coerceAtLeast(0L))
        player.playWhenReady = true
    }

    fun stop() {
        playbackGeneration.next()
        handler.removeCallbacks(channelSwitchRunnable)
        player.stop()
    }

    fun release() {
        playbackGeneration.next()
        handler.removeCallbacks(channelSwitchRunnable)
        handler.removeCallbacks(healthCheck)
        mediaSession.release()
        player.release()
    }

    fun audioTracks(): List<TrackOption> = trackOptions(C.TRACK_TYPE_AUDIO)
    fun subtitleTracks(): List<TrackOption> = trackOptions(C.TRACK_TYPE_TEXT)

    fun selectAudio(option: TrackOption) = selectTrack(C.TRACK_TYPE_AUDIO, option)
    fun selectSubtitle(option: TrackOption?) {
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (option != null) builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).addOverride(TrackSelectionOverride(option.group, option.indexes))
        else builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        player.trackSelectionParameters = builder.build()
    }

    fun refreshAudio() {
        if (player.currentMediaItem == null) return
        val wasLive = player.isCurrentMediaItemLive
        val position = player.currentPosition.coerceAtLeast(0L)
        val shouldPlay = player.playWhenReady
        audioRecoveryAttempts = 0
        recoverAudio(force = true)
        if (!wasLive && shouldPlay) player.seekTo(position)
    }

    private fun trackOptions(type: Int): List<TrackOption> = buildList {
        player.currentTracks.groups.forEach { group ->
            if (group.type != type) return@forEach
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                if (group.isTrackSupported(i)) add(TrackOption(group.mediaTrackGroup, i, format.label ?: format.language ?: "Track ${i + 1}"))
            }
        }
    }

    private fun selectTrack(type: Int, option: TrackOption) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .clearOverridesOfType(type)
            .addOverride(TrackSelectionOverride(option.group, option.indexes))
            .build()
    }

    private fun checkPlaybackHealth() {
        if (player.currentMediaItem == null || player.playbackState == Player.STATE_IDLE) return
        val now = System.currentTimeMillis()
        val position = player.currentPosition
        val playing = player.isPlaying
        if (playing && position == lastPlayingPosition) {
            if (stagnantSince == 0L) stagnantSince = now
        } else if (playing) stagnantSince = 0L
        lastPlayingPosition = position

        if (startupStartedAt != 0L && !diagnostics.firstFrameRendered && now - startupStartedAt > 20_000L) {
            if (autoRetryEnabled) recover()
            return
        }
        if (bufferingSince != 0L && now - bufferingSince > 15_000L) {
            if (autoRetryEnabled) recover()
            return
        }
        val audioStall = AudioRecoveryPolicy.shouldRecover(
            videoPlaying = playing && diagnostics.videoTrackAvailable,
            audioTrackAvailable = diagnostics.audioTrackAvailable,
            positionStagnantForMs = if (stagnantSince == 0L) 0L else now - stagnantSince,
            nowMs = now,
            lastRecoveryAtMs = lastAudioRecoveryAt,
            recoveryAttempts = audioRecoveryAttempts,
        )
        if (audioStall && autoRetryEnabled) recoverAudio()
    }

    private fun recoverAudio(force: Boolean = false) {
        val url = currentUrl ?: return
        val now = System.currentTimeMillis()
        if (!AudioRecoveryPolicy.shouldRecover(
                videoPlaying = player.isPlaying,
                audioTrackAvailable = diagnostics.audioTrackAvailable,
                positionStagnantForMs = if (stagnantSince == 0L) AudioRecoveryPolicy.STALL_THRESHOLD_MS else now - stagnantSince,
                nowMs = now,
                lastRecoveryAtMs = lastAudioRecoveryAt,
                recoveryAttempts = audioRecoveryAttempts,
                force = force,
            ) && !force) return
        if (audioRecoveryAttempts >= AudioRecoveryPolicy.MAX_RECOVERY_ATTEMPTS && !force) return
        audioRecoveryAttempts++
        lastAudioRecoveryAt = now
        stagnantSince = 0L
        lastPlayingPosition = C.TIME_UNSET
        diagnostics.audioTrackAvailable = false
        val wasLive = player.isCurrentMediaItemLive
        val position = player.currentPosition.coerceAtLeast(0L)
        val shouldPlay = player.playWhenReady
        recovering = true
        handler.postDelayed({
            if (currentUrl != url) return@postDelayed
            play(url, currentTitle, if (wasLive) C.TIME_UNSET else position, currentChannelNumber)
            player.playWhenReady = shouldPlay
            recovering = false
        }, 250L)
    }

    private fun recover() {
        if (recovering || !autoRetryEnabled) return
        val url = currentUrl ?: return
        if (recoveryAttempts >= 3) return
        recoveryAttempts++
        lastRecoveryAt = System.currentTimeMillis()
        stagnantSince = 0L
        lastPlayingPosition = C.TIME_UNSET
        diagnostics.audioTrackAvailable = false
        diagnostics.videoTrackAvailable = false
        val wasLive = player.isCurrentMediaItemLive
        val position = player.currentPosition.coerceAtLeast(0L)
        val shouldPlay = player.playWhenReady
        recovering = true
        handler.postDelayed({
            if (currentUrl != url) return@postDelayed
            play(url, currentTitle, if (wasLive) C.TIME_UNSET else position, currentChannelNumber)
            player.playWhenReady = shouldPlay
            recovering = false
        }, 350L)
    }

    private fun updateVideoDiagnostics() {
        val info = player.videoFormat
        diagnostics.videoTrackAvailable = info != null
        diagnostics.resolution = info?.let { "${it.width}x${it.height}" }
        diagnostics.bitrate = info?.bitrate?.takeIf { it > 0 }
    }
}
