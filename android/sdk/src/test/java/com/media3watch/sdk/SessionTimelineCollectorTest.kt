package com.media3watch.sdk

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@UnstableApi
class SessionTimelineCollectorTest {

    private val sessionStartTs = SystemClock.elapsedRealtime()

    private fun buildIdlePlayer(): ExoPlayer {
        val player = mock(ExoPlayer::class.java)
        doAnswer { Player.STATE_IDLE }.`when`(player).playbackState
        doAnswer { false }.`when`(player).isPlaying
        doAnswer { null }.`when`(player).videoFormat
        doAnswer { 0L }.`when`(player).totalBufferedDuration
        doAnswer { Looper.getMainLooper() }.`when`(player).applicationLooper
        return player
    }

    private fun captureEntry(
        collector: SessionTimelineCollector,
        player: ExoPlayer,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        nowWallClockMs: Long = System.currentTimeMillis(),
    ) {
        collector.capture(
            player = player,
            stats = null,
            sessionStartTs = sessionStartTs,
            nowElapsedMs = nowElapsedMs,
            nowWallClockMs = nowWallClockMs,
            connectionType = null,
        )
    }

    @Test
    fun capture_populatesAllFields() {
        val collector = SessionTimelineCollector()
        val player = buildIdlePlayer()
        val wallClock = System.currentTimeMillis()

        collector.capture(
            player = player,
            stats = null,
            sessionStartTs = sessionStartTs,
            nowElapsedMs = sessionStartTs,
            nowWallClockMs = wallClock,
            connectionType = null,
        )

        val entries = collector.drain()
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertTrue("timestampMs must be positive", entry.timestampMs > 0)
        assertTrue("elapsedMs must be non-negative", entry.elapsedMs >= 0)
        assertEquals("IDLE", entry.playbackState)
        assertEquals(null, entry.currentBitrate)
        assertEquals(0L, entry.totalDroppedFrames)
        assertEquals(0, entry.rebufferCount)
        assertEquals(0L, entry.rebufferTimeMs)
    }

    @Test
    fun drain_returnsAllCapturedEntries_andClearsBuffer() {
        val collector = SessionTimelineCollector()
        val player = buildIdlePlayer()

        captureEntry(collector, player)
        captureEntry(collector, player)
        captureEntry(collector, player)

        val first = collector.drain()
        assertEquals("drain() must return all 3 captured entries", 3, first.size)

        val second = collector.drain()
        assertTrue("second drain() must return empty list", second.isEmpty())
    }

    @Test
    fun drain_beforeAnyCapture_returnsEmpty() {
        val collector = SessionTimelineCollector()
        val result = collector.drain()
        assertTrue("drain() before any capture must return empty list", result.isEmpty())
    }

    @Test
    fun clear_emptiesBuffer() {
        val collector = SessionTimelineCollector()
        val player = buildIdlePlayer()

        captureEntry(collector, player)
        captureEntry(collector, player)

        collector.clear()

        val result = collector.drain()
        assertTrue("drain() after clear() must return empty list", result.isEmpty())
    }

    @Test
    fun networkType_isIncludedWhenProvided() {
        val collector = SessionTimelineCollector()
        val player = buildIdlePlayer()

        collector.capture(
            player = player,
            stats = null,
            sessionStartTs = sessionStartTs,
            nowElapsedMs = sessionStartTs,
            nowWallClockMs = System.currentTimeMillis(),
            connectionType = "Wi-Fi",
        )

        val entries = collector.drain()
        assertEquals("Wi-Fi", entries[0].networkType)
    }
}
