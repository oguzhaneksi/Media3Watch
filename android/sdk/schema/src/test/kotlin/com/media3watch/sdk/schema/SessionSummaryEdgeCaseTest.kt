package com.media3watch.sdk.schema

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionSummaryEdgeCaseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SessionSummary handles negative metric values`() {
        val session =
            SessionSummary(
                schemaVersion = SCHEMA_VERSION,
                sessionId = "test-negative",
                timestamp = 1706900000000L,
                startupTimeMs = -1L,
                rebufferTimeMs = -100L,
                rebufferCount = -5,
                errorCount = -1,
            )

        val jsonString = json.encodeToString(session)
        val deserialized = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals(session, deserialized)
        assertEquals(-1L, deserialized.startupTimeMs)
        assertEquals(-5, deserialized.rebufferCount)
    }

    @Test
    fun `SessionSummary handles zero values correctly`() {
        val session =
            SessionSummary(
                schemaVersion = SCHEMA_VERSION,
                sessionId = "test-zero",
                timestamp = 1706900000000L,
                startupTimeMs = 0L,
                playTimeMs = 0L,
                rebufferTimeMs = 0L,
                rebufferCount = 0,
                rebufferRatio = 0.0,
                errorCount = 0,
                droppedFrames = 0,
            )

        val jsonString = json.encodeToString(session)
        val deserialized = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals(session, deserialized)
        assertEquals(0L, deserialized.startupTimeMs)
        assertEquals(0.0, deserialized.rebufferRatio)
    }

    @Test
    fun `SessionSummary handles large metric values`() {
        val session =
            SessionSummary(
                schemaVersion = SCHEMA_VERSION,
                sessionId = "test-large",
                timestamp = 1706900000000L,
                startupTimeMs = Long.MAX_VALUE,
                playTimeMs = Long.MAX_VALUE,
                rebufferCount = Int.MAX_VALUE,
                avgBitrateKbps = Int.MAX_VALUE,
            )

        val jsonString = json.encodeToString(session)
        val deserialized = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals(session, deserialized)
        assertEquals(Long.MAX_VALUE, deserialized.startupTimeMs)
    }

    @Test
    fun `SessionSummary handles empty custom map`() {
        val session =
            SessionSummary(
                schemaVersion = SCHEMA_VERSION,
                sessionId = "test-empty-map",
                timestamp = 1706900000000L,
                custom = emptyMap(),
            )

        val jsonString = json.encodeToString(session)
        val deserialized = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals(session, deserialized)
        assertNotNull(deserialized.custom)
        assertEquals(0, deserialized.custom?.size)
    }

    @Test
    fun `SessionSummary handles large custom map`() {
        val largeMap = (1..100).associate { "key$it" to "value$it" }
        val session =
            SessionSummary(
                schemaVersion = SCHEMA_VERSION,
                sessionId = "test-large-map",
                timestamp = 1706900000000L,
                custom = largeMap,
            )

        val jsonString = json.encodeToString(session)
        val deserialized = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals(session, deserialized)
        assertEquals(100, deserialized.custom?.size)
        assertEquals("value50", deserialized.custom?.get("key50"))
    }

    @Test
    fun `SessionSummary handles special characters in custom map`() {
        val session =
            SessionSummary(
                schemaVersion = SCHEMA_VERSION,
                sessionId = "test-special-chars",
                timestamp = 1706900000000L,
                custom =
                    mapOf(
                        "key with spaces" to "value with spaces",
                        "unicode_😀" to "emoji_🎬",
                        "escaped\"quotes" to "more\"quotes",
                    ),
            )

        val jsonString = json.encodeToString(session)
        val deserialized = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals(session, deserialized)
        assertEquals("value with spaces", deserialized.custom?.get("key with spaces"))
        assertEquals("emoji_🎬", deserialized.custom?.get("unicode_😀"))
    }

    @Test
    fun `SessionSummary deserializes with unknown fields (forward compatibility)`() {
        val jsonString =
            """
            {
                "schemaVersion": "1.0.0",
                "sessionId": "future-session",
                "timestamp": 1706900000000,
                "contentId": "video-123",
                "newFutureField": "should be ignored",
                "anotherUnknownField": 42,
                "nestedUnknownObject": {
                    "foo": "bar"
                }
            }
            """.trimIndent()

        val session = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals("1.0.0", session.schemaVersion)
        assertEquals("future-session", session.sessionId)
        assertEquals("video-123", session.contentId)
    }

    @Test
    fun `SessionSummary handles different schema versions`() {
        val v1 =
            SessionSummary(
                schemaVersion = "1.0.0",
                sessionId = "v1-session",
                timestamp = 1706900000000L,
            )

        val v2 =
            SessionSummary(
                schemaVersion = "2.0.0",
                sessionId = "v2-session",
                timestamp = 1706900000000L,
            )

        val jsonV1 = json.encodeToString(v1)
        val jsonV2 = json.encodeToString(v2)

        val deserializedV1 = json.decodeFromString<SessionSummary>(jsonV1)
        val deserializedV2 = json.decodeFromString<SessionSummary>(jsonV2)

        assertEquals("1.0.0", deserializedV1.schemaVersion)
        assertEquals("2.0.0", deserializedV2.schemaVersion)
    }

    @Test
    fun `SessionSummary with extreme rebuffer ratio`() {
        val session =
            SessionSummary(
                schemaVersion = SCHEMA_VERSION,
                sessionId = "extreme-rebuffer",
                timestamp = 1706900000000L,
                rebufferRatio = 0.999999,
            )

        val jsonString = json.encodeToString(session)
        val deserialized = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals(session, deserialized)
        assertEquals(0.999999, deserialized.rebufferRatio)
    }

    @Test
    fun `SessionSummary with null error fields when no errors`() {
        val session =
            SessionSummary(
                schemaVersion = SCHEMA_VERSION,
                sessionId = "no-errors",
                timestamp = 1706900000000L,
                errorCount = 0,
                lastErrorCode = null,
                lastErrorCategory = null,
            )

        val jsonString = json.encodeToString(session)
        val deserialized = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals(0, deserialized.errorCount)
        assertNull(deserialized.lastErrorCode)
        assertNull(deserialized.lastErrorCategory)
    }

    @Test
    fun `SessionSummary with error fields populated`() {
        val session =
            SessionSummary(
                schemaVersion = SCHEMA_VERSION,
                sessionId = "with-errors",
                timestamp = 1706900000000L,
                errorCount = 3,
                lastErrorCode = 4001,
                lastErrorCategory = ErrorCategory.NETWORK,
            )

        val jsonString = json.encodeToString(session)
        val deserialized = json.decodeFromString<SessionSummary>(jsonString)

        assertEquals(3, deserialized.errorCount)
        assertEquals(4001, deserialized.lastErrorCode)
        assertEquals(ErrorCategory.NETWORK, deserialized.lastErrorCategory)
    }
}
