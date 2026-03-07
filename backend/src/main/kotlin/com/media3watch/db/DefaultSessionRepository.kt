package com.media3watch.db

import com.media3watch.domain.SessionSummary
import com.media3watch.domain.TimelineEntry
import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.use

class DefaultSessionRepository(private val dataSource: DataSource) : SessionRepository {
    private val logger = LoggerFactory.getLogger(DefaultSessionRepository::class.java)

    override fun upsertSession(session: SessionSummary): Result<Unit> {
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
     * Batch-inserts timeline entries for a session into the `session_timeline` table.
     *
     * Uses `addBatch()` + `executeBatch()` to send all rows in a single round-trip.
     * No FK constraint on `session_id` — timeline rows can be inserted independently of
     * the session row order. Orphan cleanup is handled by [deleteExpiredSessions].
     */
    override fun insertTimelineEvents(sessionId: String, events: List<TimelineEntry>): Result<Unit> {
        if (events.isEmpty()) return Result.success(Unit)
        return try {
            dataSource.connection.use { connection ->
                val sql = """
                    INSERT INTO session_timeline (
                        session_id, timestamp_ms, elapsed_ms, playback_state,
                        current_bitrate, network_type, total_dropped_frames,
                        buffered_duration_ms, rebuffer_count, rebuffer_time_ms
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()

                connection.prepareStatement(sql).use { stmt ->
                    for (event in events) {
                        stmt.setString(1, sessionId)
                        stmt.setLong(2, event.timestampMs)
                        stmt.setLong(3, event.elapsedMs)
                        stmt.setString(4, event.playbackState)
                        stmt.setObject(5, event.currentBitrate)
                        stmt.setObject(6, event.networkType)
                        stmt.setLong(7, event.totalDroppedFrames)
                        stmt.setObject(8, event.bufferedDurationMs)
                        stmt.setInt(9, event.rebufferCount)
                        stmt.setLong(10, event.rebufferTimeMs)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }
            logger.debug("Inserted ${events.size} timeline events for session: $sessionId")
            Result.success(Unit)
        } catch (e: SQLException) {
            logger.error("Failed to insert timeline events for session: $sessionId", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes sessions older than [retentionDays] days in batches of [batchSize] rows.
     *
     * Batched deletes prevent table-level lock contention on large datasets. The method
     * loops until no rows remain beyond the retention window.
     *
     * Before each batch of sessions is deleted, the corresponding `session_timeline` rows
     * are deleted first to avoid leaving orphaned timeline rows. Pre-existing orphans
     * (from sessions already absent in `sessions`) are also cleaned up at the start.
     *
     * A single connection is reused across all batches to avoid pool churn.
     *
     * @return total number of rows deleted across all batches.
     */
    override fun deleteExpiredSessions(retentionDays: Int, batchSize: Int): Int {
        val cutoffMs = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
        var totalDeleted = 0
        try {
            dataSource.connection.use { connection ->
                // Clean pre-existing orphaned timeline rows whose parent session is already gone.
                val orphanTimelineSql = """
                    DELETE FROM session_timeline
                    WHERE session_id IN (
                        SELECT session_id FROM session_timeline st
                        WHERE NOT EXISTS (SELECT 1 FROM sessions s WHERE s.session_id = st.session_id)
                        LIMIT ?
                    )
                """.trimIndent()
                connection.prepareStatement(orphanTimelineSql).use { stmt ->
                    stmt.setInt(1, batchSize)
                    do {
                        val deleted = stmt.executeUpdate()
                        if (deleted < batchSize) break  // last batch — no more orphan rows to delete
                    } while (true)
                }

                // Delete timeline rows for the batch of expired sessions before deleting the sessions
                // themselves, so that no orphaned timeline rows are left after each iteration.
                val deleteTimelineSql = """
                    DELETE FROM session_timeline
                    WHERE session_id IN (
                        SELECT session_id FROM sessions
                        WHERE timestamp < ?
                        LIMIT ?
                    )
                """.trimIndent()

                val deleteSessionSql = """
                    DELETE FROM sessions
                    WHERE id IN (
                        SELECT id FROM sessions
                        WHERE timestamp < ?
                        LIMIT ?
                    )
                """.trimIndent()

                connection.prepareStatement(deleteTimelineSql).use { timelineStmt ->
                    connection.prepareStatement(deleteSessionSql).use { sessionStmt ->
                        timelineStmt.setLong(1, cutoffMs)
                        timelineStmt.setInt(2, batchSize)
                        sessionStmt.setLong(1, cutoffMs)
                        sessionStmt.setInt(2, batchSize)
                        do {
                            timelineStmt.executeUpdate()
                            val deleted = sessionStmt.executeUpdate()
                            totalDeleted += deleted
                            if (deleted < batchSize) break  // last batch — no more rows to delete
                        } while (true)
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to delete expired sessions (cutoffMs=$cutoffMs)", e)
        }
        return totalDeleted
    }
}