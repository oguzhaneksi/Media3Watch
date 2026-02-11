package com.media3watch.sdk.android_runtime

import com.media3watch.sdk.schema.PlaybackEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundIdleTimerTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val timeoutEvents = mutableListOf<PlaybackEvent.BackgroundIdleTimeout>()
    private lateinit var timer: BackgroundIdleTimer

    @BeforeEach
    fun setup() {
        timeoutEvents.clear()
        timer =
            BackgroundIdleTimer(testScope) { event ->
                timeoutEvents.add(event)
            }
    }

    @Test
    fun `timer fires after 120 seconds when playbackActive is false at background time`() =
        runTest(testDispatcher) {
            timer.onAppBackgrounded(playbackActive = false)

            advanceTimeBy(119_999)
            assertTrue(timeoutEvents.isEmpty())

            advanceTimeBy(1)
            assertEquals(1, timeoutEvents.size)
        }

    @Test
    fun `timer does not fire if playbackActive is true at background time`() =
        runTest(testDispatcher) {
            timer.onAppBackgrounded(playbackActive = true)

            advanceTimeBy(BackgroundIdleTimer.BG_IDLE_END_TIMEOUT_MS + 1000)
            assertTrue(timeoutEvents.isEmpty())
        }

    @Test
    fun `timer cancels when app foregrounds before timeout`() =
        runTest(testDispatcher) {
            timer.onAppBackgrounded(playbackActive = false)

            advanceTimeBy(60_000)
            timer.onAppForegrounded()

            advanceTimeBy(BackgroundIdleTimer.BG_IDLE_END_TIMEOUT_MS)
            assertTrue(timeoutEvents.isEmpty())
        }

    @Test
    fun `timer cancels when playback becomes active while in background`() =
        runTest(testDispatcher) {
            timer.onAppBackgrounded(playbackActive = false)

            advanceTimeBy(60_000)
            timer.onPlaybackActiveChanged(playbackActive = true)

            advanceTimeBy(BackgroundIdleTimer.BG_IDLE_END_TIMEOUT_MS)
            assertTrue(timeoutEvents.isEmpty())
        }

    @Test
    fun `timer restarts when playback becomes inactive while still in background`() =
        runTest(testDispatcher) {
            timer.onAppBackgrounded(playbackActive = true)

            advanceTimeBy(60_000)
            assertTrue(timeoutEvents.isEmpty())

            timer.onPlaybackActiveChanged(playbackActive = false)

            advanceTimeBy(119_999)
            assertTrue(timeoutEvents.isEmpty())

            advanceTimeBy(1)
            assertEquals(1, timeoutEvents.size)
        }

    @Test
    fun `timer does not start if playback state changes while in foreground`() =
        runTest(testDispatcher) {
            timer.onPlaybackActiveChanged(playbackActive = false)

            advanceTimeBy(BackgroundIdleTimer.BG_IDLE_END_TIMEOUT_MS + 1000)
            assertTrue(timeoutEvents.isEmpty())
        }

    @Test
    fun `timer resets when user backgrounds at 119s, foregrounds, then backgrounds again`() =
        runTest(testDispatcher) {
            timer.onAppBackgrounded(playbackActive = false)

            advanceTimeBy(119_000)
            assertTrue(timeoutEvents.isEmpty())

            timer.onAppForegrounded()
            advanceTimeBy(10_000)

            timer.onAppBackgrounded(playbackActive = false)

            advanceTimeBy(119_999)
            assertTrue(timeoutEvents.isEmpty())

            advanceTimeBy(1)
            assertEquals(1, timeoutEvents.size)
        }

    @Test
    fun `multiple timer starts cancel previous timer`() =
        runTest(testDispatcher) {
            timer.onAppBackgrounded(playbackActive = false)

            advanceTimeBy(60_000)
            timer.onPlaybackActiveChanged(playbackActive = false)

            advanceTimeBy(119_999)
            assertTrue(timeoutEvents.isEmpty())

            advanceTimeBy(1)
            assertEquals(1, timeoutEvents.size)
        }

    @Test
    fun `playback starts in background cancels timer`() =
        runTest(testDispatcher) {
            timer.onAppBackgrounded(playbackActive = false)

            advanceTimeBy(30_000)
            assertTrue(timeoutEvents.isEmpty())

            timer.onPlaybackActiveChanged(playbackActive = true)

            advanceTimeBy(BackgroundIdleTimer.BG_IDLE_END_TIMEOUT_MS)
            assertTrue(timeoutEvents.isEmpty())
        }

    @Test
    fun `timeout event has valid timestamp`() =
        runTest(testDispatcher) {
            val startTime = System.currentTimeMillis()
            timer.onAppBackgrounded(playbackActive = false)

            advanceTimeBy(BackgroundIdleTimer.BG_IDLE_END_TIMEOUT_MS)

            assertEquals(1, timeoutEvents.size)
            assertTrue(timeoutEvents[0].timestamp >= startTime)
        }

    @Test
    fun `constant BG_IDLE_END_TIMEOUT_MS is 120000`() {
        assertEquals(120_000L, BackgroundIdleTimer.BG_IDLE_END_TIMEOUT_MS)
    }
}
