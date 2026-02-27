package com.media3watch.sdk

/**
 * Represents a snapshot of the current state of a playback session at a given point in time.
 *
 * @property sessionId Unique identifier for the playback session.
 * @property elapsedSessionTimeMs Total elapsed time since the session started, in milliseconds.
 * @property playbackState The current playback state of the session.
 * @property isPlaying Whether the player is actively playing content.
 * @property currentPositionMs The current playback position in the media, in milliseconds.
 *   Null if the position is not yet known (e.g. before playback begins).
 * @property startupTimeMs Time taken from session start until the first frame was rendered,
 *   in milliseconds. Null if playback has not yet started.
 * @property rebufferTimeMs Total time spent rebuffering during the session, in milliseconds.
 * @property rebufferCount Total number of rebuffering events that have occurred.
 * @property playTimeMs Total time the player has been actively playing content, in milliseconds.
 * @property rebufferRatio Ratio of rebuffer time to total session time (rebufferTimeMs / elapsedSessionTimeMs).
 *   Typically in the range [0.0, 1.0] under normal conditions; a value of 0.0 means no rebuffering occurred.
 * @property totalDroppedFrames Total number of video frames dropped during playback.
 * @property totalSeekCount Total number of seek operations performed during the session.
 * @property totalSeekTimeMs Total time spent seeking during the session, in milliseconds.
 * @property meanVideoFormatBitrate Mean bitrate of the video format selected by the adaptive
 *   bitrate logic, in bits per second. Null if no video format has been selected yet.
 * @property currentBitrate The bitrate of the currently playing media segment, in bits per second.
 *   Null if the current bitrate is not available.
 * @property errorCount Total number of playback errors encountered during the session.
 */
data class SessionSnapshot(
    val sessionId: String,
    val elapsedSessionTimeMs: Long,
    val playbackState: SessionPlaybackState,
    val isPlaying: Boolean,
    val currentPositionMs: Long?,
    val startupTimeMs: Long?,
    val rebufferTimeMs: Long,
    val rebufferCount: Int,
    val playTimeMs: Long,
    val rebufferRatio: Float,
    val totalDroppedFrames: Long,
    val totalSeekCount: Int,
    val totalSeekTimeMs: Long,
    val meanVideoFormatBitrate: Int?,
    val currentBitrate: Int?,
    val errorCount: Int,
)
