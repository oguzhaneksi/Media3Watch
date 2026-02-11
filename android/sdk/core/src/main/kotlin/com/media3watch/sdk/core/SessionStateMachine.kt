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
    var currentState: SessionState = SessionState.ATTACHED
        private set

    var endReason: EndReason? = null
        private set

    var hasMeaningfulActivity: Boolean = false
        private set

    // Playback state tracking for playbackActive computation
    private var playWhenReady: Boolean = false
    private var playbackState: Int = 1 // Media3 Player.STATE_IDLE = 1
    private var isPlaying: Boolean = false

    // Background state tracking
    private var preBackgroundState: SessionState? = null

    // State change listener
    private var listener: StateChangeListener? = null

    val isActive: Boolean
        get() = currentState != SessionState.NO_SESSION && currentState != SessionState.ENDED

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
        this.listener = listener
    }

    /**
     * Process a PlaybackEvent and transition states accordingly.
     *
     * @return true if the event was processed, false if ignored (e.g., already ENDED)
     */
    fun processEvent(event: PlaybackEvent): Boolean {
        // Once in ENDED state, reject all events
        if (currentState == SessionState.ENDED) {
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
    fun shouldDiscard(): Boolean = !hasMeaningfulActivity

    private fun handlePlayRequested(event: PlaybackEvent.PlayRequested) {
        markMeaningfulActivity()
        // PlayRequested may transition to PLAYING if isPlaying becomes true
        // We'll transition when IsPlayingChanged(true) arrives
    }

    private fun handleFirstFrameRendered(event: PlaybackEvent.FirstFrameRendered) {
        markMeaningfulActivity()
        // FirstFrameRendered implies playback started
        if (currentState == SessionState.ATTACHED) {
            transitionTo(SessionState.PLAYING, "FirstFrameRendered")
        }
    }

    private fun handleIsPlayingChanged(event: PlaybackEvent.IsPlayingChanged) {
        this.isPlaying = event.isPlaying

        if (event.isPlaying) {
            markMeaningfulActivity()
        }

        // Update state based on isPlaying
        when (currentState) {
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
        this.playWhenReady = event.playWhenReady
    }

    private fun handlePlaybackStateChanged(event: PlaybackEvent.PlaybackStateChanged) {
        this.playbackState = event.playbackState
    }

    private fun handleBufferingStarted(event: PlaybackEvent.BufferingStarted) {
        markMeaningfulActivity()

        when (currentState) {
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
        if (currentState == SessionState.BUFFERING) {
            val nextState = if (isPlaying) SessionState.PLAYING else SessionState.PAUSED
            transitionTo(nextState, "BufferingEnded")
        }
    }

    private fun handleSeekStarted(event: PlaybackEvent.SeekStarted) {
        if (currentState == SessionState.PLAYING) {
            transitionTo(SessionState.SEEKING, "SeekStarted")
        }
    }

    private fun handleSeekEnded(event: PlaybackEvent.SeekEnded) {
        if (currentState == SessionState.SEEKING) {
            val nextState = if (isPlaying) SessionState.PLAYING else SessionState.PAUSED
            transitionTo(nextState, "SeekEnded")
        }
    }

    private fun handleAppBackgrounded(event: PlaybackEvent.AppBackgrounded) {
        if (currentState in
            listOf(
                SessionState.ATTACHED,
                SessionState.PLAYING,
                SessionState.PAUSED,
                SessionState.BUFFERING,
                SessionState.SEEKING,
            )
        ) {
            preBackgroundState = currentState
            transitionTo(SessionState.BACKGROUND, "AppBackgrounded")
        }
    }

    private fun handleAppForegrounded(event: PlaybackEvent.AppForegrounded) {
        if (currentState == SessionState.BACKGROUND) {
            // Return to previous state or derive from playback state
            val nextState =
                when {
                    preBackgroundState != null -> preBackgroundState!!
                    isPlaying -> SessionState.PLAYING
                    else -> SessionState.PAUSED
                }
            preBackgroundState = null
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
        if (currentState == SessionState.BACKGROUND && !isPlaybackActive()) {
            endSession(EndReason.BACKGROUND_IDLE_TIMEOUT, "BackgroundIdleTimeout")
        }
    }

    private fun handleMediaItemTransition(event: PlaybackEvent.MediaItemTransition) {
        // MediaItemTransition ends the session if we've had meaningful activity
        if (hasMeaningfulActivity) {
            endSession(EndReason.CONTENT_SWITCH, "MediaItemTransition")
        }
    }

    private fun markMeaningfulActivity() {
        hasMeaningfulActivity = true
    }

    /**
     * Compute playbackActive as per session-lifecycle.md:
     * playbackActive = (isPlaying == true) OR (playWhenReady == true AND playbackState == BUFFERING)
     *
     * Media3 Player.STATE_BUFFERING = 3
     */
    private fun isPlaybackActive(): Boolean = isPlaying || (playWhenReady && playbackState == 3)

    private fun transitionTo(
        newState: SessionState,
        reason: String?,
    ) {
        if (newState == currentState) {
            return // Idempotent
        }

        val oldState = currentState
        currentState = newState
        listener?.onStateChanged(oldState, newState, reason)
    }

    private fun endSession(
        reason: EndReason,
        eventReason: String,
    ) {
        endReason = reason
        transitionTo(SessionState.ENDED, eventReason)
    }
}
