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

    fun buildSessionSummary(
        sessionId: String,
        sessionStartWallClockMs: Long,
        sessionStartTs: Long,
        now: Long,
        startupTimeMs: Long?,
        sessionEndStats: PlaybackStats?
    ): SessionSummary {
        return SessionSummary(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            sessionStartDateIso = toIsoDateTime(sessionStartWallClockMs),
            sessionDurationMs = (now - sessionStartTs).coerceAtLeast(0L),
            startupTimeMs = startupTimeMs,
            rebufferTimeMs = sessionEndStats?.totalRebufferTimeMs,
            rebufferCount = sessionEndStats?.totalRebufferCount,
            playTimeMs = sessionEndStats?.totalPlayTimeMs,
            rebufferRatio = sessionEndStats?.rebufferTimeRatio,
            totalDroppedFrames = sessionEndStats?.totalDroppedFrames,
            totalSeekCount = sessionEndStats?.totalSeekCount,
            totalSeekTimeMs = sessionEndStats?.totalSeekTimeMs,
            meanVideoFormatBitrate = sessionEndStats?.meanVideoFormatBitrate,
            errorCount = sessionEndStats?.fatalErrorCount
        )
    }

    fun logSessionEnd(
        sessionId: String,
        sessionStartWallClockMs: Long,
        sessionStartTs: Long,
        now: Long,
        startupTimeMs: Long?,
        sessionEndStats: PlaybackStats?
    ) {
        val summary = buildSessionSummary(
            sessionId = sessionId,
            sessionStartWallClockMs = sessionStartWallClockMs,
            sessionStartTs = sessionStartTs,
            now = now,
            startupTimeMs = startupTimeMs,
            sessionEndStats = sessionEndStats
        )
        Log.d(TAG, summary.toPrettyLog())
    }

    fun toIsoDateTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "null"
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(epochMillis))
    }
}
