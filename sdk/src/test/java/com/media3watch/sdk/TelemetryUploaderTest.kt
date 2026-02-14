package com.media3watch.sdk

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
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
    fun upload_success_sendsPayloadToServer() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender)

        uploader.upload(sessionId = 123, payload = """{"test":"data"}""")

        // Wait for async upload to complete (using real time since TelemetryUploader uses Dispatchers.IO)
        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull("Request should have been sent", request)
        assertEquals("POST", request!!.method)
        assertEquals("""{"test":"data"}""", request.body.readUtf8())
    }

    @Test
    fun upload_serverError_logsWarning() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender)

        uploader.upload(sessionId = 456, payload = """{"error":"test"}""")

        // Wait for async upload to complete and log the error
        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100) // Give time for logging to complete

        val logs = ShadowLog.getLogsForTag(LogUtils.TAG)
        val failureLog = logs?.find { it.msg.contains("session_upload_failed") && it.msg.contains("sessionId=456") }
        assertNotNull("Should log upload failure", failureLog)
        assertEquals(android.util.Log.WARN, failureLog?.type)
    }

    @Test
    fun upload_timeout_logsTimeoutWarning() = runBlocking {
        // Enqueue a response with a delay longer than the timeout
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(20, TimeUnit.SECONDS))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, uploadTimeoutMs = 100)

        uploader.upload(sessionId = 789, payload = """{"slow":"data"}""")

        // Wait for timeout to occur
        delay(300) // Wait longer than uploadTimeoutMs

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
    fun upload_multipleConcurrentUploads_allComplete() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender)

        uploader.upload(sessionId = 1, payload = """{"session":1}""")
        uploader.upload(sessionId = 2, payload = """{"session":2}""")
        uploader.upload(sessionId = 3, payload = """{"session":3}""")

        // Wait for all async uploads to complete
        server.takeRequest(2, TimeUnit.SECONDS)
        server.takeRequest(2, TimeUnit.SECONDS)
        server.takeRequest(2, TimeUnit.SECONDS)

        assertEquals("All three requests should have been sent", 3, server.requestCount)
    }

    @Test
    fun shutdown_cancelsScope_butDoesNotAffectInFlightUploads() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(50, TimeUnit.MILLISECONDS))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender)

        uploader.upload(sessionId = 999, payload = """{"shutdown":"test"}""")

        // Give upload time to start, then shutdown
        delay(10)
        uploader.shutdown()

        // Since the coroutine was already launched, it should still complete
        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull("In-flight request should complete even after shutdown", request)
    }

    @Test
    fun shutdown_preventsNewUploads() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender)

        uploader.shutdown()
        
        // Try to upload after shutdown
        uploader.upload(sessionId = 888, payload = """{"after":"shutdown"}""")

        // Give time to verify no upload occurs
        delay(100)

        // Request should not be sent because scope was cancelled
        assertEquals("No request should be sent after shutdown", 0, server.requestCount)
    }

    @Test
    fun upload_unexpectedException_logsException() = runBlocking {
        // Use invalid URL to cause an exception
        val sender = HttpSender(endpointUrl = "http://invalid-host-that-does-not-exist-12345.com/sessions")
        val uploader = TelemetryUploader(sender, uploadTimeoutMs = 1000)

        uploader.upload(sessionId = 555, payload = """{"exception":"test"}""")

        // Wait for async upload to fail
        delay(1500)

        val logs = ShadowLog.getLogsForTag(LogUtils.TAG)
        val exceptionLog = logs?.find { 
            it.msg.contains("session_upload_failed") && 
            it.msg.contains("sessionId=555")
        }
        assertNotNull("Should log exception failure", exceptionLog)
        assertEquals(android.util.Log.WARN, exceptionLog?.type)
    }

    @Test
    fun upload_customTimeout_respectsConfiguredValue() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(150, TimeUnit.MILLISECONDS))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, uploadTimeoutMs = 300)

        uploader.upload(sessionId = 111, payload = """{"custom":"timeout"}""")

        // Wait for request to complete within custom timeout
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull("Request should complete within custom timeout", request)
        
        // Give time for any potential logging
        delay(100)
        
        // Verify no timeout error was logged
        val logs = ShadowLog.getLogsForTag(LogUtils.TAG)
        val timeoutLog = logs?.find { it.msg.contains("(timeout)") }
        assertNull("Should not log timeout", timeoutLog)
    }
}
