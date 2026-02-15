package com.media3watch.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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
    val errorCount: Int?
)

private val sessionSummaryJson = Json {
    explicitNulls = true
}

internal fun SessionSummary.toJson(): String = sessionSummaryJson.encodeToString(this)

internal fun SessionSummary.toPrettyLog(): String {
    val startupStr = startupTimeMs?.toString() ?: "null"
    val rebufferTimeStr = rebufferTimeMs?.toString() ?: "null"
    val rebufferCountStr = rebufferCount?.toString() ?: "null"
    val playTimeStr = playTimeMs?.toString() ?: "null"
    val rebufferRatioStr = rebufferRatio?.toString() ?: "null"
    val errorCountStr = errorCount?.toString() ?: "null"
    val droppedFramesStr = totalDroppedFrames?.toString() ?: "null"
    val seekCountStr = totalSeekCount?.toString() ?: "null"
    val seekTimeMsStr = totalSeekTimeMs?.toString() ?: "null"
    val meanVideoFormatBitrateStr = meanVideoFormatBitrate?.toString() ?: "null"

    return buildString {
        appendLine("session_end")
        appendLine("  sessionId: $sessionId")
        appendLine("  timestamp: $timestamp")
        appendLine("  sessionStartDateIso: $sessionStartDateIso")
        appendLine("  sessionDurationMs: $sessionDurationMs")
        appendLine("  startupTimeMs: $startupStr")
        appendLine("  rebufferTimeMs: $rebufferTimeStr")
        appendLine("  rebufferCount: $rebufferCountStr")
        appendLine("  playTimeMs: $playTimeStr")
        appendLine("  rebufferRatio: $rebufferRatioStr")
        appendLine("  totalDroppedFrames: $droppedFramesStr")
        appendLine("  totalSeekCount: $seekCountStr")
        appendLine("  totalSeekTimeMs: $seekTimeMsStr")
        appendLine("  meanVideoFormatBitrate: $meanVideoFormatBitrateStr")
        appendLine("  errorCount: $errorCountStr")
    }
}
