package com.media3watch.sdk.schema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaybackEventEdgeCaseTest {
    @Test
    fun `PlaybackEvent handles zero timestamp`() {
        val event = PlaybackEvent.PlayRequested(0L)
        assertEquals(0L, event.timestamp)
    }

    @Test
    fun `PlaybackEvent handles negative timestamp`() {
        val event = PlaybackEvent.FirstFrameRendered(-1000L)
        assertEquals(-1000L, event.timestamp)
    }

    @Test
    fun `PlaybackEvent handles max timestamp`() {
        val event = PlaybackEvent.PlaybackEnded(Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, event.timestamp)
    }

    @Test
    fun `BufferingEnded handles zero duration`() {
        val event = PlaybackEvent.BufferingEnded(1706900000000L, durationMs = 0L)
        assertEquals(0L, event.durationMs)
    }

    @Test
    fun `BufferingEnded handles negative duration`() {
        val event = PlaybackEvent.BufferingEnded(1706900000000L, durationMs = -500L)
        assertEquals(-500L, event.durationMs)
    }

    @Test
    fun `BufferingEnded handles very large duration`() {
        val event = PlaybackEvent.BufferingEnded(1706900000000L, durationMs = Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, event.durationMs)
    }

    @Test
    fun `SeekEnded handles zero duration`() {
        val event = PlaybackEvent.SeekEnded(1706900000000L, durationMs = 0L)
        assertEquals(0L, event.durationMs)
    }

    @Test
    fun `SeekEnded handles negative duration`() {
        val event = PlaybackEvent.SeekEnded(1706900000000L, durationMs = -100L)
        assertEquals(-100L, event.durationMs)
    }

    @Test
    fun `MediaItemTransition handles null contentId`() {
        val event = PlaybackEvent.MediaItemTransition(1706900000000L, newContentId = null)
        assertNull(event.newContentId)
    }

    @Test
    fun `MediaItemTransition handles empty contentId`() {
        val event = PlaybackEvent.MediaItemTransition(1706900000000L, newContentId = "")
        assertEquals("", event.newContentId)
    }

    @Test
    fun `MediaItemTransition handles long contentId`() {
        val longId = "a".repeat(1000)
        val event = PlaybackEvent.MediaItemTransition(1706900000000L, newContentId = longId)
        assertEquals(longId, event.newContentId)
    }

    @Test
    fun `PlayerError handles zero error code`() {
        val event =
            PlaybackEvent.PlayerError(
                1706900000000L,
                errorCode = 0,
                errorCategory = ErrorCategory.UNKNOWN,
            )
        assertEquals(0, event.errorCode)
    }

    @Test
    fun `PlayerError handles negative error code`() {
        val event =
            PlaybackEvent.PlayerError(
                1706900000000L,
                errorCode = -1,
                errorCategory = ErrorCategory.NETWORK,
            )
        assertEquals(-1, event.errorCode)
    }

    @Test
    fun `PlayerError handles all ErrorCategory types`() {
        val categories =
            listOf(
                ErrorCategory.NETWORK,
                ErrorCategory.DRM,
                ErrorCategory.SOURCE,
                ErrorCategory.DECODER,
                ErrorCategory.UNKNOWN,
            )

        categories.forEach { category ->
            val event =
                PlaybackEvent.PlayerError(
                    1706900000000L,
                    errorCode = 1001,
                    errorCategory = category,
                )
            assertEquals(category, event.errorCategory)
        }
    }

    @Test
    fun `IsPlayingChanged toggle behavior`() {
        val playing = PlaybackEvent.IsPlayingChanged(1706900000000L, isPlaying = true)
        val paused = PlaybackEvent.IsPlayingChanged(1706900000001L, isPlaying = false)

        assertEquals(true, playing.isPlaying)
        assertEquals(false, paused.isPlaying)
    }

    @Test
    fun `PlayWhenReadyChanged toggle behavior`() {
        val ready = PlaybackEvent.PlayWhenReadyChanged(1706900000000L, playWhenReady = true)
        val notReady = PlaybackEvent.PlayWhenReadyChanged(1706900000001L, playWhenReady = false)

        assertEquals(true, ready.playWhenReady)
        assertEquals(false, notReady.playWhenReady)
    }

    @Test
    fun `PlaybackStateChanged handles various Media3 states`() {
        // Media3 Player.STATE_IDLE = 1, STATE_BUFFERING = 2, STATE_READY = 3, STATE_ENDED = 4
        val states = listOf(1, 2, 3, 4)

        states.forEach { state ->
            val event = PlaybackEvent.PlaybackStateChanged(1706900000000L, playbackState = state)
            assertEquals(state, event.playbackState)
        }
    }

    @Test
    fun `PlaybackStateChanged handles invalid state values`() {
        val invalidStates = listOf(-1, 0, 999)

        invalidStates.forEach { state ->
            val event = PlaybackEvent.PlaybackStateChanged(1706900000000L, playbackState = state)
            assertEquals(state, event.playbackState)
        }
    }
}
