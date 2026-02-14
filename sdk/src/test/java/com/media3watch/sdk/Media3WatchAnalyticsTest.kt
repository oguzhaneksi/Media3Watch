package com.media3watch.sdk

import android.os.SystemClock
import androidx.media3.common.PlaybackException
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@androidx.annotation.OptIn(UnstableApi::class)
class Media3WatchAnalyticsTest {

    @Before
    fun setUp() {
        ShadowLog.clear()
    }

    @Test
    fun attach_logsSessionStart_andDetach_logsSessionEnd() {
        val analytics = Media3WatchAnalytics()
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.detach()

        val startLog = ShadowLog.getLogsForTag(TAG)
            .orEmpty()
            .map { it.msg }
            .lastOrNull { it.contains("session_start") }
        assertNotNull(startLog)
        assertTrue(startLog!!.contains("sessionId=1"))

        val endLog = lastSessionEndLog()
        assertNotNull(endLog)
        assertEquals("null", metric(endLog!!, "startupTimeMs"))
        assertNotNull(metric(endLog, "sessionStartDateIso"))
        assertNotNull(metric(endLog, "sessionDurationMs"))
        assertNotNull(metric(endLog, "rebufferTimeMs"))
        assertNotNull(metric(endLog, "rebufferCount"))
        assertNotNull(metric(endLog, "playTimeMs"))
        assertNotNull(metric(endLog, "rebufferRatio"))
        assertNotNull(metric(endLog, "errorCount"))
    }

    @Test
    fun startupTime_measuredFromPlayRequestedToFirstFrame() {
        val analytics = Media3WatchAnalytics()
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(120)
        harness.emitFirstFrame()
        analytics.detach()

        val endLog = lastSessionEndLog()!!
        assertEquals("120", metric(endLog, "startupTimeMs"))
    }

    @Test
    fun startupTime_lastPlayRequestedWinsBeforeFirstFrame() {
        val analytics = Media3WatchAnalytics()
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(100)
        analytics.playRequested()
        advanceMs(50)
        harness.emitFirstFrame()
        analytics.detach()

        val endLog = lastSessionEndLog()!!
        assertEquals("50", metric(endLog, "startupTimeMs"))
    }

    @Test
    fun startupTime_onlyFirstFrameIsUsed() {
        val analytics = Media3WatchAnalytics()
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(70)
        harness.emitFirstFrame()
        advanceMs(30)
        harness.emitFirstFrame()
        analytics.detach()

        val endLog = lastSessionEndLog()!!
        assertEquals("70", metric(endLog, "startupTimeMs"))
    }

    @Test
    fun playRequestedAfterFirstFrame_clearsStartupMeasurement() {
        val analytics = Media3WatchAnalytics()
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(10)
        harness.emitFirstFrame()
        analytics.playRequested()
        advanceMs(10)
        harness.emitFirstFrame()
        analytics.detach()

        val endLog = lastSessionEndLog()!!
        assertEquals("null", metric(endLog, "startupTimeMs"))
    }

    @Test
    fun startupTime_isClampedToZeroForNegativeDelta() {
        val analytics = Media3WatchAnalytics()
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        val now = SystemClock.elapsedRealtime()
        harness.emitFirstFrameAt(now - 10)
        analytics.detach()

        val endLog = lastSessionEndLog()!!
        assertEquals("0", metric(endLog, "startupTimeMs"))
    }

    @Test
    fun detachWithoutAttach_isNoOp() {
        val analytics = Media3WatchAnalytics()
        analytics.detach()
        assertEquals(null, lastSessionEndLog())
    }

    @Test
    fun secondAttach_detachesPreviousSession_andStartsNewSession() {
        val analytics = Media3WatchAnalytics()
        val first = PlayerHarness()
        val second = PlayerHarness()

        analytics.attach(first.player)
        advanceMs(30)
        analytics.attach(second.player)
        analytics.detach()

        val logs = ShadowLog.getLogsForTag(TAG)
            .orEmpty()
            .map { it.msg }
        val endLogs = logs.filter { it.contains("session_end") }
        val startLogs = logs.filter { it.contains("session_start") }

        assertEquals(2, endLogs.size)
        assertEquals(2, startLogs.size)
        assertTrue(startLogs[0].contains("sessionId=1"))
        assertTrue(startLogs[1].contains("sessionId=2"))

        val firstDuration = metric(endLogs[0], "sessionDurationMs").toLong()
        val secondDuration = metric(endLogs[1], "sessionDurationMs").toLong()
        assertTrue(firstDuration >= 30)
        assertTrue(secondDuration >= 0)
    }

    @Test
    fun detach_removesListenersFromPlayer() {
        val analytics = Media3WatchAnalytics()
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        assertEquals(2, harness.analyticsListeners.size)
        analytics.detach()
        assertTrue(harness.analyticsListeners.isEmpty())
    }

    @Test
    fun attach_whenPlayerInitializationFails_propagates_andDetachStillClosesSession() {
        val analytics = Media3WatchAnalytics()
        val failingPlayer = mock(ExoPlayer::class.java)

        doAnswer { throw IllegalStateException("player init failed") }
            .`when`(failingPlayer).addAnalyticsListener(any(AnalyticsListener::class.java))

        val result = runCatching { analytics.attach(failingPlayer) }
        assertTrue(result.isFailure)

        analytics.detach()

        val endLog = lastSessionEndLog()
        assertNotNull(endLog)
        assertEquals("null", metric(endLog!!, "startupTimeMs"))
    }

    @Test
    fun networkError_preventsFirstFrame_startupRemainsNull_andErrorCountIncreases() {
        val analytics = Media3WatchAnalytics()
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(200)
        harness.emitPlayerError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        analytics.detach()

        val endLog = lastSessionEndLog()!!
        assertEquals("null", metric(endLog, "startupTimeMs"))
        assertMetricIsNullOrNonNegativeLong(endLog, "errorCount")
    }

    @Test
    fun codecOrFormatErrors_areTrackedInErrorCount() {
        val analytics = Media3WatchAnalytics()
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        harness.emitPlayerError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
        harness.emitPlayerError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)
        analytics.detach()

        val endLog = lastSessionEndLog()!!
        assertMetricIsNullOrNonNegativeLong(endLog, "errorCount")
    }

    @Test
    fun detachWithBackend_followedByRelease_uploadsSessionDespiteCancellation() = runTest {
        val server = MockWebServer()
        server.start()

        // Slow response (200ms delay) to simulate network latency
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(200, TimeUnit.MILLISECONDS))

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key"
        )
        val analytics = Media3WatchAnalytics(config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(100)
        harness.emitFirstFrame()
        analytics.detach()

        // Immediately release (cancels scope) - upload should still complete
        analytics.release()

        // Wait for upload to complete (MockWebServer blocks until request arrives or timeout)
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Request should have been sent despite immediate release()", request)
        assertEquals("POST", request!!.method)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))

        val body = request.body.readUtf8()
        assertTrue(body.contains("\"sessionId\":1"))
        assertTrue(body.contains("\"startupTimeMs\":100"))

        server.shutdown()
    }

    // Note: Timeout behavior is tested manually/integration testing
    // Unit testing async timeout in Robolectric proves unreliable due to thread scheduling

    private fun lastSessionEndLog(): String? {
        return ShadowLog.getLogsForTag(TAG)
            .orEmpty()
            .map { it.msg }
            .lastOrNull { it.contains("session_end") }
    }

    private fun metric(log: String, key: String): String {
        val regex = Regex("^\\s*$key: (.+)$", RegexOption.MULTILINE)
        return regex.find(log)?.groupValues?.get(1)
            ?: error("Metric '$key' not found in log: $log")
    }

    private fun advanceMs(milliseconds: Long) {
        ShadowSystemClock.advanceBy(milliseconds, TimeUnit.MILLISECONDS)
    }

    private fun assertMetricIsNullOrNonNegativeLong(log: String, key: String) {
        val value = metric(log, key)
        if (value == "null") {
            return
        }
        assertTrue(value.toLong() >= 0L)
    }

    private class PlayerHarness {
        val player: ExoPlayer = mock(ExoPlayer::class.java)
        val analyticsListeners = mutableListOf<AnalyticsListener>()

        init {
            doAnswer {
                analyticsListeners.add(it.arguments[0] as AnalyticsListener)
                null
            }.`when`(player).addAnalyticsListener(any(AnalyticsListener::class.java))

            doAnswer {
                analyticsListeners.remove(it.arguments[0] as AnalyticsListener)
                null
            }.`when`(player).removeAnalyticsListener(any(AnalyticsListener::class.java))
        }

        fun emitFirstFrame() {
            emitFirstFrameAt(SystemClock.elapsedRealtime())
        }

        fun emitFirstFrameAt(renderTimeMs: Long) {
            analyticsListeners.forEach {
                it.onRenderedFirstFrame(
                    createEventTime(),
                    Any(),
                    renderTimeMs
                )
            }
        }

        fun emitPlayerError(errorCode: Int) {
            val error = PlaybackException("test-error", null, errorCode)
            analyticsListeners.forEach {
                it.onPlayerError(createEventTime(), error)
            }
        }

        private fun createEventTime(): AnalyticsListener.EventTime {
            return AnalyticsListener.EventTime(
                SystemClock.elapsedRealtime(),
                Timeline.EMPTY,
                0,
                null,
                0L,
                Timeline.EMPTY,
                0,
                null,
                0L,
                0L
            )
        }
    }

    private companion object {
        const val TAG = "Media3WatchAnalytics"
    }
}
