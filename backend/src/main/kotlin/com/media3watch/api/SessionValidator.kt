package com.media3watch.api

import com.media3watch.domain.SessionSummary
import com.media3watch.domain.TimelineEntry

private val UUID_PATTERN = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    RegexOption.IGNORE_CASE
)
private const val MAX_SESSION_ID_LENGTH = 128
private const val MAX_TIMELINE_ENTRIES = 500
private const val MAX_NETWORK_TYPE_LENGTH = 16
private val VALID_PLAYBACK_STATES = setOf("IDLE", "BUFFERING", "PLAYING", "PAUSED", "ENDED")

/**
 * Validates [SessionSummary] and [TimelineEntry] payloads.
 *
 * Each function returns the first validation error message as a [String], or `null` if the
 * input is valid. Separating validation from the routing DSL keeps [SessionsRoutes] focused
 * on HTTP orchestration and makes the rules easy to unit-test in isolation.
 *
 * Note: out-of-range numeric fields are collected and reported together (not early-exited per
 * field) to preserve the existing multi-field error reporting behaviour.
 */
internal object SessionValidator {

    /**
     * Validates the top-level session fields.
     * @return an error message string, or `null` if valid.
     */
    fun validate(session: SessionSummary): String? {
        // sessionId: non-blank
        if (session.sessionId.isBlank()) {
            return "Missing or empty required field: sessionId"
        }

        // sessionId: max length
        if (session.sessionId.length > MAX_SESSION_ID_LENGTH) {
            return "sessionId exceeds maximum length of $MAX_SESSION_ID_LENGTH characters"
        }

        // sessionId: UUID format
        if (!UUID_PATTERN.matches(session.sessionId)) {
            return "sessionId must be in UUID format"
        }

        // sessionDurationMs: must be positive
        if (session.sessionDurationMs <= 0) {
            return "Invalid value for sessionDurationMs: must be positive"
        }

        // timestamp: must be positive
        if (session.timestamp <= 0) {
            return "Invalid value for timestamp: must be positive"
        }

        // sessionStartDateIso: non-blank
        if (session.sessionStartDateIso.isBlank()) {
            return "Missing or empty required field: sessionStartDateIso"
        }

        // Nullable numeric fields: collect all out-of-range fields together
        val outOfRangeFields = buildList {
            if (session.startupTimeMs != null && session.startupTimeMs < 0) add("startupTimeMs")
            if (session.rebufferTimeMs != null && session.rebufferTimeMs < 0) add("rebufferTimeMs")
            if (session.rebufferCount != null && session.rebufferCount < 0) add("rebufferCount")
            if (session.playTimeMs != null && session.playTimeMs < 0) add("playTimeMs")
            if (session.totalDroppedFrames != null && session.totalDroppedFrames < 0) add("totalDroppedFrames")
            if (session.totalSeekCount != null && session.totalSeekCount < 0) add("totalSeekCount")
            if (session.totalSeekTimeMs != null && session.totalSeekTimeMs < 0) add("totalSeekTimeMs")
            if (session.errorCount != null && session.errorCount < 0) add("errorCount")
            if (session.meanVideoFormatBitrate != null && session.meanVideoFormatBitrate < 0) add("meanVideoFormatBitrate")
        }
        if (outOfRangeFields.isNotEmpty()) {
            return "Out-of-range value for field(s): ${outOfRangeFields.joinToString()}: must be non-negative"
        }

        // rebufferRatio: must be in [0, 1]
        if (session.rebufferRatio != null && session.rebufferRatio !in 0f..1f) {
            return "rebufferRatio must be between 0 and 1 (inclusive)"
        }

        return null
    }

    /**
     * Validates a list of timeline entries.
     * @return an error message string for the first invalid entry, or `null` if all are valid.
     */
    fun validateTimeline(events: List<TimelineEntry>): String? {
        if (events.size > MAX_TIMELINE_ENTRIES) {
            return "timelineEvents exceeds maximum of $MAX_TIMELINE_ENTRIES entries"
        }

        events.forEachIndexed { index, entry ->
            if (entry.timestampMs <= 0) {
                return "timelineEvents[$index].timestampMs must be positive"
            }
            if (entry.elapsedMs < 0) {
                return "timelineEvents[$index].elapsedMs must be non-negative"
            }
            if (entry.playbackState !in VALID_PLAYBACK_STATES) {
                return "timelineEvents[$index].playbackState '${entry.playbackState}' is not a valid playbackState"
            }
            if (entry.currentBitrate != null && entry.currentBitrate < 0) {
                return "timelineEvents[$index].currentBitrate must be non-negative"
            }
            if (entry.totalDroppedFrames < 0) {
                return "timelineEvents[$index].totalDroppedFrames must be non-negative"
            }
            if (entry.rebufferCount < 0) {
                return "timelineEvents[$index].rebufferCount must be non-negative"
            }
            if (entry.rebufferTimeMs < 0) {
                return "timelineEvents[$index].rebufferTimeMs must be non-negative"
            }
            if (entry.networkType != null && entry.networkType.length > MAX_NETWORK_TYPE_LENGTH) {
                return "timelineEvents[$index].networkType exceeds maximum length of $MAX_NETWORK_TYPE_LENGTH characters"
            }
        }

        return null
    }
}
