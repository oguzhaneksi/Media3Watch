package com.media3watch

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*
import java.util.UUID
import java.sql.Connection
import java.sql.DriverManager
import com.media3watch.module
import com.media3watch.db.SessionRepository

class SessionIngestionTest {
    
    private val testApiKey = "dev-key"
    
    private fun getDbConnection(): Connection {
        val jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5433/media3watch"
        val user = System.getenv("DATABASE_USER") ?: "m3w"
        val password = System.getenv("DATABASE_PASSWORD") ?: "m3w"
        return DriverManager.getConnection(jdbcUrl, user, password)
    }
    
    @Test
    fun `test session is persisted to database`() = testApplication {
        application {
            module()
        }
        
        val testSessionId = UUID.randomUUID().toString()
        val currentTime = System.currentTimeMillis()
        
        // Prepare test payload
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
        
        // Send POST request
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(payload)
        }
        
        // Assert API response
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("success", responseBody["status"]?.jsonPrimitive?.content)
        assertEquals(testSessionId, responseBody["sessionId"]?.jsonPrimitive?.content)
        
        // Verify data was written to database
        getDbConnection().use { conn ->
            val stmt = conn.prepareStatement(
                "SELECT session_id, session_duration_ms, rebuffer_count, error_count FROM sessions WHERE session_id = ?"
            )
            stmt.setString(1, testSessionId)
            val rs = stmt.executeQuery()
            
            assertTrue(rs.next(), "Session should be persisted to database")
            assertEquals(testSessionId, rs.getString("session_id"))
            assertEquals(45000L, rs.getLong("session_duration_ms"))
            assertEquals(2, rs.getInt("rebuffer_count"))
            assertEquals(0, rs.getInt("error_count"))
            assertFalse(rs.next(), "Should only have one record")
        }
    }
    
    @Test
    fun `test multiple sessions are persisted independently`() = testApplication {
        application {
            module()
        }
        
        val session1Id = UUID.randomUUID().toString()
        val session2Id = UUID.randomUUID().toString()
        
        // Send two sessions
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
        
        // Verify both are in database
        getDbConnection().use { conn ->
            val stmt = conn.prepareStatement(
                "SELECT COUNT(*) as count FROM sessions WHERE session_id IN (?, ?)"
            )
            stmt.setString(1, session1Id)
            stmt.setString(2, session2Id)
            val rs = stmt.executeQuery()
            
            assertTrue(rs.next())
            assertEquals(2, rs.getInt("count"), "Both sessions should be persisted")
        }
    }
    
    @Test
    fun `test invalid session is rejected and not persisted`() = testApplication {
        application {
            module()
        }
        
        val invalidPayload = """{"sessionId": ""}""" // Missing required fields
        
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(invalidPayload)
        }
        
        // Assert error response - missing required fields causes deserialization failure
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
    
    @Test
    fun `test session with negative duration is rejected`() = testApplication {
        application {
            module()
        }
        
        val testSessionId = UUID.randomUUID().toString()
        val invalidPayload = """
            {
              "sessionId": "$testSessionId",
              "timestamp": ${System.currentTimeMillis()},
              "sessionStartDateIso": "2026-02-15T10:00:00.000Z",
              "sessionDurationMs": -1000
            }
        """.trimIndent()
        
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(invalidPayload)
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("sessionDurationMs"), "Error message should mention sessionDurationMs")
    }
    
    @Test
    fun `test session with zero duration is rejected`() = testApplication {
        application {
            module()
        }
        
        val testSessionId = UUID.randomUUID().toString()
        val invalidPayload = """
            {
              "sessionId": "$testSessionId",
              "timestamp": ${System.currentTimeMillis()},
              "sessionStartDateIso": "2026-02-15T10:00:00.000Z",
              "sessionDurationMs": 0
            }
        """.trimIndent()
        
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(invalidPayload)
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("sessionDurationMs"), "Error message should mention sessionDurationMs")
    }
    
    @Test
    fun `test session with zero timestamp is rejected`() = testApplication {
        application {
            module()
        }
        
        val testSessionId = UUID.randomUUID().toString()
        val invalidPayload = """
            {
              "sessionId": "$testSessionId",
              "timestamp": 0,
              "sessionStartDateIso": "2026-02-15T10:00:00.000Z",
              "sessionDurationMs": 30000
            }
        """.trimIndent()
        
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(invalidPayload)
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("timestamp"), "Error message should mention timestamp")
    }
    
    @Test
    fun `test session with negative timestamp is rejected`() = testApplication {
        application {
            module()
        }
        
        val testSessionId = UUID.randomUUID().toString()
        val invalidPayload = """
            {
              "sessionId": "$testSessionId",
              "timestamp": -123456,
              "sessionStartDateIso": "2026-02-15T10:00:00.000Z",
              "sessionDurationMs": 30000
            }
        """.trimIndent()
        
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(invalidPayload)
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("timestamp"), "Error message should mention timestamp")
    }
    
    @Test
    fun `test session with blank sessionStartDateIso is rejected`() = testApplication {
        application {
            module()
        }
        
        val testSessionId = UUID.randomUUID().toString()
        val invalidPayload = """
            {
              "sessionId": "$testSessionId",
              "timestamp": ${System.currentTimeMillis()},
              "sessionStartDateIso": "   ",
              "sessionDurationMs": 30000
            }
        """.trimIndent()
        
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(invalidPayload)
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("sessionStartDateIso"), "Error message should mention sessionStartDateIso")
    }
    
    @Test
    fun `test session with empty sessionStartDateIso is rejected`() = testApplication {
        application {
            module()
        }
        
        val testSessionId = UUID.randomUUID().toString()
        val invalidPayload = """
            {
              "sessionId": "$testSessionId",
              "timestamp": ${System.currentTimeMillis()},
              "sessionStartDateIso": "",
              "sessionDurationMs": 30000
            }
        """.trimIndent()
        
        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(invalidPayload)
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("sessionStartDateIso"), "Error message should mention sessionStartDateIso")
    }

    // ── New hardening: sessionId validation ────────────────────────────────────

    @Test
    fun `test sessionId exceeding 128 characters is rejected`() = testApplication {
        application { module() }

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
    fun `test sessionId with non-UUID format is rejected`() = testApplication {
        application { module() }

        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody("""{"sessionId":"not-a-valid-uuid","timestamp":${System.currentTimeMillis()},"sessionStartDateIso":"2026-02-24T10:00:00.000Z","sessionDurationMs":30000}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("UUID"), "Error should mention UUID")
    }

    // ── New hardening: numeric range validation ────────────────────────────────

    @Test
    fun `test negative startupTimeMs is rejected`() = testApplication {
        application { module() }

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
    fun `test negative rebufferCount is rejected`() = testApplication {
        application { module() }

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
    fun `test rebufferRatio above 1 is rejected`() = testApplication {
        application { module() }

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
    fun `test negative rebufferRatio is rejected`() = testApplication {
        application { module() }

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
    fun `test negative errorCount is rejected`() = testApplication {
        application { module() }

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
    fun `test multiple out-of-range fields are reported together`() = testApplication {
        application { module() }

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

    // ── New hardening: no exception leakage ───────────────────────────────────

    @Test
    fun `test malformed json does not leak exception class names in response`() = testApplication {
        application { module() }

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
    fun `test oversized payload is rejected with 413`() = testApplication {
        application { module() }

        val oversizedBody = "{\"data\":\"${"x".repeat(65 * 1024)}\"}" // > 64 KB

        val response = client.post("/v1/sessions") {
            header("X-API-Key", testApiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(oversizedBody)
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    // ── New hardening: /metrics authentication ────────────────────────────────

    @Test
    fun `test metrics endpoint requires api key`() = testApplication {
        application { module() }

        val response = client.get("/metrics")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `test metrics endpoint is accessible with valid api key`() = testApplication {
        application { module() }

        val response = client.get("/metrics") {
            header("X-API-Key", testApiKey)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("sessions_ingested_total"), "Prometheus response should contain counter")
    }

    // ── New hardening: retention cleanup ──────────────────────────────────────

    @Test
    fun `test deleteExpiredSessions removes expired sessions and keeps recent ones`() {
        val expiredId = UUID.randomUUID().toString()
        val recentId = UUID.randomUUID().toString()
        // 91 days ago in epoch-ms — beyond the 90-day retention window
        val expiredTimestamp = System.currentTimeMillis() - (91L * 24 * 60 * 60 * 1000)
        val recentTimestamp = System.currentTimeMillis()

        getDbConnection().use { conn ->
            for ((id, ts) in listOf(expiredId to expiredTimestamp, recentId to recentTimestamp)) {
                conn.prepareStatement(
                    "INSERT INTO sessions (session_id, timestamp, session_start_date_iso, session_duration_ms, created_at) " +
                    "VALUES (?, ?, ?, ?, NOW()) ON CONFLICT (session_id) DO NOTHING"
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.setLong(2, ts)
                    stmt.setString(3, "2026-01-01T00:00:00.000Z")
                    stmt.setLong(4, 30_000)
                    stmt.executeUpdate()
                }
            }
        }

        // Thin DataSource wrapper backed by DriverManager — avoids HikariCP lifecycle in tests
        val jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5433/media3watch"
        val dbUser = System.getenv("DATABASE_USER") ?: "m3w"
        val dbPwd = System.getenv("DATABASE_PASSWORD") ?: "m3w"
        val dataSource = object : javax.sql.DataSource {
            override fun getConnection() = DriverManager.getConnection(jdbcUrl, dbUser, dbPwd)
            override fun getConnection(u: String, p: String) = DriverManager.getConnection(jdbcUrl, u, p)
            override fun getLogWriter(): java.io.PrintWriter? = null
            override fun setLogWriter(out: java.io.PrintWriter?) {}
            override fun getLoginTimeout() = 0
            override fun setLoginTimeout(s: Int) {}
            override fun getParentLogger(): java.util.logging.Logger? = null
            override fun <T : Any> unwrap(iface: Class<T>): T = throw java.sql.SQLFeatureNotSupportedException()
            override fun isWrapperFor(iface: Class<*>) = false
        }

        val deleted = SessionRepository(dataSource).deleteExpiredSessions(retentionDays = 90)

        assertTrue(deleted >= 1, "Should have deleted at least the one expired session")

        getDbConnection().use { conn ->
            fun countById(id: String): Int {
                val rs = conn.prepareStatement("SELECT COUNT(*) FROM sessions WHERE session_id = ?")
                    .also { it.setString(1, id) }.executeQuery()
                rs.next()
                return rs.getInt(1)
            }
            assertEquals(0, countById(expiredId), "Expired session should be deleted")
            assertEquals(1, countById(recentId), "Recent session should still exist")
        }
    }
}
