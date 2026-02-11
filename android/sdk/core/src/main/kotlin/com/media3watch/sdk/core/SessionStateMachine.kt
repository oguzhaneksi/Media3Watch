package com.media3watch.sdk.core

import com.media3watch.sdk.schema.EndReason
import com.media3watch.sdk.schema.PlaybackEvent
import com.media3watch.sdk.schema.SessionState

/**
 * State machine that processes PlaybackEvents and manages session state transitions.
 *
 * This class implements the session lifecycle as defined in session-lifecycle.md.
 * It tracks the current session state, detects meaningful activity, and handles
 * session end triggers with appropriate EndReason values.
 *
 * Thread safety: This class is NOT thread-safe. Callers must synchronize access
 * if calling from multiple threads.
 */
class SessionStateMachine(
    val sessionId: String,
) {
    private var _currentState: SessionState = SessionState.ATTACHED
    private var _endReason: EndReason? = null
    private var _hasMeaningfulActivity: Boolean = false

    // Playback state tracking for playbackActive computation
    private var _playWhenReady: Boolean = false
    private var _playbackState: Int = 1 // Media3 Player.STATE_IDLE = 1
    private var _isPlaying: Boolean = false

    // Background state tracking
    private var _preBackgroundState: SessionState? = null

    // State change listener
    private var _listener: StateChangeListener? = null

    val currentState: SessionState
        get() = _currentState

    val endReason: EndReason?
        get() = _endReason

    val isActive: Boolean
        get() = _currentState != SessionState.NO_SESSION && _currentState != SessionState.ENDED

    val hasMeaningfulActivity: Boolean
        get() = _hasMeaningfulActivity

    /**
     * Listener for state changes.
     */
    fun interface StateChangeListener {
        fun onStateChanged(
            oldState: SessionState,
            newState: SessionState,
            reason: String?,
        )
    }

    /**
     * Set the state change listener.
     */
    fun setStateChangeListener(listener: StateChangeListener?) {
        _listener = listener
    }

    /**
     * Process a PlaybackEvent and transition states accordingly.
     *
     * @return true if the event was processed, false if ignored (e.g., already ENDED)
     */
    fun processEvent(event: PlaybackEvent): Boolean {
        // Once in ENDED state, reject all events
        if (_currentState == SessionState.ENDED) {
            return false
        }

        when (event) {
            is PlaybackEvent.PlayRequested -> handlePlayRequested(event)
            is PlaybackEvent.FirstFrameRendered -> handleFirstFrameRendered(event)
            is PlaybackEvent.IsPlayingChanged -> handleIsPlayingChanged(event)
            is PlaybackEvent.PlayWhenReadyChanged -> handlePlayWhenReadyChanged(event)
            is PlaybackEvent.PlaybackStateChanged -> handlePlaybackStateChanged(event)
            is PlaybackEvent.BufferingStarted -> handleBufferingStarted(event)
            is PlaybackEvent.BufferingEnded -> handleBufferingEnded(event)
            is PlaybackEvent.SeekStarted -> handleSeekStarted(event)
            is PlaybackEvent.SeekEnded -> handleSeekEnded(event)
            is PlaybackEvent.AppBackgrounded -> handleAppBackgrounded(event)
            is PlaybackEvent.AppForegrounded -> handleAppForegrounded(event)
            is PlaybackEvent.PlayerReleased -> handlePlayerReleased(event)
            is PlaybackEvent.PlaybackEnded -> handlePlaybackEnded(event)
            is PlaybackEvent.BackgroundIdleTimeout -> handleBackgroundIdleTimeout(event)
            is PlaybackEvent.MediaItemTransition -> handleMediaItemTransition(event)
            is PlaybackEvent.PlayerError -> {
                // Errors don't change state, just track them
            }
        }

        return true
    }

    /**
     * Check if a session should be discarded (no meaningful activity).
     */
    fun shouldDiscard(): Boolean {
        return !_hasMeaningfulActivity
    }

    private fun handlePlayRequested(event: PlaybackEvent.PlayRequested) {
        markMeaningfulActivity()
        // PlayRequested may transition to PLAYING if isPlaying becomes true
        // We'll transition when IsPlayingChanged(true) arrives
    }

    private fun handleFirstFrameRendered(event: PlaybackEvent.FirstFrameRendered) {
        markMeaningfulActivity()
        // FirstFrameRendered implies playback started
        if (_currentState == SessionState.ATTACHED) {
            transitionTo(SessionState.PLAYING, "FirstFrameRendered")
        }
    }

    private fun handleIsPlayingChanged(event: PlaybackEvent.IsPlayingChanged) {
        _isPlaying = event.isPlaying

        if (event.isPlaying) {
            markMeaningfulActivity()
        }

        // Update state based on isPlaying
        when (_currentState) {
            SessionState.ATTACHED -> {
                if (event.isPlaying) {
                    transitionTo(SessionState.PLAYING, "IsPlayingChanged(true)")
                }
            }
            SessionState.PLAYING -> {
                if (!event.isPlaying) {
                    transitionTo(SessionState.PAUSED, "IsPlayingChanged(false)")
                }
            }
            SessionState.PAUSED -> {
                if (event.isPlaying) {
                    transitionTo(SessionState.PLAYING, "IsPlayingChanged(true)")
                }
            }
            SessionState.BUFFERING -> {
                // Will be handled by BufferingEnded
            }
            SessionState.SEEKING -> {
                // Will be handled by SeekEnded
            }
            SessionState.BACKGROUND -> {
                // State updated, but don't transition out of BACKGROUND yet
            }
            else -> {}
        }
    }

    private fun handlePlayWhenReadyChanged(event: PlaybackEvent.PlayWhenReadyChanged) {
        _playWhenReady = event.playWhenReady
    }

    private fun handlePlaybackStateChanged(event: PlaybackEvent.PlaybackStateChanged) {
        _playbackState = event.playbackState
    }

    private fun handleBufferingStarted(event: PlaybackEvent.BufferingStarted) {
        markMeaningfulActivity()

        when (_currentState) {
            SessionState.ATTACHED -> {
                transitionTo(SessionState.BUFFERING, "BufferingStarted")
            }
            SessionState.PLAYING -> {
                transitionTo(SessionState.BUFFERING, "BufferingStarted")
            }
            else -> {}
        }
    }

    private fun handleBufferingEnded(event: PlaybackEvent.BufferingEnded) {
        if (_currentState == SessionState.BUFFERING) {
            val nextState = if (_isPlaying) SessionState.PLAYING else SessionState.PAUSED
            transitionTo(nextState, "BufferingEnded")
        }
    }

    private fun handleSeekStarted(event: PlaybackEvent.SeekStarted) {
        if (_currentState == SessionState.PLAYING) {
            transitionTo(SessionState.SEEKING, "SeekStarted")
        }
    }

    private fun handleSeekEnded(event: PlaybackEvent.SeekEnded) {
        if (_currentState == SessionState.SEEKING) {
            val nextState = if (_isPlaying) SessionState.PLAYING else SessionState.PAUSED
            transitionTo(nextState, "SeekEnded")
        }
    }

    private fun handleAppBackgrounded(event: PlaybackEvent.AppBackgrounded) {
        if (_currentState in
            listOf(
                SessionState.ATTACHED,
                SessionState.PLAYING,
                SessionState.PAUSED,
                SessionState.BUFFERING,
                SessionState.SEEKING,
            )
        ) {
            _preBackgroundState = _currentState
            transitionTo(SessionState.BACKGROUND, "AppBackgrounded")
        }
    }

    private fun handleAppForegrounded(event: PlaybackEvent.AppForegrounded) {
        if (_currentState == SessionState.BACKGROUND) {
            // Return to previous state or derive from playback state
            val nextState =
                when {
                    _preBackgroundState != null -> _preBackgroundState!!
                    _isPlaying -> SessionState.PLAYING
                    else -> SessionState.PAUSED
                }
            _preBackgroundState = null
            transitionTo(nextState, "AppForegrounded")
        }
    }

    private fun handlePlayerReleased(event: PlaybackEvent.PlayerReleased) {
        endSession(EndReason.PLAYER_RELEASED, "PlayerReleased")
    }

    private fun handlePlaybackEnded(event: PlaybackEvent.PlaybackEnded) {
        endSession(EndReason.PLAYBACK_ENDED, "PlaybackEnded")
    }

    private fun handleBackgroundIdleTimeout(event: PlaybackEvent.BackgroundIdleTimeout) {
        if (_currentState == SessionState.BACKGROUND && !isPlaybackActive()) {
            endSession(EndReason.BACKGROUND_IDLE_TIMEOUT, "BackgroundIdleTimeout")
        }
    }

    private fun handleMediaItemTransition(event: PlaybackEvent.MediaItemTransition) {
        // MediaItemTransition ends the session if we've had meaningful activity
        if (_hasMeaningfulActivity) {
            endSession(EndReason.CONTENT_SWITCH, "MediaItemTransition")
        }
    }

    private fun markMeaningfulActivity() {
        _hasMeaningfulActivity = true
    }

    /**
     * Compute playbackActive as per session-lifecycle.md:
     * playbackActive = (isPlaying == true) OR (playWhenReady == true AND playbackState == BUFFERING)
     *
     * Media3 Player.STATE_BUFFERING = 3
     */
    private fun isPlaybackActive(): Boolean {
        return _isPlaying || (_playWhenReady && _playbackState == 3)
    }

    private fun transitionTo(
        newState: SessionState,
        reason: String?,
    ) {
        if (newState == _currentState) {
            return // Idempotent
        }

        val oldState = _currentState
        _currentState = newState
        _listener?.onStateChanged(oldState, newState, reason)
    }

    private fun endSession(
        reason: EndReason,
        eventReason: String,
    ) {
        _endReason = reason
        transitionTo(SessionState.ENDED, eventReason)
    }
}
