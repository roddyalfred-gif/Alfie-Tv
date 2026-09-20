package com.alfietv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelActivationPolicyTest {
    @Test
    fun first_activation_previews_channel() {
        assertEquals(
            ChannelActivationPolicy.Action.PREVIEW,
            ChannelActivationPolicy.action(null, "channel-1")
        )
    }

    @Test
    fun different_channel_activation_previews_new_channel() {
        assertEquals(
            ChannelActivationPolicy.Action.PREVIEW,
            ChannelActivationPolicy.action("channel-1", "channel-2")
        )
    }

    @Test
    fun activating_same_channel_requests_fullscreen() {
        assertEquals(
            ChannelActivationPolicy.Action.FULLSCREEN,
            ChannelActivationPolicy.action("channel-1", "channel-1")
        )
    }

    @Test
    fun blank_previous_channel_is_treated_as_no_preview() {
        assertEquals(
            ChannelActivationPolicy.Action.PREVIEW,
            ChannelActivationPolicy.action("", "channel-1")
        )
    }
}
