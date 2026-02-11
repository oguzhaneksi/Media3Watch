package com.media3watch.sdk.android_runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for playbackActive computation logic.
 * playbackActive = (isPlaying == true) OR (playWhenReady == true AND playbackState == BUFFERING)
 */
class PlaybackActiveComputationTest {

    // Media3 Player.STATE_* constants
    private companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
    }

    // ============================================================
    // HAPPY PATH TESTS
    // ============================================================

    @Test
    fun playbackActiveComputation_isPlaying() {
        // Test: playbackActive computation - isPlaying
        // isPlaying=true → playbackActive=true
        val result = computePlaybackActive(
            isPlaying = true,
            playWhenReady = true,
            playbackState = STATE_READY,
        )
        assertTrue("playbackActive should be true when isPlaying=true", result)
    }

    @Test
    fun playbackActiveComputation_buffering() {
        // Test: playbackActive computation - buffering
        // playWhenReady=true AND playbackState=BUFFERING → playbackActive=true
        val result = computePlaybackActive(
            isPlaying = false,
            playWhenReady = true,
            playbackState = STATE_BUFFERING,
        )
        assertTrue("playbackActive should be true when buffering", result)
    }

    @Test
    fun playbackActiveComputation_paused() {
        // Test: playbackActive computation - paused
        // playWhenReady=false → playbackActive=false
        val result = computePlaybackActive(
            isPlaying = false,
            playWhenReady = false,
            playbackState = STATE_READY,
        )
        assertFalse("playbackActive should be false when paused", result)
    }

    // ============================================================
    // EDGE CASE TESTS
    // ============================================================

    @Test
    fun playbackActiveEdgeCase_playWhenReadyTrueButNotBuffering() {
        // Test: playbackActive edge case - playWhenReady true but not buffering
        // playWhenReady=true, playbackState=IDLE → playbackActive=false (spec requires BUFFERING specifically)
        val result = computePlaybackActive(
            isPlaying = false,
            playWhenReady = true,
            playbackState = STATE_IDLE,
        )
        assertFalse("playbackActive should be false when IDLE (not BUFFERING)", result)
    }

    @Test
    fun playbackActiveEdgeCase_isPlayingOverridesPlayWhenReady() {
        // Test: playbackActive edge case - isPlaying overrides playWhenReady
        // isPlaying=true, playWhenReady=false → playbackActive=true (isPlaying is sufficient)
        val result = computePlaybackActive(
            isPlaying = true,
            playWhenReady = false,
            playbackState = STATE_READY,
        )
        assertTrue("playbackActive should be true when isPlaying=true regardless of playWhenReady", result)
    }

    @Test
    fun playbackActiveAllFalse() {
        // All indicators false
        val result = computePlaybackActive(
            isPlaying = false,
            playWhenReady = false,
            playbackState = STATE_IDLE,
        )
        assertFalse("playbackActive should be false when all indicators false", result)
    }

    @Test
    fun playbackActiveStateReady() {
        // playWhenReady=true but STATE_READY (not buffering)
        val result = computePlaybackActive(
            isPlaying = false,
            playWhenReady = true,
            playbackState = STATE_READY,
        )
        assertFalse("playbackActive should be false when STATE_READY (not BUFFERING)", result)
    }

    @Test
    fun playbackActiveStateEnded() {
        // playWhenReady=true but STATE_ENDED
        val result = computePlaybackActive(
            isPlaying = false,
            playWhenReady = true,
            playbackState = STATE_ENDED,
        )
        assertFalse("playbackActive should be false when STATE_ENDED", result)
    }

    @Test
    fun playbackActiveBothConditionsTrue() {
        // Both isPlaying AND buffering condition true
        val result = computePlaybackActive(
            isPlaying = true,
            playWhenReady = true,
            playbackState = STATE_BUFFERING,
        )
        assertTrue("playbackActive should be true when both conditions satisfied", result)
    }
}
