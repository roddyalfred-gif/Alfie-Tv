package com.alfietv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackGenerationTest {
    @Test
    fun newerGenerationInvalidatesOlderToken() {
        val generation = PlaybackGeneration()
        val first = generation.next()
        val second = generation.next()

        assertFalse(generation.isCurrent(first))
        assertTrue(generation.isCurrent(second))
    }

    @Test
    fun currentTokenRemainsValidUntilNextGeneration() {
        val generation = PlaybackGeneration()
        val token = generation.next()

        assertTrue(generation.isCurrent(token))
        assertTrue(generation.current() == token)
    }
}
