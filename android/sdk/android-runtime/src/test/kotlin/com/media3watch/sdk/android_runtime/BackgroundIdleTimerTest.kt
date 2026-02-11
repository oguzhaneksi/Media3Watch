package com.media3watch.sdk.android_runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive tests for BackgroundIdleTimer using TestCoroutineScheduler.
 * Tests all happy path, edge cases, and failure scenarios per spec.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundIdleTimerTest {

    // Test constants per spec
    private companion object {
        const val TIMEOUT_MINUS_1S = 119_000L
        const val TIMEOUT_PLUS_1S = 121_000L
        const val HALF_TIMEOUT = 60_000L
        const val TEN_SECONDS = 10_000L
        const val TWENTY_SECONDS = 20_000L
    }

    // ============================================================
    // HAPPY PATH TESTS
    // ============================================================

    @Test
    fun timerStartsOnBackgroundWhenIdle() {
        // Test: Timer starts on background when idle
        // App backgrounds with playbackActive=false → timer starts 120s countdown
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            timer.onAppBackgrounded(playbackActive = false)

            assertTrue("Timer should be running", timer.isRunning())
            assertFalse("Timeout should not have fired yet", timeoutFired)
        }
    }

    @Test
    fun timerFiresAfter120Seconds() {
        // Test: Timer fires after 120s
        // Timer starts → advance 120s → BackgroundIdleTimeout event emitted
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(BG_IDLE_END_TIMEOUT_MS)

            assertTrue("Timeout should have fired", timeoutFired)
            assertFalse("Timer should not be running after firing", timer.isRunning())
        }
    }

    @Test
    fun timerCancelsOnForeground() {
        // Test: Timer cancels on foreground
        // Timer running → app foregrounds at 60s → timer cancels, no event emitted
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(HALF_TIMEOUT)
            assertTrue("Timer should be running at 60s", timer.isRunning())

            timer.onAppForegrounded()
            assertFalse("Timer should be cancelled", timer.isRunning())

            advanceTimeBy(HALF_TIMEOUT) // Advance another 60s
            assertFalse("Timeout should not fire after foreground", timeoutFired)
        }
    }

    @Test
    fun timerDoesNotStartWhenPlaying() {
        // Test: Timer does not start when playing
        // App backgrounds with playbackActive=true (isPlaying=true) → timer does NOT start
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            timer.onAppBackgrounded(playbackActive = true)

            assertFalse("Timer should not start when playing", timer.isRunning())
            advanceTimeBy(BG_IDLE_END_TIMEOUT_MS)
            assertFalse("Timeout should not fire", timeoutFired)
        }
    }

    // ============================================================
    // EDGE CASE TESTS
    // ============================================================

    @Test
    fun timerCancelsWhenPlaybackBecomesActiveInBackground() {
        // Test: Timer cancels when playback becomes active in background
        // Timer running at 60s → playback starts (isPlaying=true) → timer cancels
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(HALF_TIMEOUT)
            assertTrue("Timer should be running", timer.isRunning())

            timer.onPlaybackActiveChanged(playbackActive = true)
            assertFalse("Timer should be cancelled", timer.isRunning())

            advanceTimeBy(HALF_TIMEOUT)
            assertFalse("Timeout should not fire", timeoutFired)
        }
    }

    @Test
    fun timerRestartsWhenPlaybackBecomesInactiveInBackground() {
        // Test: Timer restarts when playback becomes inactive in background
        // App backgrounded with playbackActive=true (timer not started) → 
        // playback pauses → timer starts fresh 120s countdown
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            timer.onAppBackgrounded(playbackActive = true)
            assertFalse("Timer should not be running", timer.isRunning())

            advanceTimeBy(HALF_TIMEOUT)
            timer.onPlaybackActiveChanged(playbackActive = false)
            assertTrue("Timer should start", timer.isRunning())

            advanceTimeBy(BG_IDLE_END_TIMEOUT_MS)
            assertTrue("Timeout should fire after 120s from restart", timeoutFired)
        }
    }

    @Test
    fun rapidForegroundBackgroundForeground() {
        // Test: Rapid foreground/background/foreground
        // App backgrounds → timer starts → foregrounds at 10s → backgrounds again at 20s → 
        // timer should restart fresh (not resume from 10s)
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            // First background
            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(TEN_SECONDS)
            assertTrue("Timer should be running", timer.isRunning())

            // Foreground
            timer.onAppForegrounded()
            assertFalse("Timer should be cancelled", timer.isRunning())
            advanceTimeBy(TEN_SECONDS)

            // Second background - should start fresh
            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(TIMEOUT_MINUS_1S)
            assertFalse("Timeout should not fire at 119s (10+10+119 != 120 from restart)", timeoutFired)

            advanceTimeBy(1000L) // Complete 120s from second background
            assertTrue("Timeout should fire after fresh 120s", timeoutFired)
        }
    }

    @Test
    fun timerRestartWhileAlreadyRunning() {
        // Test: Timer restart while already running
        // Timer running at 60s → playback becomes active → playback becomes inactive again → 
        // timer restarts from 0s
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(HALF_TIMEOUT)
            assertTrue("Timer should be running", timer.isRunning())

            timer.onPlaybackActiveChanged(playbackActive = true)
            assertFalse("Timer should be cancelled", timer.isRunning())

            timer.onPlaybackActiveChanged(playbackActive = false)
            assertTrue("Timer should restart", timer.isRunning())

            advanceTimeBy(TIMEOUT_MINUS_1S)
            assertFalse("Timeout should not fire at 119s from restart", timeoutFired)

            advanceTimeBy(1000L)
            assertTrue("Timeout should fire after 120s from restart", timeoutFired)
        }
    }

    @Test
    fun edgeOfTimeout() {
        // Test: Edge of timeout
        // Timer at 119s → foreground → no event, timer at 120s → event fires
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            // Scenario 1: Cancel at 119s
            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(TIMEOUT_MINUS_1S)
            assertFalse("Timeout should not fire at 119s", timeoutFired)
            timer.onAppForegrounded()
            advanceTimeBy(2000L)
            assertFalse("Timeout should not fire after foreground", timeoutFired)
        }

        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            // Scenario 2: Fire at 120s
            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(BG_IDLE_END_TIMEOUT_MS)
            assertTrue("Timeout should fire at exactly 120s", timeoutFired)
        }
    }

    @Test
    fun multipleBackgroundSessions() {
        // Test: Multiple background sessions
        // First background → timer fires → second background → new timer starts (if playbackActive=false)
        runTest {
            var timeoutCount = 0
            val timer = BackgroundIdleTimer(this) { timeoutCount++ }

            // First background session
            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(BG_IDLE_END_TIMEOUT_MS)
            assertEquals("First timeout should fire", 1, timeoutCount)

            // Foreground
            timer.onAppForegrounded()
            advanceTimeBy(TEN_SECONDS)

            // Second background session
            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(BG_IDLE_END_TIMEOUT_MS)
            assertEquals("Second timeout should fire", 2, timeoutCount)
        }
    }

    // ============================================================
    // FAILURE SCENARIO TESTS
    // ============================================================

    @Test
    fun coroutineCancellationSafety() {
        // Test: Coroutine cancellation safety
        // Timer coroutine cancelled mid-countdown → no crash, no event emission
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(HALF_TIMEOUT)

            timer.cancel()
            assertFalse("Timer should be cancelled", timer.isRunning())

            advanceTimeBy(HALF_TIMEOUT)
            assertFalse("Timeout should not fire after explicit cancel", timeoutFired)
        }
    }

    @Test
    fun sessionEndsBeforeTimerFires() {
        // Test: Session ends before timer fires
        // Timer running at 60s → session explicitly ended → timer cancels gracefully
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(HALF_TIMEOUT)
            assertTrue("Timer should be running", timer.isRunning())

            // Session ends
            timer.cancel()
            assertFalse("Timer should be cancelled", timer.isRunning())

            advanceTimeBy(HALF_TIMEOUT)
            assertFalse("Timeout should not fire", timeoutFired)
        }
    }

    @Test
    fun concurrentStateChanges() {
        // Test: Concurrent state changes
        // Rapid playbackActive changes while timer running → timer behaves deterministically (last state wins)
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            timer.onAppBackgrounded(playbackActive = false)
            advanceTimeBy(TEN_SECONDS)

            // Rapid changes
            timer.onPlaybackActiveChanged(playbackActive = true)
            timer.onPlaybackActiveChanged(playbackActive = false)
            timer.onPlaybackActiveChanged(playbackActive = true)
            timer.onPlaybackActiveChanged(playbackActive = false) // Last state: inactive

            // Timer should be running (last state wins)
            assertTrue("Timer should be running (last state inactive)", timer.isRunning())

            advanceTimeBy(BG_IDLE_END_TIMEOUT_MS)
            assertTrue("Timeout should fire", timeoutFired)
        }
    }

    @Test
    fun playbackActiveChangesWhileInForeground() {
        // Test: playbackActive changes while in foreground should be ignored
        runTest {
            var timeoutFired = false
            val timer = BackgroundIdleTimer(this) { timeoutFired = true }

            // Not in background
            timer.onPlaybackActiveChanged(playbackActive = false)
            assertFalse("Timer should not start", timer.isRunning())

            timer.onPlaybackActiveChanged(playbackActive = true)
            assertFalse("Timer should not start", timer.isRunning())
        }
    }
}
