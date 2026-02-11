package com.media3watch.sdk.android_runtime

/**
 * Helper for computing playbackActive state per session-lifecycle.md §4.
 *
 * Definition (MVP):
 * playbackActive = (isPlaying == true) OR (playWhenReady == true AND playbackState == BUFFERING)
 *
 * Supports:
 * - PiP / audio-only (notification) playback in background without ending sessions
 * - Prevents ending sessions during transient buffering when user intent is "keep playing"
 */
object PlaybackActiveHelper {
    /**
     * Media3 Player.STATE_BUFFERING constant value.
     */
    const val PLAYER_STATE_BUFFERING = 2

    /**
     * Computes whether playback is currently active.
     *
     * @param isPlaying true if player is currently playing (Media3 isPlaying callback)
     * @param playWhenReady true if player should play when ready (Media3 playWhenReady)
     * @param playbackState current playback state (Media3 Player.STATE_* constants)
     * @return true if playback is active, false otherwise
     */
    fun isPlaybackActive(
        isPlaying: Boolean,
        playWhenReady: Boolean,
        playbackState: Int,
    ): Boolean = isPlaying || (playWhenReady && playbackState == PLAYER_STATE_BUFFERING)
}
