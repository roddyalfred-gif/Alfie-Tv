package com.alfietv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRecoveryPolicyTest {
    @Test
    fun recoversWhenAudioTrackIsMissing() {
        assertTrue(
            AudioRecoveryPolicy.shouldRecover(
                videoPlaying = true,
                audioTrackAvailable = false,
                positionStagnantForMs = 0L,
                nowMs = 20_000L,
                lastRecoveryAtMs = 0L,
                recoveryAttempts = 0,
            ),
        )
    }

    @Test
    fun recoversWhenVideoIsPlayingButPositionStalls() {
        assertTrue(
            AudioRecoveryPolicy.shouldRecover(
                videoPlaying = true,
                audioTrackAvailable = true,
                positionStagnantForMs = AudioRecoveryPolicy.STALL_THRESHOLD_MS,
                nowMs = 20_000L,
                lastRecoveryAtMs = 0L,
                recoveryAttempts = 0,
            ),
        )
    }

    @Test
    fun doesNotRecoverWhileVideoIsStopped() {
        assertFalse(
            AudioRecoveryPolicy.shouldRecover(
                videoPlaying = false,
                audioTrackAvailable = false,
                positionStagnantForMs = 60_000L,
                nowMs = 20_000L,
                lastRecoveryAtMs = 0L,
                recoveryAttempts = 0,
            ),
        )
    }

    @Test
    fun cooldownPreventsRapidRepeatedRecovery() {
        assertFalse(
            AudioRecoveryPolicy.shouldRecover(
                videoPlaying = true,
                audioTrackAvailable = false,
                positionStagnantForMs = 60_000L,
                nowMs = 20_000L,
                lastRecoveryAtMs = 15_000L,
                recoveryAttempts = 1,
            ),
        )
    }

    @Test
    fun retryLimitBlocksAutomaticRecoveryButNotForcedRecovery() {
        assertFalse(
            AudioRecoveryPolicy.shouldRecover(
                videoPlaying = true,
                audioTrackAvailable = false,
                positionStagnantForMs = 60_000L,
                nowMs = 40_000L,
                lastRecoveryAtMs = 0L,
                recoveryAttempts = AudioRecoveryPolicy.MAX_RECOVERY_ATTEMPTS,
            ),
        )
        assertTrue(
            AudioRecoveryPolicy.shouldRecover(
                videoPlaying = true,
                audioTrackAvailable = false,
                positionStagnantForMs = 60_000L,
                nowMs = 40_000L,
                lastRecoveryAtMs = 0L,
                recoveryAttempts = AudioRecoveryPolicy.MAX_RECOVERY_ATTEMPTS,
                force = true,
            ),
        )
    }
}
