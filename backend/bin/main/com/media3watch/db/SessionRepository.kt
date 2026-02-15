package com.media3watch.db

import com.media3watch.domain.SessionSummary
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.sql.Timestamp
import javax.sql.DataSource

class SessionRepository(private val dataSource: DataSource) {
    private val logger = LoggerFactory.getLogger(SessionRepository::class.java)

    fun upsertSession(session: SessionSummary): Result<Unit> {
        return try {
            dataSource.connection.use { connection ->
                val sql = """
                    INSERT INTO sessions (
                        session_id, timestamp, content_id, stream_type,
                        player_startup_ms, rebuffer_time_ms, rebuffer_count,
                        error_count, payload, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW())
                    ON CONFLICT (session_id) 
                    DO UPDATE SET
                        timestamp = EXCLUDED.timestamp,
                        content_id = EXCLUDED.content_id,
                        stream_type = EXCLUDED.stream_type,
                        player_startup_ms = EXCLUDED.player_startup_ms,
                        rebuffer_time_ms = EXCLUDED.rebuffer_time_ms,
                        rebuffer_count = EXCLUDED.rebuffer_count,
                        error_count = EXCLUDED.error_count,
                        payload = EXCLUDED.payload,
                        created_at = EXCLUDED.created_at
                    WHERE sessions.created_at <= EXCLUDED.created_at
                """.trimIndent()

                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, session.sessionId)
                    stmt.setTimestamp(2, Timestamp(session.timestamp))
                    stmt.setString(3, session.contentId)
                    stmt.setString(4, session.streamType)
                    stmt.setObject(5, session.playerStartupMs)
                    stmt.setObject(6, session.rebufferTimeMs)
                    stmt.setObject(7, session.rebufferCount)
                    stmt.setObject(8, session.errorCount)
                    stmt.setString(9, session.payload ?: "{}")
                    stmt.executeUpdate()
                }
            }
            logger.debug("Successfully upserted session: ${session.sessionId}")
            Result.success(Unit)
        } catch (e: SQLException) {
            logger.error("Database upsert failed for session: ${session.sessionId}", e)
            Result.failure(e)
        }
    }
}

