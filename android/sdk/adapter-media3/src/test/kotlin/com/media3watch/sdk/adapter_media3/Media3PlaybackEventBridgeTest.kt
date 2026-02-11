package com.media3watch.sdk.adapter_media3

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.media3watch.sdk.schema.ErrorCategory
import com.media3watch.sdk.schema.PlaybackEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class Media3PlaybackEventBridgeTest {
    private lateinit var capturedEvents: MutableList<PlaybackEvent>
    private lateinit var bridge: Media3PlaybackEventBridge
    private lateinit var mockPlayer: MockPlayer

    @BeforeEach
    fun setup() {
        capturedEvents = mutableListOf()
        bridge = Media3PlaybackEventBridge { event ->
            capturedEvents.add(event)
        }
        mockPlayer = MockPlayer()
    }

    @Test
    fun `attach registers listener on player`() {
        bridge.attach(mockPlayer)

        assertTrue(mockPlayer.hasListener(bridge))
    }

    @Test
    fun `detach removes listener from player`() {
        bridge.attach(mockPlayer)
        bridge.detach()

        assertTrue(!mockPlayer.hasListener(bridge))
    }

    @Test
    fun `onPlayWhenReadyChanged emits PlayWhenReadyChanged event`() {
        bridge.attach(mockPlayer)

        bridge.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(1, capturedEvents.size)
        val event = capturedEvents[0]
        assertInstanceOf(PlaybackEvent.PlayWhenReadyChanged::class.java, event)
        assertEquals(true, (event as PlaybackEvent.PlayWhenReadyChanged).playWhenReady)
    }

    @Test
    fun `onPlayWhenReadyChanged false emits PlayWhenReadyChanged event with false`() {
        bridge.attach(mockPlayer)

        bridge.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(1, capturedEvents.size)
        val event = capturedEvents[0]
        assertInstanceOf(PlaybackEvent.PlayWhenReadyChanged::class.java, event)
        assertEquals(false, (event as PlaybackEvent.PlayWhenReadyChanged).playWhenReady)
    }

    @Test
    fun `onIsPlayingChanged emits IsPlayingChanged event`() {
        bridge.attach(mockPlayer)

        bridge.onIsPlayingChanged(true)

        assertEquals(1, capturedEvents.size)
        val event = capturedEvents[0]
        assertInstanceOf(PlaybackEvent.IsPlayingChanged::class.java, event)
        assertEquals(true, (event as PlaybackEvent.IsPlayingChanged).isPlaying)
    }

    @Test
    fun `onIsPlayingChanged false emits IsPlayingChanged event with false`() {
        bridge.attach(mockPlayer)

        bridge.onIsPlayingChanged(false)

        assertEquals(1, capturedEvents.size)
        val event = capturedEvents[0]
        assertInstanceOf(PlaybackEvent.IsPlayingChanged::class.java, event)
        assertEquals(false, (event as PlaybackEvent.IsPlayingChanged).isPlaying)
    }

    @Test
    fun `onPlaybackStateChanged emits PlaybackStateChanged event with correct state`() {
        bridge.attach(mockPlayer)

        bridge.onPlaybackStateChanged(Player.STATE_READY)

        val stateChangedEvent = capturedEvents.filterIsInstance<PlaybackEvent.PlaybackStateChanged>()
        assertEquals(1, stateChangedEvent.size)
        assertEquals(Player.STATE_READY, stateChangedEvent[0].playbackState)
    }

    @Test
    fun `onPlaybackStateChanged STATE_BUFFERING emits BufferingStarted`() {
        bridge.attach(mockPlayer)

        bridge.onPlaybackStateChanged(Player.STATE_BUFFERING)

        val bufferingStartedEvents = capturedEvents.filterIsInstance<PlaybackEvent.BufferingStarted>()
        assertEquals(1, bufferingStartedEvents.size)
    }

    @Test
    fun `onPlaybackStateChanged STATE_READY after STATE_BUFFERING emits BufferingEnded`() {
        bridge.attach(mockPlayer)

        bridge.onPlaybackStateChanged(Player.STATE_BUFFERING)
        Thread.sleep(10)
        bridge.onPlaybackStateChanged(Player.STATE_READY)

        val bufferingStartedEvents = capturedEvents.filterIsInstance<PlaybackEvent.BufferingStarted>()
        val bufferingEndedEvents = capturedEvents.filterIsInstance<PlaybackEvent.BufferingEnded>()

        assertEquals(1, bufferingStartedEvents.size)
        assertEquals(1, bufferingEndedEvents.size)
        assertTrue(bufferingEndedEvents[0].durationMs >= 0)
    }

    @Test
    fun `onPlaybackStateChanged STATE_READY without prior buffering does not emit BufferingEnded`() {
        bridge.attach(mockPlayer)

        bridge.onPlaybackStateChanged(Player.STATE_READY)

        val bufferingEndedEvents = capturedEvents.filterIsInstance<PlaybackEvent.BufferingEnded>()
        assertEquals(0, bufferingEndedEvents.size)
    }

    @Test
    fun `onPlaybackStateChanged STATE_ENDED emits PlaybackEnded`() {
        bridge.attach(mockPlayer)

        bridge.onPlaybackStateChanged(Player.STATE_ENDED)

        val playbackEndedEvents = capturedEvents.filterIsInstance<PlaybackEvent.PlaybackEnded>()
        assertEquals(1, playbackEndedEvents.size)
    }

    @Test
    fun `onRenderedFirstFrame emits FirstFrameRendered event`() {
        bridge.attach(mockPlayer)

        bridge.onRenderedFirstFrame()

        assertEquals(1, capturedEvents.size)
        assertInstanceOf(PlaybackEvent.FirstFrameRendered::class.java, capturedEvents[0])
    }

    @Test
    fun `onPlayerError with network error maps to NETWORK category`() {
        bridge.attach(mockPlayer)

        val error = PlaybackException(
            "Network error",
            null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        )
        bridge.onPlayerError(error)

        assertEquals(1, capturedEvents.size)
        val event = capturedEvents[0]
        assertInstanceOf(PlaybackEvent.PlayerError::class.java, event)
        val playerError = event as PlaybackEvent.PlayerError
        assertEquals(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, playerError.errorCode)
        assertEquals(ErrorCategory.NETWORK, playerError.errorCategory)
    }

    @Test
    fun `onPlayerError with DRM error maps to DRM category`() {
        bridge.attach(mockPlayer)

        val error = PlaybackException(
            "DRM error",
            null,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
        )
        bridge.onPlayerError(error)

        val event = capturedEvents[0] as PlaybackEvent.PlayerError
        assertEquals(ErrorCategory.DRM, event.errorCategory)
    }

    @Test
    fun `onPlayerError with source parsing error maps to SOURCE category`() {
        bridge.attach(mockPlayer)

        val error = PlaybackException(
            "Parsing error",
            null,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        )
        bridge.onPlayerError(error)

        val event = capturedEvents[0] as PlaybackEvent.PlayerError
        assertEquals(ErrorCategory.SOURCE, event.errorCategory)
    }

    @Test
    fun `onPlayerError with decoder error maps to DECODER category`() {
        bridge.attach(mockPlayer)

        val error = PlaybackException(
            "Decoder error",
            null,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        )
        bridge.onPlayerError(error)

        val event = capturedEvents[0] as PlaybackEvent.PlayerError
        assertEquals(ErrorCategory.DECODER, event.errorCategory)
    }

    @Test
    fun `onPlayerError with unknown error code maps to UNKNOWN category`() {
        bridge.attach(mockPlayer)

        val error = PlaybackException(
            "Unknown error",
            null,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        )
        bridge.onPlayerError(error)

        val event = capturedEvents[0] as PlaybackEvent.PlayerError
        assertEquals(ErrorCategory.UNKNOWN, event.errorCategory)
    }

    @Test
    fun `onMediaItemTransition emits MediaItemTransition event with contentId`() {
        bridge.attach(mockPlayer)

        val mediaItem = MediaItem.Builder()
            .setMediaId("test-media-id-123")
            .build()
        bridge.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        assertEquals(1, capturedEvents.size)
        val event = capturedEvents[0]
        assertInstanceOf(PlaybackEvent.MediaItemTransition::class.java, event)
        assertEquals("test-media-id-123", (event as PlaybackEvent.MediaItemTransition).newContentId)
    }

    @Test
    fun `onMediaItemTransition with null mediaItem emits event with null contentId`() {
        bridge.attach(mockPlayer)

        bridge.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        assertEquals(1, capturedEvents.size)
        val event = capturedEvents[0]
        assertInstanceOf(PlaybackEvent.MediaItemTransition::class.java, event)
        assertEquals(null, (event as PlaybackEvent.MediaItemTransition).newContentId)
    }

    @Test
    fun `onPositionDiscontinuity with SEEK reason emits SeekStarted and SeekEnded`() {
        bridge.attach(mockPlayer)

        val oldPosition = Player.PositionInfo(
            null,
            0,
            null,
            null,
            0,
            0,
            0,
            0,
            0,
        )
        val newPosition = Player.PositionInfo(
            null,
            0,
            null,
            null,
            0,
            5000,
            0,
            0,
            0,
        )

        bridge.onPositionDiscontinuity(oldPosition, newPosition, Player.DISCONTINUITY_REASON_SEEK)

        val seekStartedEvents = capturedEvents.filterIsInstance<PlaybackEvent.SeekStarted>()
        val seekEndedEvents = capturedEvents.filterIsInstance<PlaybackEvent.SeekEnded>()

        assertEquals(1, seekStartedEvents.size)
        assertEquals(1, seekEndedEvents.size)
        assertTrue(seekEndedEvents[0].durationMs >= 0)
    }

    @Test
    fun `onPositionDiscontinuity with non-SEEK reason does not emit seek events`() {
        bridge.attach(mockPlayer)

        val oldPosition = Player.PositionInfo(
            null,
            0,
            null,
            null,
            0,
            0,
            0,
            0,
            0,
        )
        val newPosition = Player.PositionInfo(
            null,
            0,
            null,
            null,
            0,
            5000,
            0,
            0,
            0,
        )

        bridge.onPositionDiscontinuity(oldPosition, newPosition, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)

        val seekEvents = capturedEvents.filterIsInstance<PlaybackEvent.SeekStarted>() +
            capturedEvents.filterIsInstance<PlaybackEvent.SeekEnded>()

        assertEquals(0, seekEvents.size)
    }

    @Test
    fun `events after detach are not forwarded`() {
        bridge.attach(mockPlayer)
        bridge.detach()

        bridge.onIsPlayingChanged(true)
        bridge.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(0, capturedEvents.size)
    }

    @Test
    fun `multiple STATE_BUFFERING without STATE_READY emits only one BufferingStarted`() {
        bridge.attach(mockPlayer)

        bridge.onPlaybackStateChanged(Player.STATE_BUFFERING)
        bridge.onPlaybackStateChanged(Player.STATE_BUFFERING)

        val bufferingStartedEvents = capturedEvents.filterIsInstance<PlaybackEvent.BufferingStarted>()
        assertEquals(1, bufferingStartedEvents.size)
    }

    @Test
    fun `realistic callback sequence emits correct events`() {
        bridge.attach(mockPlayer)

        bridge.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        bridge.onPlaybackStateChanged(Player.STATE_BUFFERING)
        Thread.sleep(10)
        bridge.onPlaybackStateChanged(Player.STATE_READY)
        bridge.onIsPlayingChanged(true)
        bridge.onRenderedFirstFrame()

        assertTrue(capturedEvents.any { it is PlaybackEvent.PlayWhenReadyChanged })
        assertTrue(capturedEvents.any { it is PlaybackEvent.BufferingStarted })
        assertTrue(capturedEvents.any { it is PlaybackEvent.BufferingEnded })
        assertTrue(capturedEvents.any { it is PlaybackEvent.IsPlayingChanged })
        assertTrue(capturedEvents.any { it is PlaybackEvent.FirstFrameRendered })
    }
}
