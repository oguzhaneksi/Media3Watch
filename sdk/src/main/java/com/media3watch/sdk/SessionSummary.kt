package com.media3watch.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SessionSummary(
    val sessionId: Long,
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

private val json = Json { prettyPrint = false }

fun SessionSummary.toJson(): String = json.encodeToString(this)

fun SessionSummary.toPrettyLog(): String = buildString {
    appendLine("session_end")
    appendLine("  sessionId: $sessionId")
    appendLine("  sessionStartDateIso: $sessionStartDateIso")
    appendLine("  sessionDurationMs: $sessionDurationMs")
    appendLine("  startupTimeMs: ${startupTimeMs ?: "null"}")
    appendLine("  rebufferTimeMs: ${rebufferTimeMs ?: "null"}")
    appendLine("  rebufferCount: ${rebufferCount ?: "null"}")
    appendLine("  playTimeMs: ${playTimeMs ?: "null"}")
    appendLine("  rebufferRatio: ${rebufferRatio ?: "null"}")
    appendLine("  totalDroppedFrames: ${totalDroppedFrames ?: "null"}")
    appendLine("  totalSeekCount: ${totalSeekCount ?: "null"}")
    appendLine("  totalSeekTimeMs: ${totalSeekTimeMs ?: "null"}")
    appendLine("  meanVideoFormatBitrate: ${meanVideoFormatBitrate ?: "null"}")
    appendLine("  errorCount: ${errorCount ?: "null"}")
}
