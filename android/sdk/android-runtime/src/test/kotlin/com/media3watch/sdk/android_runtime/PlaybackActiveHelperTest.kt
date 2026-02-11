package com.media3watch.sdk.android_runtime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackActiveHelperTest {
    @Test
    fun `isPlaying true makes playbackActive true`() {
        assertTrue(
            PlaybackActiveHelper.isPlaybackActive(
                isPlaying = true,
                playWhenReady = false,
                playbackState = 1,
            ),
        )
    }

    @Test
    fun `playWhenReady true and BUFFERING makes playbackActive true`() {
        assertTrue(
            PlaybackActiveHelper.isPlaybackActive(
                isPlaying = false,
                playWhenReady = true,
                playbackState = PlaybackActiveHelper.PLAYER_STATE_BUFFERING,
            ),
        )
    }

    @Test
    fun `paused (isPlaying false, playWhenReady false) makes playbackActive false`() {
        assertFalse(
            PlaybackActiveHelper.isPlaybackActive(
                isPlaying = false,
                playWhenReady = false,
                playbackState = 3,
            ),
        )
    }

    @Test
    fun `playWhenReady true but not BUFFERING makes playbackActive false`() {
        assertFalse(
            PlaybackActiveHelper.isPlaybackActive(
                isPlaying = false,
                playWhenReady = true,
                playbackState = 1, // STATE_IDLE
            ),
        )

        assertFalse(
            PlaybackActiveHelper.isPlaybackActive(
                isPlaying = false,
                playWhenReady = true,
                playbackState = 3, // STATE_READY
            ),
        )

        assertFalse(
            PlaybackActiveHelper.isPlaybackActive(
                isPlaying = false,
                playWhenReady = true,
                playbackState = 4, // STATE_ENDED
            ),
        )
    }

    @Test
    fun `isPlaying true overrides other conditions`() {
        assertTrue(
            PlaybackActiveHelper.isPlaybackActive(
                isPlaying = true,
                playWhenReady = false,
                playbackState = 1,
            ),
        )

        assertTrue(
            PlaybackActiveHelper.isPlaybackActive(
                isPlaying = true,
                playWhenReady = true,
                playbackState = 3,
            ),
        )
    }

    @Test
    fun `both isPlaying and playWhenReady false makes playbackActive false`() {
        assertFalse(
            PlaybackActiveHelper.isPlaybackActive(
                isPlaying = false,
                playWhenReady = false,
                playbackState = PlaybackActiveHelper.PLAYER_STATE_BUFFERING,
            ),
        )
    }

    @Test
    fun `PLAYER_STATE_BUFFERING constant is 2`() {
        assertTrue(PlaybackActiveHelper.PLAYER_STATE_BUFFERING == 2)
    }
}
