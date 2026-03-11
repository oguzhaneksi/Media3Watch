package com.media3watch.db

import com.media3watch.domain.SessionSummary
import com.media3watch.domain.TimelineEntry
import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.use

class DefaultSessionRepository(private val dataSource: DataSource) : SessionRepository {
    private val logger = LoggerFactory.getLogger(DefaultSessionRepository::class.java)

    /**
     * Atomically upserts a session and batch-inserts its timeline entries within a single
     * database transaction. If either operation fails the whole transaction is rolled back,
     * so clients can safely retry without risking partial data or duplicate timeline rows.
     */
    override fun upsertSessionWithTimeline(
        session: SessionSummary,
        timelineEvents: List<TimelineEntry>?
    ): Result<Unit> {
        return try {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val sessionSql = """
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

                    connection.prepareStatement(sessionSql).use { stmt ->
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

                    if (!timelineEvents.isNullOrEmpty()) {
                        val timelineSql = """
                            INSERT INTO session_timeline (
                                session_id, timestamp_ms, elapsed_ms, playback_state,
                                current_bitrate, network_type, total_dropped_frames,
                                buffered_duration_ms, rebuffer_count, rebuffer_time_ms
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent()

                        connection.prepareStatement(timelineSql).use { stmt ->
                            for (event in timelineEvents) {
                                stmt.setString(1, session.sessionId)
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

                    connection.commit()
                    logger.debug("Successfully upserted session with timeline: ${session.sessionId}")
                    Result.success(Unit)
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to upsert session with timeline: ${session.sessionId}", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes sessions older than [retentionDays] days in batches of [batchSize] rows.
     *
     * Batched deletes prevent table-level lock contention on large datasets. The method
     * loops until no rows remain beyond the retention window.
     *
     * Each batch uses a data-modifying CTE that selects the target session ids once and
     * feeds the same set to both the `session_timeline` delete and the `sessions` delete,
     * so the two operations are guaranteed to act on identical rows within a single
     * statement. Pre-existing orphans (timeline rows whose parent session is already absent)
     * are cleaned up at the start.
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

                // A single data-modifying CTE selects the batch of expired sessions once
                // and reuses it for both deletes, guaranteeing that the timeline rows and the
                // session rows always refer to the exact same set of ids. This prevents the race
                // condition that would arise if two independent LIMIT subqueries were evaluated
                // separately and happened to select different rows.
                val deleteBatchSql = """
                    WITH batch AS (
                        SELECT id, session_id FROM sessions WHERE timestamp < ? LIMIT ?
                    ),
                    deleted_timeline AS (
                        DELETE FROM session_timeline
                        WHERE session_id IN (SELECT session_id FROM batch)
                    )
                    DELETE FROM sessions
                    WHERE id IN (SELECT id FROM batch)
                """.trimIndent()

                connection.prepareStatement(deleteBatchSql).use { stmt ->
                    do {
                        stmt.setLong(1, cutoffMs)
                        stmt.setInt(2, batchSize)
                        val deleted = stmt.executeUpdate()
                        totalDeleted += deleted

                        // Fewer rows than batchSize means we have reached the last batch.
                        if (deleted < batchSize) break
                    } while (true)
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to delete expired sessions (cutoffMs=$cutoffMs)", e)
        }
        return totalDeleted
    }
}