package com.media3watch

import com.media3watch.db.SessionRepository
import com.media3watch.domain.SessionSummary
import com.media3watch.domain.TimelineEntry

/**
 * In-memory [SessionRepository] for unit / API-layer tests.
 *
 * No database required — data is stored in plain Kotlin collections.
 * Note: unlike [com.media3watch.db.DefaultSessionRepository], upsert does NOT enforce timestamp-based
 * conflict resolution; that logic is covered by [SessionRepositoryIntegrationTest].
 */
class FakeSessionRepository : SessionRepository {

    private val _sessions = mutableMapOf<String, SessionSummary>()
    private val _timeline = mutableListOf<Pair<String, TimelineEntry>>()

    /** Read-only view of all upserted sessions, keyed by sessionId. */
    val sessions: Map<String, SessionSummary> get() = _sessions

    /** Returns all timeline entries recorded for the given sessionId. */
    fun timelineForSession(sessionId: String): List<TimelineEntry> =
        _timeline.filter { it.first == sessionId }.map { it.second }

    override fun upsertSession(session: SessionSummary): Result<Unit> {
        _sessions[session.sessionId] = session
        return Result.success(Unit)
    }

    override fun insertTimelineEvents(sessionId: String, events: List<TimelineEntry>): Result<Unit> {
        _timeline.addAll(events.map { sessionId to it })
        return Result.success(Unit)
    }

    override fun deleteExpiredSessions(retentionDays: Int, batchSize: Int): Int = 0
}
