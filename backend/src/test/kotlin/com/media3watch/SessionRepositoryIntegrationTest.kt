package com.media3watch

import com.media3watch.db.DefaultSessionRepository
import com.media3watch.domain.SessionSummary
import com.media3watch.domain.TimelineEntry
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests that exercise the real SQL logic inside [DefaultSessionRepository].
 *
 * These tests are fully self-contained: a PostgreSQL instance is started automatically
 * via Testcontainers (requires Docker to be available on the host). No external database
 * or environment variables are needed.
 *
 * They are intentionally kept separate from [SessionIngestionTest], which runs
 * entirely in-memory and does not need Docker.
 */
@Testcontainers
class SessionRepositoryIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("media3watch")
            .withUsername("m3w")
            .withPassword("m3w")

        /** Run Flyway migrations once after the container has started, before any test runs. */
        @JvmStatic
        @BeforeAll
        fun runMigrations() {
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }
    }

    private fun getDbConnection(): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun buildDataSource(): DataSource {
        val jdbcUrl = postgres.jdbcUrl
        val dbUser = postgres.username
        val dbPwd = postgres.password
        return object : DataSource {
            override fun getConnection() = DriverManager.getConnection(jdbcUrl, dbUser, dbPwd)
            override fun getConnection(u: String, p: String) = DriverManager.getConnection(jdbcUrl, u, p)
            override fun getLogWriter(): java.io.PrintWriter? = null
            override fun setLogWriter(out: java.io.PrintWriter?) {}
            override fun getLoginTimeout() = 0
            override fun setLoginTimeout(s: Int) {}
            override fun getParentLogger(): java.util.logging.Logger =
                throw java.sql.SQLFeatureNotSupportedException()
            override fun <T : Any> unwrap(iface: Class<T>): T = throw java.sql.SQLFeatureNotSupportedException()
            override fun isWrapperFor(iface: Class<*>) = false
        }
    }

    @Test
    fun `upsertSessionWithTimeline persists session and timeline atomically`() {
        val sessionId = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val repo = DefaultSessionRepository(buildDataSource())

        val session = SessionSummary(
            sessionId = sessionId,
            timestamp = ts,
            sessionStartDateIso = "2026-01-01T00:00:00.000Z",
            sessionDurationMs = 30_000
        )
        val events = listOf(
            TimelineEntry(
                timestampMs = ts,
                elapsedMs = 0,
                playbackState = "PLAYING",
                totalDroppedFrames = 0,
                rebufferCount = 0,
                rebufferTimeMs = 0
            ),
            TimelineEntry(
                timestampMs = ts + 10_000,
                elapsedMs = 10_000,
                playbackState = "BUFFERING",
                totalDroppedFrames = 2,
                rebufferCount = 1,
                rebufferTimeMs = 300
            )
        )

        val result = repo.upsertSessionWithTimeline(session, events)
        assertTrue(result.isSuccess, "upsertSessionWithTimeline should succeed")

        getDbConnection().use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM sessions WHERE session_id = ?").use { stmt ->
                stmt.setString(1, sessionId)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1), "Session row must be present after atomic upsert")
                }
            }
            conn.prepareStatement("SELECT COUNT(*) FROM session_timeline WHERE session_id = ?").use { stmt ->
                stmt.setString(1, sessionId)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    assertEquals(2, rs.getInt(1), "Both timeline rows must be present after atomic upsert")
                }
            }
        }
    }

    @Test
    fun `deleteExpiredSessions removes expired sessions and keeps recent ones`() {
        val expiredId = UUID.randomUUID().toString()
        val recentId = UUID.randomUUID().toString()
        val orphanId = UUID.randomUUID().toString() // no matching row in sessions
        val expiredTimestamp = System.currentTimeMillis() - (91L * 24 * 60 * 60 * 1000) // 91 days ago
        val recentTimestamp = System.currentTimeMillis()

        getDbConnection().use { conn ->
            // Insert expired and recent sessions
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

            // Insert timeline rows for expired session, recent session, and a pre-existing orphan
            val insertTimelineSql =
                "INSERT INTO session_timeline (session_id, timestamp_ms, elapsed_ms, playback_state, " +
                "total_dropped_frames, rebuffer_count, rebuffer_time_ms) VALUES (?, ?, ?, ?, ?, ?, ?)"
            for ((id, ts) in listOf(expiredId to expiredTimestamp, recentId to recentTimestamp, orphanId to expiredTimestamp)) {
                conn.prepareStatement(insertTimelineSql).use { stmt ->
                    stmt.setString(1, id)
                    stmt.setLong(2, ts)
                    stmt.setLong(3, 0L)
                    stmt.setString(4, "PLAYING")
                    stmt.setLong(5, 0L)
                    stmt.setInt(6, 0)
                    stmt.setLong(7, 0L)
                    stmt.executeUpdate()
                }
            }
        }

        val deleted = DefaultSessionRepository(buildDataSource()).deleteExpiredSessions(retentionDays = 90)

        assertTrue(deleted >= 1, "Should have deleted at least the one expired session")

        getDbConnection().use { conn ->
            fun countSessionsById(id: String): Int {
                conn.prepareStatement("SELECT COUNT(*) FROM sessions WHERE session_id = ?").use { stmt ->
                    stmt.setString(1, id)
                    stmt.executeQuery().use { rs ->
                        rs.next()
                        return rs.getInt(1)
                    }
                }
            }
            fun countTimelineById(id: String): Int {
                conn.prepareStatement("SELECT COUNT(*) FROM session_timeline WHERE session_id = ?").use { stmt ->
                    stmt.setString(1, id)
                    stmt.executeQuery().use { rs ->
                        rs.next()
                        return rs.getInt(1)
                    }
                }
            }

            assertEquals(0, countSessionsById(expiredId), "Expired session should be deleted from sessions")
            assertEquals(1, countSessionsById(recentId), "Recent session should still exist in sessions")

            assertEquals(0, countTimelineById(expiredId), "Timeline rows for expired session should be deleted")
            assertEquals(1, countTimelineById(recentId), "Timeline rows for recent session should be preserved")
            assertEquals(0, countTimelineById(orphanId), "Pre-existing orphan timeline rows should be cleaned up")
        }
    }
}

