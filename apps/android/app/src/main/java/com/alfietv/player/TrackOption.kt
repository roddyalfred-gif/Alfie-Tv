package com.alfietv.player

import androidx.media3.common.TrackGroup

/** A selectable audio or subtitle track exposed by the native Media3 player. */
data class TrackOption(
    val label: String,
    val mediaTrackGroup: TrackGroup,
    val trackIndex: Int
)
