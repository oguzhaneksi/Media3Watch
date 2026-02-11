package com.media3watch.sdk.core

import com.media3watch.sdk.schema.PlaybackEvent
import com.media3watch.sdk.schema.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Table-driven tests covering all state transitions from session-lifecycle.md Mermaid diagram.
 *
 * This test ensures every documented transition path is validated.
 */
@DisplayName("SessionStateMachine State Transitions Table")
class SessionStateMachineStateTransitionTableTest {
    private lateinit var stateMachine: SessionStateMachine
    private val testSessionId = "test-session-table"
    private val testTimestamp = 1706900000000L

    @BeforeEach
    fun setup() {
        stateMachine = SessionStateMachine(testSessionId)
    }

    /**
     * Table-driven test covering all 30+ state transitions.
     *
     * Format: (fromState, setupEvents, triggerEvent, expectedToState, description)
     */
    @ParameterizedTest(name = "{4}: {0} → {3}")
    @MethodSource("stateTransitionTable")
    @DisplayName("State transition")
    fun test_state_transition(
        fromState: SessionState,
        setupEvents: List<PlaybackEvent>,
        triggerEvent: PlaybackEvent,
        expectedToState: SessionState,
        description: String,
    ) {
        // Setup: transition to fromState
        setupEvents.forEach { stateMachine.processEvent(it) }
        assertEquals(fromState, stateMachine.getState(), "Failed to setup state $fromState")

        // Execute: process trigger event
        stateMachine.processEvent(triggerEvent)

        // Verify: state transitioned to expectedToState
        assertEquals(expectedToState, stateMachine.getState(), description)
    }

    companion object {
        private const val ts = 1706900000000L

        @JvmStatic
        fun stateTransitionTable(): Stream<Arguments> =
            Stream.of(
                // NO_SESSION is not tested here as it's the initial creation state (before attach)

                // From ATTACHED
                Arguments.of(
                    SessionState.ATTACHED,
                    emptyList<PlaybackEvent>(),
                    PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                    SessionState.PLAYING,
                    "ATTACHED → PLAYING: IsPlayingChanged(true) with PlayRequested implied",
                ),
                Arguments.of(
                    SessionState.ATTACHED,
                    listOf(PlaybackEvent.PlayRequested(ts)),
                    PlaybackEvent.IsPlayingChanged(ts + 10, isPlaying = true),
                    SessionState.PLAYING,
                    "ATTACHED → PLAYING: PlayRequested + IsPlayingChanged(true)",
                ),
                Arguments.of(
                    SessionState.ATTACHED,
                    emptyList<PlaybackEvent>(),
                    PlaybackEvent.BufferingStarted(ts),
                    SessionState.BUFFERING,
                    "ATTACHED → BUFFERING: BufferingStarted",
                ),
                Arguments.of(
                    SessionState.ATTACHED,
                    emptyList<PlaybackEvent>(),
                    PlaybackEvent.FirstFrameRendered(ts),
                    SessionState.PLAYING,
                    "ATTACHED → PLAYING: FirstFrameRendered (implies started)",
                ),
                Arguments.of(
                    SessionState.ATTACHED,
                    emptyList<PlaybackEvent>(),
                    PlaybackEvent.AppBackgrounded(ts),
                    SessionState.BACKGROUND,
                    "ATTACHED → BACKGROUND: AppBackgrounded",
                ),
                Arguments.of(
                    SessionState.ATTACHED,
                    emptyList<PlaybackEvent>(),
                    PlaybackEvent.PlayerReleased(ts),
                    SessionState.ENDED,
                    "ATTACHED → ENDED: PlayerReleased",
                ),

                // From PLAYING
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(ts, isPlaying = true)),
                    PlaybackEvent.IsPlayingChanged(ts + 10, isPlaying = false),
                    SessionState.PAUSED,
                    "PLAYING → PAUSED: IsPlayingChanged(false)",
                ),
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(ts, isPlaying = true)),
                    PlaybackEvent.BufferingStarted(ts + 10),
                    SessionState.BUFFERING,
                    "PLAYING → BUFFERING: BufferingStarted",
                ),
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(ts, isPlaying = true)),
                    PlaybackEvent.SeekStarted(ts + 10),
                    SessionState.SEEKING,
                    "PLAYING → SEEKING: SeekStarted",
                ),
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(ts, isPlaying = true)),
                    PlaybackEvent.AppBackgrounded(ts + 10),
                    SessionState.BACKGROUND,
                    "PLAYING → BACKGROUND: AppBackgrounded",
                ),
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(ts, isPlaying = true)),
                    PlaybackEvent.PlayerReleased(ts + 10),
                    SessionState.ENDED,
                    "PLAYING → ENDED: PlayerReleased",
                ),
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(ts, isPlaying = true)),
                    PlaybackEvent.PlaybackEnded(ts + 10),
                    SessionState.ENDED,
                    "PLAYING → ENDED: PlaybackEnded",
                ),
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(ts, isPlaying = true)),
                    PlaybackEvent.MediaItemTransition(ts + 10, newContentId = "video-2"),
                    SessionState.ENDED,
                    "PLAYING → ENDED: MediaItemTransition (CONTENT_SWITCH)",
                ),

                // From PAUSED
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(ts + 10, isPlaying = false),
                    ),
                    PlaybackEvent.IsPlayingChanged(ts + 20, isPlaying = true),
                    SessionState.PLAYING,
                    "PAUSED → PLAYING: IsPlayingChanged(true)",
                ),
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(ts + 10, isPlaying = false),
                    ),
                    PlaybackEvent.BufferingStarted(ts + 20),
                    SessionState.BUFFERING,
                    "PAUSED → BUFFERING: BufferingStarted",
                ),
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(ts + 10, isPlaying = false),
                    ),
                    PlaybackEvent.SeekStarted(ts + 20),
                    SessionState.SEEKING,
                    "PAUSED → SEEKING: SeekStarted",
                ),
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(ts + 10, isPlaying = false),
                    ),
                    PlaybackEvent.AppBackgrounded(ts + 20),
                    SessionState.BACKGROUND,
                    "PAUSED → BACKGROUND: AppBackgrounded",
                ),
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(ts + 10, isPlaying = false),
                    ),
                    PlaybackEvent.PlayerReleased(ts + 20),
                    SessionState.ENDED,
                    "PAUSED → ENDED: PlayerReleased",
                ),
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(ts + 10, isPlaying = false),
                    ),
                    PlaybackEvent.PlaybackEnded(ts + 20),
                    SessionState.ENDED,
                    "PAUSED → ENDED: PlaybackEnded",
                ),
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(ts + 10, isPlaying = false),
                    ),
                    PlaybackEvent.MediaItemTransition(ts + 20, newContentId = "video-2"),
                    SessionState.ENDED,
                    "PAUSED → ENDED: MediaItemTransition (CONTENT_SWITCH)",
                ),

                // From BUFFERING
                Arguments.of(
                    SessionState.BUFFERING,
                    listOf(
                        PlaybackEvent.BufferingStarted(ts),
                        PlaybackEvent.IsPlayingChanged(ts + 10, isPlaying = true),
                    ),
                    PlaybackEvent.BufferingEnded(ts + 20, durationMs = 100),
                    SessionState.PLAYING,
                    "BUFFERING → PLAYING: BufferingEnded with isPlaying=true",
                ),
                Arguments.of(
                    SessionState.BUFFERING,
                    listOf(
                        PlaybackEvent.BufferingStarted(ts),
                        PlaybackEvent.IsPlayingChanged(ts + 10, isPlaying = false),
                    ),
                    PlaybackEvent.BufferingEnded(ts + 20, durationMs = 100),
                    SessionState.PAUSED,
                    "BUFFERING → PAUSED: BufferingEnded with isPlaying=false",
                ),
                Arguments.of(
                    SessionState.BUFFERING,
                    listOf(PlaybackEvent.BufferingStarted(ts)),
                    PlaybackEvent.AppBackgrounded(ts + 10),
                    SessionState.BACKGROUND,
                    "BUFFERING → BACKGROUND: AppBackgrounded",
                ),
                Arguments.of(
                    SessionState.BUFFERING,
                    listOf(PlaybackEvent.BufferingStarted(ts)),
                    PlaybackEvent.PlayerReleased(ts + 10),
                    SessionState.ENDED,
                    "BUFFERING → ENDED: PlayerReleased",
                ),
                Arguments.of(
                    SessionState.BUFFERING,
                    listOf(PlaybackEvent.BufferingStarted(ts)),
                    PlaybackEvent.PlaybackEnded(ts + 10),
                    SessionState.ENDED,
                    "BUFFERING → ENDED: PlaybackEnded",
                ),
                Arguments.of(
                    SessionState.BUFFERING,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.BufferingStarted(ts + 10),
                    ),
                    PlaybackEvent.MediaItemTransition(ts + 20, newContentId = "video-2"),
                    SessionState.ENDED,
                    "BUFFERING → ENDED: MediaItemTransition (CONTENT_SWITCH)",
                ),

                // From SEEKING
                Arguments.of(
                    SessionState.SEEKING,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.SeekStarted(ts + 10),
                    ),
                    PlaybackEvent.SeekEnded(ts + 20, durationMs = 200),
                    SessionState.PLAYING,
                    "SEEKING → PLAYING: SeekEnded with isPlaying=true",
                ),
                Arguments.of(
                    SessionState.SEEKING,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(ts + 5, isPlaying = false),
                        PlaybackEvent.SeekStarted(ts + 10),
                    ),
                    PlaybackEvent.SeekEnded(ts + 20, durationMs = 200),
                    SessionState.PAUSED,
                    "SEEKING → PAUSED: SeekEnded with isPlaying=false",
                ),
                Arguments.of(
                    SessionState.SEEKING,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.SeekStarted(ts + 10),
                    ),
                    PlaybackEvent.AppBackgrounded(ts + 20),
                    SessionState.BACKGROUND,
                    "SEEKING → BACKGROUND: AppBackgrounded",
                ),
                Arguments.of(
                    SessionState.SEEKING,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.SeekStarted(ts + 10),
                    ),
                    PlaybackEvent.PlayerReleased(ts + 20),
                    SessionState.ENDED,
                    "SEEKING → ENDED: PlayerReleased",
                ),

                // From BACKGROUND
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.AppBackgrounded(ts + 10),
                    ),
                    PlaybackEvent.AppForegrounded(ts + 20),
                    SessionState.PLAYING,
                    "BACKGROUND → PLAYING: AppForegrounded with isPlaying=true",
                ),
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(ts + 5, isPlaying = false),
                        PlaybackEvent.AppBackgrounded(ts + 10),
                    ),
                    PlaybackEvent.AppForegrounded(ts + 20),
                    SessionState.PAUSED,
                    "BACKGROUND → PAUSED: AppForegrounded with isPlaying=false",
                ),
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(ts + 5, isPlaying = false),
                        PlaybackEvent.AppBackgrounded(ts + 10),
                    ),
                    PlaybackEvent.BackgroundIdleTimeout(ts + 20),
                    SessionState.ENDED,
                    "BACKGROUND → ENDED: BackgroundIdleTimeout (playbackActive=false)",
                ),
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.AppBackgrounded(ts + 10),
                    ),
                    PlaybackEvent.PlayerReleased(ts + 20),
                    SessionState.ENDED,
                    "BACKGROUND → ENDED: PlayerReleased",
                ),

                // BACKGROUND state memory tests (return to previous state)
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.BufferingStarted(ts + 10),
                        PlaybackEvent.AppBackgrounded(ts + 20),
                    ),
                    PlaybackEvent.AppForegrounded(ts + 30),
                    SessionState.BUFFERING,
                    "BACKGROUND → BUFFERING: AppForegrounded (restoring BUFFERING state)",
                ),
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(ts, isPlaying = true),
                        PlaybackEvent.SeekStarted(ts + 10),
                        PlaybackEvent.AppBackgrounded(ts + 20),
                    ),
                    PlaybackEvent.AppForegrounded(ts + 30),
                    SessionState.SEEKING,
                    "BACKGROUND → SEEKING: AppForegrounded (restoring SEEKING state)",
                ),

                // Additional edge case: ATTACHED → BACKGROUND → ATTACHED
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.AppBackgrounded(ts),
                    ),
                    PlaybackEvent.AppForegrounded(ts + 10),
                    SessionState.ATTACHED,
                    "BACKGROUND → ATTACHED: AppForegrounded from ATTACHED state",
                ),
            )
    }
}
