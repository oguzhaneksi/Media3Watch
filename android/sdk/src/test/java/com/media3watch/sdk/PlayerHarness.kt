package com.media3watch.sdk

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.ArgumentMatchers.any

/**
 * A harness for testing ExoPlayer functionality by mocking its behavior and emitting analytics events.
 */
class PlayerHarness {
    /** Mocked ExoPlayer instance. */
    val player: ExoPlayer = mock(ExoPlayer::class.java)

    /** List of registered analytics listeners. */
    val analyticsListeners = mutableListOf<AnalyticsListener>()

    /** Tracks whether the player is in a playing state. */
    private var isPlayingState: Boolean = false

    /** Tracks the current playback state of the player. */
    private var playbackStateValue: Int = Player.STATE_IDLE
    private var currentPositionMsValue: Long = 0L
    private var currentVideoFormatValue: Format? = null

    init {
        // Mock the addition of analytics listeners.
        doAnswer {
            analyticsListeners.add(it.arguments[0] as AnalyticsListener)
            null
        }.`when`(player).addAnalyticsListener(any(AnalyticsListener::class.java))

        // Mock the removal of analytics listeners.
        doAnswer {
            analyticsListeners.remove(it.arguments[0] as AnalyticsListener)
            null
        }.`when`(player).removeAnalyticsListener(any(AnalyticsListener::class.java))

        // Mock the isPlaying property.
        doAnswer { isPlayingState }.`when`(player).isPlaying

        // Mock the playbackState property.
        doAnswer { playbackStateValue }.`when`(player).playbackState

        // Main-thread analytics callbacks are a contract for snapshot observers.
        doAnswer { Looper.getMainLooper() }.`when`(player).applicationLooper

        doAnswer { currentPositionMsValue }.`when`(player).currentPosition
        doAnswer { currentVideoFormatValue }.`when`(player).videoFormat
    }

    /**
     * Emits an event indicating the first frame has been rendered.
     */
    fun emitFirstFrame() {
        emitFirstFrameAt(SystemClock.elapsedRealtime())
    }

    /**
     * Emits an event indicating the first frame has been rendered at a specific time.
     *
     * @param renderTimeMs The time at which the first frame was rendered.
     */
    fun emitFirstFrameAt(renderTimeMs: Long) {
        analyticsListeners.forEach {
            it.onRenderedFirstFrame(
                createEventTime(),
                Any(),
                renderTimeMs
            )
        }
    }

    /**
     * Emits a player error event with the specified error code.
     *
     * @param errorCode The error code to emit.
     */
    fun emitPlayerError(errorCode: Int) {
        val error = PlaybackException("test-error", null, errorCode)
        analyticsListeners.forEach {
            it.onPlayerError(createEventTime(), error)
        }
    }

    /**
     * Emits an event indicating whether the player is playing.
     *
     * @param isPlaying True if the player is playing, false otherwise.
     */
    fun emitIsPlayingChanged(isPlaying: Boolean) {
        isPlayingState = isPlaying
        analyticsListeners.forEach {
            it.onIsPlayingChanged(createEventTime(), isPlaying)
        }
    }

    /**
     * Sets the playback state of the player.
     *
     * @param state The playback state to set.
     */
    fun setPlaybackState(state: Int) {
        playbackStateValue = state
    }

    /**
     * Sets the current playback position of the player without emitting any analytics events.
     *
     * This is useful in tests that need to control what the mocked player reports as its current
     * position (for example, to verify logic that depends on `getCurrentPosition()`), without
     * simulating a seek or position discontinuity.
     *
     * @param positionMs The playback position to set, in milliseconds.
     */
    fun setCurrentPosition(positionMs: Long) {
        currentPositionMsValue = positionMs
    }

    /**
     * Sets the current video format of the player without emitting a format change event.
     *
     * This should be used in tests that need the mocked player to expose a specific video
     * bitrate (for example, when asserting behavior that depends on the current format), but
     * where an analytics callback is not required. To simulate a real format change and trigger
     * analytics events, use [emitVideoFormatChanged] instead.
     *
     * @param bitrate The average bitrate of the video format in bits per second, or `null` to
     *   clear the current video format.
     */
    fun setVideoFormat(bitrate: Int?) {
        currentVideoFormatValue = bitrate?.let { Format.Builder().setAverageBitrate(it).build() }
    }

    /**
     * Emits an event indicating a seek operation has started.
     */
    fun emitSeekStarted() {
        analyticsListeners.forEach {
            it.onPositionDiscontinuity(
                createEventTime(),
                mock(Player.PositionInfo::class.java),
                mock(Player.PositionInfo::class.java),
                Player.DISCONTINUITY_REASON_SEEK
            )
        }
    }

    /**
     * Emits an event indicating dropped video frames.
     *
     * @param droppedFrames The number of dropped frames.
     */
    fun emitDroppedVideoFrames(droppedFrames: Int) {
        analyticsListeners.forEach {
            it.onDroppedVideoFrames(createEventTime(), droppedFrames, 100L)
        }
    }

    /**
     * Emits an event indicating a period transition in the timeline.
     */
    fun emitPeriodTransition() {
        analyticsListeners.forEach {
            it.onPositionDiscontinuity(
                createEventTime(),
                mock(Player.PositionInfo::class.java),
                mock(Player.PositionInfo::class.java),
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION
            )
        }
    }

    /**
     * Emits an event indicating a change in the video format.
     *
     * @param bitrate The average bitrate of the new video format.
     */
    fun emitVideoFormatChanged(bitrate: Int) {
        val format = Format.Builder().setAverageBitrate(bitrate).build()
        currentVideoFormatValue = format
        analyticsListeners.forEach {
            it.onVideoInputFormatChanged(createEventTime(), format, null)
        }
    }

    /**
     * Emits an event indicating a change in the playback state.
     *
     * @param state The new playback state.
     */
    fun emitPlaybackStateChanged(state: Int) {
        playbackStateValue = state
        analyticsListeners.forEach {
            it.onPlaybackStateChanged(createEventTime(), state)
        }
    }

    /**
     * Creates a mock event time for analytics events.
     *
     * @return A mock EventTime instance.
     */
    private fun createEventTime(): AnalyticsListener.EventTime {
        return AnalyticsListener.EventTime(
            SystemClock.elapsedRealtime(),
            Timeline.EMPTY,
            0,
            null,
            0L,
            Timeline.EMPTY,
            0,
            null,
            0L,
            0L
        )
    }
}
