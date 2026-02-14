package com.media3watch.sdk

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSummaryTest {

    private val fullSummary = SessionSummary(
        sessionId = 1L,
        sessionStartDateIso = "2026-02-14T10:30:00.000Z",
        sessionDurationMs = 45000L,
        startupTimeMs = 450L,
        rebufferTimeMs = 1200L,
        rebufferCount = 2,
        playTimeMs = 42000L,
        rebufferRatio = 0.028f,
        totalDroppedFrames = 12L,
        totalSeekCount = 1,
        totalSeekTimeMs = 300L,
        meanVideoFormatBitrate = 2500000,
        errorCount = 0
    )

    private val nullFieldsSummary = SessionSummary(
        sessionId = 2L,
        sessionStartDateIso = "2026-02-14T11:00:00.000Z",
        sessionDurationMs = 1000L,
        startupTimeMs = null,
        rebufferTimeMs = null,
        rebufferCount = null,
        playTimeMs = null,
        rebufferRatio = null,
        totalDroppedFrames = null,
        totalSeekCount = null,
        totalSeekTimeMs = null,
        meanVideoFormatBitrate = null,
        errorCount = null
    )

    @Test
    fun toJson_containsAllFields() {
        val json = fullSummary.toJson()

        assertTrue(json.contains("\"sessionId\":1"))
        assertTrue(json.contains("\"sessionStartDateIso\":\"2026-02-14T10:30:00.000Z\""))
        assertTrue(json.contains("\"sessionDurationMs\":45000"))
        assertTrue(json.contains("\"startupTimeMs\":450"))
        assertTrue(json.contains("\"rebufferTimeMs\":1200"))
        assertTrue(json.contains("\"rebufferCount\":2"))
        assertTrue(json.contains("\"playTimeMs\":42000"))
        assertTrue(json.contains("\"totalDroppedFrames\":12"))
        assertTrue(json.contains("\"totalSeekCount\":1"))
        assertTrue(json.contains("\"totalSeekTimeMs\":300"))
        assertTrue(json.contains("\"meanVideoFormatBitrate\":2500000"))
        assertTrue(json.contains("\"errorCount\":0"))
    }

    @Test
    fun toJson_nullableFieldsSerializedCorrectly() {
        val json = nullFieldsSummary.toJson()

        assertTrue(json.contains("\"startupTimeMs\":null"))
        assertTrue(json.contains("\"rebufferTimeMs\":null"))
        assertTrue(json.contains("\"rebufferCount\":null"))
        assertTrue(json.contains("\"playTimeMs\":null"))
        assertTrue(json.contains("\"rebufferRatio\":null"))
        assertTrue(json.contains("\"totalDroppedFrames\":null"))
        assertTrue(json.contains("\"totalSeekCount\":null"))
        assertTrue(json.contains("\"totalSeekTimeMs\":null"))
        assertTrue(json.contains("\"meanVideoFormatBitrate\":null"))
        assertTrue(json.contains("\"errorCount\":null"))

        // Non-nullable fields should still be present
        assertTrue(json.contains("\"sessionId\":2"))
        assertTrue(json.contains("\"sessionDurationMs\":1000"))
    }

    @Test
    fun fromJson_roundTrip() {
        val jsonString = fullSummary.toJson()
        val deserialized = Json.decodeFromString<SessionSummary>(jsonString)

        assertEquals(fullSummary, deserialized)
    }

    @Test
    fun toPrettyLog_matchesExpectedFormat() {
        val log = fullSummary.toPrettyLog()

        assertTrue(log.contains("session_end"))
        assertTrue(log.contains("  sessionId: 1"))
        assertTrue(log.contains("  sessionStartDateIso: 2026-02-14T10:30:00.000Z"))
        assertTrue(log.contains("  sessionDurationMs: 45000"))
        assertTrue(log.contains("  startupTimeMs: 450"))
        assertTrue(log.contains("  rebufferTimeMs: 1200"))
        assertTrue(log.contains("  rebufferCount: 2"))
        assertTrue(log.contains("  playTimeMs: 42000"))
        assertTrue(log.contains("  totalDroppedFrames: 12"))
        assertTrue(log.contains("  totalSeekCount: 1"))
        assertTrue(log.contains("  totalSeekTimeMs: 300"))
        assertTrue(log.contains("  meanVideoFormatBitrate: 2500000"))
        assertTrue(log.contains("  errorCount: 0"))
    }

    @Test
    fun toPrettyLog_nullFieldsShowNullString() {
        val log = nullFieldsSummary.toPrettyLog()

        assertTrue(log.contains("  startupTimeMs: null"))
        assertTrue(log.contains("  rebufferTimeMs: null"))
        assertTrue(log.contains("  errorCount: null"))
    }
}
