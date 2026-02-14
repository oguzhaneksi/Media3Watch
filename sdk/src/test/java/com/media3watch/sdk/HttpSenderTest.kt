package com.media3watch.sdk

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val sender = HttpSender(server.url("/sessions").toString())
        val result = sender.send(SAMPLE_JSON)

        assertTrue(result.isSuccess)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertEquals(SAMPLE_JSON, request.body.readUtf8())
    }

    @Test
    fun send_serverError_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val sender = HttpSender(server.url("/sessions").toString())
        val result = sender.send(SAMPLE_JSON)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun send_withApiKey_includesAuthHeader() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val sender = HttpSender(
            endpointUrl = server.url("/sessions").toString(),
            apiKey = "test-api-key-123"
        )
        val result = sender.send(SAMPLE_JSON)

        assertTrue(result.isSuccess)

        val request = server.takeRequest()
        assertEquals("Bearer test-api-key-123", request.getHeader("Authorization"))
    }

    @Test
    fun send_withoutApiKey_noAuthHeader() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val sender = HttpSender(server.url("/sessions").toString())
        sender.send(SAMPLE_JSON)

        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
    }

    private companion object {
        const val SAMPLE_JSON = """{"sessionId":1,"sessionDurationMs":45000}"""
    }
}
