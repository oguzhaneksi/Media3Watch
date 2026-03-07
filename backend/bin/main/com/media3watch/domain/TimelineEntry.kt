package com.media3watch.domain

import kotlinx.serialization.Serializable

@Serializable
data class TimelineEntry(
    val timestampMs: Long,
    val elapsedMs: Long,
    val playbackState: String,
    val currentBitrate: Int? = null,
    val networkType: String? = null,
    val totalDroppedFrames: Long,
    val bufferedDurationMs: Long? = null,
    val rebufferCount: Int,
    val rebufferTimeMs: Long,
)
