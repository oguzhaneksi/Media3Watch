package com.media3watch.sdk

import com.media3watch.sdk.model.SendResult
import com.media3watch.sdk.transport.HttpSender
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpSenderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun send_success_returnsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/session").toString())

        val result = sender.send("""{"ok":true}""")

        assertTrue(result is SendResult.Success)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/session", request.path)
        assertEquals("""{"ok":true}""", request.body.readUtf8())
    }

    @Test
    fun send_withApiKey_includesAuthHeader() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(
            endpointUrl = server.url("/session").toString(),
            apiKey = "abc123"
        )

        val result = sender.send("""{"a":1}""")

        assertTrue(result is SendResult.Success)
        val request = server.takeRequest()
        assertEquals("abc123", request.getHeader("X-API-Key"))
        assertNotNull(request.getHeader("Content-Type"))
    }

    // ── Retryable failures (5xx, 408, 429) ────────────────────────────────────

    @Test
    fun send_500_returnsRetryableFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val sender = HttpSender(endpointUrl = server.url("/session").toString())

        val result = sender.send("""{"ok":false}""")

        assertTrue("500 should be retryable", result is SendResult.RetryableFailure)
        assertTrue(
            (result as SendResult.RetryableFailure).cause.message?.contains("HTTP 500") == true
        )
    }

    @Test
    fun send_503_returnsRetryableFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val sender = HttpSender(endpointUrl = server.url("/session").toString())

        val result = sender.send("""{"ok":false}""")

        assertTrue("503 should be retryable", result is SendResult.RetryableFailure)
    }

    @Test
    fun send_408_returnsRetryableFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(408))
        val sender = HttpSender(endpointUrl = server.url("/session").toString())

        val result = sender.send("""{"ok":false}""")

        assertTrue("408 Request Timeout should be retryable", result is SendResult.RetryableFailure)
    }

    @Test
    fun send_429_returnsRetryableFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val sender = HttpSender(endpointUrl = server.url("/session").toString())

        val result = sender.send("""{"ok":false}""")

        assertTrue("429 Too Many Requests should be retryable", result is SendResult.RetryableFailure)
    }

    // ── Non-retryable failures (4xx except 408/429) ───────────────────────────

    @Test
    fun send_400_returnsNonRetryableFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))
        val sender = HttpSender(endpointUrl = server.url("/session").toString())

        val result = sender.send("""{"bad":"payload"}""")

        assertTrue("400 Bad Request should be non-retryable", result is SendResult.NonRetryableFailure)
        assertTrue(
            (result as SendResult.NonRetryableFailure).cause.message?.contains("HTTP 400") == true
        )
    }

    @Test
    fun send_401_returnsNonRetryableFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val sender = HttpSender(endpointUrl = server.url("/session").toString())

        val result = sender.send("""{"ok":false}""")

        assertTrue("401 Unauthorized should be non-retryable", result is SendResult.NonRetryableFailure)
    }

    @Test
    fun send_403_returnsNonRetryableFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val sender = HttpSender(endpointUrl = server.url("/session").toString())

        val result = sender.send("""{"ok":false}""")

        assertTrue("403 Forbidden should be non-retryable", result is SendResult.NonRetryableFailure)
    }

    @Test
    fun send_404_returnsNonRetryableFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val sender = HttpSender(endpointUrl = server.url("/session").toString())

        val result = sender.send("""{"ok":false}""")

        assertTrue("404 Not Found should be non-retryable", result is SendResult.NonRetryableFailure)
    }

    @Test
    fun send_422_returnsNonRetryableFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(422))
        val sender = HttpSender(endpointUrl = server.url("/session").toString())

        val result = sender.send("""{"ok":false}""")

        assertTrue("422 Unprocessable Entity should be non-retryable", result is SendResult.NonRetryableFailure)
    }
}
