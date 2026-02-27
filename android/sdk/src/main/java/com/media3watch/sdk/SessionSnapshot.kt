package com.media3watch.sdk

data class SessionSnapshot(
    val sessionId: String,
    val elapsedSessionTimeMs: Long,
    val playbackState: SessionPlaybackState,
    val isPlaying: Boolean,
    val currentPositionMs: Long?,
    val startupTimeMs: Long?,
    val rebufferTimeMs: Long,
    val rebufferCount: Int,
    val playTimeMs: Long,
    val rebufferRatio: Float,
    val totalDroppedFrames: Long,
    val totalSeekCount: Int,
    val totalSeekTimeMs: Long,
    val meanVideoFormatBitrate: Int?,
    val currentBitrate: Int?,
    val errorCount: Int,
)
