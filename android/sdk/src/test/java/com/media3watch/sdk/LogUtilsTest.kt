package com.media3watch.sdk

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LogUtils].
 *
 * These tests run on the JVM (no Robolectric required) because [LogUtils] is pure Kotlin/Java
 * with no Android framework dependencies beyond [android.util.Log] — which is not exercised in
 * the formatter tests below.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class LogUtilsTest {

    // ── toIsoDateTime ────────────────────────────────────────────────────────────

    @Test
    fun toIsoDateTime_zeroEpoch_returnsNullString() {
        assertEquals("null", LogUtils.toIsoDateTime(0L))
    }

    @Test
    fun toIsoDateTime_negativeEpoch_returnsNullString() {
        assertEquals("null", LogUtils.toIsoDateTime(-1L))
    }

    @Test
    fun toIsoDateTime_validEpoch_returnsIso8601String() {
        // 2024-01-15T00:00:00.000Z in epoch millis
        val epochMillis = 1705276800000L
        val result = LogUtils.toIsoDateTime(epochMillis)

        // Must match the ISO-8601 pattern yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
        val iso8601Pattern = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")
        assertTrue("Expected ISO-8601 string, got: $result", iso8601Pattern.matches(result))
        assertTrue("Expected date to start with 2024-01-15", result.startsWith("2024-01-15"))
    }

    @Test
    fun toIsoDateTime_isConcurrentlySafe() {
        // Verify the ThreadLocal formatter doesn't produce corrupted output under concurrent load.
        val epochMillis = 1705276800000L
        val results = mutableListOf<String>()
        val threads = (1..10).map {
            Thread { synchronized(results) { results += LogUtils.toIsoDateTime(epochMillis) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertTrue("Expected 10 results", results.size == 10)
        results.forEach { result ->
            assertFalse("Result must not be 'null' for a valid epoch", result == "null")
            assertTrue(
                "All concurrent results must be identical, got: $result",
                result == results[0]
            )
        }
    }

    @Test
    fun toIsoDateTime_alwaysUsesUtcTimezone() {
        // Epoch 0 + 1 ms → we already return "null" for epoch <= 0, so pick epoch = 1 ms past epoch.
        // This is just before 1970-01-01T00:00:00.001Z in UTC.
        val result = LogUtils.toIsoDateTime(1L)
        assertTrue("Expected UTC date 1970-01-01, got: $result", result.startsWith("1970-01-01"))
        assertTrue("Expected time 00:00:00.001 in UTC, got: $result", result.contains("T00:00:00.001Z"))
    }

    // ── buildSessionSummary ──────────────────────────────────────────────────────

    @Test
    fun buildSessionSummary_withNullStats_setsNullableFieldsToNull() {
        val summary = LogUtils.buildSessionSummary(
            sessionId = "test-id",
            sessionStartWallClockMs = 1705276800000L,
            sessionStartTs = 1000L,
            now = 2000L,
            startupTimeMs = null,
            sessionEndStats = null,
        )

        assertEquals("test-id", summary.sessionId)
        assertEquals(1000L, summary.sessionDurationMs)
        assertEquals(null, summary.rebufferTimeMs)
        assertEquals(null, summary.rebufferCount)
        assertEquals(null, summary.playTimeMs)
        assertEquals(null, summary.errorCount)
    }

    @Test
    fun buildSessionSummary_sessionDurationMs_isClampedToZeroWhenNowLessThanStart() {
        val summary = LogUtils.buildSessionSummary(
            sessionId = "test-id",
            sessionStartWallClockMs = 1705276800000L,
            sessionStartTs = 5000L,
            now = 4000L, // now < start → should clamp to 0
            startupTimeMs = null,
            sessionEndStats = null,
        )

        assertEquals(0L, summary.sessionDurationMs)
    }

    @Test
    fun buildSessionSummary_deviceAndConnectionMetadata_passedThrough() {
        val summary = LogUtils.buildSessionSummary(
            sessionId = "meta-test",
            sessionStartWallClockMs = 1705276800000L,
            sessionStartTs = 0L,
            now = 1000L,
            startupTimeMs = 200L,
            sessionEndStats = null,
            deviceModel = "Pixel 7",
            osVersion = 33,
            sdkVersion = "1.0.0",
            connectionType = "Wi-Fi",
        )

        assertEquals("Pixel 7", summary.deviceModel)
        assertEquals(33, summary.osVersion)
        assertEquals("1.0.0", summary.sdkVersion)
        assertEquals("Wi-Fi", summary.connectionType)
    }

    @Test
    fun buildSessionSummary_sessionStartDateIso_formattedFromWallClockMs() {
        val summary = LogUtils.buildSessionSummary(
            sessionId = "date-test",
            sessionStartWallClockMs = 1705276800000L, // 2024-01-15T00:00:00.000Z
            sessionStartTs = 0L,
            now = 1000L,
            startupTimeMs = null,
            sessionEndStats = null,
        )

        assertTrue(
            "Expected ISO date starting with 2024-01-15, got: ${summary.sessionStartDateIso}",
            summary.sessionStartDateIso.startsWith("2024-01-15")
        )
    }
}
