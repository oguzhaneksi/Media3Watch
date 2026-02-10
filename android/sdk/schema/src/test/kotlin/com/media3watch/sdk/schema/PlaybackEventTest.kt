package com.media3watch.sdk.schema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class PlaybackEventTest {
    private val testTimestamp = 1706900000000L

    @Test
    fun `PlayRequested event is instantiable`() {
        val event = PlaybackEvent.PlayRequested(testTimestamp)
        assertEquals(testTimestamp, event.timestamp)
    }

    @Test
    fun `FirstFrameRendered event is instantiable`() {
        val event = PlaybackEvent.FirstFrameRendered(testTimestamp)
        assertEquals(testTimestamp, event.timestamp)
    }

    @Test
    fun `BufferingStarted event is instantiable`() {
        val event = PlaybackEvent.BufferingStarted(testTimestamp)
        assertEquals(testTimestamp, event.timestamp)
    }

    @Test
    fun `BufferingEnded event is instantiable with duration`() {
        val event = PlaybackEvent.BufferingEnded(testTimestamp, durationMs = 500)
        assertEquals(testTimestamp, event.timestamp)
        assertEquals(500L, event.durationMs)
    }

    @Test
    fun `IsPlayingChanged event is instantiable with isPlaying flag`() {
        val event = PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true)
        assertEquals(testTimestamp, event.timestamp)
        assertEquals(true, event.isPlaying)
    }

    @Test
    fun `SeekStarted event is instantiable`() {
        val event = PlaybackEvent.SeekStarted(testTimestamp)
        assertEquals(testTimestamp, event.timestamp)
    }

    @Test
    fun `SeekEnded event is instantiable with duration`() {
        val event = PlaybackEvent.SeekEnded(testTimestamp, durationMs = 300)
        assertEquals(testTimestamp, event.timestamp)
        assertEquals(300L, event.durationMs)
    }

    @Test
    fun `PlayerError event is instantiable with error details`() {
        val event = PlaybackEvent.PlayerError(
            testTimestamp,
            errorCode = 1001,
            errorCategory = ErrorCategory.NETWORK
        )
        assertEquals(testTimestamp, event.timestamp)
        assertEquals(1001, event.errorCode)
        assertEquals(ErrorCategory.NETWORK, event.errorCategory)
    }

    @Test
    fun `MediaItemTransition event is instantiable with contentId`() {
        val event = PlaybackEvent.MediaItemTransition(testTimestamp, newContentId = "video-123")
        assertEquals(testTimestamp, event.timestamp)
        assertEquals("video-123", event.newContentId)
    }

    @Test
    fun `AppBackgrounded event is instantiable`() {
        val event = PlaybackEvent.AppBackgrounded(testTimestamp)
        assertEquals(testTimestamp, event.timestamp)
    }

    @Test
    fun `AppForegrounded event is instantiable`() {
        val event = PlaybackEvent.AppForegrounded(testTimestamp)
        assertEquals(testTimestamp, event.timestamp)
    }

    @Test
    fun `PlayerReleased event is instantiable`() {
        val event = PlaybackEvent.PlayerReleased(testTimestamp)
        assertEquals(testTimestamp, event.timestamp)
    }

    @Test
    fun `PlaybackEnded event is instantiable`() {
        val event = PlaybackEvent.PlaybackEnded(testTimestamp)
        assertEquals(testTimestamp, event.timestamp)
    }

    @Test
    fun `BackgroundIdleTimeout event is instantiable`() {
        val event = PlaybackEvent.BackgroundIdleTimeout(testTimestamp)
        assertEquals(testTimestamp, event.timestamp)
    }

    @Test
    fun `all PlaybackEvent types are sealed class subtypes`() {
        val events: List<PlaybackEvent> = listOf(
            PlaybackEvent.PlayRequested(testTimestamp),
            PlaybackEvent.FirstFrameRendered(testTimestamp),
            PlaybackEvent.BufferingStarted(testTimestamp),
            PlaybackEvent.BufferingEnded(testTimestamp, 100),
            PlaybackEvent.IsPlayingChanged(testTimestamp, true),
            PlaybackEvent.SeekStarted(testTimestamp),
            PlaybackEvent.SeekEnded(testTimestamp, 200),
            PlaybackEvent.PlayerError(testTimestamp, 1001, ErrorCategory.NETWORK),
            PlaybackEvent.MediaItemTransition(testTimestamp, "test"),
            PlaybackEvent.AppBackgrounded(testTimestamp),
            PlaybackEvent.AppForegrounded(testTimestamp),
            PlaybackEvent.PlayerReleased(testTimestamp),
            PlaybackEvent.PlaybackEnded(testTimestamp),
            PlaybackEvent.BackgroundIdleTimeout(testTimestamp)
        )

        assertEquals(14, events.size)
        events.forEach { event ->
            assertNotNull(event)
            assertEquals(testTimestamp, event.timestamp)
        }
    }
}
