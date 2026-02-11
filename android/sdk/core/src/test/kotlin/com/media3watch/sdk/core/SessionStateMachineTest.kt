package com.media3watch.sdk.core

import com.media3watch.sdk.schema.EndReason
import com.media3watch.sdk.schema.PlaybackEvent
import com.media3watch.sdk.schema.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.EnumSource
import java.util.stream.Stream

/**
 * Comprehensive test suite for SessionStateMachine.
 *
 * Tests cover:
 * - All state transitions from session-lifecycle.md Mermaid diagram
 * - Meaningful activity detection
 * - End triggers with all 5 EndReasons
 * - Edge cases (rapid transitions, out-of-order events, duplicates)
 * - Idempotency guarantees
 * - Background state memory
 * - playbackActive computation
 * - State change callbacks
 */
@DisplayName("SessionStateMachine Tests")
class SessionStateMachineTest {
    private lateinit var stateMachine: SessionStateMachine
    private val testSessionId = "test-session-001"
    private val testTimestamp = 1706900000000L

    @BeforeEach
    fun setup() {
        stateMachine = SessionStateMachine(testSessionId)
    }

    // ======================
    // Happy Path: Basic State Transitions
    // ======================

    @Test
    @DisplayName("ATTACHED → PLAYING: PlayRequested + IsPlayingChanged(true)")
    fun test_ATTACHED_to_PLAYING_via_PlayRequested_and_IsPlayingChanged() {
        // Initial state is ATTACHED
        assertEquals(SessionState.ATTACHED, stateMachine.getState())

        // PlayRequested alone doesn't change state
        stateMachine.processEvent(PlaybackEvent.PlayRequested(testTimestamp))
        assertEquals(SessionState.ATTACHED, stateMachine.getState())

        // IsPlayingChanged(true) promotes to PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 100, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())
        assertTrue(stateMachine.hasMeaningfulActivity())
    }

    @Test
    @DisplayName("PLAYING → PAUSED: IsPlayingChanged(false)")
    fun test_PLAYING_to_PAUSED_via_IsPlayingChanged() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // PLAYING → PAUSED
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 100, isPlaying = false))
        assertEquals(SessionState.PAUSED, stateMachine.getState())
    }

    @Test
    @DisplayName("PAUSED → PLAYING: IsPlayingChanged(true)")
    fun test_PAUSED_to_PLAYING_via_IsPlayingChanged() {
        // Setup: ATTACHED → PLAYING → PAUSED
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 100, isPlaying = false))
        assertEquals(SessionState.PAUSED, stateMachine.getState())

        // PAUSED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 200, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("PLAYING → BUFFERING: BufferingStarted")
    fun test_PLAYING_to_BUFFERING_via_BufferingStarted() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // PLAYING → BUFFERING
        stateMachine.processEvent(PlaybackEvent.BufferingStarted(testTimestamp + 100))
        assertEquals(SessionState.BUFFERING, stateMachine.getState())
    }

    @Test
    @DisplayName("BUFFERING → PLAYING: BufferingEnded with isPlaying=true")
    fun test_BUFFERING_to_PLAYING_via_BufferingEnded() {
        // Setup: ATTACHED → BUFFERING → ensure isPlaying=true
        stateMachine.processEvent(PlaybackEvent.BufferingStarted(testTimestamp))
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 50, isPlaying = true))
        assertEquals(SessionState.BUFFERING, stateMachine.getState())

        // BUFFERING → PLAYING
        stateMachine.processEvent(PlaybackEvent.BufferingEnded(testTimestamp + 100, durationMs = 500))
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("PLAYING → SEEKING: SeekStarted")
    fun test_PLAYING_to_SEEKING_via_SeekStarted() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // PLAYING → SEEKING
        stateMachine.processEvent(PlaybackEvent.SeekStarted(testTimestamp + 100))
        assertEquals(SessionState.SEEKING, stateMachine.getState())
    }

    @Test
    @DisplayName("SEEKING → PLAYING: SeekEnded with isPlaying=true")
    fun test_SEEKING_to_PLAYING_via_SeekEnded() {
        // Setup: ATTACHED → PLAYING → SEEKING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.SeekStarted(testTimestamp + 100))
        assertEquals(SessionState.SEEKING, stateMachine.getState())

        // SEEKING → PLAYING
        stateMachine.processEvent(PlaybackEvent.SeekEnded(testTimestamp + 200, durationMs = 300))
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("PLAYING → BACKGROUND: AppBackgrounded")
    fun test_PLAYING_to_BACKGROUND_via_AppBackgrounded() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // PLAYING → BACKGROUND
        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 100))
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())
    }

    @Test
    @DisplayName("BACKGROUND → PLAYING: AppForegrounded with playbackActive=true")
    fun test_BACKGROUND_to_PLAYING_via_AppForegrounded() {
        // Setup: ATTACHED → PLAYING → BACKGROUND
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 100))
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())

        // BACKGROUND → PLAYING (isPlaying still true)
        stateMachine.processEvent(PlaybackEvent.AppForegrounded(testTimestamp + 200))
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("BACKGROUND → PAUSED: AppForegrounded with playbackActive=false")
    fun test_BACKGROUND_to_PAUSED_via_AppForegrounded() {
        // Setup: ATTACHED → PLAYING → PAUSED → BACKGROUND
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 100, isPlaying = false))
        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 200))
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())

        // BACKGROUND → PAUSED (isPlaying still false)
        stateMachine.processEvent(PlaybackEvent.AppForegrounded(testTimestamp + 300))
        assertEquals(SessionState.PAUSED, stateMachine.getState())
    }

    // ======================
    // Happy Path: Meaningful Activity Detection
    // ======================

    @Test
    @DisplayName("ATTACHED + PlayRequested → meaningful activity = true")
    fun test_PlayRequested_marks_meaningful_activity() {
        assertFalse(stateMachine.hasMeaningfulActivity())

        stateMachine.processEvent(PlaybackEvent.PlayRequested(testTimestamp))

        assertTrue(stateMachine.hasMeaningfulActivity())
    }

    @Test
    @DisplayName("ATTACHED + FirstFrameRendered → meaningful activity = true")
    fun test_FirstFrameRendered_marks_meaningful_activity() {
        assertFalse(stateMachine.hasMeaningfulActivity())

        stateMachine.processEvent(PlaybackEvent.FirstFrameRendered(testTimestamp))

        assertTrue(stateMachine.hasMeaningfulActivity())
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("ATTACHED + BufferingStarted → meaningful activity = true")
    fun test_BufferingStarted_marks_meaningful_activity() {
        assertFalse(stateMachine.hasMeaningfulActivity())

        stateMachine.processEvent(PlaybackEvent.BufferingStarted(testTimestamp))

        assertTrue(stateMachine.hasMeaningfulActivity())
        assertEquals(SessionState.BUFFERING, stateMachine.getState())
    }

    @Test
    @DisplayName("ATTACHED + IsPlayingChanged(true) → meaningful activity = true")
    fun test_IsPlayingChanged_true_marks_meaningful_activity() {
        assertFalse(stateMachine.hasMeaningfulActivity())

        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))

        assertTrue(stateMachine.hasMeaningfulActivity())
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("ATTACHED with no events → meaningful activity = false")
    fun test_ATTACHED_with_no_events_has_no_meaningful_activity() {
        assertEquals(SessionState.ATTACHED, stateMachine.getState())
        assertFalse(stateMachine.hasMeaningfulActivity())
    }

    // ======================
    // Happy Path: End Triggers
    // ======================

    @Test
    @DisplayName("PlayerReleased in PLAYING state → ENDED with PLAYER_RELEASED")
    fun test_PlayerReleased_in_PLAYING_ends_session() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // End trigger
        stateMachine.processEvent(PlaybackEvent.PlayerReleased(testTimestamp + 100))

        assertEquals(SessionState.ENDED, stateMachine.getState())
        assertEquals(EndReason.PLAYER_RELEASED, stateMachine.getEndReason())
    }

    @Test
    @DisplayName("PlaybackEnded in PLAYING state → ENDED with PLAYBACK_ENDED")
    fun test_PlaybackEnded_in_PLAYING_ends_session() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // End trigger
        stateMachine.processEvent(PlaybackEvent.PlaybackEnded(testTimestamp + 100))

        assertEquals(SessionState.ENDED, stateMachine.getState())
        assertEquals(EndReason.PLAYBACK_ENDED, stateMachine.getEndReason())
    }

    @Test
    @DisplayName("ContentSwitch in PLAYING state → ENDED with CONTENT_SWITCH")
    fun test_ContentSwitch_in_PLAYING_ends_session() {
        // Setup: ATTACHED → PLAYING (meaningful activity)
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())
        assertTrue(stateMachine.hasMeaningfulActivity())

        // End trigger
        stateMachine.processEvent(PlaybackEvent.MediaItemTransition(testTimestamp + 100, newContentId = "video-456"))

        assertEquals(SessionState.ENDED, stateMachine.getState())
        assertEquals(EndReason.CONTENT_SWITCH, stateMachine.getEndReason())
    }

    @Test
    @DisplayName("BackgroundIdleTimeout in BACKGROUND (playbackActive=false) → ENDED with BACKGROUND_IDLE_TIMEOUT")
    fun test_BackgroundIdleTimeout_when_not_playing_ends_session() {
        // Setup: ATTACHED → PLAYING → PAUSED → BACKGROUND
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 100, isPlaying = false))
        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 200))
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())
        assertFalse(stateMachine.isPlaybackActive())

        // End trigger
        stateMachine.processEvent(PlaybackEvent.BackgroundIdleTimeout(testTimestamp + 300))

        assertEquals(SessionState.ENDED, stateMachine.getState())
        assertEquals(EndReason.BACKGROUND_IDLE_TIMEOUT, stateMachine.getEndReason())
    }

    @Test
    @DisplayName("BackgroundIdleTimeout when playbackActive=true → no state change")
    fun test_BackgroundIdleTimeout_when_playing_does_not_end() {
        // Setup: ATTACHED → PLAYING → BACKGROUND (but still playing)
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 100))
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())
        assertTrue(stateMachine.isPlaybackActive())

        // Timeout should NOT end session
        stateMachine.processEvent(PlaybackEvent.BackgroundIdleTimeout(testTimestamp + 200))

        assertEquals(SessionState.BACKGROUND, stateMachine.getState())
        assertNull(stateMachine.getEndReason())
    }

    // ======================
    // Happy Path: Discard Rule
    // ======================

    @Test
    @DisplayName("Session with meaningful activity + PlayerReleased → shouldDiscard = false")
    fun test_session_with_activity_should_not_discard() {
        // Setup: ATTACHED → PLAYING (meaningful activity)
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertTrue(stateMachine.hasMeaningfulActivity())

        // End session
        stateMachine.processEvent(PlaybackEvent.PlayerReleased(testTimestamp + 100))
        assertEquals(SessionState.ENDED, stateMachine.getState())

        // Should NOT discard
        assertFalse(stateMachine.shouldDiscard())
    }

    @Test
    @DisplayName("Session with NO meaningful activity + PlayerReleased → shouldDiscard = true")
    fun test_session_without_activity_should_discard() {
        // Session in ATTACHED with no activity
        assertEquals(SessionState.ATTACHED, stateMachine.getState())
        assertFalse(stateMachine.hasMeaningfulActivity())

        // End session
        stateMachine.processEvent(PlaybackEvent.PlayerReleased(testTimestamp))
        assertEquals(SessionState.ENDED, stateMachine.getState())

        // Should discard
        assertTrue(stateMachine.shouldDiscard())
    }

    @Test
    @DisplayName("ATTACHED → ENDED (no activity) → shouldDiscard = true")
    fun test_attach_only_session_should_discard() {
        // Just attached, no events
        assertFalse(stateMachine.hasMeaningfulActivity())

        // End immediately
        stateMachine.processEvent(PlaybackEvent.PlayerReleased(testTimestamp))

        assertTrue(stateMachine.shouldDiscard())
    }

    // ======================
    // Happy Path: State Change Callbacks
    // ======================

    @Test
    @DisplayName("State change ATTACHED → PLAYING → callback invoked with correct states")
    fun test_state_change_callback_invoked() {
        var callbackInvoked = false
        var capturedOldState: SessionState? = null
        var capturedNewState: SessionState? = null
        var capturedSessionId: String? = null

        stateMachine.addStateChangeListener { sessionId, oldState, newState ->
            callbackInvoked = true
            capturedSessionId = sessionId
            capturedOldState = oldState
            capturedNewState = newState
        }

        // ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))

        assertTrue(callbackInvoked)
        assertEquals(testSessionId, capturedSessionId)
        assertEquals(SessionState.ATTACHED, capturedOldState)
        assertEquals(SessionState.PLAYING, capturedNewState)
    }

    @Test
    @DisplayName("Multiple state changes → callbacks invoked in correct order")
    fun test_multiple_state_change_callbacks() {
        val capturedTransitions = mutableListOf<Pair<SessionState, SessionState>>()

        stateMachine.addStateChangeListener { _, oldState, newState ->
            capturedTransitions.add(oldState to newState)
        }

        // ATTACHED → PLAYING → PAUSED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 100, isPlaying = false))
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 200, isPlaying = true))

        assertEquals(3, capturedTransitions.size)
        assertEquals(SessionState.ATTACHED to SessionState.PLAYING, capturedTransitions[0])
        assertEquals(SessionState.PLAYING to SessionState.PAUSED, capturedTransitions[1])
        assertEquals(SessionState.PAUSED to SessionState.PLAYING, capturedTransitions[2])
    }

    @Test
    @DisplayName("No callback when state doesn't change (idempotent event)")
    fun test_no_callback_on_idempotent_event() {
        var callbackCount = 0

        stateMachine.addStateChangeListener { _, _, _ ->
            callbackCount++
        }

        // Transition to PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(1, callbackCount)

        // Idempotent event (PlayRequested doesn't change state from PLAYING)
        stateMachine.processEvent(PlaybackEvent.PlayRequested(testTimestamp + 100))
        assertEquals(1, callbackCount) // Should not increment
    }

    @Test
    @DisplayName("Remove state change listener → stops receiving callbacks")
    fun test_removeStateChangeListener_stops_callbacks() {
        var callbackCount = 0
        val listener = SessionStateMachine.StateChangeListener { _, _, _ -> callbackCount++ }

        stateMachine.addStateChangeListener(listener)
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(1, callbackCount)

        stateMachine.removeStateChangeListener(listener)
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 100, isPlaying = false))
        assertEquals(1, callbackCount) // Should not increment
    }

    // ======================
    // Edge Cases: Rapid State Transitions
    // ======================

    @Test
    @DisplayName("PLAYING → PAUSED → PLAYING → PAUSED (rapid toggle) → state tracks correctly")
    fun test_rapid_play_pause_toggle() {
        // ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // Rapid toggles
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 10, isPlaying = false))
        assertEquals(SessionState.PAUSED, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 20, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 30, isPlaying = false))
        assertEquals(SessionState.PAUSED, stateMachine.getState())
    }

    @Test
    @DisplayName("PLAYING → BUFFERING → PLAYING → BUFFERING → PLAYING (rapid rebuffering) → state tracks correctly")
    fun test_rapid_rebuffering() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // Rapid buffering cycles
        stateMachine.processEvent(PlaybackEvent.BufferingStarted(testTimestamp + 10))
        assertEquals(SessionState.BUFFERING, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.BufferingEnded(testTimestamp + 20, durationMs = 100))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.BufferingStarted(testTimestamp + 30))
        assertEquals(SessionState.BUFFERING, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.BufferingEnded(testTimestamp + 40, durationMs = 100))
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("PLAYING → SEEKING → SEEKING → PLAYING (duplicate seek start) → idempotent, state correct")
    fun test_duplicate_seek_start_is_idempotent() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // First SeekStarted
        stateMachine.processEvent(PlaybackEvent.SeekStarted(testTimestamp + 10))
        assertEquals(SessionState.SEEKING, stateMachine.getState())

        // Duplicate SeekStarted (idempotent)
        stateMachine.processEvent(PlaybackEvent.SeekStarted(testTimestamp + 20))
        assertEquals(SessionState.SEEKING, stateMachine.getState())

        // SeekEnded
        stateMachine.processEvent(PlaybackEvent.SeekEnded(testTimestamp + 30, durationMs = 200))
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("ATTACHED → PlayRequested → PlayRequested → PLAYING (duplicate play) → idempotent")
    fun test_duplicate_play_requested_is_idempotent() {
        assertEquals(SessionState.ATTACHED, stateMachine.getState())

        // First PlayRequested
        stateMachine.processEvent(PlaybackEvent.PlayRequested(testTimestamp))
        assertTrue(stateMachine.hasMeaningfulActivity())

        // Duplicate PlayRequested
        stateMachine.processEvent(PlaybackEvent.PlayRequested(testTimestamp + 10))
        assertTrue(stateMachine.hasMeaningfulActivity())
        assertEquals(SessionState.ATTACHED, stateMachine.getState())

        // Promote to PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 20, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    // ======================
    // Edge Cases: Background State Memory
    // ======================

    @Test
    @DisplayName("PLAYING → BACKGROUND → foreground → returns to PLAYING")
    fun test_background_memory_from_playing() {
        // Setup: ATTACHED → PLAYING → BACKGROUND
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 100))
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())

        // Return to foreground
        stateMachine.processEvent(PlaybackEvent.AppForegrounded(testTimestamp + 200))

        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("BUFFERING → BACKGROUND → foreground → returns to BUFFERING")
    fun test_background_memory_from_buffering() {
        // Setup: ATTACHED → BUFFERING → BACKGROUND
        stateMachine.processEvent(PlaybackEvent.BufferingStarted(testTimestamp))
        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 100))
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())

        // Return to foreground
        stateMachine.processEvent(PlaybackEvent.AppForegrounded(testTimestamp + 200))

        assertEquals(SessionState.BUFFERING, stateMachine.getState())
    }

    @Test
    @DisplayName("SEEKING → BACKGROUND → foreground → returns to SEEKING")
    fun test_background_memory_from_seeking() {
        // Setup: ATTACHED → PLAYING → SEEKING → BACKGROUND
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.SeekStarted(testTimestamp + 100))
        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 200))
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())

        // Return to foreground
        stateMachine.processEvent(PlaybackEvent.AppForegrounded(testTimestamp + 300))

        assertEquals(SessionState.SEEKING, stateMachine.getState())
    }

    @Test
    @DisplayName("PAUSED → BACKGROUND → foreground → returns to PAUSED")
    fun test_background_memory_from_paused() {
        // Setup: ATTACHED → PLAYING → PAUSED → BACKGROUND
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 100, isPlaying = false))
        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 200))
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())

        // Return to foreground
        stateMachine.processEvent(PlaybackEvent.AppForegrounded(testTimestamp + 300))

        assertEquals(SessionState.PAUSED, stateMachine.getState())
    }

    // ======================
    // Edge Cases: playbackActive Computation
    // ======================

    @Test
    @DisplayName("playWhenReady=true + playbackState=STATE_READY + isPlaying=true → playbackActive=true")
    fun test_playbackActive_when_playing() {
        // Setup playing state
        stateMachine.processEvent(PlaybackEvent.PlayWhenReadyChanged(testTimestamp, playWhenReady = true))
        stateMachine.processEvent(
            PlaybackEvent.PlaybackStateChanged(
                testTimestamp + 10,
                playbackState = SessionStateMachine.STATE_READY,
            ),
        )
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 20, isPlaying = true))

        assertTrue(stateMachine.isPlaybackActive())
    }

    @Test
    @DisplayName("playWhenReady=true + playbackState=STATE_BUFFERING + isPlaying=false → playbackActive=true")
    fun test_playbackActive_when_buffering_with_playWhenReady() {
        // Setup buffering state with playWhenReady
        stateMachine.processEvent(PlaybackEvent.PlayWhenReadyChanged(testTimestamp, playWhenReady = true))
        stateMachine.processEvent(
            PlaybackEvent.PlaybackStateChanged(
                testTimestamp + 10,
                playbackState = SessionStateMachine.STATE_BUFFERING,
            ),
        )
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 20, isPlaying = false))

        assertTrue(stateMachine.isPlaybackActive())
    }

    @Test
    @DisplayName("playWhenReady=false + playbackState=STATE_READY + isPlaying=false → playbackActive=false")
    fun test_playbackActive_when_paused() {
        // Setup paused state
        stateMachine.processEvent(PlaybackEvent.PlayWhenReadyChanged(testTimestamp, playWhenReady = false))
        stateMachine.processEvent(
            PlaybackEvent.PlaybackStateChanged(
                testTimestamp + 10,
                playbackState = SessionStateMachine.STATE_READY,
            ),
        )
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 20, isPlaying = false))

        assertFalse(stateMachine.isPlaybackActive())
    }

    @Test
    @DisplayName("BACKGROUND state + playWhenReady changes → doesn't exit BACKGROUND until AppForegrounded")
    fun test_playWhenReady_change_in_background_doesnt_exit_background() {
        // Setup: ATTACHED → PLAYING → BACKGROUND
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 100))
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())

        // Change playWhenReady while in background
        stateMachine.processEvent(PlaybackEvent.PlayWhenReadyChanged(testTimestamp + 200, playWhenReady = false))

        // Should still be in BACKGROUND
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())
    }

    @Test
    @DisplayName("playWhenReady + playbackState updated out-of-order → playbackActive computed correctly")
    fun test_playbackActive_with_out_of_order_updates() {
        // Send updates out of typical order
        stateMachine.processEvent(
            PlaybackEvent.PlaybackStateChanged(
                testTimestamp,
                playbackState = SessionStateMachine.STATE_BUFFERING,
            ),
        )
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 10, isPlaying = false))
        stateMachine.processEvent(PlaybackEvent.PlayWhenReadyChanged(testTimestamp + 20, playWhenReady = true))

        // Should compute playbackActive correctly
        assertTrue(stateMachine.isPlaybackActive())
    }

    // ======================
    // Edge Cases: End Trigger Edge Cases
    // ======================

    @Test
    @DisplayName("PlayerReleased in ATTACHED state (no activity) → discard rule applies")
    fun test_player_released_in_attached_applies_discard_rule() {
        assertEquals(SessionState.ATTACHED, stateMachine.getState())
        assertFalse(stateMachine.hasMeaningfulActivity())

        stateMachine.processEvent(PlaybackEvent.PlayerReleased(testTimestamp))

        assertEquals(SessionState.ENDED, stateMachine.getState())
        assertTrue(stateMachine.shouldDiscard())
    }

    @Test
    @DisplayName("Multiple end triggers (PlayerReleased twice) → idempotent, state stays ENDED")
    fun test_multiple_end_triggers_are_idempotent() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))

        // First end trigger
        stateMachine.processEvent(PlaybackEvent.PlayerReleased(testTimestamp + 100))
        assertEquals(SessionState.ENDED, stateMachine.getState())
        assertEquals(EndReason.PLAYER_RELEASED, stateMachine.getEndReason())

        // Second end trigger (should be no-op)
        stateMachine.processEvent(PlaybackEvent.PlaybackEnded(testTimestamp + 200))
        assertEquals(SessionState.ENDED, stateMachine.getState())
        assertEquals(EndReason.PLAYER_RELEASED, stateMachine.getEndReason()) // Original reason preserved
    }

    @Test
    @DisplayName("End trigger in ENDED state → no-op")
    fun test_end_trigger_in_ended_state_is_noop() {
        // Setup: ATTACHED → PLAYING → ENDED
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.PlayerReleased(testTimestamp + 100))
        assertEquals(SessionState.ENDED, stateMachine.getState())

        // Try another end trigger
        stateMachine.processEvent(PlaybackEvent.PlaybackEnded(testTimestamp + 200))

        // Should stay ENDED with original reason
        assertEquals(SessionState.ENDED, stateMachine.getState())
        assertEquals(EndReason.PLAYER_RELEASED, stateMachine.getEndReason())
    }

    // ======================
    // Edge Cases: Out-of-Order Events
    // ======================

    @Test
    @DisplayName("IsPlayingChanged(true) before PlayRequested → state handles gracefully")
    fun test_isPlayingChanged_before_playRequested() {
        assertEquals(SessionState.ATTACHED, stateMachine.getState())

        // IsPlayingChanged before PlayRequested
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))

        // Should transition to PLAYING and mark meaningful activity
        assertEquals(SessionState.PLAYING, stateMachine.getState())
        assertTrue(stateMachine.hasMeaningfulActivity())

        // Late PlayRequested (should be idempotent)
        stateMachine.processEvent(PlaybackEvent.PlayRequested(testTimestamp + 100))

        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("BufferingEnded without BufferingStarted → no crash, state stays as-is")
    fun test_bufferingEnded_without_bufferingStarted() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // BufferingEnded without BufferingStarted
        stateMachine.processEvent(PlaybackEvent.BufferingEnded(testTimestamp + 100, durationMs = 500))

        // Should stay in PLAYING (no crash)
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("SeekEnded without SeekStarted → no crash, state stays as-is")
    fun test_seekEnded_without_seekStarted() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // SeekEnded without SeekStarted
        stateMachine.processEvent(PlaybackEvent.SeekEnded(testTimestamp + 100, durationMs = 300))

        // Should stay in PLAYING (no crash)
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("AppForegrounded without AppBackgrounded → no crash")
    fun test_appForegrounded_without_appBackgrounded() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // AppForegrounded without AppBackgrounded (already foreground)
        stateMachine.processEvent(PlaybackEvent.AppForegrounded(testTimestamp + 100))

        // Should stay in PLAYING (no crash)
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    // ======================
    // Edge Cases: Invalid Event Sequences
    // ======================

    @Test
    @DisplayName("Events arriving in ENDED state → rejected/no-op")
    fun test_events_in_ended_state_are_noop() {
        // Setup: ATTACHED → PLAYING → ENDED
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.PlayerReleased(testTimestamp + 100))
        assertEquals(SessionState.ENDED, stateMachine.getState())

        // Try various events
        stateMachine.processEvent(PlaybackEvent.BufferingStarted(testTimestamp + 200))
        assertEquals(SessionState.ENDED, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 300, isPlaying = false))
        assertEquals(SessionState.ENDED, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.SeekStarted(testTimestamp + 400))
        assertEquals(SessionState.ENDED, stateMachine.getState())
    }

    @Test
    @DisplayName("PlayerError event doesn't change state")
    fun test_playerError_doesnt_change_state() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // PlayerError
        stateMachine.processEvent(
            PlaybackEvent.PlayerError(
                testTimestamp + 100,
                errorCode = 1001,
                errorCategory = com.media3watch.sdk.schema.ErrorCategory.NETWORK,
            ),
        )

        // Should stay in PLAYING
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    // ======================
    // Failure Scenarios: Duplicate Event Idempotency
    // ======================

    @Test
    @DisplayName("Duplicate PlayRequested in ATTACHED → doesn't double-count meaningful activity")
    fun test_duplicate_playRequested_doesnt_double_count() {
        assertFalse(stateMachine.hasMeaningfulActivity())

        stateMachine.processEvent(PlaybackEvent.PlayRequested(testTimestamp))
        assertTrue(stateMachine.hasMeaningfulActivity())

        // Duplicate
        stateMachine.processEvent(PlaybackEvent.PlayRequested(testTimestamp + 10))
        assertTrue(stateMachine.hasMeaningfulActivity())

        // State should still be ATTACHED
        assertEquals(SessionState.ATTACHED, stateMachine.getState())
    }

    @Test
    @DisplayName("Duplicate BufferingStarted in BUFFERING → no state corruption")
    fun test_duplicate_bufferingStarted_in_buffering() {
        // Setup: ATTACHED → BUFFERING
        stateMachine.processEvent(PlaybackEvent.BufferingStarted(testTimestamp))
        assertEquals(SessionState.BUFFERING, stateMachine.getState())

        // Duplicate BufferingStarted
        stateMachine.processEvent(PlaybackEvent.BufferingStarted(testTimestamp + 10))
        assertEquals(SessionState.BUFFERING, stateMachine.getState())

        // Should still be able to exit buffering normally
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 20, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.BufferingEnded(testTimestamp + 30, durationMs = 100))
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("Duplicate PlayerReleased in ENDED → no exception, idempotent")
    fun test_duplicate_playerReleased_in_ended() {
        // Setup: ATTACHED → PLAYING → ENDED
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.PlayerReleased(testTimestamp + 100))
        assertEquals(SessionState.ENDED, stateMachine.getState())

        // Duplicate PlayerReleased (should be no-op)
        stateMachine.processEvent(PlaybackEvent.PlayerReleased(testTimestamp + 200))

        assertEquals(SessionState.ENDED, stateMachine.getState())
        assertEquals(EndReason.PLAYER_RELEASED, stateMachine.getEndReason())
    }

    @Test
    @DisplayName("Duplicate AppBackgrounded in BACKGROUND → idempotent")
    fun test_duplicate_appBackgrounded_in_background() {
        // Setup: ATTACHED → PLAYING → BACKGROUND
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 100))
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())

        // Duplicate AppBackgrounded
        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 200))

        assertEquals(SessionState.BACKGROUND, stateMachine.getState())
    }

    // ======================
    // Failure Scenarios: State Machine Consistency Under Load
    // ======================

    @Test
    @DisplayName("1000 rapid state transitions → state machine remains consistent")
    fun test_1000_rapid_transitions() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        // 1000 rapid play/pause cycles
        for (i in 1..1000) {
            val isPaused = i % 2 == 0
            stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + i, isPlaying = !isPaused))
        }

        // Final state should be consistent (last event was isPlaying=false since 1000 is even)
        assertEquals(SessionState.PAUSED, stateMachine.getState())
        assertTrue(stateMachine.hasMeaningfulActivity())
    }

    @Test
    @DisplayName("Alternating PLAYING/PAUSED 100 times → final state correct")
    fun test_alternating_play_pause_100_times() {
        // Setup: ATTACHED → PLAYING
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))

        // 100 alternations
        for (i in 1..100) {
            val shouldPlay = i % 2 == 0
            stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + i, isPlaying = shouldPlay))
        }

        // Final state: 100 is even, so isPlaying=true
        assertEquals(SessionState.PLAYING, stateMachine.getState())
    }

    @Test
    @DisplayName("Mixed events in sequence → state always valid")
    fun test_mixed_events_sequence() {
        // Complex event sequence
        stateMachine.processEvent(PlaybackEvent.PlayRequested(testTimestamp))
        assertEquals(SessionState.ATTACHED, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.BufferingStarted(testTimestamp + 10))
        assertEquals(SessionState.BUFFERING, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 20, isPlaying = true))
        assertEquals(SessionState.BUFFERING, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.BufferingEnded(testTimestamp + 30, durationMs = 100))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.SeekStarted(testTimestamp + 40))
        assertEquals(SessionState.SEEKING, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.SeekEnded(testTimestamp + 50, durationMs = 200))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.AppBackgrounded(testTimestamp + 60))
        assertEquals(SessionState.BACKGROUND, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.AppForegrounded(testTimestamp + 70))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 80, isPlaying = false))
        assertEquals(SessionState.PAUSED, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.PlayerReleased(testTimestamp + 90))
        assertEquals(SessionState.ENDED, stateMachine.getState())
    }

    // ======================
    // Failure Scenarios: Callback Failures
    // ======================

    @Test
    @DisplayName("State change callback throws exception → state machine continues")
    fun test_callback_exception_doesnt_break_state_machine() {
        var successCallbackInvoked = false

        // Add failing listener
        stateMachine.addStateChangeListener { _, _, _ ->
            throw RuntimeException("Callback failed")
        }

        // Add successful listener
        stateMachine.addStateChangeListener { _, _, _ ->
            successCallbackInvoked = true
        }

        // State change should succeed despite exception
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))

        assertEquals(SessionState.PLAYING, stateMachine.getState())
        assertTrue(successCallbackInvoked)
    }

    @Test
    @DisplayName("Multiple listeners, one throws → other listeners still invoked")
    fun test_callback_exception_doesnt_prevent_other_listeners() {
        var listener1Invoked = false
        var listener2Invoked = false
        var listener3Invoked = false

        stateMachine.addStateChangeListener { _, _, _ -> listener1Invoked = true }
        stateMachine.addStateChangeListener { _, _, _ -> throw RuntimeException("Fail") }
        stateMachine.addStateChangeListener { _, _, _ -> listener2Invoked = true }
        stateMachine.addStateChangeListener { _, _, _ -> throw RuntimeException("Fail again") }
        stateMachine.addStateChangeListener { _, _, _ -> listener3Invoked = true }

        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))

        assertTrue(listener1Invoked)
        assertTrue(listener2Invoked)
        assertTrue(listener3Invoked)
    }

    // ======================
    // Thread Safety: Documentation Tests
    // ======================

    @Test
    @DisplayName("Thread-safety: Single-threaded contract is documented (not thread-safe)")
    fun test_single_threaded_contract_documented() {
        // This test documents the single-threaded contract of SessionStateMachine.
        // As documented in SessionStateMachine.kt:
        // "Thread-safety: Not thread-safe. Caller must synchronize access if needed."
        //
        // This is an acceptable MVP design decision because:
        // 1. Media3 Player callbacks run on a single thread (main thread by default)
        // 2. The state machine will be called from Media3 adapter callbacks sequentially
        // 3. Adding synchronization adds complexity without benefit in the expected usage
        //
        // If concurrent access is needed in the future, the caller should:
        // - Wrap calls in synchronized blocks
        // - Use a serial executor/dispatcher
        // - Add @Synchronized annotations to public methods

        // Demonstration: Sequential access works correctly
        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp, isPlaying = true))
        assertEquals(SessionState.PLAYING, stateMachine.getState())

        stateMachine.processEvent(PlaybackEvent.IsPlayingChanged(testTimestamp + 100, isPlaying = false))
        assertEquals(SessionState.PAUSED, stateMachine.getState())

        // Note: Concurrent access is not tested as it would require implementation changes.
        // The contract explicitly states the class is not thread-safe.
    }

    // ======================
    // Parameterized Tests: All End Reasons
    // ======================

    @ParameterizedTest(name = "End trigger: {0}")
    @MethodSource("endReasonScenarios")
    @DisplayName("Parameterized test for all 5 end triggers")
    fun test_all_end_reasons(
        endReason: EndReason,
        setupEvents: List<PlaybackEvent>,
        triggerEvent: PlaybackEvent,
    ) {
        // Execute setup events
        setupEvents.forEach { stateMachine.processEvent(it) }

        // Trigger end
        stateMachine.processEvent(triggerEvent)

        // Verify
        assertEquals(SessionState.ENDED, stateMachine.getState())
        assertEquals(endReason, stateMachine.getEndReason())
    }

    companion object {
        private const val testTs = 1706900000000L

        @JvmStatic
        fun endReasonScenarios(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    EndReason.PLAYER_RELEASED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(testTs, isPlaying = true),
                    ),
                    PlaybackEvent.PlayerReleased(testTs + 100),
                ),
                Arguments.of(
                    EndReason.PLAYBACK_ENDED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(testTs, isPlaying = true),
                    ),
                    PlaybackEvent.PlaybackEnded(testTs + 100),
                ),
                Arguments.of(
                    EndReason.CONTENT_SWITCH,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(testTs, isPlaying = true),
                    ),
                    PlaybackEvent.MediaItemTransition(testTs + 100, newContentId = "video-456"),
                ),
                Arguments.of(
                    EndReason.BACKGROUND_IDLE_TIMEOUT,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(testTs, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(testTs + 50, isPlaying = false),
                        PlaybackEvent.AppBackgrounded(testTs + 100),
                    ),
                    PlaybackEvent.BackgroundIdleTimeout(testTs + 200),
                ),
                // Note: PLAYER_REPLACED is not included in this parameterized test because it requires
                // a higher-level API call (Media3Watch.attach(newPlayer)) rather than just a PlaybackEvent.
                // PLAYER_REPLACED will be tested in integration tests when the Media3 adapter and
                // runtime modules are implemented (see Issue #20).
                // The end trigger logic itself is validated through ContentSwitch and PlayerReleased,
                // which exercise the same transitionToEnded() code path.
            )
    }
}
