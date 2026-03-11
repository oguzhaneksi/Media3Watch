package com.media3watch.sdk.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class SessionSummary(
    val sessionId: String,
    val timestamp: Long,
    val sessionStartDateIso: String,
    val sessionDurationMs: Long,
    val startupTimeMs: Long?,
    val rebufferTimeMs: Long?,
    val rebufferCount: Int?,
    val playTimeMs: Long?,
    val rebufferRatio: Float?,
    val totalDroppedFrames: Long?,
    val totalSeekCount: Int?,
    val totalSeekTimeMs: Long?,
    val meanVideoFormatBitrate: Int?,
    val errorCount: Int?,
    // Device & SDK metadata (nullable for backward compatibility)
    val deviceModel: String? = null,
    val osVersion: Int? = null,
    val sdkVersion: String? = null,
    val connectionType: String? = null,
    val timelineEvents: List<TimelineEntry>? = null,
)

private val sessionSummaryJson = Json {
    explicitNulls = true
}

internal fun SessionSummary.toJson(): String = sessionSummaryJson.encodeToString(this)

internal fun SessionSummary.toPrettyLog(): String = buildString {
    appendLine("session_end")
    appendLine("  sessionId: $sessionId")
    appendLine("  timestamp: $timestamp")
    appendLine("  sessionStartDateIso: $sessionStartDateIso")
    appendLine("  sessionDurationMs: $sessionDurationMs")
    appendLine("  startupTimeMs: $startupTimeMs")
    appendLine("  rebufferTimeMs: $rebufferTimeMs")
    appendLine("  rebufferCount: $rebufferCount")
    appendLine("  playTimeMs: $playTimeMs")
    appendLine("  rebufferRatio: $rebufferRatio")
    appendLine("  totalDroppedFrames: $totalDroppedFrames")
    appendLine("  totalSeekCount: $totalSeekCount")
    appendLine("  totalSeekTimeMs: $totalSeekTimeMs")
    appendLine("  meanVideoFormatBitrate: $meanVideoFormatBitrate")
    appendLine("  errorCount: $errorCount")
    appendLine("  deviceModel: $deviceModel")
    appendLine("  osVersion: $osVersion")
    appendLine("  sdkVersion: $sdkVersion")
    appendLine("  connectionType: $connectionType")
}
