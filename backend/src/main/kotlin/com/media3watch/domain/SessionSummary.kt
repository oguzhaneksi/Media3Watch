package com.media3watch.domain

import kotlinx.serialization.Serializable

@Serializable
data class SessionSummary(
    val sessionId: String,
    val timestamp: Long,
    val sessionStartDateIso: String,
    val sessionDurationMs: Long,
    val startupTimeMs: Long? = null,
    val rebufferTimeMs: Long? = null,
    val rebufferCount: Int? = null,
    val playTimeMs: Long? = null,
    val rebufferRatio: Float? = null,
    val totalDroppedFrames: Long? = null,
    val totalSeekCount: Int? = null,
    val totalSeekTimeMs: Long? = null,
    val meanVideoFormatBitrate: Int? = null,
    val errorCount: Int? = null
)
