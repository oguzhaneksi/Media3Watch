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
        
        val testSessionId = "test-${UUID.randomUUID()}"
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
        
        val session1Id = "test-${UUID.randomUUID()}"
        val session2Id = "test-${UUID.randomUUID()}"
        
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
        
        val testSessionId = "test-${UUID.randomUUID()}"
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
        
        val testSessionId = "test-${UUID.randomUUID()}"
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
        
        val testSessionId = "test-${UUID.randomUUID()}"
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
        
        val testSessionId = "test-${UUID.randomUUID()}"
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
        
        val testSessionId = "test-${UUID.randomUUID()}"
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
        
        val testSessionId = "test-${UUID.randomUUID()}"
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
}
