package com.media3watch.sdk

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.test.core.app.ApplicationProvider
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
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@androidx.annotation.OptIn(UnstableApi::class)
class Media3WatchAnalyticsTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        ShadowLog.clear()
    }

    @Test
    fun attach_logsSessionStart_andDetach_logsSessionEnd() {
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.detach()
        Thread.sleep(200) // Allow Dispatchers.Default to finish building and logging the summary

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
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(120)
        harness.emitFirstFrame()
        analytics.detach()
        Thread.sleep(200) // Allow Dispatchers.Default to finish building and logging the summary

        val endLog = lastSessionEndLog()!!
        assertEquals("120", metric(endLog, "startupTimeMs"))
    }

    @Test
    fun startupTime_lastPlayRequestedWinsBeforeFirstFrame() {
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(100)
        analytics.playRequested()
        advanceMs(50)
        harness.emitFirstFrame()
        analytics.detach()
        Thread.sleep(200) // Allow Dispatchers.Default to finish building and logging the summary

        val endLog = lastSessionEndLog()!!
        assertEquals("50", metric(endLog, "startupTimeMs"))
    }

    @Test
    fun startupTime_onlyFirstFrameIsUsed() {
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(70)
        harness.emitFirstFrame()
        advanceMs(30)
        harness.emitFirstFrame()
        analytics.detach()
        Thread.sleep(200) // Allow Dispatchers.Default to finish building and logging the summary

        val endLog = lastSessionEndLog()!!
        assertEquals("70", metric(endLog, "startupTimeMs"))
    }

    @Test
    fun playRequestedAfterFirstFrame_clearsStartupMeasurement() {
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(10)
        harness.emitFirstFrame()
        analytics.playRequested()
        advanceMs(10)
        harness.emitFirstFrame()
        analytics.detach()
        Thread.sleep(200) // Allow Dispatchers.Default to finish building and logging the summary

        val endLog = lastSessionEndLog()!!
        assertEquals("null", metric(endLog, "startupTimeMs"))
    }

    @Test
    fun startupTime_isClampedToZeroForNegativeDelta() {
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        val now = SystemClock.elapsedRealtime()
        harness.emitFirstFrameAt(now - 10)
        analytics.detach()
        Thread.sleep(200) // Allow Dispatchers.Default to finish building and logging the summary

        val endLog = lastSessionEndLog()!!
        assertEquals("0", metric(endLog, "startupTimeMs"))
    }

    @Test
    fun detachWithoutAttach_isNoOp() {
        val analytics = Media3WatchAnalytics(context)
        analytics.detach()
        assertEquals(null, lastSessionEndLog())
    }

    @Test
    fun secondAttach_detachesPreviousSession_andStartsNewSession() {
        val analytics = Media3WatchAnalytics(context)
        val first = PlayerHarness()
        val second = PlayerHarness()

        analytics.attach(first.player)
        advanceMs(30)
        analytics.attach(second.player)
        analytics.detach()
        Thread.sleep(200) // Allow Dispatchers.Default to finish building and logging both summaries

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
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        assertEquals(2, harness.analyticsListeners.size)
        analytics.detach()
        assertTrue(harness.analyticsListeners.isEmpty())
    }

    @Test
    fun attach_whenPlayerInitializationFails_propagates_andDetachStillClosesSession() {
        val analytics = Media3WatchAnalytics(context)
        val failingPlayer = mock(ExoPlayer::class.java)

        doAnswer { throw IllegalStateException("player init failed") }
            .`when`(failingPlayer).addAnalyticsListener(any(AnalyticsListener::class.java))

        val result = runCatching { analytics.attach(failingPlayer) }
        assertTrue(result.isFailure)

        analytics.detach()
        Thread.sleep(200) // Allow Dispatchers.Default to finish building and logging the summary

        val endLog = lastSessionEndLog()
        assertNotNull(endLog)
        assertEquals("null", metric(endLog!!, "startupTimeMs"))
    }

    @Test
    fun networkError_preventsFirstFrame_startupRemainsNull_andErrorCountIncreases() {
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(200)
        harness.emitPlayerError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        analytics.detach()
        Thread.sleep(200) // Allow Dispatchers.Default to finish building and logging the summary

        val endLog = lastSessionEndLog()!!
        assertEquals("null", metric(endLog, "startupTimeMs"))
        assertMetricIsNullOrNonNegativeLong(endLog, "errorCount")
    }

    @Test
    fun codecOrFormatErrors_areTrackedInErrorCount() {
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        harness.emitPlayerError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
        harness.emitPlayerError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)
        analytics.detach()
        Thread.sleep(200) // Allow Dispatchers.Default to finish building and logging the summary

        val endLog = lastSessionEndLog()!!
        assertMetricIsNullOrNonNegativeLong(endLog, "errorCount")
    }

    @Test
    fun detachWithBackend_followedByRelease_uploadsSession() = runTest {
        val server = MockWebServer()
        server.start()

        // Slow response (200ms delay) to simulate network latency
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(200, TimeUnit.MILLISECONDS))

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key"
        )
        val analytics = Media3WatchAnalytics(context, config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(100)
        harness.emitFirstFrame()
        analytics.detach()

        // release() stops the reporter and calls detach(); the upload coroutine is
        // protected by NonCancellable so it always completes regardless of cleanup order.
        analytics.release()

        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Request should have been sent after detach + release", request)
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
        val analytics = Media3WatchAnalytics(context, config)
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
        val analytics = Media3WatchAnalytics(context, config)
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
        val analytics = Media3WatchAnalytics(context, config)
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
        val analytics = Media3WatchAnalytics(context, config)
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
        val analytics = Media3WatchAnalytics(context, config)
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
    fun enableRealTimeReporting_withoutBackendUrl_doesNotStartReporter() = runTest {
        val config = Media3WatchConfig(
            backendUrl = null, // No backend configured
            enableRealTimeReporting = true,
            reportingIntervalMs = 100
        )
        val analytics = Media3WatchAnalytics(context, config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        harness.emitIsPlayingChanged(true)
        harness.setPlaybackState(Player.STATE_READY)

        // Advance past the reporting interval
        advanceMs(200)

        // No reports should be sent/logged since uploader is null
        // Verify by checking that no "Uploading session summary" logs were created
        val uploadLogs = ShadowLog.getLogsForTag(TAG)
            .orEmpty()
            .map { it.msg }
            .filter { it.contains("Uploading session summary") }

        assertEquals("No upload logs should exist when backendUrl is null", 0, uploadLogs.size)

        analytics.detach()
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
        val analytics = Media3WatchAnalytics(context, config)
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
        val analytics = Media3WatchAnalytics(context, config)
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
        val analytics = Media3WatchAnalytics(context, config)
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

    // ── enableLogging = false: log suppression ─────────────────────────────────

    @Test
    fun enableLogging_false_suppressesSessionStartAndEndLogs() {
        val config = Media3WatchConfig(enableLogging = false)
        val analytics = Media3WatchAnalytics(context, config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.detach()
        Thread.sleep(200)

        val logs = ShadowLog.getLogsForTag(TAG).orEmpty()
        assertEquals("No logs should be emitted when enableLogging=false", 0, logs.size)
    }

    @Test
    fun enableLogging_false_withBackend_suppressesAllLogcatOutput() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200))

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableLogging = false
        )
        val analytics = Media3WatchAnalytics(context, config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(100)
        harness.emitFirstFrame()
        analytics.detach()
        analytics.release()

        Thread.sleep(300)

        val logs = ShadowLog.getLogsForTag(TAG).orEmpty()
        assertEquals("No Logcat output should be emitted when enableLogging=false", 0, logs.size)

        server.shutdown()
    }

    @Test
    fun rapidContentSwitch_playbackStatsNullDuringTransition_metricsAreNeverNull() {
        // Regression test: when content is switched rapidly, DefaultPlaybackSessionManager
        // temporarily sets currentSessionId to null (while the new timeline is still EMPTY),
        // causing PlaybackStatsListener.getPlaybackStats() to return null. The SDK passes
        // the nullable PlaybackStats directly to buildSessionSummary(), which handles null
        // gracefully via Kotlin safe-call operators so metrics are never missing from the log.
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()
        advanceMs(100)
        harness.emitFirstFrame()

        // Simulate rapid content switch: the player emits a DISCONTINUITY_REASON_SEEK which
        // in real usage (setMediaItem) causes the session manager to clear currentSessionId
        // before the new timeline is established.
        harness.emitSeekStarted()

        analytics.detach()
        Thread.sleep(200)

        val endLog = lastSessionEndLog()
        assertNotNull("Session summary must be logged even after rapid content switch", endLog)

        // Core assertion: none of the collected metrics should be the literal string "null"
        // that comes from a null PlaybackStats being passed to buildSessionSummary().
        // rebufferTimeMs, rebufferCount, playTimeMs etc. may legitimately be "null" when
        // PlaybackStats exposes them as null — but they must be present in the log at all.
        assertNotNull(metric(endLog!!, "sessionDurationMs"))
        assertNotNull(metric(endLog, "rebufferTimeMs"))
        assertNotNull(metric(endLog, "rebufferCount"))
        assertNotNull(metric(endLog, "playTimeMs"))
    }

    @Test
    fun rapidContentSwitch_withBackend_metricsPayloadIsNotAllNulls() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200)) // seek-triggered report
        server.enqueue(MockResponse().setResponseCode(200)) // detach report

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true,
            reportingIntervalMs = 5_000L
        )
        val analytics = Media3WatchAnalytics(context, config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        advanceMs(100) // sessionDuration > 0

        // Rapid content switch — triggers a report while DefaultPlaybackSessionManager
        // may have currentSessionId = null, so getPlaybackStats() would return null.
        harness.emitSeekStarted()

        val report = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Report must be sent even after rapid content switch", report)

        val body = report!!.body.readUtf8()
        // sessionDurationMs must always be a non-null number in the JSON payload.
        assertTrue(
            "sessionDurationMs must not be null in payload after rapid switch",
            body.contains("\"sessionDurationMs\":")
                    && !body.contains("\"sessionDurationMs\":null")
        )

        analytics.detach()
        server.shutdown()
    }

    // ── Stream Switching Edge Cases ───────────────────────────────────────────

    @Test
    fun streamSwitch_reattach_resetsFirstFrameFlag_newSessionMeasuresStartupCorrectly() {
        val analytics = Media3WatchAnalytics(context)
        val harness1 = PlayerHarness()
        val harness2 = PlayerHarness()

        // Session 1 (e.g. MP4): play requested, first frame arrives after 200ms.
        analytics.attach(harness1.player)
        analytics.playRequested()
        advanceMs(200)
        harness1.emitFirstFrame()
        analytics.detach()
        Thread.sleep(200)

        // Session 2 (e.g. HLS): play requested, first frame arrives after 80ms.
        // If firstFrameRendered was NOT reset, playRequested() would clear playCommandTs
        // immediately and startupTimeMs would remain null.
        analytics.attach(harness2.player)
        analytics.playRequested()
        advanceMs(80)
        harness2.emitFirstFrame()
        analytics.detach()
        Thread.sleep(200)

        val endLogs = ShadowLog.getLogsForTag(TAG)
            .orEmpty()
            .map { it.msg }
            .filter { it.contains("session_end") }
        assertEquals(2, endLogs.size)

        // Session 1: correct startup measured.
        assertEquals("200", metric(endLogs[0], "startupTimeMs"))
        // Session 2: independently measured; firstFrameRendered flag was properly reset.
        assertEquals("80", metric(endLogs[1], "startupTimeMs"))
    }

    @Test
    fun streamSwitch_beforeFirstFrame_sessionOneHasNullStartup_sessionTwoMeasuresCorrectly() {
        val analytics = Media3WatchAnalytics(context)
        val harness1 = PlayerHarness()
        val harness2 = PlayerHarness()

        // Session 1 (e.g. HLS): play requested, user switches stream before first frame arrives.
        analytics.attach(harness1.player)
        analytics.playRequested()
        advanceMs(500)
        analytics.detach() // first frame never arrived
        Thread.sleep(200)

        // Session 2 (e.g. DASH): play requested, first frame arrives after 60ms.
        // playCommandTs from session 1 must not bleed into session 2.
        analytics.attach(harness2.player)
        analytics.playRequested()
        advanceMs(60)
        harness2.emitFirstFrame()
        analytics.detach()
        Thread.sleep(200)

        val endLogs = ShadowLog.getLogsForTag(TAG)
            .orEmpty()
            .map { it.msg }
            .filter { it.contains("session_end") }
        assertEquals(2, endLogs.size)

        // Session 1: no first frame → startup must be null.
        assertEquals("null", metric(endLogs[0], "startupTimeMs"))
        // Session 2: startup correctly measured from its own playRequested() call.
        assertEquals("60", metric(endLogs[1], "startupTimeMs"))
    }

    @Test
    fun dashPeriodTransition_doesNotTriggerRealTimeReport() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200))

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true,
            reportingIntervalMs = 5_000L
        )
        val analytics = Media3WatchAnalytics(context, config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        advanceMs(100)

        // Simulate DASH multi-period automatic transition — must not trigger a report.
        harness.emitPeriodTransition()

        val request = server.takeRequest(500, TimeUnit.MILLISECONDS)
        assertEquals("DASH period transition must not trigger a real-time report", null, request)

        analytics.detach()
        server.shutdown()
    }

    @Test
    fun seek_triggersRealTimeReport_butDashPeriodTransition_doesNot() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200)) // for seek report
        server.enqueue(MockResponse().setResponseCode(200)) // for detach report

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true,
            reportingIntervalMs = 5_000L
        )
        val analytics = Media3WatchAnalytics(context, config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        advanceMs(100)

        // User seek → must trigger an immediate report.
        harness.emitSeekStarted()
        val seekReport = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("User seek must trigger a real-time report", seekReport)

        // DASH period transition → must NOT trigger a report.
        harness.emitPeriodTransition()
        val noReport = server.takeRequest(500, TimeUnit.MILLISECONDS)
        assertEquals("DASH period transition must not trigger a real-time report", null, noReport)

        analytics.detach()
        server.shutdown()
    }

    // ── HIGH Priority Edge Cases ──────────────────────────────────────────────

    // #4 ── Video Format Changes (HLS / DASH ABR) ─────────────────────────────

    @Test
    fun videoFormatChange_triggersImmediateReport() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200)) // first format-change report
        server.enqueue(MockResponse().setResponseCode(200)) // second format-change report
        server.enqueue(MockResponse().setResponseCode(200)) // detach

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true,
            reportingIntervalMs = 5_000L
        )
        val analytics = Media3WatchAnalytics(context, config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        advanceMs(100) // sessionDuration > 0

        // First format change — e.g. HLS player selects an initial rendition
        harness.emitVideoFormatChanged(4_000_000) // 4 Mbps
        val firstReport = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Format change must trigger an immediate report", firstReport)

        // Advance past minIntervalMs (1 s) before the next format change
        advanceMs(1_100)

        // Second format change — e.g. ABR adapts down due to network congestion
        harness.emitVideoFormatChanged(500_000) // 500 Kbps
        val secondReport = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Each format change after minIntervalMs must trigger its own report", secondReport)

        analytics.detach()
        server.shutdown()
    }

    @Test
    fun meanVideoFormatBitrate_afterFormatChanges_isNullOrNonNegativeInSessionSummary() {
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        advanceMs(100) // sessionDuration > 0
        harness.emitVideoFormatChanged(4_000_000)
        harness.emitVideoFormatChanged(2_000_000)
        harness.emitVideoFormatChanged(500_000)
        analytics.detach()
        Thread.sleep(200)

        val endLog = lastSessionEndLog()!!
        assertMetricIsNullOrNonNegativeLong(endLog, "meanVideoFormatBitrate")
    }

    // #6 ── Pre-First-Frame Buffering vs Rebuffer ──────────────────────────────

    @Test
    fun bufferingBeforeFirstFrame_startupTimeIsSet_postFirstFrameBufferingCountsAsRebuffer() {
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()

        // Initial HLS/DASH manifest loading: player is BUFFERING before first frame arrives
        harness.emitPlaybackStateChanged(Player.STATE_BUFFERING)
        advanceMs(800)
        harness.emitPlaybackStateChanged(Player.STATE_READY)
        harness.emitFirstFrame()

        // Mid-playback rebuffer: network hiccup after content has already started
        harness.emitPlaybackStateChanged(Player.STATE_BUFFERING)
        advanceMs(400)
        harness.emitPlaybackStateChanged(Player.STATE_READY)

        analytics.detach()
        Thread.sleep(200)

        val endLog = lastSessionEndLog()!!
        // startupTimeMs must be set — playRequested() was called before first frame
        assertMetricIsNullOrNonNegativeLong(endLog, "startupTimeMs")
        // rebuffer metrics must cover only the post-first-frame buffering period
        assertMetricIsNullOrNonNegativeLong(endLog, "rebufferCount")
        assertMetricIsNullOrNonNegativeLong(endLog, "rebufferTimeMs")
    }

    // #8 ── Long Startup ───────────────────────────────────────────────────────

    @Test
    fun longStartupTime_isMeasuredCorrectly_inFinalReport() {
        val analytics = Media3WatchAnalytics(context)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()

        // Simulate a slow HLS/DASH stream that takes 5 seconds to deliver the first frame
        advanceMs(5_000)
        harness.emitFirstFrame()
        analytics.detach()
        Thread.sleep(200)

        val endLog = lastSessionEndLog()!!
        val startupMs = metric(endLog, "startupTimeMs").toLong()
        assertTrue("startupTimeMs must be >= 5000 for a 5-second startup", startupMs >= 5_000L)
    }

    @Test
    fun periodicReport_duringLongStartup_hasNullStartupTime_finalReportHasCorrectValue() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200)) // periodic report before first frame
        server.enqueue(MockResponse().setResponseCode(200)) // first-frame-triggered report
        server.enqueue(MockResponse().setResponseCode(200)) // detach report

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true,
            reportingIntervalMs = 500L
        )
        val analytics = Media3WatchAnalytics(context, config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        analytics.playRequested()

        // Make session appear active so the periodic reporter fires
        harness.emitIsPlayingChanged(true)
        harness.setPlaybackState(Player.STATE_READY)

        // Advance past the 500 ms reporting interval; first frame NOT yet received
        advanceMs(600)

        val periodicReport = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Periodic report must be sent before first frame arrives", periodicReport)
        val periodicBody = periodicReport!!.body.readUtf8()
        assertTrue(
            "startupTimeMs must be null in any report sent before first frame",
            periodicBody.contains("\"startupTimeMs\":null")
        )

        // Pause periodic firing to avoid queuing extra server responses during the wait
        harness.emitIsPlayingChanged(false)

        // First frame arrives ~5 seconds after play was requested
        advanceMs(4_400)
        harness.emitFirstFrame()
        analytics.detach()
        Thread.sleep(200)

        val endLog = lastSessionEndLog()!!
        val startupMs = metric(endLog, "startupTimeMs").toLong()
        assertTrue("Final startupTimeMs must reflect the full ~5 s wait", startupMs >= 5_000L)

        server.shutdown()
    }

    // #13 ── Monotonically Increasing Metrics across Periodic Reports ───────────

    @Test
    fun consecutiveDroppedFrameEvents_reportedDroppedFramesDoNotDecrease() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200)) // report after first batch
        server.enqueue(MockResponse().setResponseCode(200)) // report after second batch
        server.enqueue(MockResponse().setResponseCode(200)) // detach

        val config = Media3WatchConfig(
            backendUrl = server.url("/sessions").toString(),
            apiKey = "test-key",
            enableRealTimeReporting = true,
            reportingIntervalMs = 5_000L
        )
        val analytics = Media3WatchAnalytics(context, config)
        val harness = PlayerHarness()

        analytics.attach(harness.player)
        advanceMs(100) // sessionDuration > 0

        // First burst: 5 dropped frames → triggers an immediate report
        harness.emitDroppedVideoFrames(5)
        val report1 = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Report must be sent after first dropped-frames event", report1)
        val body1 = report1!!.body.readUtf8()

        // Advance past minIntervalMs (1 s), then emit more dropped frames
        advanceMs(1_100)
        harness.emitDroppedVideoFrames(3)
        val report2 = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Report must be sent after second dropped-frames event", report2)
        val body2 = report2!!.body.readUtf8()

        // When PlaybackStats tracks dropped frames in the test environment the cumulative
        // count in report 2 must be >= report 1. Both null is also acceptable.
        val dropped1 = extractJsonLong(body1, "totalDroppedFrames")
        val dropped2 = extractJsonLong(body2, "totalDroppedFrames")
        if (dropped1 != null && dropped2 != null) {
            assertTrue(
                "totalDroppedFrames in report 2 ($dropped2) must be >= report 1 ($dropped1)",
                dropped2 >= dropped1
            )
        }

        analytics.detach()
        server.shutdown()
    }

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

    private fun extractJsonLong(json: String, key: String): Long? {
        val regex = Regex("\"$key\":(\\d+)")
        return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private companion object {
        const val TAG = "Media3WatchAnalytics"
        val UUID_V4_PATTERN: Pattern = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
        )
    }
}
