package com.media3watch.sdk.schema

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumSerializationTest {
    private val json = Json

    @Test
    fun `StreamType serializes to correct strings`() {
        assertEquals("\"VOD\"", json.encodeToString(StreamType.VOD))
        assertEquals("\"LIVE\"", json.encodeToString(StreamType.LIVE))
    }

    @Test
    fun `StreamType deserializes from strings`() {
        assertEquals(StreamType.VOD, json.decodeFromString<StreamType>("\"VOD\""))
        assertEquals(StreamType.LIVE, json.decodeFromString<StreamType>("\"LIVE\""))
    }

    @Test
    fun `ErrorCategory serializes to correct strings`() {
        assertEquals("\"NETWORK\"", json.encodeToString(ErrorCategory.NETWORK))
        assertEquals("\"DRM\"", json.encodeToString(ErrorCategory.DRM))
        assertEquals("\"SOURCE\"", json.encodeToString(ErrorCategory.SOURCE))
        assertEquals("\"DECODER\"", json.encodeToString(ErrorCategory.DECODER))
        assertEquals("\"UNKNOWN\"", json.encodeToString(ErrorCategory.UNKNOWN))
    }

    @Test
    fun `ErrorCategory deserializes from strings`() {
        assertEquals(ErrorCategory.NETWORK, json.decodeFromString<ErrorCategory>("\"NETWORK\""))
        assertEquals(ErrorCategory.DRM, json.decodeFromString<ErrorCategory>("\"DRM\""))
        assertEquals(ErrorCategory.SOURCE, json.decodeFromString<ErrorCategory>("\"SOURCE\""))
        assertEquals(ErrorCategory.DECODER, json.decodeFromString<ErrorCategory>("\"DECODER\""))
        assertEquals(ErrorCategory.UNKNOWN, json.decodeFromString<ErrorCategory>("\"UNKNOWN\""))
    }

    @Test
    fun `EndReason values are correct`() {
        val reasons = EndReason.entries
        assertEquals(5, reasons.size)
        assertEquals(EndReason.PLAYER_RELEASED, EndReason.valueOf("PLAYER_RELEASED"))
        assertEquals(EndReason.PLAYBACK_ENDED, EndReason.valueOf("PLAYBACK_ENDED"))
        assertEquals(EndReason.PLAYER_REPLACED, EndReason.valueOf("PLAYER_REPLACED"))
        assertEquals(EndReason.CONTENT_SWITCH, EndReason.valueOf("CONTENT_SWITCH"))
        assertEquals(EndReason.BACKGROUND_IDLE_TIMEOUT, EndReason.valueOf("BACKGROUND_IDLE_TIMEOUT"))
    }

    @Test
    fun `SessionState values are correct`() {
        val states = SessionState.entries
        assertEquals(8, states.size)
        assertEquals(SessionState.NO_SESSION, SessionState.valueOf("NO_SESSION"))
        assertEquals(SessionState.ATTACHED, SessionState.valueOf("ATTACHED"))
        assertEquals(SessionState.PLAYING, SessionState.valueOf("PLAYING"))
        assertEquals(SessionState.PAUSED, SessionState.valueOf("PAUSED"))
        assertEquals(SessionState.BUFFERING, SessionState.valueOf("BUFFERING"))
        assertEquals(SessionState.SEEKING, SessionState.valueOf("SEEKING"))
        assertEquals(SessionState.BACKGROUND, SessionState.valueOf("BACKGROUND"))
        assertEquals(SessionState.ENDED, SessionState.valueOf("ENDED"))
    }
}
