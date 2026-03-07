package com.media3watch

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * API-layer tests that run entirely in-memory via [FakeSessionRepository].
 * No database or Docker required.
 *
 * Tests that exercise the actual SQL upsert / retention logic live in
 * [SessionRepositoryIntegrationTest] and require a running PostgreSQL instance.
 */
class SessionIngestionTest {

    private val testApiKey = "dev-key"

    private fun testApp(block: suspend ApplicationTestBuilder.(FakeSessionRepository) -> Unit) {
        val fakeRepo = FakeSessionRepository()
        testApplication {
            application { module(repository = fakeRepo) }
            block(fakeRepo)
        }
    }

    // ── Persistence verification ───────────────────────────────────────────────

    @Test
    fun `test session is persisted to database`() = testApp { fakeRepo ->
        val testSessionId = UUID.randomUUID().toString()
        val currentTime = System.currentTimeMillis()

        val payload = """
            {
              "sessionId": "$testSessionId",
              "timestamp": $currentTime,
              "sessionStartDateIso": "2026-02-15T10:00:00.000Z",
              "sessionDurationMs": 45000,
              "startupTimeMs": 450,
              "rebufferTimeMs": 1200,
              "rebufferCount": 2,
              "playTimeMs": 42000,
              "rebufferRatio": 0.028,
              "totalDroppedFrames": 12,
              "totalSeekCount": 1,
              "totalSeekTimeMs": 300,
              "meanVideoFormatBitrate": 2500000,
              "errorCount": 0
            }
        """.trimIndent()

        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(payload)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("success", responseBody["status"]?.jsonPrimitive?.content)
        assertEquals(testSessionId, responseBody["sessionId"]?.jsonPrimitive?.content)

        val persisted = fakeRepo.sessions[testSessionId]
        assertNotNull(persisted, "Session should be persisted")
        assertEquals(testSessionId, persisted.sessionId)
        assertEquals(45000L, persisted.sessionDurationMs)
        assertEquals(2, persisted.rebufferCount)
        assertEquals(0, persisted.errorCount)
        assertEquals(1, fakeRepo.sessions.size, "Should only have one record")
    }

    @Test
    fun `test multiple sessions are persisted independently`() = testApp { fakeRepo ->
        val session1Id = UUID.randomUUID().toString()
        val session2Id = UUID.randomUUID().toString()

        listOf(session1Id, session2Id).forEach { sessionId ->
            client.post("/v1/sessions") {
                header("X-API-Key", testApiKey)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody("""
                    {
                      "sessionId": "$sessionId",
                      "timestamp": ${System.currentTimeMillis()},
                      "sessionStartDateIso": "2026-02-15T10:00:00.000Z",
                      "sessionDurationMs": 30000,
                      "rebufferCount": 1,
                      "errorCount": 0
                    }
                """.trimIndent())
            }
        }

        assertEquals(2, fakeRepo.sessions.size, "Both sessions should be persisted")
        assertTrue(fakeRepo.sessions.containsKey(session1Id))
        assertTrue(fakeRepo.sessions.containsKey(session2Id))
    }

    // ── Validation: required fields ────────────────────────────────────────────

    @Test
    fun `test invalid session is rejected and not persisted`() = testApp { _ ->
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""{"sessionId": ""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `test session with negative duration is rejected`() = testApp { _ ->
        val testSessionId = UUID.randomUUID().toString()
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""
                {
                  "sessionId": "$testSessionId",
                  "timestamp": ${System.currentTimeMillis()},
                  "sessionStartDateIso": "2026-02-15T10:00:00.000Z",
                  "sessionDurationMs": -1000
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("sessionDurationMs"), "Error message should mention sessionDurationMs")
    }

    @Test
    fun `test session with zero duration is rejected`() = testApp { _ ->
        val testSessionId = UUID.randomUUID().toString()
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""
                {
                  "sessionId": "$testSessionId",
                  "timestamp": ${System.currentTimeMillis()},
                  "sessionStartDateIso": "2026-02-15T10:00:00.000Z",
                  "sessionDurationMs": 0
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("sessionDurationMs"), "Error message should mention sessionDurationMs")
    }

    @Test
    fun `test session with zero timestamp is rejected`() = testApp { _ ->
        val testSessionId = UUID.randomUUID().toString()
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""
                {
                  "sessionId": "$testSessionId",
                  "timestamp": 0,
                  "sessionStartDateIso": "2026-02-15T10:00:00.000Z",
                  "sessionDurationMs": 30000
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("timestamp"), "Error message should mention timestamp")
    }

    @Test
    fun `test session with negative timestamp is rejected`() = testApp { _ ->
        val testSessionId = UUID.randomUUID().toString()
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""
                {
                  "sessionId": "$testSessionId",
                  "timestamp": -123456,
                  "sessionStartDateIso": "2026-02-15T10:00:00.000Z",
                  "sessionDurationMs": 30000
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("timestamp"), "Error message should mention timestamp")
    }

    @Test
    fun `test session with blank sessionStartDateIso is rejected`() = testApp { _ ->
        val testSessionId = UUID.randomUUID().toString()
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""
                {
                  "sessionId": "$testSessionId",
                  "timestamp": ${System.currentTimeMillis()},
                  "sessionStartDateIso": "   ",
                  "sessionDurationMs": 30000
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("sessionStartDateIso"), "Error message should mention sessionStartDateIso")
    }

    @Test
    fun `test session with empty sessionStartDateIso is rejected`() = testApp { _ ->
        val testSessionId = UUID.randomUUID().toString()
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""
                {
                  "sessionId": "$testSessionId",
                  "timestamp": ${System.currentTimeMillis()},
                  "sessionStartDateIso": "",
                  "sessionDurationMs": 30000
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("sessionStartDateIso"), "Error message should mention sessionStartDateIso")
    }

    // ── Validation: sessionId ──────────────────────────────────────────────────

    @Test
    fun `test sessionId exceeding 128 characters is rejected`() = testApp { _ ->
        val longId = "a".repeat(129)
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""{"sessionId":"$longId","timestamp":${System.currentTimeMillis()},"sessionStartDateIso":"2026-02-24T10:00:00.000Z","sessionDurationMs":30000}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("sessionId"), "Error should mention sessionId")
    }

    @Test
    fun `test sessionId with non-UUID format is rejected`() = testApp { _ ->
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""{"sessionId":"not-a-valid-uuid","timestamp":${System.currentTimeMillis()},"sessionStartDateIso":"2026-02-24T10:00:00.000Z","sessionDurationMs":30000}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("UUID"), "Error should mention UUID")
    }

    // ── Validation: numeric ranges ─────────────────────────────────────────────

    @Test
    fun `test negative startupTimeMs is rejected`() = testApp { _ ->
        val sessionId = UUID.randomUUID().toString()
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""{"sessionId":"$sessionId","timestamp":${System.currentTimeMillis()},"sessionStartDateIso":"2026-02-24T10:00:00.000Z","sessionDurationMs":30000,"startupTimeMs":-1}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("startupTimeMs"), "Error should mention startupTimeMs")
    }

    @Test
    fun `test negative rebufferCount is rejected`() = testApp { _ ->
        val sessionId = UUID.randomUUID().toString()
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""{"sessionId":"$sessionId","timestamp":${System.currentTimeMillis()},"sessionStartDateIso":"2026-02-24T10:00:00.000Z","sessionDurationMs":30000,"rebufferCount":-3}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("rebufferCount"), "Error should mention rebufferCount")
    }

    @Test
    fun `test rebufferRatio above 1 is rejected`() = testApp { _ ->
        val sessionId = UUID.randomUUID().toString()
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""{"sessionId":"$sessionId","timestamp":${System.currentTimeMillis()},"sessionStartDateIso":"2026-02-24T10:00:00.000Z","sessionDurationMs":30000,"rebufferRatio":1.5}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("rebufferRatio"), "Error should mention rebufferRatio")
    }

    @Test
    fun `test negative rebufferRatio is rejected`() = testApp { _ ->
        val sessionId = UUID.randomUUID().toString()
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""{"sessionId":"$sessionId","timestamp":${System.currentTimeMillis()},"sessionStartDateIso":"2026-02-24T10:00:00.000Z","sessionDurationMs":30000,"rebufferRatio":-0.1}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("rebufferRatio"), "Error should mention rebufferRatio")
    }

    @Test
    fun `test negative errorCount is rejected`() = testApp { _ ->
        val sessionId = UUID.randomUUID().toString()
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""{"sessionId":"$sessionId","timestamp":${System.currentTimeMillis()},"sessionStartDateIso":"2026-02-24T10:00:00.000Z","sessionDurationMs":30000,"errorCount":-5}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("errorCount"), "Error should mention errorCount")
    }

    @Test
    fun `test multiple out-of-range fields are reported together`() = testApp { _ ->
        val sessionId = UUID.randomUUID().toString()
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""{"sessionId":"$sessionId","timestamp":${System.currentTimeMillis()},"sessionStartDateIso":"2026-02-24T10:00:00.000Z","sessionDurationMs":30000,"startupTimeMs":-1,"rebufferCount":-2}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("startupTimeMs"), "Error should mention startupTimeMs")
        assertTrue(body.contains("rebufferCount"), "Error should mention rebufferCount")
    }

    // ── Error safety ───────────────────────────────────────────────────────────

    @Test
    fun `test malformed json does not leak exception class names in response`() = testApp { _ ->
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("{ this is not valid json at all }")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertFalse(body.contains("Exception"), "Error response must not leak exception class names")
        assertFalse(body.contains("at com."), "Error response must not contain stack trace fragments")
    }

    @Test
    fun `test oversized payload is rejected with 413`() = testApp { _ ->
        val oversizedBody = "{\"data\":\"${"x".repeat(257 * 1024)}\"}" // > 256 KB body limit

        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(oversizedBody)
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    // ── Metrics endpoint ───────────────────────────────────────────────────────

    @Test
    fun `test metrics endpoint requires api key`() = testApp { _ ->
        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `test metrics endpoint is accessible with valid api key`() = testApp { _ ->
        val response = client.get("/metrics") {
            header("X-API-Key", testApiKey)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("sessions_ingested_total"), "Prometheus response should contain counter")
    }

    // ── Timeline ingestion tests ───────────────────────────────────────────────

    private fun validPayloadWithTimeline(sessionId: String, timelineJson: String): String = """
        {
          "sessionId": "$sessionId",
          "timestamp": ${System.currentTimeMillis()},
          "sessionStartDateIso": "2026-03-07T10:00:00.000Z",
          "sessionDurationMs": 30000,
          "timelineEvents": $timelineJson
        }
    """.trimIndent()

    private fun timelineEntry(
        timestampMs: Long = System.currentTimeMillis(),
        elapsedMs: Long = 0,
        playbackState: String = "PLAYING",
        currentBitrate: Int? = 2000000,
        networkType: String? = "Wi-Fi",
        totalDroppedFrames: Long = 0,
        bufferedDurationMs: Long? = 8000,
        rebufferCount: Int = 0,
        rebufferTimeMs: Long = 0,
    ): String = buildString {
        append("{")
        append("\"timestampMs\":$timestampMs,")
        append("\"elapsedMs\":$elapsedMs,")
        append("\"playbackState\":\"$playbackState\",")
        if (currentBitrate != null) append("\"currentBitrate\":$currentBitrate,") else append("\"currentBitrate\":null,")
        if (networkType != null) append("\"networkType\":\"$networkType\",") else append("\"networkType\":null,")
        append("\"totalDroppedFrames\":$totalDroppedFrames,")
        if (bufferedDurationMs != null) append("\"bufferedDurationMs\":$bufferedDurationMs,") else append("\"bufferedDurationMs\":null,")
        append("\"rebufferCount\":$rebufferCount,")
        append("\"rebufferTimeMs\":$rebufferTimeMs")
        append("}")
    }

    @Test
    fun `timeline events are persisted to session_timeline table`() = testApp { fakeRepo ->
        val sessionId = UUID.randomUUID().toString()
        val entries = "[${timelineEntry()},${timelineEntry(elapsedMs = 15000)},${timelineEntry(elapsedMs = 30000)}]"

        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(validPayloadWithTimeline(sessionId, entries))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("success", body["status"]?.jsonPrimitive?.content)
        assertEquals(3, fakeRepo.timelineForSession(sessionId).size, "All 3 timeline entries should be persisted")
    }

    @Test
    fun `timeline entry fields are correctly stored`() = testApp { fakeRepo ->
        val sessionId = UUID.randomUUID().toString()
        val ts = 1700000000000L
        val entry = timelineEntry(
            timestampMs = ts,
            elapsedMs = 5000,
            playbackState = "BUFFERING",
            currentBitrate = 3000000,
            networkType = "Wi-Fi",
            totalDroppedFrames = 4,
            bufferedDurationMs = 8000,
            rebufferCount = 1,
            rebufferTimeMs = 500,
        )

        client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(validPayloadWithTimeline(sessionId, "[$entry]"))
        }

        val stored = fakeRepo.timelineForSession(sessionId).firstOrNull()
        assertNotNull(stored, "Row must exist in session_timeline")
        assertEquals(ts, stored.timestampMs)
        assertEquals(5000L, stored.elapsedMs)
        assertEquals("BUFFERING", stored.playbackState)
        assertEquals(3000000, stored.currentBitrate)
        assertEquals("Wi-Fi", stored.networkType)
        assertEquals(4L, stored.totalDroppedFrames)
        assertEquals(8000L, stored.bufferedDurationMs)
        assertEquals(1, stored.rebufferCount)
        assertEquals(500L, stored.rebufferTimeMs)
    }

    @Test
    fun `missing timelineEvents field is backward compatible`() = testApp { fakeRepo ->
        val sessionId = UUID.randomUUID().toString()
        val payload = """
            {
              "sessionId": "$sessionId",
              "timestamp": ${System.currentTimeMillis()},
              "sessionStartDateIso": "2026-03-07T10:00:00.000Z",
              "sessionDurationMs": 30000
            }
        """.trimIndent()

        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(payload)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, fakeRepo.timelineForSession(sessionId).size, "No timeline rows should exist for session without timelineEvents")
    }

    @Test
    fun `empty timelineEvents array is accepted`() = testApp { fakeRepo ->
        val sessionId = UUID.randomUUID().toString()

        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(validPayloadWithTimeline(sessionId, "[]"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, fakeRepo.timelineForSession(sessionId).size, "Empty timelineEvents must persist 0 rows")
    }

    @Test
    fun `timeline entry with negative timestampMs is rejected`() = testApp { _ ->
        val sessionId = UUID.randomUUID().toString()
        val entry = timelineEntry(timestampMs = -1)

        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(validPayloadWithTimeline(sessionId, "[$entry]"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("timestampMs"))
    }

    @Test
    fun `timeline entry with negative elapsedMs is rejected`() = testApp { _ ->
        val sessionId = UUID.randomUUID().toString()
        val entry = timelineEntry(elapsedMs = -500)

        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(validPayloadWithTimeline(sessionId, "[$entry]"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("elapsedMs"))
    }

    @Test
    fun `timeline entry with unknown playbackState is rejected`() = testApp { _ ->
        val sessionId = UUID.randomUUID().toString()
        val entry = timelineEntry(playbackState = "UNKNOWN_STATE")

        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(validPayloadWithTimeline(sessionId, "[$entry]"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("playbackState"))
    }

    @Test
    fun `timeline entry with negative currentBitrate is rejected`() = testApp { _ ->
        val sessionId = UUID.randomUUID().toString()
        val entry = timelineEntry(currentBitrate = -100)

        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(validPayloadWithTimeline(sessionId, "[$entry]"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("currentBitrate"))
    }

    @Test
    fun `exceeding max timeline entries limit is rejected`() = testApp { _ ->
        val sessionId = UUID.randomUUID().toString()
        val entries = (1..501).joinToString(",") { timelineEntry(elapsedMs = it.toLong() * 1000) }

        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(validPayloadWithTimeline(sessionId, "[$entries]"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("timelineEvents") || body.contains("500"))
    }

    @Test
    fun `upsert preserves existing timeline rows`() = testApp { fakeRepo ->
        val sessionId = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()

        // First POST: 2 timeline entries
        client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(validPayloadWithTimeline(sessionId,
                "[${timelineEntry(timestampMs = ts)},${timelineEntry(timestampMs = ts + 15000, elapsedMs = 15000)}]"
            ))
        }

        // Second POST: 3 more timeline entries
        client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(validPayloadWithTimeline(sessionId,
                "[${timelineEntry(timestampMs = ts + 30000, elapsedMs = 30000)},${timelineEntry(timestampMs = ts + 45000, elapsedMs = 45000)},${timelineEntry(timestampMs = ts + 60000, elapsedMs = 60000)}]"
            ))
        }

        assertEquals(5, fakeRepo.timelineForSession(sessionId).size, "Total timeline rows should be 5 after two POSTs")
    }
}

