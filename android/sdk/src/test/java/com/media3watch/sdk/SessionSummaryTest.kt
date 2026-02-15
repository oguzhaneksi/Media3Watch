package com.media3watch.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSummaryTest {

    @Test
    fun toJson_containsAllFields() {
        val summary = SessionSummary(
            sessionId = 5L,
            sessionStartDateIso = "2026-02-14T12:00:00.000Z",
            sessionDurationMs = 1200L,
            startupTimeMs = 220L,
            rebufferTimeMs = 30L,
            rebufferCount = 2,
            playTimeMs = 1000L,
            rebufferRatio = 0.03f,
            totalDroppedFrames = 10L,
            totalSeekCount = 3,
            totalSeekTimeMs = 90L,
            meanVideoFormatBitrate = 1800000,
            errorCount = 1
        )

        val jsonObject = Json.parseToJsonElement(summary.toJson()).jsonObject

        assertEquals(5L, jsonObject.getValue("sessionId").jsonPrimitive.long)
        assertEquals("2026-02-14T12:00:00.000Z", jsonObject.getValue("sessionStartDateIso").jsonPrimitive.content)
        assertEquals(1200L, jsonObject.getValue("sessionDurationMs").jsonPrimitive.long)
        assertEquals(220L, jsonObject.getValue("startupTimeMs").jsonPrimitive.long)
        assertEquals(30L, jsonObject.getValue("rebufferTimeMs").jsonPrimitive.long)
        assertEquals(2, jsonObject.getValue("rebufferCount").jsonPrimitive.int)
        assertEquals(1000L, jsonObject.getValue("playTimeMs").jsonPrimitive.long)
        assertEquals(0.03f, jsonObject.getValue("rebufferRatio").jsonPrimitive.float)
        assertEquals(10L, jsonObject.getValue("totalDroppedFrames").jsonPrimitive.long)
        assertEquals(3, jsonObject.getValue("totalSeekCount").jsonPrimitive.int)
        assertEquals(90L, jsonObject.getValue("totalSeekTimeMs").jsonPrimitive.long)
        assertEquals(1800000, jsonObject.getValue("meanVideoFormatBitrate").jsonPrimitive.int)
        assertEquals(1, jsonObject.getValue("errorCount").jsonPrimitive.int)
    }

    @Test
    fun toJson_nullableFieldsSerializedCorrectly() {
        val summary = SessionSummary(
            sessionId = 9L,
            sessionStartDateIso = "2026-02-14T00:00:00.000Z",
            sessionDurationMs = 500L,
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

        val jsonObject = Json.parseToJsonElement(summary.toJson()).jsonObject

        assertEquals(JsonNull, jsonObject["startupTimeMs"])
        assertEquals(JsonNull, jsonObject["rebufferTimeMs"])
        assertEquals(JsonNull, jsonObject["rebufferCount"])
        assertEquals(JsonNull, jsonObject["playTimeMs"])
        assertEquals(JsonNull, jsonObject["rebufferRatio"])
        assertEquals(JsonNull, jsonObject["totalDroppedFrames"])
        assertEquals(JsonNull, jsonObject["totalSeekCount"])
        assertEquals(JsonNull, jsonObject["totalSeekTimeMs"])
        assertEquals(JsonNull, jsonObject["meanVideoFormatBitrate"])
        assertEquals(JsonNull, jsonObject["errorCount"])
    }

    @Test
    fun toPrettyLog_matchesExpectedFormat() {
        val summary = SessionSummary(
            sessionId = 11L,
            sessionStartDateIso = "2026-02-14T18:30:00.000Z",
            sessionDurationMs = 1600L,
            startupTimeMs = 80L,
            rebufferTimeMs = 20L,
            rebufferCount = 1,
            playTimeMs = 1400L,
            rebufferRatio = 0.014f,
            totalDroppedFrames = 5L,
            totalSeekCount = 4,
            totalSeekTimeMs = 120L,
            meanVideoFormatBitrate = 2400000,
            errorCount = 0
        )

        val pretty = summary.toPrettyLog()

        val expected = buildString {
            appendLine("session_end")
            appendLine("  sessionId: 11")
            appendLine("  sessionStartDateIso: 2026-02-14T18:30:00.000Z")
            appendLine("  sessionDurationMs: 1600")
            appendLine("  startupTimeMs: 80")
            appendLine("  rebufferTimeMs: 20")
            appendLine("  rebufferCount: 1")
            appendLine("  playTimeMs: 1400")
            appendLine("  rebufferRatio: 0.014")
            appendLine("  totalDroppedFrames: 5")
            appendLine("  totalSeekCount: 4")
            appendLine("  totalSeekTimeMs: 120")
            appendLine("  meanVideoFormatBitrate: 2400000")
            appendLine("  errorCount: 0")
        }
        assertEquals(expected, pretty)
    }
}
