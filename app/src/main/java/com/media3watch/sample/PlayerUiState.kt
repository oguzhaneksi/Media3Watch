package com.media3watch.sample

data class PlayerUiState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isScrubbing: Boolean = false,
    val scrubPositionMs: Long = 0L,
) {
    val sliderValueMs: Long
        get() = if (isScrubbing) scrubPositionMs else positionMs
}