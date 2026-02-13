package com.media3watch.sdk

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlaybackStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@UnstableApi
internal object LogUtils {
    const val TAG = "Media3WatchAnalytics"

    fun logSessionEnd(
        sessionId: Long,
        sessionStartWallClockMs: Long,
        sessionStartTs: Long,
        now: Long,
        startupTimeMs: Long?,
        sessionEndStats: PlaybackStats?
    ) {
        val sessionStartDateIso = toIsoDateTime(sessionStartWallClockMs)
        val startupStr = startupTimeMs?.toString() ?: "null"
        val rebufferTimeStr = sessionEndStats?.totalRebufferTimeMs?.toString() ?: "null"
        val rebufferCountStr = sessionEndStats?.totalRebufferCount?.toString() ?: "null"
        val playTimeStr = sessionEndStats?.totalPlayTimeMs?.toString() ?: "null"
        val rebufferRatioStr = sessionEndStats?.rebufferTimeRatio?.toString() ?: "null"
        val errorCountStr = sessionEndStats?.fatalErrorCount?.toString() ?: "null"
        val droppedFramesStr = sessionEndStats?.totalDroppedFrames?.toString() ?: "null"
        val seekCountStr = sessionEndStats?.totalSeekCount?.toString() ?: "null"
        val seekTimeMsStr = sessionEndStats?.totalSeekTimeMs?.toString() ?: "null"
        val meanVideoFormatBitrateStr = sessionEndStats?.meanVideoFormatBitrate?.toString() ?: "null"
        val sessionDuration = now - sessionStartTs

        val message = buildString {
            appendLine("session_end")
            appendLine("  sessionId: $sessionId")
            appendLine("  sessionStartDateIso: $sessionStartDateIso")
            appendLine("  sessionDurationMs: $sessionDuration")
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

        Log.d(TAG, message)
    }

    fun toIsoDateTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "null"
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(epochMillis))
    }
}
