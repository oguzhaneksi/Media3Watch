package com.media3watch.sdk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import kotlin.io.path.createTempDirectory


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
        
        // Use the implicit TestScope from runTest
        val uploader = TelemetryUploader(sender, coroutineScope = this)

        // Track if CancellationException was thrown
        var cancellationExceptionThrown = false
        
        // Launch upload in a supervised job that can catch the rethrown exception
        val job = launch {
            try {
                uploader.upload(sessionId = "test-cancel", payload = """{"cancel":"test"}""")
            } catch (e: CancellationException) {
                cancellationExceptionThrown = true
                throw e  // Rethrow to maintain cancellation semantics
            }
        }
        
        // Advance time to mid-upload (halfway through the 500ms response delay), then cancel
        testScheduler.advanceTimeBy(250)
        job.cancel()
        
        // Process all pending coroutines
        testScheduler.advanceUntilIdle()
        
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

    // ── enableLogging = false: log suppression ─────────────────────────────────

    @Test
    fun upload_success_loggingDisabled_doesNotLogSuccessMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, coroutineScope = this, enableLogging = false)

        uploader.upload(sessionId = "silent-session-ok", payload = """{"test":"data"}""")

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        val successLog = ShadowLog.getLogsForTag(LogUtils.TAG)
            ?.find { it.msg.contains("session_report_success") }
        assertNull("Should not log upload success when enableLogging=false", successLog)
    }

    @Test
    fun upload_failure_loggingDisabled_doesNotLogFailureMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, coroutineScope = this, enableLogging = false)

        uploader.upload(sessionId = "silent-session-fail", payload = """{"test":"data"}""")

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        val failLog = ShadowLog.getLogsForTag(LogUtils.TAG)
            ?.find { it.msg.contains("session_report_failed") }
        assertNull("Should not log upload failure when enableLogging=false", failLog)
    }

    // ── Offline resilience (FileQueue) ─────────────────────────────────────────

    @Test
    fun upload_failure_persistsPayloadToQueue() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        val uploader = TelemetryUploader(sender, coroutineScope = this, fileQueue = queue)

        uploader.upload(sessionId = "offline-session", payload = """{"o":1}""")

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        assertEquals("Failed payload should be persisted", 1, queue.size())
        val entries = queue.peekAll()
        assertEquals("offline-session", entries[0].sessionId)
        assertEquals("""{"o":1}""", entries[0].payload)
    }

    @Test
    fun upload_success_removesStaleQueueEntry() = runBlocking {
        // Pre-seed queue with a stale entry for this session.
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        queue.enqueue("session-stale", """{"old":true}""")
        assertEquals(1, queue.size())

        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, coroutineScope = this, fileQueue = queue)

        uploader.upload(sessionId = "session-stale", payload = """{"new":true}""")

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(200)

        assertEquals("Stale queue entry should be removed on successful upload", 0, queue.size())
    }

    @Test
    fun upload_success_clearsStaleAndFlushesOtherPendingEntries() = runBlocking {
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        queue.enqueue("session-live", """{"old":"stale"}""")
        queue.enqueue("session-prev", """{"from":"previous"}""")
        assertEquals(2, queue.size())

        server.enqueue(MockResponse().setResponseCode(200)) // current upload
        server.enqueue(MockResponse().setResponseCode(200)) // flush pending

        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, coroutineScope = this, fileQueue = queue)

        uploader.upload(sessionId = "session-live", payload = """{"new":"live"}""")

        val firstRequest = server.takeRequest(2, TimeUnit.SECONDS)
        val secondRequest = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(firstRequest)
        assertNotNull(secondRequest)

        val bodies = listOf(firstRequest!!.body.readUtf8(), secondRequest!!.body.readUtf8())
        assertTrue("Should send current payload", bodies.contains("""{"new":"live"}"""))
        assertTrue("Should flush previous pending payload", bodies.contains("""{"from":"previous"}"""))

        delay(200)
        assertEquals("Both stale and pending entries should be cleared", 0, queue.size())
    }

    @Test
    fun upload_failureThenSuccess_upsertsThenClears() = runBlocking {
        // First upload fails — payload persisted.
        server.enqueue(MockResponse().setResponseCode(500))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        val uploader = TelemetryUploader(sender, coroutineScope = this, fileQueue = queue)

        uploader.upload(sessionId = "s1", payload = """{"v":1}""")
        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)
        assertEquals(1, queue.size())

        // Second upload (newer payload) succeeds — queue entry removed.
        server.enqueue(MockResponse().setResponseCode(200))
        uploader.upload(sessionId = "s1", payload = """{"v":2}""")
        server.takeRequest(2, TimeUnit.SECONDS)
        delay(200)

        assertEquals("Queue should be empty after successful upload", 0, queue.size())
    }

    @Test
    fun flushPending_drainsQueueOnSuccess() = runBlocking {
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        queue.enqueue("prev-1", """{"p":1}""")
        queue.enqueue("prev-2", """{"p":2}""")

        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, coroutineScope = this, fileQueue = queue)

        uploader.flushPending()

        // Wait for both network calls.
        server.takeRequest(2, TimeUnit.SECONDS)
        server.takeRequest(2, TimeUnit.SECONDS)
        delay(200)

        assertEquals("Queue should be empty after successful flush", 0, queue.size())
    }

    @Test
    fun flushPending_continuedFailure_retainsEntries() = runBlocking {
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        queue.enqueue("prev-fail", """{"f":1}""")

        server.enqueue(MockResponse().setResponseCode(503))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, coroutineScope = this, fileQueue = queue)

        uploader.flushPending()

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        assertEquals("Entry should remain in queue on continued failure", 1, queue.size())
    }

    @Test
    fun upload_withNullFileQueue_fireAndForget_doesNotCrash() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        // No fileQueue — classic fire-and-forget.
        val uploader = TelemetryUploader(sender, coroutineScope = this, fileQueue = null)

        // Should complete without exception even on failure.
        uploader.upload(sessionId = "no-queue-session", payload = """{"x":1}""")
        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)
        // No queue to assert on — just verifying no crash.
    }
}
