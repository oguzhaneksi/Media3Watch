package com.media3watch.sdk.core

import com.media3watch.sdk.schema.EndReason
import com.media3watch.sdk.schema.ErrorCategory
import com.media3watch.sdk.schema.PlaybackEvent
import com.media3watch.sdk.schema.PlaybackState
import com.media3watch.sdk.schema.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionStateMachineTest {
    private fun createStateMachine(): SessionStateMachine {
        return SessionStateMachine("test-session-123")
    }

    @Test
    fun `initial state is ATTACHED`() {
        val sm = createStateMachine()
        assertEquals(SessionState.ATTACHED, sm.currentState)
        assertTrue(sm.isActive)
        assertNull(sm.endReason)
        assertFalse(sm.hasMeaningfulActivity)
    }

    @Test
    fun `sessionId validation rejects blank sessionId`() {
        try {
            SessionStateMachine("")
            throw AssertionError("Expected IllegalArgumentException for blank sessionId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("sessionId") == true)
        }
    }

    @Test
    fun `PlayRequested marks meaningful activity`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.PlayRequested(timestamp = 1000L))

        assertTrue(sm.hasMeaningfulActivity)
        assertEquals(SessionState.ATTACHED, sm.currentState) // Still attached until isPlaying
    }

    @Test
    fun `ATTACHED to PLAYING via IsPlayingChanged(true)`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.PlayRequested(timestamp = 1000L))
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1100L, isPlaying = true))

        assertEquals(SessionState.PLAYING, sm.currentState)
        assertTrue(sm.hasMeaningfulActivity)
    }

    @Test
    fun `ATTACHED to PLAYING via FirstFrameRendered`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.FirstFrameRendered(timestamp = 1000L))

        assertEquals(SessionState.PLAYING, sm.currentState)
        assertTrue(sm.hasMeaningfulActivity)
    }

    @Test
    fun `ATTACHED to BUFFERING via BufferingStarted`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.BufferingStarted(timestamp = 1000L))

        assertEquals(SessionState.BUFFERING, sm.currentState)
        assertTrue(sm.hasMeaningfulActivity)
    }

    @Test
    fun `PLAYING to PAUSED via IsPlayingChanged(false)`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.PlayRequested(timestamp = 1000L))
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1100L, isPlaying = true))
        assertEquals(SessionState.PLAYING, sm.currentState)

        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1200L, isPlaying = false))
        assertEquals(SessionState.PAUSED, sm.currentState)
    }

    @Test
    fun `PAUSED to PLAYING via IsPlayingChanged(true)`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1100L, isPlaying = false))
        assertEquals(SessionState.PAUSED, sm.currentState)

        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1200L, isPlaying = true))
        assertEquals(SessionState.PLAYING, sm.currentState)
    }

    @Test
    fun `PLAYING to BUFFERING to PLAYING round-trip`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        assertEquals(SessionState.PLAYING, sm.currentState)

        sm.processEvent(PlaybackEvent.BufferingStarted(timestamp = 1100L))
        assertEquals(SessionState.BUFFERING, sm.currentState)

        sm.processEvent(PlaybackEvent.BufferingEnded(timestamp = 1200L, durationMs = 100L))
        assertEquals(SessionState.PLAYING, sm.currentState)
    }

    @Test
    fun `BUFFERING to PAUSED when isPlaying is false`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.BufferingStarted(timestamp = 1000L))
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1050L, isPlaying = false))
        assertEquals(SessionState.BUFFERING, sm.currentState)

        sm.processEvent(PlaybackEvent.BufferingEnded(timestamp = 1200L, durationMs = 200L))
        assertEquals(SessionState.PAUSED, sm.currentState)
    }

    @Test
    fun `PLAYING to SEEKING to PLAYING round-trip`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        assertEquals(SessionState.PLAYING, sm.currentState)

        sm.processEvent(PlaybackEvent.SeekStarted(timestamp = 1100L))
        assertEquals(SessionState.SEEKING, sm.currentState)

        sm.processEvent(PlaybackEvent.SeekEnded(timestamp = 1200L, durationMs = 100L))
        assertEquals(SessionState.PLAYING, sm.currentState)
    }

    @Test
    fun `SEEKING to PAUSED when isPlaying is false`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        sm.processEvent(PlaybackEvent.SeekStarted(timestamp = 1100L))
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1150L, isPlaying = false))
        assertEquals(SessionState.SEEKING, sm.currentState)

        sm.processEvent(PlaybackEvent.SeekEnded(timestamp = 1200L, durationMs = 100L))
        assertEquals(SessionState.PAUSED, sm.currentState)
    }

    @Test
    fun `PLAYING to BACKGROUND via AppBackgrounded`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        assertEquals(SessionState.PLAYING, sm.currentState)

        sm.processEvent(PlaybackEvent.AppBackgrounded(timestamp = 1100L))
        assertEquals(SessionState.BACKGROUND, sm.currentState)
    }

    @Test
    fun `BACKGROUND to PLAYING via AppForegrounded`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        sm.processEvent(PlaybackEvent.AppBackgrounded(timestamp = 1100L))
        assertEquals(SessionState.BACKGROUND, sm.currentState)

        sm.processEvent(PlaybackEvent.AppForegrounded(timestamp = 1200L))
        assertEquals(SessionState.PLAYING, sm.currentState)
    }

    @Test
    fun `BACKGROUND to PAUSED via AppForegrounded when isPlaying is false`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1050L, isPlaying = false))
        sm.processEvent(PlaybackEvent.AppBackgrounded(timestamp = 1100L))
        assertEquals(SessionState.BACKGROUND, sm.currentState)

        sm.processEvent(PlaybackEvent.AppForegrounded(timestamp = 1200L))
        assertEquals(SessionState.PAUSED, sm.currentState)
    }

    @Test
    fun `ATTACHED to BACKGROUND to foreground preserves ATTACHED state`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.AppBackgrounded(timestamp = 1000L))
        assertEquals(SessionState.BACKGROUND, sm.currentState)

        sm.processEvent(PlaybackEvent.AppForegrounded(timestamp = 1100L))
        assertEquals(SessionState.ATTACHED, sm.currentState)
    }

    @Test
    fun `PlayerReleased ends session with PLAYER_RELEASED reason`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        sm.processEvent(PlaybackEvent.PlayerReleased(timestamp = 1100L))

        assertEquals(SessionState.ENDED, sm.currentState)
        assertEquals(EndReason.PLAYER_RELEASED, sm.endReason)
        assertFalse(sm.isActive)
    }

    @Test
    fun `PlaybackEnded ends session with PLAYBACK_ENDED reason`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        sm.processEvent(PlaybackEvent.PlaybackEnded(timestamp = 1100L))

        assertEquals(SessionState.ENDED, sm.currentState)
        assertEquals(EndReason.PLAYBACK_ENDED, sm.endReason)
    }

    @Test
    fun `MediaItemTransition ends session with CONTENT_SWITCH reason when meaningful activity exists`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.PlayRequested(timestamp = 1000L))
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1100L, isPlaying = true))
        sm.processEvent(PlaybackEvent.MediaItemTransition(timestamp = 1200L, newContentId = "new-content"))

        assertEquals(SessionState.ENDED, sm.currentState)
        assertEquals(EndReason.CONTENT_SWITCH, sm.endReason)
    }

    @Test
    fun `MediaItemTransition does not end session without meaningful activity`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.MediaItemTransition(timestamp = 1000L, newContentId = "new-content"))

        assertEquals(SessionState.ATTACHED, sm.currentState)
        assertNull(sm.endReason)
    }

    @Test
    fun `PlayerReplaced ends session with PLAYER_REPLACED reason`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.PlayRequested(timestamp = 1000L))
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1100L, isPlaying = true))
        sm.processEvent(PlaybackEvent.PlayerReplaced(timestamp = 1200L))

        assertEquals(SessionState.ENDED, sm.currentState)
        assertEquals(EndReason.PLAYER_REPLACED, sm.endReason)
    }

    @Test
    fun `PlayerReplaced ends session from ATTACHED state`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.PlayerReplaced(timestamp = 1000L))

        assertEquals(SessionState.ENDED, sm.currentState)
        assertEquals(EndReason.PLAYER_REPLACED, sm.endReason)
    }

    @Test
    fun `BackgroundIdleTimeout ends session when not playbackActive`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        sm.processEvent(PlaybackEvent.AppBackgrounded(timestamp = 1100L))
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1150L, isPlaying = false))

        sm.processEvent(PlaybackEvent.BackgroundIdleTimeout(timestamp = 121100L))

        assertEquals(SessionState.ENDED, sm.currentState)
        assertEquals(EndReason.BACKGROUND_IDLE_TIMEOUT, sm.endReason)
    }

    @Test
    fun `BackgroundIdleTimeout does not end session when playbackActive (isPlaying=true)`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        sm.processEvent(PlaybackEvent.AppBackgrounded(timestamp = 1100L))

        sm.processEvent(PlaybackEvent.BackgroundIdleTimeout(timestamp = 121100L))

        assertEquals(SessionState.BACKGROUND, sm.currentState)
        assertNull(sm.endReason)
    }

    @Test
    fun `BackgroundIdleTimeout does not end session when playbackActive (playWhenReady=true and buffering)`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.PlayWhenReadyChanged(timestamp = 1000L, playWhenReady = true))
        sm.processEvent(PlaybackEvent.PlaybackStateChanged(timestamp = 1050L, playbackState = PlaybackState.BUFFERING))
        sm.processEvent(PlaybackEvent.AppBackgrounded(timestamp = 1100L))

        sm.processEvent(PlaybackEvent.BackgroundIdleTimeout(timestamp = 121100L))

        assertEquals(SessionState.BACKGROUND, sm.currentState)
        assertNull(sm.endReason)
    }

    @Test
    fun `shouldDiscard returns true when no meaningful activity`() {
        val sm = createStateMachine()
        assertTrue(sm.shouldDiscard())

        sm.processEvent(PlaybackEvent.PlayerReleased(timestamp = 1000L))
        assertTrue(sm.shouldDiscard())
    }

    @Test
    fun `shouldDiscard returns false after PlayRequested`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.PlayRequested(timestamp = 1000L))

        assertFalse(sm.shouldDiscard())
    }

    @Test
    fun `shouldDiscard returns false after FirstFrameRendered`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.FirstFrameRendered(timestamp = 1000L))

        assertFalse(sm.shouldDiscard())
    }

    @Test
    fun `shouldDiscard returns false after BufferingStarted`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.BufferingStarted(timestamp = 1000L))

        assertFalse(sm.shouldDiscard())
    }

    @Test
    fun `shouldDiscard returns false after IsPlayingChanged(true)`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))

        assertFalse(sm.shouldDiscard())
    }

    @Test
    fun `events in ENDED state are rejected`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        sm.processEvent(PlaybackEvent.PlayerReleased(timestamp = 1100L))
        assertEquals(SessionState.ENDED, sm.currentState)

        val result = sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1200L, isPlaying = false))

        assertFalse(result)
        assertEquals(SessionState.ENDED, sm.currentState)
    }

    @Test
    fun `duplicate BufferingStarted events are idempotent`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        sm.processEvent(PlaybackEvent.BufferingStarted(timestamp = 1100L))
        assertEquals(SessionState.BUFFERING, sm.currentState)

        sm.processEvent(PlaybackEvent.BufferingStarted(timestamp = 1150L))
        assertEquals(SessionState.BUFFERING, sm.currentState)
    }

    @Test
    fun `PlayerError does not change state`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        assertEquals(SessionState.PLAYING, sm.currentState)

        sm.processEvent(
            PlaybackEvent.PlayerError(
                timestamp = 1100L,
                errorCode = 1001,
                errorCategory = ErrorCategory.SOURCE,
            ),
        )

        assertEquals(SessionState.PLAYING, sm.currentState)
        assertTrue(sm.isActive)
    }

    @Test
    fun `state change listener is called on transitions`() {
        val sm = createStateMachine()
        val transitions = mutableListOf<Triple<SessionState, SessionState, String?>>()

        sm.setStateChangeListener { oldState, newState, reason ->
            transitions.add(Triple(oldState, newState, reason))
        }

        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1100L, isPlaying = false))

        assertEquals(2, transitions.size)
        assertEquals(Triple(SessionState.ATTACHED, SessionState.PLAYING, "IsPlayingChanged(true)"), transitions[0])
        assertEquals(Triple(SessionState.PLAYING, SessionState.PAUSED, "IsPlayingChanged(false)"), transitions[1])
    }

    @Test
    fun `state change listener can be cleared`() {
        val sm = createStateMachine()
        val transitions = mutableListOf<Triple<SessionState, SessionState, String?>>()

        sm.setStateChangeListener { oldState, newState, reason ->
            transitions.add(Triple(oldState, newState, reason))
        }

        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true))
        assertEquals(1, transitions.size)

        sm.setStateChangeListener(null)
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1100L, isPlaying = false))
        assertEquals(1, transitions.size) // No new transition recorded
    }

    @Test
    fun `playbackActive computation with playWhenReady and buffering`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.PlayWhenReadyChanged(timestamp = 1000L, playWhenReady = true))
        sm.processEvent(PlaybackEvent.PlaybackStateChanged(timestamp = 1050L, playbackState = PlaybackState.BUFFERING))
        sm.processEvent(PlaybackEvent.AppBackgrounded(timestamp = 1100L))

        // BackgroundIdleTimeout should not end session because playbackActive = true
        sm.processEvent(PlaybackEvent.BackgroundIdleTimeout(timestamp = 121100L))

        assertEquals(SessionState.BACKGROUND, sm.currentState)
        assertNull(sm.endReason)
    }

    @Test
    fun `playbackActive false when playWhenReady=false even if buffering`() {
        val sm = createStateMachine()
        sm.processEvent(PlaybackEvent.PlayWhenReadyChanged(timestamp = 1000L, playWhenReady = false))
        sm.processEvent(PlaybackEvent.PlaybackStateChanged(timestamp = 1050L, playbackState = PlaybackState.BUFFERING))
        sm.processEvent(PlaybackEvent.AppBackgrounded(timestamp = 1100L))

        // BackgroundIdleTimeout should end session because playbackActive = false
        sm.processEvent(PlaybackEvent.BackgroundIdleTimeout(timestamp = 121100L))

        assertEquals(SessionState.ENDED, sm.currentState)
        assertEquals(EndReason.BACKGROUND_IDLE_TIMEOUT, sm.endReason)
    }

    @Test
    fun `complex state transition sequence`() {
        val sm = createStateMachine()

        // Start playback
        sm.processEvent(PlaybackEvent.PlayRequested(timestamp = 1000L))
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 1100L, isPlaying = true))
        assertEquals(SessionState.PLAYING, sm.currentState)

        // Pause
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 2000L, isPlaying = false))
        assertEquals(SessionState.PAUSED, sm.currentState)

        // Resume
        sm.processEvent(PlaybackEvent.IsPlayingChanged(timestamp = 3000L, isPlaying = true))
        assertEquals(SessionState.PLAYING, sm.currentState)

        // Buffering
        sm.processEvent(PlaybackEvent.BufferingStarted(timestamp = 4000L))
        assertEquals(SessionState.BUFFERING, sm.currentState)

        sm.processEvent(PlaybackEvent.BufferingEnded(timestamp = 4500L, durationMs = 500L))
        assertEquals(SessionState.PLAYING, sm.currentState)

        // Seek
        sm.processEvent(PlaybackEvent.SeekStarted(timestamp = 5000L))
        assertEquals(SessionState.SEEKING, sm.currentState)

        sm.processEvent(PlaybackEvent.SeekEnded(timestamp = 5200L, durationMs = 200L))
        assertEquals(SessionState.PLAYING, sm.currentState)

        // Background
        sm.processEvent(PlaybackEvent.AppBackgrounded(timestamp = 6000L))
        assertEquals(SessionState.BACKGROUND, sm.currentState)

        sm.processEvent(PlaybackEvent.AppForegrounded(timestamp = 7000L))
        assertEquals(SessionState.PLAYING, sm.currentState)

        // End
        sm.processEvent(PlaybackEvent.PlaybackEnded(timestamp = 8000L))
        assertEquals(SessionState.ENDED, sm.currentState)
        assertEquals(EndReason.PLAYBACK_ENDED, sm.endReason)
        assertTrue(sm.hasMeaningfulActivity)
        assertFalse(sm.shouldDiscard())
    }
}
