package com.media3watch.sdk.schema

/**
 * Common playback states for players.
 * These map to standard player states (e.g., Media3 Player.STATE_* constants).
 */
enum class PlaybackState {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
}
