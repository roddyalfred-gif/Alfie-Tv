package com.alfietv.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

data class PlaybackDiagnostics(
    var startupLatencyMs: Long? = null,
    var bitrate: Int? = null,
    var resolution: String? = null,
    var bufferedSeconds: Long? = null,
    var rebufferCount: Int = 0,
    var recoveryCount: Int = 0,
    var droppedFrames: Int? = null,
    var lastErrorCategory: String? = null,
    var lastErrorAt: Long? = null
)

class DiagnosticsListener(private val diagnostics: PlaybackDiagnostics) : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_BUFFERING) diagnostics.rebufferCount++
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
