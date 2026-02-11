package com.media3watch.sdk.schema

import kotlinx.serialization.Serializable

@Serializable
data class SessionSummary(
    val schemaVersion: String,
    val sessionId: String,
    val timestamp: Long,
    val contentId: String? = null,
    val streamType: StreamType? = null,
    val startupTimeMs: Long? = null,
    val playTimeMs: Long? = null,
    val rebufferTimeMs: Long? = null,
    val rebufferCount: Int? = null,
    val rebufferRatio: Double? = null,
    val errorCount: Int? = null,
    val lastErrorCode: Int? = null,
    val lastErrorCategory: ErrorCategory? = null,
    val qualitySwitchCount: Int? = null,
    val avgBitrateKbps: Int? = null,
    val droppedFrames: Int? = null,
    val device: DeviceInfo? = null,
    val app: AppInfo? = null,
    val custom: Map<String, String>? = null,
)
