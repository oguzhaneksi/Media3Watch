package com.media3watch.sdk.adapter_media3

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.media3watch.sdk.schema.ErrorCategory
import com.media3watch.sdk.schema.PlaybackEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

/**
 * Comprehensive tests for Media3PlaybackEventBridge.
 * Tests cover all Player.Listener callback mappings, error categorization,
 * lifecycle management, and edge cases as specified in issue requirements.
 */
class Media3PlaybackEventBridgeTest {
    private lateinit var bridge: Media3PlaybackEventBridge
    private lateinit var mockPlayer: Player
    private val events = mutableListOf<PlaybackEvent>()
    private lateinit var listenerSlot: slot<Player.Listener>

    @BeforeEach
    fun setup() {
        events.clear()
        mockPlayer = mockk(relaxed = true)
        listenerSlot = slot()
        every { mockPlayer.addListener(capture(listenerSlot)) } returns Unit
        every { mockPlayer.removeListener(any()) } returns Unit

        bridge =
            Media3PlaybackEventBridge { event ->
                events.add(event)
            }
    }

    @AfterEach
    fun tearDown() {
        events.clear()
    }

    // ========== HAPPY PATH: Basic Callback Mapping ==========

    @Test
    fun `onPlayWhenReadyChanged true emits PlayWhenReadyChanged with playWhenReady=true`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(1, events.size)
        val event = events[0] as PlaybackEvent.PlayWhenReadyChanged
        assertTrue(event.playWhenReady)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `onPlayWhenReadyChanged false emits PlayWhenReadyChanged with playWhenReady=false`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(1, events.size)
        val event = events[0] as PlaybackEvent.PlayWhenReadyChanged
        assertFalse(event.playWhenReady)
    }

    @Test
    fun `onIsPlayingChanged true emits IsPlayingChanged with isPlaying=true`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onIsPlayingChanged(true)

        assertEquals(1, events.size)
        val event = events[0] as PlaybackEvent.IsPlayingChanged
        assertTrue(event.isPlaying)
    }

    @Test
    fun `onIsPlayingChanged false emits IsPlayingChanged with isPlaying=false`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onIsPlayingChanged(false)

        assertEquals(1, events.size)
        val event = events[0] as PlaybackEvent.IsPlayingChanged
        assertFalse(event.isPlaying)
    }

    @Test
    fun `onPlaybackStateChanged STATE_IDLE emits PlaybackStateChanged`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_IDLE)

        assertEquals(1, events.size)
        val event = events[0] as PlaybackEvent.PlaybackStateChanged
        assertEquals(Player.STATE_IDLE, event.playbackState)
    }

    @Test
    fun `onPlaybackStateChanged STATE_BUFFERING emits both PlaybackStateChanged and BufferingStarted`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)

        assertEquals(2, events.size)
        assertTrue(events[0] is PlaybackEvent.BufferingStarted)
        assertTrue(events[1] is PlaybackEvent.PlaybackStateChanged)
        assertEquals(Player.STATE_BUFFERING, (events[1] as PlaybackEvent.PlaybackStateChanged).playbackState)
    }

    @Test
    fun `onPlaybackStateChanged STATE_READY emits PlaybackStateChanged`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_READY)

        assertEquals(1, events.size)
        assertTrue(events[0] is PlaybackEvent.PlaybackStateChanged)
        assertEquals(Player.STATE_READY, (events[0] as PlaybackEvent.PlaybackStateChanged).playbackState)
    }

    @Test
    fun `onPlaybackStateChanged STATE_READY after STATE_BUFFERING emits BufferingEnded`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        events.clear()
        Thread.sleep(10)

        listener.onPlaybackStateChanged(Player.STATE_READY)

        assertTrue(events.any { it is PlaybackEvent.BufferingEnded })
        val bufferingEnded = events.first { it is PlaybackEvent.BufferingEnded } as PlaybackEvent.BufferingEnded
        assertTrue(bufferingEnded.durationMs >= 0)
    }

    @Test
    fun `onPlaybackStateChanged STATE_ENDED emits both PlaybackStateChanged and PlaybackEnded`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_ENDED)

        assertEquals(2, events.size)
        assertTrue(events[0] is PlaybackEvent.PlaybackStateChanged)
        assertTrue(events[1] is PlaybackEvent.PlaybackEnded)
    }

    @Test
    fun `onRenderedFirstFrame emits FirstFrameRendered`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onRenderedFirstFrame()

        assertEquals(1, events.size)
        assertTrue(events[0] is PlaybackEvent.FirstFrameRendered)
    }

    @Test
    fun `onMediaItemTransition emits MediaItemTransition with contentId`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val mediaItem = mockk<MediaItem>()
        every { mediaItem.mediaId } returns "video-123"

        listener.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        assertEquals(1, events.size)
        val event = events[0] as PlaybackEvent.MediaItemTransition
        assertEquals("video-123", event.newContentId)
    }

    // ========== HAPPY PATH: Seek Detection ==========

    @Test
    fun `onPositionDiscontinuity with DISCONTINUITY_REASON_SEEK emits SeekStarted and SeekEnded`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val oldPos = mockk<Player.PositionInfo>()
        val newPos = mockk<Player.PositionInfo>()

        listener.onPositionDiscontinuity(oldPos, newPos, Player.DISCONTINUITY_REASON_SEEK)

        assertEquals(2, events.size)
        assertTrue(events[0] is PlaybackEvent.SeekStarted)
        assertTrue(events[1] is PlaybackEvent.SeekEnded)
    }

    @Test
    fun `onPositionDiscontinuity with DISCONTINUITY_REASON_AUTO_TRANSITION does NOT emit seek events`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val oldPos = mockk<Player.PositionInfo>()
        val newPos = mockk<Player.PositionInfo>()

        listener.onPositionDiscontinuity(oldPos, newPos, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)

        assertEquals(0, events.size)
    }

    // ========== HAPPY PATH: Error Categorization ==========

    @Test
    fun `Media3 IOException maps to PlayerError with ErrorCategory NETWORK`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val exception =
            PlaybackException(
                "Network error",
                IOException("Connection failed"),
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            )

        listener.onPlayerError(exception)

        assertEquals(1, events.size)
        val event = events[0] as PlaybackEvent.PlayerError
        assertEquals(ErrorCategory.NETWORK, event.errorCategory)
    }

    @Test
    fun `Media3 DRM error maps to PlayerError with ErrorCategory DRM`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val exception =
            PlaybackException(
                "DRM error",
                null,
                PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            )

        listener.onPlayerError(exception)

        assertEquals(1, events.size)
        val event = events[0] as PlaybackEvent.PlayerError
        assertEquals(ErrorCategory.DRM, event.errorCategory)
    }

    @Test
    fun `Media3 source error maps to PlayerError with ErrorCategory SOURCE`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val exception =
            PlaybackException(
                "Source error",
                null,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            )

        listener.onPlayerError(exception)

        assertEquals(1, events.size)
        val event = events[0] as PlaybackEvent.PlayerError
        assertEquals(ErrorCategory.SOURCE, event.errorCategory)
    }

    @Test
    fun `Media3 decoder error maps to PlayerError with ErrorCategory DECODER`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val exception =
            PlaybackException(
                "Decoder error",
                null,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            )

        listener.onPlayerError(exception)

        assertEquals(1, events.size)
        val event = events[0] as PlaybackEvent.PlayerError
        assertEquals(ErrorCategory.DECODER, event.errorCategory)
    }

    @Test
    fun `Unknown error type maps to PlayerError with ErrorCategory UNKNOWN`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val exception =
            PlaybackException(
                "Unknown error",
                null,
                PlaybackException.ERROR_CODE_UNSPECIFIED,
            )

        listener.onPlayerError(exception)

        assertEquals(1, events.size)
        val event = events[0] as PlaybackEvent.PlayerError
        assertEquals(ErrorCategory.UNKNOWN, event.errorCategory)
    }

    // ========== HAPPY PATH: Lifecycle ==========

    @Test
    fun `attach registers listener on player`() {
        bridge.attach(mockPlayer)

        verify(exactly = 1) { mockPlayer.addListener(any()) }
    }

    @Test
    fun `detach unregisters listener from player`() {
        bridge.attach(mockPlayer)
        bridge.detach()

        verify { mockPlayer.removeListener(any()) }
    }

    @Test
    fun `events emitted after attach are forwarded correctly`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onIsPlayingChanged(true)

        assertEquals(1, events.size)
        assertTrue(events[0] is PlaybackEvent.IsPlayingChanged)
    }

    @Test
    fun `no events emitted before attach`() {
        val bridge =
            Media3PlaybackEventBridge { event ->
                events.add(event)
            }

        assertEquals(0, events.size)
    }

    // ========== EDGE CASES: Rapid State Transitions ==========

    @Test
    fun `rapid play pause play sequence emits all corresponding events in order`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        listener.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(3, events.size)
        assertTrue((events[0] as PlaybackEvent.PlayWhenReadyChanged).playWhenReady)
        assertFalse((events[1] as PlaybackEvent.PlayWhenReadyChanged).playWhenReady)
        assertTrue((events[2] as PlaybackEvent.PlayWhenReadyChanged).playWhenReady)
    }

    @Test
    fun `STATE_BUFFERING to STATE_READY to STATE_BUFFERING emits correct sequence`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        val firstBufferingEvents = events.size
        events.clear()

        listener.onPlaybackStateChanged(Player.STATE_READY)
        assertTrue(events.any { it is PlaybackEvent.BufferingEnded })
        events.clear()

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertTrue(events.any { it is PlaybackEvent.BufferingStarted })
    }

    @Test
    fun `multiple consecutive onPlayWhenReadyChanged true emits multiple events`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(3, events.size)
        events.forEach {
            assertTrue(it is PlaybackEvent.PlayWhenReadyChanged)
            assertTrue((it as PlaybackEvent.PlayWhenReadyChanged).playWhenReady)
        }
    }

    @Test
    fun `STATE_ENDED immediately after STATE_BUFFERING emits correct sequence`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        events.clear()

        listener.onPlaybackStateChanged(Player.STATE_ENDED)

        assertTrue(events.any { it is PlaybackEvent.PlaybackStateChanged })
        assertTrue(events.any { it is PlaybackEvent.PlaybackEnded })
    }

    // ========== EDGE CASES: Buffering ==========

    @Test
    fun `STATE_BUFFERING to STATE_BUFFERING duplicate does NOT emit duplicate BufferingStarted`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        val firstEventCount = events.filter { it is PlaybackEvent.BufferingStarted }.size
        events.clear()

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        val secondEventCount = events.filter { it is PlaybackEvent.BufferingStarted }.size

        assertEquals(0, secondEventCount)
    }

    @Test
    fun `STATE_READY to STATE_READY duplicate does NOT emit BufferingEnded`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_READY)
        events.clear()

        listener.onPlaybackStateChanged(Player.STATE_READY)

        assertFalse(events.any { it is PlaybackEvent.BufferingEnded })
    }

    @Test
    fun `first STATE_BUFFERING after initialization emits BufferingStarted`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)

        assertTrue(events.any { it is PlaybackEvent.BufferingStarted })
    }

    // ========== EDGE CASES: Lifecycle ==========

    @Test
    fun `events after detach are NOT forwarded`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        bridge.detach()

        listener.onIsPlayingChanged(true)

        assertEquals(0, events.size)
    }

    @Test
    fun `detach when not attached does not crash`() {
        bridge.detach()
        // Should not throw
    }

    @Test
    fun `attach twice without detach replaces previous listener`() {
        val mockPlayer2 = mockk<Player>(relaxed = true)
        val listenerSlot2 = slot<Player.Listener>()
        every { mockPlayer2.addListener(capture(listenerSlot2)) } returns Unit

        bridge.attach(mockPlayer)
        bridge.attach(mockPlayer2)

        verify { mockPlayer.removeListener(any()) }
        verify { mockPlayer2.addListener(any()) }
    }

    // ========== EDGE CASES: Callback Ordering ==========

    @Test
    fun `onPlaybackStateChanged fires before onIsPlayingChanged - both events emitted correctly`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_READY)
        listener.onIsPlayingChanged(true)

        assertEquals(2, events.size)
        assertTrue(events[0] is PlaybackEvent.PlaybackStateChanged)
        assertTrue(events[1] is PlaybackEvent.IsPlayingChanged)
    }

    @Test
    fun `onRenderedFirstFrame fires after STATE_READY - both events captured`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_READY)
        listener.onRenderedFirstFrame()

        assertEquals(2, events.size)
        assertTrue(events[0] is PlaybackEvent.PlaybackStateChanged)
        assertTrue(events[1] is PlaybackEvent.FirstFrameRendered)
    }

    @Test
    fun `onPlayerError fires during STATE_BUFFERING - error event and buffering state tracked`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        events.clear()

        val exception =
            PlaybackException(
                "Test error",
                null,
                PlaybackException.ERROR_CODE_UNSPECIFIED,
            )
        listener.onPlayerError(exception)

        assertTrue(events.any { it is PlaybackEvent.PlayerError })
    }

    // ========== EDGE CASES: Seek ==========

    @Test
    fun `seek during buffering emits seek events correctly`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val oldPos = mockk<Player.PositionInfo>()
        val newPos = mockk<Player.PositionInfo>()

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        events.clear()

        listener.onPositionDiscontinuity(oldPos, newPos, Player.DISCONTINUITY_REASON_SEEK)

        assertTrue(events.any { it is PlaybackEvent.SeekStarted })
        assertTrue(events.any { it is PlaybackEvent.SeekEnded })
    }

    @Test
    fun `multiple seeks in quick succession emit all seek pairs`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val oldPos = mockk<Player.PositionInfo>()
        val newPos = mockk<Player.PositionInfo>()

        listener.onPositionDiscontinuity(oldPos, newPos, Player.DISCONTINUITY_REASON_SEEK)
        listener.onPositionDiscontinuity(oldPos, newPos, Player.DISCONTINUITY_REASON_SEEK)
        listener.onPositionDiscontinuity(oldPos, newPos, Player.DISCONTINUITY_REASON_SEEK)

        val seekStartedCount = events.count { it is PlaybackEvent.SeekStarted }
        val seekEndedCount = events.count { it is PlaybackEvent.SeekEnded }
        assertEquals(3, seekStartedCount)
        assertEquals(3, seekEndedCount)
    }

    @Test
    fun `onPositionDiscontinuity with unknown reason does not crash`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val oldPos = mockk<Player.PositionInfo>()
        val newPos = mockk<Player.PositionInfo>()

        listener.onPositionDiscontinuity(oldPos, newPos, 999)

        // Should not crash, no seek events emitted
        assertFalse(events.any { it is PlaybackEvent.SeekStarted })
    }

    // ========== FAILURE SCENARIOS: Error Handling ==========

    @Test
    fun `onPlayerError with null cause does not crash`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val exception =
            PlaybackException(
                "Error without cause",
                null,
                PlaybackException.ERROR_CODE_UNSPECIFIED,
            )

        listener.onPlayerError(exception)

        assertEquals(1, events.size)
        assertTrue(events[0] is PlaybackEvent.PlayerError)
    }

    @Test
    fun `onPlayerError with unsupported error subclass maps to ErrorCategory UNKNOWN`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val exception =
            PlaybackException(
                "Unsupported error",
                RuntimeException("Unknown"),
                9999,
            )

        listener.onPlayerError(exception)

        val event = events[0] as PlaybackEvent.PlayerError
        assertEquals(ErrorCategory.UNKNOWN, event.errorCategory)
    }

    @Test
    fun `malformed Media3 error with null cause handled gracefully`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val exception =
            PlaybackException(
                null,
                null,
                PlaybackException.ERROR_CODE_UNSPECIFIED,
            )

        listener.onPlayerError(exception)

        assertTrue(events[0] is PlaybackEvent.PlayerError)
    }

    // ========== FAILURE SCENARIOS: Null Safety ==========

    @Test
    fun `onMediaItemTransition with null mediaItem does not crash`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        assertEquals(1, events.size)
        val event = events[0] as PlaybackEvent.MediaItemTransition
        assertEquals(null, event.newContentId)
    }

    @Test
    fun `attach with null player throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            bridge.attach(null!!)
        }
    }

    // ========== FAILURE SCENARIOS: Callback Exceptions ==========

    @Test
    fun `if PlaybackEvent callback throws exception bridge remains operational`() {
        var throwCount = 0
        val faultyBridge =
            Media3PlaybackEventBridge { event ->
                throwCount++
                if (throwCount == 1) {
                    throw RuntimeException("Test exception")
                }
                events.add(event)
            }
        faultyBridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onIsPlayingChanged(true)
        listener.onIsPlayingChanged(false)

        // Second event should still be processed
        assertEquals(1, events.size)
    }

    @Test
    fun `exception in one callback does not prevent subsequent callbacks from firing`() {
        var firstCall = true
        val faultyBridge =
            Media3PlaybackEventBridge { event ->
                if (firstCall) {
                    firstCall = false
                    throw RuntimeException("First call exception")
                }
                events.add(event)
            }
        faultyBridge.attach(mockPlayer)
        val listener = listenerSlot.captured

        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        listener.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(1, events.size)
    }

    // ========== CODE QUALITY: Error Categorization Comprehensive ==========

    @Test
    fun `all DRM error codes map to ErrorCategory DRM`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val drmErrorCodes =
            listOf(
                PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
                PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
                PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
                PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
                PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
                PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
                PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED,
                PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
            )

        drmErrorCodes.forEach { errorCode ->
            events.clear()
            val exception = PlaybackException("DRM error", null, errorCode)
            listener.onPlayerError(exception)

            val event = events[0] as PlaybackEvent.PlayerError
            assertEquals(ErrorCategory.DRM, event.errorCategory, "Failed for error code: $errorCode")
        }
    }

    @Test
    fun `all source parsing error codes map to ErrorCategory SOURCE`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val sourceErrorCodes =
            listOf(
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            )

        sourceErrorCodes.forEach { errorCode ->
            events.clear()
            val exception = PlaybackException("Source error", null, errorCode)
            listener.onPlayerError(exception)

            val event = events[0] as PlaybackEvent.PlayerError
            assertEquals(ErrorCategory.SOURCE, event.errorCategory, "Failed for error code: $errorCode")
        }
    }

    @Test
    fun `all decoder error codes map to ErrorCategory DECODER`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val decoderErrorCodes =
            listOf(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            )

        decoderErrorCodes.forEach { errorCode ->
            events.clear()
            val exception = PlaybackException("Decoder error", null, errorCode)
            listener.onPlayerError(exception)

            val event = events[0] as PlaybackEvent.PlayerError
            assertEquals(ErrorCategory.DECODER, event.errorCategory, "Failed for error code: $errorCode")
        }
    }

    // ========== CODE QUALITY: Performance ==========

    @Test
    fun `no blocking calls in any callback - verified by test execution time`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val startTime = System.currentTimeMillis()

        repeat(1000) {
            listener.onIsPlayingChanged(it % 2 == 0)
        }

        val duration = System.currentTimeMillis() - startTime
        // All 1000 callbacks should complete quickly (< 1 second)
        assertTrue(duration < 1000, "Callbacks took too long: ${duration}ms")
    }

    // ========== ADDITIONAL COVERAGE: SEEK_ADJUSTMENT ==========

    @Test
    fun `onPositionDiscontinuity with DISCONTINUITY_REASON_SEEK_ADJUSTMENT emits seek events`() {
        bridge.attach(mockPlayer)
        val listener = listenerSlot.captured
        val oldPos = mockk<Player.PositionInfo>()
        val newPos = mockk<Player.PositionInfo>()

        listener.onPositionDiscontinuity(oldPos, newPos, Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)

        assertEquals(2, events.size)
        assertTrue(events[0] is PlaybackEvent.SeekStarted)
        assertTrue(events[1] is PlaybackEvent.SeekEnded)
    }
}
