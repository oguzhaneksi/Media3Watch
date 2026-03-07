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
            override fun getParentLogger(): java.util.logging.Logger? = null
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
    fun `upsert only overwrites session when incoming timestamp is newer`() {
        val sessionId = UUID.randomUUID().toString()
        val t1 = System.currentTimeMillis() - 2000L   // oldest
        val t2 = t1 + 1000L                           // newer  — should overwrite
        val t3 = t1 + 500L                            // older than t2 — should NOT overwrite

        val repo = DefaultSessionRepository(buildDataSource())

        fun makeSession(ts: Long, duration: Long) = SessionSummary(
            sessionId = sessionId,
            timestamp = ts,
            sessionStartDateIso = "2026-01-01T00:00:00.000Z",
            sessionDurationMs = duration
        )

        fun queryDuration(): Long {
            getDbConnection().use { conn ->
                conn.prepareStatement("SELECT session_duration_ms FROM sessions WHERE session_id = ?").use { stmt ->
                    stmt.setString(1, sessionId)
                    stmt.executeQuery().use { rs ->
                        return if (rs.next()) rs.getLong("session_duration_ms") else -1L
                    }
                }
            }
        }

        // Step 1: Insert initial session at T1
        repo.upsertSession(makeSession(t1, 10_000))
        assertEquals(10_000L, queryDuration(), "Initial insert should persist duration 10_000")

        // Step 2: Update with T2 > T1 — should overwrite
        repo.upsertSession(makeSession(t2, 20_000))
        assertEquals(20_000L, queryDuration(), "Newer timestamp T2 should overwrite to duration 20_000")

        // Step 3: Update with T3 < T2 — should be rejected
        repo.upsertSession(makeSession(t3, 30_000))
        assertEquals(20_000L, queryDuration(), "Older timestamp T3 should NOT overwrite; duration must remain 20_000")
    }

    @Test
    fun `deleteExpiredSessions removes expired sessions and keeps recent ones`() {
        val expiredId = UUID.randomUUID().toString()
        val recentId = UUID.randomUUID().toString()
        val expiredTimestamp = System.currentTimeMillis() - (91L * 24 * 60 * 60 * 1000) // 91 days ago
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

        val deleted = DefaultSessionRepository(buildDataSource()).deleteExpiredSessions(retentionDays = 90)

        assertTrue(deleted >= 1, "Should have deleted at least the one expired session")

        getDbConnection().use { conn ->
            fun countById(id: String): Int {
                conn.prepareStatement("SELECT COUNT(*) FROM sessions WHERE session_id = ?").use { stmt ->
                    stmt.setString(1, id)
                    stmt.executeQuery().use { rs ->
                        rs.next()
                        return rs.getInt(1)
                    }
                }
            }
            assertEquals(0, countById(expiredId), "Expired session should be deleted")
            assertEquals(1, countById(recentId), "Recent session should still exist")
        }
    }
}

