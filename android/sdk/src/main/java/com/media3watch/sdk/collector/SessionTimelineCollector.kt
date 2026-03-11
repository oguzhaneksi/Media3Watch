package com.media3watch.sdk.collector

import androidx.annotation.MainThread
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.PlaybackStats
import com.media3watch.sdk.util.currentBitrate
import com.media3watch.sdk.model.TimelineEntry
import com.media3watch.sdk.util.toSessionPlaybackState

@UnstableApi
internal class SessionTimelineCollector {

    private val buffer = mutableListOf<TimelineEntry>()

    @MainThread
    fun capture(
        player: ExoPlayer,
        stats: PlaybackStats?,
        sessionStartTs: Long,
        nowElapsedMs: Long,
        nowWallClockMs: Long,
        connectionType: String?,
    ) {
        val elapsedMs = (nowElapsedMs - sessionStartTs).coerceAtLeast(0L)

        val playbackStateStr = toSessionPlaybackState(player.playbackState, player.isPlaying).name

        val currentBitrate = player.currentBitrate

        val bufferedDurationMs = player.totalBufferedDuration.takeIf { it != C.TIME_UNSET }

        val entry = TimelineEntry(
            timestampMs = nowWallClockMs,
            elapsedMs = elapsedMs,
            playbackState = playbackStateStr,
            currentBitrate = currentBitrate,
            networkType = connectionType,
            totalDroppedFrames = stats?.totalDroppedFrames ?: 0L,
            bufferedDurationMs = bufferedDurationMs,
            rebufferCount = stats?.totalRebufferCount ?: 0,
            rebufferTimeMs = stats?.totalRebufferTimeMs ?: 0L,
        )

        buffer.add(entry)
    }

    @MainThread
    fun drain(): List<TimelineEntry> {
        val result = buffer.toList()
        buffer.clear()
        return result
    }

    @MainThread
    fun clear() {
        buffer.clear()
    }
}
