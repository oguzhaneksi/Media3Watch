package com.media3watch

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import java.sql.Connection
import java.sql.DriverManager
import com.media3watch.module

class SessionIngestionTest {
    
    private val testApiKey = "dev-key"
    
    @BeforeEach
    fun cleanupTestSessions() {
        // Clean up any sessions with session_id starting with "test-" before each test
        getDbConnection().use { conn ->
            val stmt = conn.prepareStatement("DELETE FROM sessions WHERE session_id LIKE 'test-%'")
            stmt.executeUpdate()
        }
    }
    
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
        
        // Assert error response
        assertEquals(HttpStatusCode.BadRequest, response.status)
        
        // Verify nothing was written to database (count only test sessions)
        getDbConnection().use { conn ->
            val stmt = conn.prepareStatement("SELECT COUNT(*) as count FROM sessions WHERE session_id LIKE 'test-%'")
            val rs = stmt.executeQuery()
            
            assertTrue(rs.next())
            assertEquals(0, rs.getInt("count"), "Invalid session should not be persisted")
        }
    }
}
