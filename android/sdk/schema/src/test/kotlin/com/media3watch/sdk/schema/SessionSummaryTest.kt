package com.media3watch.sdk.schema

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SessionSummaryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serialize and deserialize full SessionSummary`() {
        val original = SessionSummary(
            schemaVersion = SCHEMA_VERSION,
            sessionId = "test-session-123",
            timestamp = 1706900000000L,
            contentId = "video-456",
            streamType = StreamType.VOD,
            startupTimeMs = 1850L,
            playTimeMs = 120000L,
            rebufferTimeMs = 1200L,
            rebufferCount = 2,
            rebufferRatio = 0.0099,
            errorCount = 0,
            lastErrorCode = null,
            lastErrorCategory = null,
            qualitySwitchCount = 3,
            avgBitrateKbps = 4200,
            droppedFrames = 12,
            device = DeviceInfo(
                model = "Pixel 8",
                os = "Android",
                osVersion = "14"
            ),
            app = AppInfo(
                name = "MyApp",
                version = "1.0.0"
            ),
            custom = mapOf(
                "userId" to "user-abc",
                "experimentGroup" to "variant-b"
            )
        )

        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals(original, deserialized)
    }

    @Test
    fun `serialize and deserialize minimal SessionSummary with nulls`() {
        val minimal = SessionSummary(
            schemaVersion = SCHEMA_VERSION,
            sessionId = "minimal-session",
            timestamp = 1706900000000L
        )

        val jsonString = json.encodeToString(minimal)
        val deserialized = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals(minimal, deserialized)
        assertEquals(SCHEMA_VERSION, deserialized.schemaVersion)
        assertEquals("minimal-session", deserialized.sessionId)
        assertEquals(1706900000000L, deserialized.timestamp)
    }

    @Test
    fun `deserialize SessionSummary from JSON string`() {
        val jsonString = """
            {
                "schemaVersion": "1.0.0",
                "sessionId": "abc-123",
                "timestamp": 1706900000000,
                "contentId": "video-456",
                "streamType": "VOD",
                "startupTimeMs": 1850,
                "playTimeMs": 120000,
                "rebufferTimeMs": 1200,
                "rebufferCount": 2,
                "rebufferRatio": 0.0099,
                "errorCount": 0,
                "qualitySwitchCount": 3,
                "avgBitrateKbps": 4200,
                "droppedFrames": 12,
                "device": {
                    "model": "Pixel 8",
                    "os": "Android",
                    "osVersion": "14"
                },
                "app": {
                    "name": "MyApp",
                    "version": "1.0.0"
                }
            }
        """.trimIndent()

        val session = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals("1.0.0", session.schemaVersion)
        assertEquals("abc-123", session.sessionId)
        assertEquals(StreamType.VOD, session.streamType)
        assertEquals(1850L, session.startupTimeMs)
        assertNotNull(session.device)
        assertEquals("Pixel 8", session.device?.model)
    }
}
