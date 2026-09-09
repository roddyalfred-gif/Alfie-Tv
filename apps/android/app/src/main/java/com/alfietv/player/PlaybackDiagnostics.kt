package com.alfietv.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/** Runtime metrics used to diagnose long-running live-TV playback. */
data class PlaybackDiagnostics(
    var startupLatencyMs: Long? = null,
    var bitrate: Int? = null,
    var resolution: String? = null,
    var bufferedSeconds: Long? = null,
    var rebufferCount: Int = 0,
    var recoveryCount: Int = 0,
    var audioRecoveryCount: Int = 0,
    var droppedFrames: Int? = null,
    var lastErrorCategory: String? = null,
    var lastErrorAt: Long? = null,
    var audioTrackAvailable: Boolean = false,
    var videoTrackAvailable: Boolean = false,
    var audioSessionId: Int? = null,
    var lastAudioTrackChangeAt: Long? = null,
    var lastVideoTrackChangeAt: Long? = null
)

class DiagnosticsListener(private val diagnostics: PlaybackDiagnostics) : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_BUFFERING) diagnostics.rebufferCount++
    }

    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
        val audioAvailable = tracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && it.isSupported }
        val videoAvailable = tracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && it.isSupported }
        if (audioAvailable != diagnostics.audioTrackAvailable) diagnostics.lastAudioTrackChangeAt = System.currentTimeMillis()
        if (videoAvailable != diagnostics.videoTrackAvailable) diagnostics.lastVideoTrackChangeAt = System.currentTimeMillis()
        diagnostics.audioTrackAvailable = audioAvailable
        diagnostics.videoTrackAvailable = videoAvailable
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        diagnostics.audioSessionId = audioSessionId
    }

    override fun onPlayerError(error: PlaybackException) {
        diagnostics.lastErrorCategory = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "network"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> "decoder"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "format"
            else -> "unknown"
        }
        diagnostics.lastErrorAt = System.currentTimeMillis()
    }
}
