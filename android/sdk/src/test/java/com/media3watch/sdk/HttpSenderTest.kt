package com.media3watch.sdk

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
    fun send_success_returnsOk() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(endpointUrl = server.url("/session").toString())

        val result = sender.send("""{"ok":true}""")

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/session", request.path)
        assertEquals("""{"ok":true}""", request.body.readUtf8())
    }

    @Test
    fun send_serverError_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val sender = HttpSender(endpointUrl = server.url("/session").toString())

        val result = sender.send("""{"ok":false}""")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("HTTP 500") == true)
    }

    @Test
    fun send_withApiKey_includesAuthHeader() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val sender = HttpSender(
            endpointUrl = server.url("/session").toString(),
            apiKey = "abc123"
        )

        val result = sender.send("""{"a":1}""")

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("Bearer abc123", request.getHeader("Authorization"))
        assertNotNull(request.getHeader("Content-Type"))
    }
}
