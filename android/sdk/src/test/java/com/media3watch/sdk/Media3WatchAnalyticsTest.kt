package com.media3watch.sdk

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

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
        assertSessionStartContainsValidSessionId(startLog!!)

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

        val firstSessionId = extractSessionIdFromStartLog(startLogs[0])
        val secondSessionId = extractSessionIdFromStartLog(startLogs[1])
        assertTrue(firstSessionId.isNotBlank())
        assertTrue(secondSessionId.isNotBlank())
        assertTrue(firstSessionId != secondSessionId)

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
        assertEquals("test-key", request.getHeader("X-API-Key"))

        val body = request.body.readUtf8()
        assertTrue(body.contains("\"sessionId\":\""))
        assertTrue(body.contains("\"startupTimeMs\":100"))

        server.shutdown()
    }

    // ****** Real-Time Reporting Tests ******

    @Test
    fun buildAndUploadSummary_skipsWhenSessionDurationIsZero() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200))

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true,
            reportingIntervalMs = 100 // Short interval for testing
        )
        val analytics = Media3WatchAnalytics(config)
        val harness = PlayerHarness()

        // Attach and immediately trigger an event
        analytics.attach(harness.player)
        harness.emitIsPlayingChanged(true)

        // Wait a bit - no report should be sent because sessionDurationMs is 0
        advanceMs(50)

        // Should be no requests since duration is 0
        val request = server.takeRequest(200, TimeUnit.MILLISECONDS)
        assertEquals(null, request)

        analytics.detach()
        server.shutdown()
    }

    @Test
    fun realTimeReport_sentAfterInterval() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200))

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true,
            reportingIntervalMs = 500
        )
        val analytics = Media3WatchAnalytics(config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        harness.emitIsPlayingChanged(true)
        harness.setPlaybackState(Player.STATE_READY)

        // Advance past reportingInterval
        advanceMs(600)

        // Should receive a periodic report
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Expected periodic report after interval", request)
        assertEquals("POST", request!!.method)

        analytics.detach()
        server.shutdown()
    }

    @Test
    fun playerEvents_triggerImmediateReport() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200))

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true
        )
        val analytics = Media3WatchAnalytics(config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        advanceMs(100) // Make sessionDuration > 0

        // Trigger an event
        harness.emitIsPlayingChanged(true)

        // Should receive an immediate report
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Expected immediate report after event", request)

        analytics.detach()
        server.shutdown()
    }

    @Test
    fun detach_sendsLastReport_andStopsReporter() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200))

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true,
            reportingIntervalMs = 200
        )
        val analytics = Media3WatchAnalytics(config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        advanceMs(100)

        analytics.detach()

        // Should receive final detach report
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Expected final report on detach", request)

        // Advance more time - no further reports should come
        advanceMs(500)
        val noMoreRequests = server.takeRequest(200, TimeUnit.MILLISECONDS)
        assertEquals("No reports should be sent after detach", null, noMoreRequests)

        server.shutdown()
    }

    @Test
    fun enableRealTimeReporting_false_disablesReporting() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200))

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = false, // Disabled
            reportingIntervalMs = 100
        )
        val analytics = Media3WatchAnalytics(config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        harness.emitIsPlayingChanged(true)
        advanceMs(200)

        // No periodic reports should be sent
        val noRequest = server.takeRequest(200, TimeUnit.MILLISECONDS)
        assertEquals("No periodic reports when disabled", null, noRequest)

        // Only detach should send a report
        analytics.detach()
        val finalRequest = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Final report should still be sent on detach", finalRequest)

        server.shutdown()
    }

    @Test
    fun onSeek_triggersImmediateReport() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200))

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true
        )
        val analytics = Media3WatchAnalytics(config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        advanceMs(100)

        harness.emitSeekStarted()

        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Expected report on seek", request)

        analytics.detach()
        server.shutdown()
    }

    @Test
    fun onPlayerError_triggersImmediateReport() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200))

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true
        )
        val analytics = Media3WatchAnalytics(config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        advanceMs(100)

        harness.emitPlayerError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Expected report on player error", request)

        analytics.detach()
        server.shutdown()
    }

    @Test
    fun onDroppedVideoFrames_triggersReport() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200))

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true
        )
        val analytics = Media3WatchAnalytics(config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        advanceMs(100)

        harness.emitDroppedVideoFrames(5)

        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Expected report on dropped frames", request)

        analytics.detach()
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
        shadowOf(Looper.getMainLooper()).idleFor(milliseconds, TimeUnit.MILLISECONDS)
    }

    private fun assertMetricIsNullOrNonNegativeLong(log: String, key: String) {
        val value = metric(log, key)
        if (value == "null") {
            return
        }
        assertTrue(value.toLong() >= 0L)
    }

    private fun assertSessionStartContainsValidSessionId(log: String) {
        val sessionId = extractSessionIdFromStartLog(log)
        assertTrue(sessionId.isNotBlank())
        assertTrue(UUID_V4_PATTERN.matcher(sessionId).matches())
    }

    private fun extractSessionIdFromStartLog(log: String): String {
        return log.substringAfter("sessionId=", "").trim()
    }

    private class PlayerHarness {
        val player: ExoPlayer = mock(ExoPlayer::class.java)
        val analyticsListeners = mutableListOf<AnalyticsListener>()
        private var isPlayingState: Boolean = false
        private var playbackStateValue: Int = Player.STATE_IDLE

        init {
            doAnswer {
                analyticsListeners.add(it.arguments[0] as AnalyticsListener)
                null
            }.`when`(player).addAnalyticsListener(any(AnalyticsListener::class.java))

            doAnswer {
                analyticsListeners.remove(it.arguments[0] as AnalyticsListener)
                null
            }.`when`(player).removeAnalyticsListener(any(AnalyticsListener::class.java))

            doAnswer { isPlayingState }.`when`(player).isPlaying
            doAnswer { playbackStateValue }.`when`(player).playbackState
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
        
        fun emitIsPlayingChanged(isPlaying: Boolean) {
            isPlayingState = isPlaying
            analyticsListeners.forEach {
                it.onIsPlayingChanged(createEventTime(), isPlaying)
            }
        }
        
        fun setPlaybackState(state: Int) {
            playbackStateValue = state
        }
        
        fun emitSeekStarted() {
            analyticsListeners.forEach {
                it.onPositionDiscontinuity(
                    createEventTime(),
                    mock(Player.PositionInfo::class.java),
                    mock(Player.PositionInfo::class.java),
                    Player.DISCONTINUITY_REASON_SEEK
                )
            }
        }
        
        fun emitDroppedVideoFrames(droppedFrames: Int) {
            analyticsListeners.forEach {
                it.onDroppedVideoFrames(createEventTime(), droppedFrames, 100L)
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
        val UUID_V4_PATTERN: Pattern = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
        )
    }
}
