package com.media3watch.db

import com.media3watch.domain.SessionSummary
import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource

class SessionRepository(private val dataSource: DataSource) {
    private val logger = LoggerFactory.getLogger(SessionRepository::class.java)

    fun upsertSession(session: SessionSummary): Result<Unit> {
        return try {
            dataSource.connection.use { connection ->
                val sql = """
                    INSERT INTO sessions (
                        session_id, timestamp, session_start_date_iso, session_duration_ms,
                        startup_time_ms, rebuffer_time_ms, rebuffer_count,
                        play_time_ms, rebuffer_ratio, total_dropped_frames,
                        total_seek_count, total_seek_time_ms, mean_video_format_bitrate,
                        error_count, device_model, os_version, sdk_version, connection_type,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                    ON CONFLICT (session_id) 
                    DO UPDATE SET
                        timestamp = EXCLUDED.timestamp,
                        session_start_date_iso = EXCLUDED.session_start_date_iso,
                        session_duration_ms = EXCLUDED.session_duration_ms,
                        startup_time_ms = EXCLUDED.startup_time_ms,
                        rebuffer_time_ms = EXCLUDED.rebuffer_time_ms,
                        rebuffer_count = EXCLUDED.rebuffer_count,
                        play_time_ms = EXCLUDED.play_time_ms,
                        rebuffer_ratio = EXCLUDED.rebuffer_ratio,
                        total_dropped_frames = EXCLUDED.total_dropped_frames,
                        total_seek_count = EXCLUDED.total_seek_count,
                        total_seek_time_ms = EXCLUDED.total_seek_time_ms,
                        mean_video_format_bitrate = EXCLUDED.mean_video_format_bitrate,
                        error_count = EXCLUDED.error_count,
                        device_model = EXCLUDED.device_model,
                        os_version = EXCLUDED.os_version,
                        sdk_version = EXCLUDED.sdk_version,
                        connection_type = EXCLUDED.connection_type
                    WHERE sessions.timestamp <= EXCLUDED.timestamp
                """.trimIndent()

                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, session.sessionId)
                    stmt.setLong(2, session.timestamp)
                    stmt.setString(3, session.sessionStartDateIso)
                    stmt.setLong(4, session.sessionDurationMs)
                    stmt.setObject(5, session.startupTimeMs)
                    stmt.setObject(6, session.rebufferTimeMs)
                    stmt.setObject(7, session.rebufferCount)
                    stmt.setObject(8, session.playTimeMs)
                    stmt.setObject(9, session.rebufferRatio)
                    stmt.setObject(10, session.totalDroppedFrames)
                    stmt.setObject(11, session.totalSeekCount)
                    stmt.setObject(12, session.totalSeekTimeMs)
                    stmt.setObject(13, session.meanVideoFormatBitrate)
                    stmt.setObject(14, session.errorCount)
                    stmt.setObject(15, session.deviceModel)
                    stmt.setObject(16, session.osVersion)
                    stmt.setObject(17, session.sdkVersion)
                    stmt.setObject(18, session.connectionType)
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

    /**
     * Deletes sessions older than [retentionDays] days in batches of [batchSize] rows.
     *
     * Batched deletes prevent table-level lock contention on large datasets. The method
     * loops until no rows remain beyond the retention window.
     *
     * A single connection is reused across all batches to avoid pool churn.
     *
     * @return total number of rows deleted across all batches.
     */
    fun deleteExpiredSessions(retentionDays: Int, batchSize: Int = 10_000): Int {
        val cutoffMs = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
        var totalDeleted = 0
        try {
            dataSource.connection.use { connection ->
                val sql = """
                    DELETE FROM sessions
                    WHERE id IN (
                        SELECT id FROM sessions
                        WHERE timestamp < ?
                        LIMIT ?
                    )
                """.trimIndent()
                do {
                    val deleted = connection.prepareStatement(sql).use { stmt ->
                        stmt.setLong(1, cutoffMs)
                        stmt.setInt(2, batchSize)
                        stmt.executeUpdate()
                    }
                    totalDeleted += deleted
                    if (deleted < batchSize) break  // last batch — no more rows to delete
                } while (true)
            }
        } catch (e: SQLException) {
            logger.error("Failed to delete expired sessions (cutoffMs=$cutoffMs)", e)
        }
        return totalDeleted
    }
}
