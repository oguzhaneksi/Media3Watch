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
        private const val TS = 1706900000000L

        @JvmStatic
        fun stateTransitionTable(): Stream<Arguments> =
            Stream.of(
                // NO_SESSION is not tested here as it's the initial creation state (before attach)
                // From ATTACHED
                Arguments.of(
                    SessionState.ATTACHED,
                    emptyList<PlaybackEvent>(),
                    PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                    SessionState.PLAYING,
                    "ATTACHED → PLAYING: IsPlayingChanged(true) with PlayRequested implied",
                ),
                Arguments.of(
                    SessionState.ATTACHED,
                    listOf(PlaybackEvent.PlayRequested(TS)),
                    PlaybackEvent.IsPlayingChanged(TS + 10, isPlaying = true),
                    SessionState.PLAYING,
                    "ATTACHED → PLAYING: PlayRequested + IsPlayingChanged(true)",
                ),
                Arguments.of(
                    SessionState.ATTACHED,
                    emptyList<PlaybackEvent>(),
                    PlaybackEvent.BufferingStarted(TS),
                    SessionState.BUFFERING,
                    "ATTACHED → BUFFERING: BufferingStarted",
                ),
                Arguments.of(
                    SessionState.ATTACHED,
                    emptyList<PlaybackEvent>(),
                    PlaybackEvent.FirstFrameRendered(TS),
                    SessionState.PLAYING,
                    "ATTACHED → PLAYING: FirstFrameRendered (implies started)",
                ),
                Arguments.of(
                    SessionState.ATTACHED,
                    emptyList<PlaybackEvent>(),
                    PlaybackEvent.AppBackgrounded(TS),
                    SessionState.BACKGROUND,
                    "ATTACHED → BACKGROUND: AppBackgrounded",
                ),
                Arguments.of(
                    SessionState.ATTACHED,
                    emptyList<PlaybackEvent>(),
                    PlaybackEvent.PlayerReleased(TS),
                    SessionState.ENDED,
                    "ATTACHED → ENDED: PlayerReleased",
                ),
                // From PLAYING
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(TS, isPlaying = true)),
                    PlaybackEvent.IsPlayingChanged(TS + 10, isPlaying = false),
                    SessionState.PAUSED,
                    "PLAYING → PAUSED: IsPlayingChanged(false)",
                ),
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(TS, isPlaying = true)),
                    PlaybackEvent.BufferingStarted(TS + 10),
                    SessionState.BUFFERING,
                    "PLAYING → BUFFERING: BufferingStarted",
                ),
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(TS, isPlaying = true)),
                    PlaybackEvent.SeekStarted(TS + 10),
                    SessionState.SEEKING,
                    "PLAYING → SEEKING: SeekStarted",
                ),
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(TS, isPlaying = true)),
                    PlaybackEvent.AppBackgrounded(TS + 10),
                    SessionState.BACKGROUND,
                    "PLAYING → BACKGROUND: AppBackgrounded",
                ),
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(TS, isPlaying = true)),
                    PlaybackEvent.PlayerReleased(TS + 10),
                    SessionState.ENDED,
                    "PLAYING → ENDED: PlayerReleased",
                ),
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(TS, isPlaying = true)),
                    PlaybackEvent.PlaybackEnded(TS + 10),
                    SessionState.ENDED,
                    "PLAYING → ENDED: PlaybackEnded",
                ),
                Arguments.of(
                    SessionState.PLAYING,
                    listOf(PlaybackEvent.IsPlayingChanged(TS, isPlaying = true)),
                    PlaybackEvent.MediaItemTransition(TS + 10, newContentId = "video-2"),
                    SessionState.ENDED,
                    "PLAYING → ENDED: MediaItemTransition (CONTENT_SWITCH)",
                ),
                // From PAUSED
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(TS + 10, isPlaying = false),
                    ),
                    PlaybackEvent.IsPlayingChanged(TS + 20, isPlaying = true),
                    SessionState.PLAYING,
                    "PAUSED → PLAYING: IsPlayingChanged(true)",
                ),
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(TS + 10, isPlaying = false),
                    ),
                    PlaybackEvent.BufferingStarted(TS + 20),
                    SessionState.BUFFERING,
                    "PAUSED → BUFFERING: BufferingStarted",
                ),
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(TS + 10, isPlaying = false),
                    ),
                    PlaybackEvent.SeekStarted(TS + 20),
                    SessionState.SEEKING,
                    "PAUSED → SEEKING: SeekStarted",
                ),
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(TS + 10, isPlaying = false),
                    ),
                    PlaybackEvent.AppBackgrounded(TS + 20),
                    SessionState.BACKGROUND,
                    "PAUSED → BACKGROUND: AppBackgrounded",
                ),
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(TS + 10, isPlaying = false),
                    ),
                    PlaybackEvent.PlayerReleased(TS + 20),
                    SessionState.ENDED,
                    "PAUSED → ENDED: PlayerReleased",
                ),
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(TS + 10, isPlaying = false),
                    ),
                    PlaybackEvent.PlaybackEnded(TS + 20),
                    SessionState.ENDED,
                    "PAUSED → ENDED: PlaybackEnded",
                ),
                Arguments.of(
                    SessionState.PAUSED,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(TS + 10, isPlaying = false),
                    ),
                    PlaybackEvent.MediaItemTransition(TS + 20, newContentId = "video-2"),
                    SessionState.ENDED,
                    "PAUSED → ENDED: MediaItemTransition (CONTENT_SWITCH)",
                ),
                // From BUFFERING
                Arguments.of(
                    SessionState.BUFFERING,
                    listOf(
                        PlaybackEvent.BufferingStarted(TS),
                        PlaybackEvent.IsPlayingChanged(TS + 10, isPlaying = true),
                    ),
                    PlaybackEvent.BufferingEnded(TS + 20, durationMs = 100),
                    SessionState.PLAYING,
                    "BUFFERING → PLAYING: BufferingEnded with isPlaying=true",
                ),
                Arguments.of(
                    SessionState.BUFFERING,
                    listOf(
                        PlaybackEvent.BufferingStarted(TS),
                        PlaybackEvent.IsPlayingChanged(TS + 10, isPlaying = false),
                    ),
                    PlaybackEvent.BufferingEnded(TS + 20, durationMs = 100),
                    SessionState.PAUSED,
                    "BUFFERING → PAUSED: BufferingEnded with isPlaying=false",
                ),
                Arguments.of(
                    SessionState.BUFFERING,
                    listOf(PlaybackEvent.BufferingStarted(TS)),
                    PlaybackEvent.AppBackgrounded(TS + 10),
                    SessionState.BACKGROUND,
                    "BUFFERING → BACKGROUND: AppBackgrounded",
                ),
                Arguments.of(
                    SessionState.BUFFERING,
                    listOf(PlaybackEvent.BufferingStarted(TS)),
                    PlaybackEvent.PlayerReleased(TS + 10),
                    SessionState.ENDED,
                    "BUFFERING → ENDED: PlayerReleased",
                ),
                Arguments.of(
                    SessionState.BUFFERING,
                    listOf(PlaybackEvent.BufferingStarted(TS)),
                    PlaybackEvent.PlaybackEnded(TS + 10),
                    SessionState.ENDED,
                    "BUFFERING → ENDED: PlaybackEnded",
                ),
                Arguments.of(
                    SessionState.BUFFERING,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.BufferingStarted(TS + 10),
                    ),
                    PlaybackEvent.MediaItemTransition(TS + 20, newContentId = "video-2"),
                    SessionState.ENDED,
                    "BUFFERING → ENDED: MediaItemTransition (CONTENT_SWITCH)",
                ),
                // From SEEKING
                Arguments.of(
                    SessionState.SEEKING,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.SeekStarted(TS + 10),
                    ),
                    PlaybackEvent.SeekEnded(TS + 20, durationMs = 200),
                    SessionState.PLAYING,
                    "SEEKING → PLAYING: SeekEnded with isPlaying=true",
                ),
                Arguments.of(
                    SessionState.SEEKING,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(TS + 5, isPlaying = false),
                        PlaybackEvent.SeekStarted(TS + 10),
                    ),
                    PlaybackEvent.SeekEnded(TS + 20, durationMs = 200),
                    SessionState.PAUSED,
                    "SEEKING → PAUSED: SeekEnded with isPlaying=false",
                ),
                Arguments.of(
                    SessionState.SEEKING,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.SeekStarted(TS + 10),
                    ),
                    PlaybackEvent.AppBackgrounded(TS + 20),
                    SessionState.BACKGROUND,
                    "SEEKING → BACKGROUND: AppBackgrounded",
                ),
                Arguments.of(
                    SessionState.SEEKING,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.SeekStarted(TS + 10),
                    ),
                    PlaybackEvent.PlayerReleased(TS + 20),
                    SessionState.ENDED,
                    "SEEKING → ENDED: PlayerReleased",
                ),
                // From BACKGROUND
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.AppBackgrounded(TS + 10),
                    ),
                    PlaybackEvent.AppForegrounded(TS + 20),
                    SessionState.PLAYING,
                    "BACKGROUND → PLAYING: AppForegrounded with isPlaying=true",
                ),
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(TS + 5, isPlaying = false),
                        PlaybackEvent.AppBackgrounded(TS + 10),
                    ),
                    PlaybackEvent.AppForegrounded(TS + 20),
                    SessionState.PAUSED,
                    "BACKGROUND → PAUSED: AppForegrounded with isPlaying=false",
                ),
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.IsPlayingChanged(TS + 5, isPlaying = false),
                        PlaybackEvent.AppBackgrounded(TS + 10),
                    ),
                    PlaybackEvent.BackgroundIdleTimeout(TS + 20),
                    SessionState.ENDED,
                    "BACKGROUND → ENDED: BackgroundIdleTimeout (playbackActive=false)",
                ),
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.AppBackgrounded(TS + 10),
                    ),
                    PlaybackEvent.PlayerReleased(TS + 20),
                    SessionState.ENDED,
                    "BACKGROUND → ENDED: PlayerReleased",
                ),
                // BACKGROUND state memory tests (per spec: only PLAYING/PAUSED on foreground)
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.BufferingStarted(TS + 10),
                        PlaybackEvent.AppBackgrounded(TS + 20),
                        // isPlaying is still true, so should go to PLAYING per spec
                    ),
                    PlaybackEvent.AppForegrounded(TS + 30),
                    SessionState.PLAYING,
                    "BACKGROUND → PLAYING: AppForegrounded with isPlaying=true (was BUFFERING)",
                ),
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.IsPlayingChanged(TS, isPlaying = true),
                        PlaybackEvent.SeekStarted(TS + 10),
                        PlaybackEvent.AppBackgrounded(TS + 20),
                        // isPlaying is still true, so should go to PLAYING per spec
                    ),
                    PlaybackEvent.AppForegrounded(TS + 30),
                    SessionState.PLAYING,
                    "BACKGROUND → PLAYING: AppForegrounded with isPlaying=true (was SEEKING)",
                ),
                // Additional edge case: ATTACHED → BACKGROUND → PAUSED (isPlaying=false by default)
                Arguments.of(
                    SessionState.BACKGROUND,
                    listOf(
                        PlaybackEvent.AppBackgrounded(TS),
                    ),
                    PlaybackEvent.AppForegrounded(TS + 10),
                    SessionState.PAUSED,
                    "BACKGROUND → PAUSED: AppForegrounded with isPlaying=false (was ATTACHED)",
                ),
            )
    }
}
