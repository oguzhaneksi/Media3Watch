package com.media3watch.sdk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        val uploader = TelemetryUploader(sender, coroutineScope = this)

        uploader.upload(sessionId = "test-session-123", payload = """{"test":"data"}""")

        // Launch is on Default; network I/O is on IO inside HttpSender — wait for the real thread.
        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull("Request should have been sent", request)
        assertEquals("POST", request!!.method)
        assertEquals("""{"test":"data"}""", request.body.readUtf8())
    }

    @Test
    fun upload_serverError_logsWarning() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, coroutineScope = this)

        uploader.upload(sessionId = "test-session-456", payload = """{"error":"test"}""")

        // Wait for async upload to complete and log the error
        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100) // Give time for logging to complete

        val logs = ShadowLog.getLogsForTag(LogUtils.TAG)
        val failureLog = logs?.find { it.msg.contains("session_report_failed") && it.msg.contains("sessionId=test-session-456") }
        assertNotNull("Should log upload failure", failureLog)
        assertEquals(android.util.Log.WARN, failureLog?.type)
    }

    @Test
    fun upload_timeout_logsTimeoutWarning() = runBlocking {
        // Never respond so the HTTP call/upload timeout is guaranteed to trigger.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, uploadTimeoutMs = 100, coroutineScope = this)

        uploader.upload(sessionId = "test-session-789", payload = """{"slow":"data"}""")

        // Wait for timeout to occur
        delay(600) // Wait comfortably longer than uploadTimeoutMs

        val logs = ShadowLog.getLogsForTag(LogUtils.TAG)
        val timeoutLog = logs?.find { 
            it.msg.contains("session_report_failed") && 
            it.msg.contains("sessionId=test-session-789") &&
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
        val uploader = TelemetryUploader(sender, coroutineScope = this)

        uploader.upload(sessionId = "session-1", payload = """{"session":1}""")
        uploader.upload(sessionId = "session-2", payload = """{"session":2}""")
        uploader.upload(sessionId = "session-3", payload = """{"session":3}""")

        // Wait for all async uploads to complete
        server.takeRequest(2, TimeUnit.SECONDS)
        server.takeRequest(2, TimeUnit.SECONDS)
        server.takeRequest(2, TimeUnit.SECONDS)

        assertEquals("All three requests should have been sent", 3, server.requestCount)
    }

    @Test
    fun upload_unexpectedException_logsException() = runBlocking {
        // Use invalid URL to cause an exception
        val sender = HttpSender(endpointUrl = "http://invalid-host-that-does-not-exist-12345.com/sessions")
        val uploader = TelemetryUploader(sender, uploadTimeoutMs = 1000, coroutineScope = this)

        uploader.upload(sessionId = "session-555", payload = """{"exception":"test"}""")

        // Wait for async upload to fail
        delay(1500)

        val logs = ShadowLog.getLogsForTag(LogUtils.TAG)
        val exceptionLog = logs?.find { 
            it.msg.contains("session_report_failed") && 
            it.msg.contains("sessionId=session-555")
        }
        assertNotNull("Should log exception failure", exceptionLog)
        assertEquals(android.util.Log.WARN, exceptionLog?.type)
    }

    @Test
    fun upload_customTimeout_respectsConfiguredValue() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(150, TimeUnit.MILLISECONDS))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, uploadTimeoutMs = 300, coroutineScope = this)

        uploader.upload(sessionId = "session-111", payload = """{"custom":"timeout"}""")

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

    @Test
    fun upload_scopeCancelled_rethrowsCancellationException() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBodyDelay(500, TimeUnit.MILLISECONDS))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        
        // Create a test scope that we can cancel
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val uploader = TelemetryUploader(sender, coroutineScope = testScope)

        // Track if CancellationException was thrown
        var cancellationExceptionThrown = false
        
        // Launch upload in a supervised job that can catch the rethrown exception
        val job = testScope.launch {
            try {
                uploader.upload(sessionId = "test-cancel", payload = """{"cancel":"test"}""")
            } catch (e: CancellationException) {
                cancellationExceptionThrown = true
                throw e  // Rethrow to maintain cancellation semantics
            }
        }
        
        // Advance time a bit, then cancel the scope
        testScheduler.advanceTimeBy(50)
        testScope.cancel()
        
        // Advance until all coroutines are done
        try {
            advanceUntilIdle()
        } catch (e: CancellationException) {
            // Expected when scope is cancelled
        }
        
        // Verify that CancellationException was thrown (and thus rethrown by our code)
        assertTrue("CancellationException should have been rethrown", cancellationExceptionThrown)
        
        // Also verify that CancellationException was NOT logged
        val logs = ShadowLog.getLogsForTag(LogUtils.TAG)
        val cancellationLog = logs?.find { 
            it.msg.contains("test-cancel") && 
            (it.throwable is CancellationException || it.msg.contains("CancellationException"))
        }
        assertNull("CancellationException should not be logged", cancellationLog)
    }
}
