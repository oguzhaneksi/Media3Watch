package com.media3watch.sdk

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        val uploader = TelemetryUploader(sender)

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
        val uploader = TelemetryUploader(sender)

        uploader.upload(sessionId = "test-session-456", payload = """{"error":"test"}""")

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

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
        val uploader = TelemetryUploader(sender, uploadTimeoutMs = 100)

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
        val uploader = TelemetryUploader(sender)

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
        val uploader = TelemetryUploader(sender, uploadTimeoutMs = 1000)

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
        val uploader = TelemetryUploader(sender, uploadTimeoutMs = 300)

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
    fun upload_nonCancellable_completesEvenWhenScopeIsCancelled() = runBlocking {
        // upload() runs under NonCancellable by design — it must complete even when the
        // outer coroutine is cancelled, to avoid leaving the queue in an inconsistent state.
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender)

        // Launch upload, yield so the coroutine starts, then cancel the outer job.
        val job = launch {
            uploader.upload(sessionId = "test-cancel", payload = """{"cancel":"test"}""")
        }
        // Yield once: lets the launched coroutine enter upload() / NonCancellable context.
        delay(50)
        job.cancel()

        // The request MUST still arrive at the server because upload() is NonCancellable.
        val request = server.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull("Non-cancellable upload should still reach the server", request)
        assertEquals("POST", request!!.method)
    }

    // ── enableLogging = false: log suppression ─────────────────────────────────

    @Test
    fun upload_success_loggingDisabled_doesNotLogSuccessMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, enableLogging = false)

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
        val uploader = TelemetryUploader(sender, enableLogging = false)

        uploader.upload(sessionId = "silent-session-fail", payload = """{"test":"data"}""")

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        val failLog = ShadowLog.getLogsForTag(LogUtils.TAG)
            ?.find { it.msg.contains("session_report_failed") }
        assertNull("Should not log upload failure when enableLogging=false", failLog)
    }

    // ── Offline resilience (FileQueue) — retryable errors ─────────────────────

    @Test
    fun upload_retryableFailure_503_persistsPayloadToQueue() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        val uploader = TelemetryUploader(sender, fileQueue = queue)

        uploader.upload(sessionId = "offline-session", payload = """{"o":1}""")

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        assertEquals("Retryable (503) payload should be persisted", 1, queue.size())
        val entries = queue.peekAll()
        assertEquals("offline-session", entries[0].sessionId)
        assertEquals("""{"o":1}""", entries[0].payload)
    }

    @Test
    fun upload_retryableFailure_500_persistsPayloadToQueue() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        val uploader = TelemetryUploader(sender, fileQueue = queue)

        uploader.upload(sessionId = "offline-500", payload = """{"o":1}""")

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        assertEquals("Retryable (500) payload should be persisted", 1, queue.size())
    }

    // ── Offline resilience (FileQueue) — non-retryable errors ─────────────────

    @Test
    fun upload_nonRetryableFailure_400_doesNotPersistPayloadToQueue() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        val uploader = TelemetryUploader(sender, fileQueue = queue)

        uploader.upload(sessionId = "bad-payload-session", payload = """{"invalid":"schema"}""")

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        assertEquals("400 Bad Request (client error) should NOT be queued", 0, queue.size())
    }

    @Test
    fun upload_nonRetryableFailure_401_doesNotPersistPayloadToQueue() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        val uploader = TelemetryUploader(sender, fileQueue = queue)

        uploader.upload(sessionId = "wrong-key-session", payload = """{"o":1}""")

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        assertEquals("401 Unauthorized (bad API key) should NOT be queued", 0, queue.size())
    }

    @Test
    fun upload_nonRetryableFailure_400_logsDroppedWarning() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        val uploader = TelemetryUploader(sender, fileQueue = queue)

        uploader.upload(sessionId = "dropped-session", payload = """{"bad":"data"}""")

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        val logs = ShadowLog.getLogsForTag(LogUtils.TAG)
        val droppedLog = logs?.find {
            it.msg.contains("session_report_dropped") &&
            it.msg.contains("sessionId=dropped-session") &&
            it.msg.contains("non-retryable")
        }
        assertNotNull("Should log a 'dropped' warning for non-retryable errors", droppedLog)
        assertEquals(android.util.Log.WARN, droppedLog?.type)
    }

    // ── Success path clears queue ──────────────────────────────────────────────

    @Test
    fun upload_success_removesStaleQueueEntry() = runBlocking {
        // Pre-seed queue with a stale entry for this session.
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        queue.enqueue("session-stale", """{"old":true}""")
        assertEquals(1, queue.size())

        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, fileQueue = queue)

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
        val uploader = TelemetryUploader(sender, fileQueue = queue)

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
        // First upload fails (retryable) — payload persisted.
        server.enqueue(MockResponse().setResponseCode(500))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        val uploader = TelemetryUploader(sender, fileQueue = queue)

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

    // ── flushPending ──────────────────────────────────────────────────────────

    @Test
    fun flushPending_drainsQueueOnSuccess() = runBlocking {
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        queue.enqueue("prev-1", """{"p":1}""")
        queue.enqueue("prev-2", """{"p":2}""")

        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, fileQueue = queue)

        uploader.flushPending()

        // Wait for both network calls.
        server.takeRequest(2, TimeUnit.SECONDS)
        server.takeRequest(2, TimeUnit.SECONDS)
        delay(200)

        assertEquals("Queue should be empty after successful flush", 0, queue.size())
    }

    @Test
    fun flushPending_retryableFailure_retainsEntries() = runBlocking {
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        queue.enqueue("prev-fail", """{"f":1}""")

        server.enqueue(MockResponse().setResponseCode(503))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, fileQueue = queue)

        uploader.flushPending()

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        assertEquals("Entry should remain in queue on retryable failure (503)", 1, queue.size())
    }

    @Test
    fun flushPending_nonRetryableFailure_removesEntryFromQueue() = runBlocking {
        // A queued payload responds with 400 — it should be purged, not left forever.
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        queue.enqueue("stale-bad", """{"bad":"data"}""")

        server.enqueue(MockResponse().setResponseCode(400))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, fileQueue = queue)

        uploader.flushPending()

        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        assertEquals(
            "Non-retryable response (400) during flush should remove the queued entry",
            0,
            queue.size()
        )
    }

    @Test
    fun flushPending_concurrentCalls_eachPayloadSentOnlyOnce() = runBlocking {
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        queue.enqueue("concurrent-1", """{"c":1}""")
        queue.enqueue("concurrent-2", """{"c":2}""")

        // Enqueue exactly 2 responses — if payloads are sent more than twice total the test fails.
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))

        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, fileQueue = queue)

        // Launch two concurrent flushes.
        val flushJob1 = launch { uploader.flushPending() }
        val flushJob2 = launch { uploader.flushPending() }
        flushJob1.join()
        flushJob2.join()

        // Only 2 requests should have been sent — one per unique payload.
        assertEquals("Each payload should be sent exactly once", 2, server.requestCount)
        assertEquals("Queue should be empty after flush", 0, queue.size())
    }

    @Test
    fun upload_withNullFileQueue_fireAndForget_doesNotCrash() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        // No fileQueue — classic fire-and-forget.
        val uploader = TelemetryUploader(sender, fileQueue = null)

        // Should complete without exception even on failure.
        uploader.upload(sessionId = "no-queue-session", payload = """{"x":1}""")
        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)
        // No queue to assert on — just verifying no crash.
    }

    @Test
    fun upload_withNullFileQueue_nonRetryable_doesNotCrash() = runBlocking {
        // Even without a queue, non-retryable errors should be dropped (not retried) and must not crash.
        server.enqueue(MockResponse().setResponseCode(400))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val uploader = TelemetryUploader(sender, fileQueue = null)

        uploader.upload(sessionId = "no-queue-400", payload = """{"x":1}""")
        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)
    }

    @Test
    fun upload_retryableThenNonRetryable_removesStaleQueueEntry() = runBlocking {
        // First upload: retryable failure → payload is persisted to queue.
        server.enqueue(MockResponse().setResponseCode(503))
        val sender = HttpSender(endpointUrl = server.url("/sessions").toString())
        val queue = FileQueue(dir = createTempDirectory("queue").toFile())
        val uploader = TelemetryUploader(sender, fileQueue = queue)

        uploader.upload(sessionId = "session-doomed", payload = """{"v":1}""")
        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)
        assertEquals("Entry should be queued after retryable failure", 1, queue.size())

        // Second upload: non-retryable failure → stale queue entry must be purged.
        server.enqueue(MockResponse().setResponseCode(400))
        uploader.upload(sessionId = "session-doomed", payload = """{"v":2}""")
        server.takeRequest(2, TimeUnit.SECONDS)
        delay(100)

        assertEquals(
            "Stale queue entry should be removed after non-retryable failure",
            0,
            queue.size()
        )
    }
}
