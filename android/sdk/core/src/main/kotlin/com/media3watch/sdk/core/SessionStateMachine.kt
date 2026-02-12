package com.media3watch.sdk.core

import com.media3watch.sdk.schema.EndReason
import com.media3watch.sdk.schema.PlaybackEvent
import com.media3watch.sdk.schema.SessionState

/**
 * Manages session lifecycle and state transitions based on PlaybackEvents.
 *
 * The state machine tracks:
 * - Current session state (8 states defined in SessionState enum)
 * - Meaningful activity detection (determines if session should be discarded)
 * - playbackActive computation (for background timeout logic)
 * - End reason tracking (why session ended)
 * - State change notifications
 *
 * Thread-safety: Not thread-safe. Caller must synchronize access if needed.
 */
class SessionStateMachine(
    private val sessionId: String,
) {
    companion object {
        // Media3 Player.STATE_* constants (avoiding Media3 dependency in core)
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
    }

    private var currentState: SessionState = SessionState.ATTACHED
    private var hasMeaningfulActivity: Boolean = false
    private var endReason: EndReason? = null

    // Background state memory (state to restore when returning from background)
    private var stateBeforeBackground: SessionState? = null

    // playbackActive computation state
    private var playWhenReady: Boolean = false
    private var playbackState: Int = STATE_IDLE
    private var isPlaying: Boolean = false

    // State change listeners (CopyOnWriteArrayList to avoid allocation on state change)
    private val stateChangeListeners = java.util.concurrent.CopyOnWriteArrayList<StateChangeListener>()

    /**
     * Returns current session state.
     */
    fun getState(): SessionState = currentState

    /**
     * Returns whether this session has meaningful activity.
     * Sessions without meaningful activity should be discarded on end.
     */
    fun hasMeaningfulActivity(): Boolean = hasMeaningfulActivity

    /**
     * Returns the end reason if session has ended, null otherwise.
     */
    fun getEndReason(): EndReason? = endReason

    /**
     * Returns whether session should be discarded (ended with no meaningful activity).
     */
    fun shouldDiscard(): Boolean = currentState == SessionState.ENDED && !hasMeaningfulActivity

    /**
     * Returns whether playback is currently active.
     * playbackActive = isPlaying OR (playWhenReady AND playbackState == BUFFERING)
     */
    fun isPlaybackActive(): Boolean = isPlaying || (playWhenReady && playbackState == STATE_BUFFERING)

    /**
     * Adds a state change listener.
     */
    fun addStateChangeListener(listener: StateChangeListener) {
        stateChangeListeners.add(listener)
    }

    /**
     * Removes a state change listener.
     */
    fun removeStateChangeListener(listener: StateChangeListener) {
        stateChangeListeners.remove(listener)
    }

    /**
     * Processes a playback event and updates state accordingly.
     * Returns true if state changed, false if event was idempotent.
     */
    fun processEvent(event: PlaybackEvent): Boolean {
        // Events in ENDED state are no-ops
        if (currentState == SessionState.ENDED) {
            return false
        }

        val oldState = currentState

        when (event) {
            is PlaybackEvent.PlayRequested -> handlePlayRequested(event)
            is PlaybackEvent.FirstFrameRendered -> handleFirstFrameRendered(event)
            is PlaybackEvent.BufferingStarted -> handleBufferingStarted(event)
            is PlaybackEvent.BufferingEnded -> handleBufferingEnded(event)
            is PlaybackEvent.IsPlayingChanged -> handleIsPlayingChanged(event)
            is PlaybackEvent.PlayWhenReadyChanged -> handlePlayWhenReadyChanged(event)
            is PlaybackEvent.PlaybackStateChanged -> handlePlaybackStateChanged(event)
            is PlaybackEvent.SeekStarted -> handleSeekStarted(event)
            is PlaybackEvent.SeekEnded -> handleSeekEnded(event)
            is PlaybackEvent.AppBackgrounded -> handleAppBackgrounded(event)
            is PlaybackEvent.AppForegrounded -> handleAppForegrounded(event)
            is PlaybackEvent.PlayerReleased -> handlePlayerReleased(event)
            is PlaybackEvent.PlaybackEnded -> handlePlaybackEnded(event)
            is PlaybackEvent.BackgroundIdleTimeout -> handleBackgroundIdleTimeout(event)
            is PlaybackEvent.MediaItemTransition -> handleMediaItemTransition(event)
            is PlaybackEvent.PlayerError -> {
                // Errors don't change state in MVP, just increment counters (handled elsewhere)
            }
        }

        val stateChanged = oldState != currentState
        if (stateChanged) {
            notifyStateChange(oldState, currentState)
        }

        return stateChanged
    }

    private fun handlePlayRequested(event: PlaybackEvent.PlayRequested) {
        markMeaningfulActivity()
        // State transition happens on IsPlayingChanged
    }

    private fun handleFirstFrameRendered(event: PlaybackEvent.FirstFrameRendered) {
        markMeaningfulActivity()
        // If still in ATTACHED, promote to PLAYING
        if (currentState == SessionState.ATTACHED) {
            transitionTo(SessionState.PLAYING)
        }
    }

    private fun handleBufferingStarted(event: PlaybackEvent.BufferingStarted) {
        markMeaningfulActivity()
        if (currentState == SessionState.ATTACHED ||
            currentState == SessionState.PLAYING ||
            currentState == SessionState.PAUSED ||
            currentState == SessionState.SEEKING
        ) {
            transitionTo(SessionState.BUFFERING)
        }
    }

    private fun handleBufferingEnded(event: PlaybackEvent.BufferingEnded) {
        if (currentState == SessionState.BUFFERING) {
            // Transition based on playback state
            transitionTo(if (isPlaying) SessionState.PLAYING else SessionState.PAUSED)
        }
    }

    private fun handleIsPlayingChanged(event: PlaybackEvent.IsPlayingChanged) {
        isPlaying = event.isPlaying

        if (event.isPlaying) {
            markMeaningfulActivity()
        }

        // Update state based on new isPlaying value
        when (currentState) {
            SessionState.ATTACHED -> {
                if (event.isPlaying) {
                    transitionTo(SessionState.PLAYING)
                }
            }
            SessionState.PLAYING -> {
                if (!event.isPlaying) {
                    transitionTo(SessionState.PAUSED)
                }
            }
            SessionState.PAUSED -> {
                if (event.isPlaying) {
                    transitionTo(SessionState.PLAYING)
                }
            }
            SessionState.SEEKING -> {
                // IsPlaying changes during seek don't affect SEEKING state
                // (handled by SeekEnded)
            }
            SessionState.BUFFERING -> {
                // IsPlaying changes during buffering don't affect BUFFERING state
                // (handled by BufferingEnded)
            }
            else -> {
                // No state change for other states
            }
        }
    }

    private fun handlePlayWhenReadyChanged(event: PlaybackEvent.PlayWhenReadyChanged) {
        playWhenReady = event.playWhenReady
        // playWhenReady alone doesn't trigger state transitions in MVP
        // State changes on IsPlayingChanged
    }

    private fun handlePlaybackStateChanged(event: PlaybackEvent.PlaybackStateChanged) {
        playbackState = event.playbackState
        // playbackState alone doesn't trigger state transitions in MVP
        // State changes on specific events (BufferingStarted, etc.)
    }

    private fun handleSeekStarted(event: PlaybackEvent.SeekStarted) {
        if (currentState == SessionState.PLAYING || currentState == SessionState.PAUSED) {
            transitionTo(SessionState.SEEKING)
        }
    }

    private fun handleSeekEnded(event: PlaybackEvent.SeekEnded) {
        if (currentState == SessionState.SEEKING) {
            transitionTo(if (isPlaying) SessionState.PLAYING else SessionState.PAUSED)
        }
    }

    private fun handleAppBackgrounded(event: PlaybackEvent.AppBackgrounded) {
        if (currentState != SessionState.ENDED && currentState != SessionState.BACKGROUND) {
            stateBeforeBackground = currentState
            transitionTo(SessionState.BACKGROUND)
        }
    }

    private fun handleAppForegrounded(event: PlaybackEvent.AppForegrounded) {
        if (currentState == SessionState.BACKGROUND) {
            // Per spec: BACKGROUND can only transition to PLAYING or PAUSED on foreground
            // Derive target state from current playback state (isPlaying)
            val targetState = if (isPlaying) SessionState.PLAYING else SessionState.PAUSED
            stateBeforeBackground = null
            transitionTo(targetState)
        }
    }

    private fun handlePlayerReleased(event: PlaybackEvent.PlayerReleased) {
        transitionToEnded(EndReason.PLAYER_RELEASED)
    }

    private fun handlePlaybackEnded(event: PlaybackEvent.PlaybackEnded) {
        transitionToEnded(EndReason.PLAYBACK_ENDED)
    }

    private fun handleBackgroundIdleTimeout(event: PlaybackEvent.BackgroundIdleTimeout) {
        // Only end if in BACKGROUND and playback is not active
        if (currentState == SessionState.BACKGROUND && !isPlaybackActive()) {
            transitionToEnded(EndReason.BACKGROUND_IDLE_TIMEOUT)
        }
    }

    private fun handleMediaItemTransition(event: PlaybackEvent.MediaItemTransition) {
        // Per spec: CONTENT_SWITCH should only end from PLAYING/PAUSED/BUFFERING states
        if (
            hasMeaningfulActivity &&
            (currentState == SessionState.PLAYING ||
                currentState == SessionState.PAUSED ||
                currentState == SessionState.BUFFERING)
        ) {
            transitionToEnded(EndReason.CONTENT_SWITCH)
        }
    }

    private fun markMeaningfulActivity() {
        hasMeaningfulActivity = true
    }

    private fun transitionTo(newState: SessionState) {
        if (currentState != newState) {
            currentState = newState
        }
    }

    private fun transitionToEnded(reason: EndReason) {
        endReason = reason
        transitionTo(SessionState.ENDED)
    }

    private fun notifyStateChange(
        oldState: SessionState,
        newState: SessionState,
    ) {
        // CopyOnWriteArrayList allows safe iteration without copying
        stateChangeListeners.forEach { listener ->
            try {
                listener.onStateChanged(sessionId, oldState, newState)
            } catch (e: Exception) {
                // Listener threw exception, continue with other listeners
                // In production, this might be logged
            }
        }
    }

    /**
     * Listener interface for state changes.
     */
    fun interface StateChangeListener {
        fun onStateChanged(
            sessionId: String,
            oldState: SessionState,
            newState: SessionState,
        )
    }
}
