package com.media3watch.sdk

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryUploaderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun upload_success_sendsPayloadToServer() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender)

        uploader.upload(sessionId = 123, payload = """{"test":"data"}""")

        // Wait for async upload to complete
        advanceTimeBy(100)
        testScheduler.advanceUntilIdle()

        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Request should have been sent", request)
        assertEquals("POST", request!!.method)
        assertEquals("""{"test":"data"}""", request.body.readUtf8())
    }

    @Test
    fun upload_serverError_logsWarning() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender)

        uploader.upload(sessionId = 456, payload = """{"error":"test"}""")

        // Wait for async upload to complete
        advanceTimeBy(100)
        testScheduler.advanceUntilIdle()

        val logs = ShadowLog.getLogsForTag(LogUtils.TAG)
        val failureLog = logs?.find { it.msg.contains("session_upload_failed") && it.msg.contains("sessionId=456") }
        assertNotNull("Should log upload failure", failureLog)
        assertEquals(android.util.Log.WARN, failureLog?.type)
    }

    @Test
    fun upload_timeout_logsTimeoutWarning() = runTest {
        // Enqueue a response with a delay longer than the timeout
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(20, TimeUnit.SECONDS))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, uploadTimeoutMs = 100)

        uploader.upload(sessionId = 789, payload = """{"slow":"data"}""")

        // Advance time past the timeout
        advanceTimeBy(150)
        testScheduler.advanceUntilIdle()

        val logs = ShadowLog.getLogsForTag(LogUtils.TAG)
        val timeoutLog = logs?.find { 
            it.msg.contains("session_upload_failed") && 
            it.msg.contains("sessionId=789") &&
            it.msg.contains("(timeout)")
        }
        assertNotNull("Should log timeout failure", timeoutLog)
        assertEquals(android.util.Log.WARN, timeoutLog?.type)
    }

    @Test
    fun upload_multipleConcurrentUploads_allComplete() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender)

        uploader.upload(sessionId = 1, payload = """{"session":1}""")
        uploader.upload(sessionId = 2, payload = """{"session":2}""")
        uploader.upload(sessionId = 3, payload = """{"session":3}""")

        // Wait for all async uploads to complete
        advanceTimeBy(200)
        testScheduler.advanceUntilIdle()

        assertEquals("All three requests should have been sent", 3, server.requestCount)
    }

    @Test
    fun shutdown_cancelsScope_butDoesNotAffectInFlightUploads() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(50, TimeUnit.MILLISECONDS))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender)

        uploader.upload(sessionId = 999, payload = """{"shutdown":"test"}""")

        // Immediately shutdown after starting upload
        advanceTimeBy(10)
        uploader.shutdown()

        // Even after shutdown, the in-flight request should complete
        advanceTimeBy(100)
        testScheduler.advanceUntilIdle()

        // Since the coroutine was already launched, it should still complete
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("In-flight request should complete even after shutdown", request)
    }

    @Test
    fun shutdown_preventsNewUploads() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender)

        uploader.shutdown()
        
        // Try to upload after shutdown
        uploader.upload(sessionId = 888, payload = """{"after":"shutdown"}""")

        advanceTimeBy(100)
        testScheduler.advanceUntilIdle()

        // Request should not be sent because scope was cancelled
        assertEquals("No request should be sent after shutdown", 0, server.requestCount)
    }

    @Test
    fun upload_unexpectedException_logsException() = runTest {
        // Use invalid URL to cause an exception
        val sender = HttpSender(endpointUrl = "http://invalid-host-that-does-not-exist-12345.com/sessions")
        val uploader = TelemetryUploader(sender, uploadTimeoutMs = 1000)

        uploader.upload(sessionId = 555, payload = """{"exception":"test"}""")

        // Wait for async upload to fail
        advanceTimeBy(1500)
        testScheduler.advanceUntilIdle()

        val logs = ShadowLog.getLogsForTag(LogUtils.TAG)
        val exceptionLog = logs?.find { 
            it.msg.contains("session_upload_failed") && 
            it.msg.contains("sessionId=555")
        }
        assertNotNull("Should log exception failure", exceptionLog)
        assertEquals(android.util.Log.WARN, exceptionLog?.type)
    }

    @Test
    fun upload_customTimeout_respectsConfiguredValue() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(150, TimeUnit.MILLISECONDS))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, uploadTimeoutMs = 200)

        uploader.upload(sessionId = 111, payload = """{"custom":"timeout"}""")

        // Advance just past the response delay but within custom timeout
        advanceTimeBy(160)
        testScheduler.advanceUntilIdle()

        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Request should complete within custom timeout", request)
        
        // Verify no timeout error was logged
        val logs = ShadowLog.getLogsForTag(LogUtils.TAG)
        val timeoutLog = logs?.find { it.msg.contains("(timeout)") }
        assertTrue("Should not log timeout", timeoutLog == null)
    }
}
