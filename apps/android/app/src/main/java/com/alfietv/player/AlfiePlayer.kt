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
    private val channelSwitchRunnable = Runnable {
        val url = pendingChannelUrl ?: return@Runnable
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
                override fun onPlayerError(error: PlaybackException) { recover() }
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
        view.useController = true
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        view.requestFocus()
        handler.removeCallbacks(healthCheck)
        handler.postDelayed(healthCheck, 4_000)
    }

    fun play(url: String, title: String? = null, positionMs: Long = C.TIME_UNSET, channelNumber: String? = null) {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Unsupported stream URL" }
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
        pendingChannelUrl = url
        pendingChannelTitle = title
        pendingChannelNumber = channelNumber
        handler.postDelayed(channelSwitchRunnable, 150L)
    }

    fun playLastPosition() {
        if (player.currentMediaItem == null) return
        if (player.isCurrentMediaItemLive) player.seekToDefaultPosition() else player.seekTo(lastPositionMs.coerceAtLeast(0L))
        player.playWhenReady = true
    }

    fun stop() {
        handler.removeCallbacks(channelSwitchRunnable)
        pendingChannelUrl = null
        pendingChannelTitle = null
        pendingChannelNumber = null
        lastPositionMs = player.currentPosition.coerceAtLeast(0L)
        player.stop()
    }

    fun release() {
        handler.removeCallbacks(healthCheck)
        handler.removeCallbacks(channelSwitchRunnable)
        pendingChannelUrl = null
        pendingChannelTitle = null
        pendingChannelNumber = null
        lastPositionMs = player.currentPosition.coerceAtLeast(0L)
        currentUrl = null
        mediaSession.release()
        player.release()
    }

    fun audioTracks(): List<TrackOption> = trackOptions(C.TRACK_TYPE_AUDIO)
    fun subtitleTracks(): List<TrackOption> = trackOptions(C.TRACK_TYPE_TEXT)
    fun selectAudio(track: TrackOption?) = selectTrack(C.TRACK_TYPE_AUDIO, track)

    fun refreshAudio() {
        if (player.currentMediaItem == null || recovering) return
        recoverAudio(force = true)
    }

    fun selectSubtitle(track: TrackOption?) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (track == null) builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(track.mediaTrackGroup, track.trackIndex))
        player.trackSelectionParameters = builder.build()
    }

    fun statusText(): String {
        if (player.playbackState == Player.STATE_BUFFERING) return "BUFFERING"
        if (player.playbackState == Player.STATE_ENDED) return "ENDED"
        if (player.playerError != null) return "ERROR ${diagnostics.lastErrorCategory ?: "unknown"}"
        if (player.isPlaying) return "PLAYING"
        return "PAUSED"
    }

    fun errorText(): String? {
        val category = diagnostics.lastErrorCategory ?: return null
        val code = diagnostics.lastErrorCodeName ?: "UNKNOWN"
        val message = diagnostics.lastErrorMessage?.takeIf { it.isNotBlank() }
        return if (message == null) "$category • $code" else "$category • $code • $message"
    }

    fun diagnosticsText(): String {
        val buffer = diagnostics.bufferedSeconds?.let { "BUF ${it}s" } ?: "BUF —"
        val tracks = when {
            diagnostics.audioTrackAvailable && diagnostics.videoTrackAvailable -> "A/V ✓"
            diagnostics.videoTrackAvailable -> "AUDIO —"
            diagnostics.audioTrackAvailable -> "VIDEO —"
            else -> "A/V —"
        }
        val startup = diagnostics.startupLatencyMs?.let { "START ${it}ms" } ?: "START —"
        val recovery = if (diagnostics.recoveryCount > 0 || diagnostics.audioRecoveryCount > 0) "REC ${diagnostics.recoveryCount}/${diagnostics.audioRecoveryCount}" else null
        return listOfNotNull(buffer, tracks, startup, recovery).joinToString("  •  ")
    }

    fun videoFormatText(): String {
        val format = player.videoFormat ?: return "Video —"
        val resolution = if (format.width > 0 && format.height > 0) "${format.width}×${format.height}" else "Video"
        val bitrate = if (format.bitrate > 0) " ${(format.bitrate / 1000)} kbps" else ""
        return resolution + bitrate
    }

    private var lastPositionMs = 0L

    private fun updateVideoDiagnostics() {
        val format = player.videoFormat ?: return
        if (format.width > 0 && format.height > 0) diagnostics.resolution = "${format.width}×${format.height}"
        if (format.bitrate > 0) diagnostics.bitrate = format.bitrate
    }

    private fun trackOptions(trackType: Int): List<TrackOption> {
        val result = mutableListOf<TrackOption>()
        player.currentTracks.groups.forEach { group ->
            if (group.type != trackType || !group.isSupported) return@forEach
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                val label = format.label?.takeIf { it.isNotBlank() } ?: format.language?.takeIf { it.isNotBlank() }
                    ?: if (trackType == C.TRACK_TYPE_AUDIO) "Audio ${index + 1}" else "Subtitle ${index + 1}"
                result += TrackOption(label, group.mediaTrackGroup, index)
            }
        }
        return result
    }

    private fun selectTrack(trackType: Int, track: TrackOption?) {
        val builder = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(trackType, track == null)
        if (track != null) builder.setOverrideForType(TrackSelectionOverride(track.mediaTrackGroup, track.trackIndex))
        player.trackSelectionParameters = builder.build()
    }

    private fun checkPlaybackHealth() {
        val p = player
        if (!p.playWhenReady || p.currentMediaItem == null || recovering) return
        val now = System.currentTimeMillis()
        diagnostics.bufferedSeconds = ((p.bufferedPosition - p.currentPosition).coerceAtLeast(0L) / 1000L)
        updateVideoDiagnostics()
        if (startupStartedAt != 0L && !diagnostics.firstFrameRendered && now - startupStartedAt >= 15_000L) { recover(); return }
        if (p.playbackState == Player.STATE_ENDED && p.isCurrentMediaItemLive) { recover(); return }
        val videoPlaying = p.isPlaying && diagnostics.videoTrackAvailable
        val audioAvailable = diagnostics.audioTrackAvailable
        if (videoPlaying && audioAvailable) {
            val position = p.currentPosition
            if (lastPlayingPosition != C.TIME_UNSET && position == lastPlayingPosition) {
                if (stagnantSince == 0L) stagnantSince = now
                if (now - stagnantSince >= 12_000L && now - lastAudioRecoveryAt >= 12_000L) recoverAudio()
            } else stagnantSince = 0L
            lastPlayingPosition = position
            return
        }
        if (p.playbackState == Player.STATE_BUFFERING && p.playerError == null) {
            if (bufferingSince == 0L) bufferingSince = now
            if (now - bufferingSince >= 10_000L) recover()
        } else if (videoPlaying && !audioAvailable && now - lastAudioRecoveryAt >= 12_000L) recoverAudio()
    }

    private fun recoverAudio(force: Boolean = false) {
        if (recovering || player.currentMediaItem == null) return
        val now = System.currentTimeMillis()
        if (!force && (now - lastAudioRecoveryAt < 12_000 || audioRecoveryAttempts >= 3)) return
        lastAudioRecoveryAt = now
        if (!force) audioRecoveryAttempts++
        diagnostics.audioRecoveryCount++
        val wasPlaying = player.isPlaying || player.playWhenReady
        val item = player.currentMediaItem ?: return
        val live = player.isCurrentMediaItemLive
        val position = player.currentPosition.coerceAtLeast(0L)
        player.stop()
        if (live) {
            player.setMediaItem(item)
            player.prepare()
            player.seekToDefaultPosition()
        } else {
            player.setMediaItem(item, position)
            player.prepare()
            player.seekTo(position)
        }
        player.playWhenReady = wasPlaying
    }

    private fun recover() {
        val url = currentUrl ?: return
        if (recovering) return
        val now = System.currentTimeMillis()
        if (now - lastRecoveryAt < 2_000 || recoveryAttempts >= 4) return
        recovering = true
        recoveryAttempts++
        diagnostics.recoveryCount++
        lastRecoveryAt = now
        bufferingSince = 0L
        startupStartedAt = now
        diagnostics.firstFrameRendered = false
        val live = player.isCurrentMediaItemLive
        val position = player.currentPosition.coerceAtLeast(0L)
        val originalItem = player.currentMediaItem
        handler.postDelayed({
            if (currentUrl != url) { recovering = false; return@postDelayed }
            val item = originalItem ?: MediaItem.Builder().setUri(url).setMediaId(currentTitle ?: "alfie-tv").build()
            player.setMediaItem(item, if (live) C.TIME_UNSET else position)
            player.prepare()
            if (live) player.seekToDefaultPosition()
            player.playWhenReady = true
            recovering = false
        }, 350L)
    }

    private fun inferSourceType(url: String): SourceType {
        val path = url.substringBefore('?').lowercase()
        val query = url.substringAfter('?', "").lowercase()
        return when {
            path.endsWith(".m3u8") || query.contains(".m3u8") -> SourceType.HLS
            path.endsWith(".mpd") || query.contains(".mpd") -> SourceType.DASH
            path.endsWith(".mp4") -> SourceType.MP4
            path.endsWith(".mkv") -> SourceType.MKV
            path.endsWith(".webm") -> SourceType.WEBM
            path.endsWith(".ts") || path.endsWith(".mpegts") -> SourceType.MPEG_TS
            else -> SourceType.UNKNOWN
        }
    }

    private enum class SourceType { HLS, DASH, MP4, MKV, WEBM, MPEG_TS, UNKNOWN }
}
