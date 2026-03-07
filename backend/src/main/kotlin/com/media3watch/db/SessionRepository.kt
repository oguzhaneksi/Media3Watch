package com.media3watch.db

import com.media3watch.domain.SessionSummary
import com.media3watch.domain.TimelineEntry

interface SessionRepository {
    fun upsertSession(session: SessionSummary): Result<Unit>
    fun insertTimelineEvents(sessionId: String, events: List<TimelineEntry>): Result<Unit>
    fun upsertSessionWithTimeline(session: SessionSummary, timelineEvents: List<TimelineEntry>?): Result<Unit>
    fun deleteExpiredSessions(retentionDays: Int, batchSize: Int = 10_000): Int
}
