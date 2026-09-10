package com.alfietv.player

/**
 * Deterministic policy for deciding when stalled audio should be recovered.
 *
 * Keeping the decision separate from Media3 makes the recovery thresholds testable and
 * prevents channel switches from being mistaken for audio stalls.
 */
internal object AudioRecoveryPolicy {
    const val STALL_THRESHOLD_MS = 12_000L
    const val RETRY_COOLDOWN_MS = 12_000L
    const val MAX_RECOVERY_ATTEMPTS = 3

    fun shouldRecover(
        videoPlaying: Boolean,
        audioTrackAvailable: Boolean,
        positionStagnantForMs: Long,
        nowMs: Long,
        lastRecoveryAtMs: Long,
        recoveryAttempts: Int,
        force: Boolean = false,
    ): Boolean {
        if (!videoPlaying) return false
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS && !force) return false
        if (!force && lastRecoveryAtMs > 0L && nowMs - lastRecoveryAtMs < RETRY_COOLDOWN_MS) return false

        return force || !audioTrackAvailable || positionStagnantForMs >= STALL_THRESHOLD_MS
    }
}
