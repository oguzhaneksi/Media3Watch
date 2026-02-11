package com.media3watch.sdk.core

import com.media3watch.sdk.schema.EndReason
import com.media3watch.sdk.schema.PlaybackEvent
import com.media3watch.sdk.schema.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Table-driven tests for SessionStateMachine state transitions.
 * These tests verify every edge in the Mermaid diagram from session-lifecycle.md §10.
 */
class SessionStateMachineTransitionTableTest {
    data class TransitionTest(
        val name: String,
        val initialState: SessionState,
        val setupEvents: List<PlaybackEvent>,
        val triggerEvent: PlaybackEvent,
        val expectedState: SessionState,
        val expectedEndReason: EndReason? = null,
    )

    companion object {
        @JvmStatic
        fun transitionTestCases(): Stream<Arguments> =
            Stream.of(
                // ATTACHED → active states
                Arguments.of(
                    TransitionTest(
                        name = "ATTACHED → PLAYING via PlayRequested + IsPlayingChanged(true)",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.PlayRequested(timestamp = 1000L),
                            ),
                        triggerEvent = PlaybackEvent.IsPlayingChanged(timestamp = 1100L, isPlaying = true),
                        expectedState = SessionState.PLAYING,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "ATTACHED → BUFFERING via BufferingStarted",
                        initialState = SessionState.ATTACHED,
                        setupEvents = emptyList(),
                        triggerEvent = PlaybackEvent.BufferingStarted(timestamp = 1000L),
                        expectedState = SessionState.BUFFERING,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "ATTACHED → PLAYING via FirstFrameRendered",
                        initialState = SessionState.ATTACHED,
                        setupEvents = emptyList(),
                        triggerEvent = PlaybackEvent.FirstFrameRendered(timestamp = 1000L),
                        expectedState = SessionState.PLAYING,
                    ),
                ),
                // PLAYING → PAUSED → PLAYING
                Arguments.of(
                    TransitionTest(
                        name = "PLAYING → PAUSED via IsPlayingChanged(false)",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                            ),
                        triggerEvent = PlaybackEvent.IsPlayingChanged(timestamp = 1100L, isPlaying = false),
                        expectedState = SessionState.PAUSED,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "PAUSED → PLAYING via IsPlayingChanged(true)",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                                PlaybackEvent.IsPlayingChanged(timestamp = 1100L, isPlaying = false),
                            ),
                        triggerEvent = PlaybackEvent.IsPlayingChanged(timestamp = 1200L, isPlaying = true),
                        expectedState = SessionState.PLAYING,
                    ),
                ),
                // PLAYING → BUFFERING → PLAYING
                Arguments.of(
                    TransitionTest(
                        name = "PLAYING → BUFFERING via BufferingStarted",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                            ),
                        triggerEvent = PlaybackEvent.BufferingStarted(timestamp = 1100L),
                        expectedState = SessionState.BUFFERING,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "BUFFERING → PLAYING via BufferingEnded (isPlaying=true)",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                                PlaybackEvent.BufferingStarted(timestamp = 1100L),
                            ),
                        triggerEvent = PlaybackEvent.BufferingEnded(timestamp = 1200L, durationMs = 100L),
                        expectedState = SessionState.PLAYING,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "BUFFERING → PAUSED via BufferingEnded (isPlaying=false)",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.BufferingStarted(timestamp = 1000L),
                                PlaybackEvent.IsPlayingChanged(timestamp = 1050L, isPlaying = false),
                            ),
                        triggerEvent = PlaybackEvent.BufferingEnded(timestamp = 1200L, durationMs = 200L),
                        expectedState = SessionState.PAUSED,
                    ),
                ),
                // PLAYING → SEEKING → PLAYING
                Arguments.of(
                    TransitionTest(
                        name = "PLAYING → SEEKING via SeekStarted",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                            ),
                        triggerEvent = PlaybackEvent.SeekStarted(timestamp = 1100L),
                        expectedState = SessionState.SEEKING,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "SEEKING → PLAYING via SeekEnded (isPlaying=true)",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                                PlaybackEvent.SeekStarted(timestamp = 1100L),
                            ),
                        triggerEvent = PlaybackEvent.SeekEnded(timestamp = 1200L, durationMs = 100L),
                        expectedState = SessionState.PLAYING,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "SEEKING → PAUSED via SeekEnded (isPlaying=false)",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                                PlaybackEvent.SeekStarted(timestamp = 1100L),
                                PlaybackEvent.IsPlayingChanged(timestamp = 1150L, isPlaying = false),
                            ),
                        triggerEvent = PlaybackEvent.SeekEnded(timestamp = 1200L, durationMs = 100L),
                        expectedState = SessionState.PAUSED,
                    ),
                ),
                // Background transitions
                Arguments.of(
                    TransitionTest(
                        name = "ATTACHED → BACKGROUND via AppBackgrounded",
                        initialState = SessionState.ATTACHED,
                        setupEvents = emptyList(),
                        triggerEvent = PlaybackEvent.AppBackgrounded(timestamp = 1000L),
                        expectedState = SessionState.BACKGROUND,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "PLAYING → BACKGROUND via AppBackgrounded",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                            ),
                        triggerEvent = PlaybackEvent.AppBackgrounded(timestamp = 1100L),
                        expectedState = SessionState.BACKGROUND,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "PAUSED → BACKGROUND via AppBackgrounded",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                                PlaybackEvent.IsPlayingChanged(timestamp = 1050L, isPlaying = false),
                            ),
                        triggerEvent = PlaybackEvent.AppBackgrounded(timestamp = 1100L),
                        expectedState = SessionState.BACKGROUND,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "BUFFERING → BACKGROUND via AppBackgrounded",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.BufferingStarted(timestamp = 1000L),
                            ),
                        triggerEvent = PlaybackEvent.AppBackgrounded(timestamp = 1100L),
                        expectedState = SessionState.BACKGROUND,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "SEEKING → BACKGROUND via AppBackgrounded",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                                PlaybackEvent.SeekStarted(timestamp = 1050L),
                            ),
                        triggerEvent = PlaybackEvent.AppBackgrounded(timestamp = 1100L),
                        expectedState = SessionState.BACKGROUND,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "BACKGROUND → PLAYING via AppForegrounded (was playing)",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                                PlaybackEvent.AppBackgrounded(timestamp = 1100L),
                            ),
                        triggerEvent = PlaybackEvent.AppForegrounded(timestamp = 1200L),
                        expectedState = SessionState.PLAYING,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "BACKGROUND → PAUSED via AppForegrounded (was paused)",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                                PlaybackEvent.IsPlayingChanged(timestamp = 1050L, isPlaying = false),
                                PlaybackEvent.AppBackgrounded(timestamp = 1100L),
                            ),
                        triggerEvent = PlaybackEvent.AppForegrounded(timestamp = 1200L),
                        expectedState = SessionState.PAUSED,
                    ),
                ),
                // End triggers → ENDED
                Arguments.of(
                    TransitionTest(
                        name = "PLAYING → ENDED via PlayerReleased",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                            ),
                        triggerEvent = PlaybackEvent.PlayerReleased(timestamp = 1100L),
                        expectedState = SessionState.ENDED,
                        expectedEndReason = EndReason.PLAYER_RELEASED,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "PLAYING → ENDED via PlaybackEnded",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                            ),
                        triggerEvent = PlaybackEvent.PlaybackEnded(timestamp = 1100L),
                        expectedState = SessionState.ENDED,
                        expectedEndReason = EndReason.PLAYBACK_ENDED,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "PLAYING → ENDED via MediaItemTransition (with meaningful activity)",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.PlayRequested(timestamp = 1000L),
                                PlaybackEvent.IsPlayingChanged(timestamp = 1100L, isPlaying = true),
                            ),
                        triggerEvent = PlaybackEvent.MediaItemTransition(timestamp = 1200L, newContentId = "new"),
                        expectedState = SessionState.ENDED,
                        expectedEndReason = EndReason.CONTENT_SWITCH,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "BACKGROUND → ENDED via BackgroundIdleTimeout (not playbackActive)",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                                PlaybackEvent.AppBackgrounded(timestamp = 1100L),
                                PlaybackEvent.IsPlayingChanged(timestamp = 1150L, isPlaying = false),
                            ),
                        triggerEvent = PlaybackEvent.BackgroundIdleTimeout(timestamp = 121100L),
                        expectedState = SessionState.ENDED,
                        expectedEndReason = EndReason.BACKGROUND_IDLE_TIMEOUT,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "ATTACHED → ENDED via PlayerReleased",
                        initialState = SessionState.ATTACHED,
                        setupEvents = emptyList(),
                        triggerEvent = PlaybackEvent.PlayerReleased(timestamp = 1000L),
                        expectedState = SessionState.ENDED,
                        expectedEndReason = EndReason.PLAYER_RELEASED,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "PAUSED → ENDED via PlayerReleased",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                                PlaybackEvent.IsPlayingChanged(timestamp = 1050L, isPlaying = false),
                            ),
                        triggerEvent = PlaybackEvent.PlayerReleased(timestamp = 1100L),
                        expectedState = SessionState.ENDED,
                        expectedEndReason = EndReason.PLAYER_RELEASED,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "BUFFERING → ENDED via PlayerReleased",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.BufferingStarted(timestamp = 1000L),
                            ),
                        triggerEvent = PlaybackEvent.PlayerReleased(timestamp = 1100L),
                        expectedState = SessionState.ENDED,
                        expectedEndReason = EndReason.PLAYER_RELEASED,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "SEEKING → ENDED via PlayerReleased",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                                PlaybackEvent.SeekStarted(timestamp = 1050L),
                            ),
                        triggerEvent = PlaybackEvent.PlayerReleased(timestamp = 1100L),
                        expectedState = SessionState.ENDED,
                        expectedEndReason = EndReason.PLAYER_RELEASED,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "BACKGROUND → ENDED via PlayerReleased",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                                PlaybackEvent.AppBackgrounded(timestamp = 1100L),
                            ),
                        triggerEvent = PlaybackEvent.PlayerReleased(timestamp = 1200L),
                        expectedState = SessionState.ENDED,
                        expectedEndReason = EndReason.PLAYER_RELEASED,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "PLAYING → ENDED via PlayerReplaced",
                        initialState = SessionState.ATTACHED,
                        setupEvents =
                            listOf(
                                PlaybackEvent.IsPlayingChanged(timestamp = 1000L, isPlaying = true),
                            ),
                        triggerEvent = PlaybackEvent.PlayerReplaced(timestamp = 1100L),
                        expectedState = SessionState.ENDED,
                        expectedEndReason = EndReason.PLAYER_REPLACED,
                    ),
                ),
                Arguments.of(
                    TransitionTest(
                        name = "ATTACHED → ENDED via PlayerReplaced",
                        initialState = SessionState.ATTACHED,
                        setupEvents = emptyList(),
                        triggerEvent = PlaybackEvent.PlayerReplaced(timestamp = 1000L),
                        expectedState = SessionState.ENDED,
                        expectedEndReason = EndReason.PLAYER_REPLACED,
                    ),
                ),
            )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transitionTestCases")
    fun `verify state transition`(test: TransitionTest) {
        val sm = SessionStateMachine("test-session")
        assertEquals(test.initialState, sm.currentState, "Initial state mismatch")

        // Apply setup events
        test.setupEvents.forEach { event ->
            sm.processEvent(event)
        }

        // Apply trigger event
        sm.processEvent(test.triggerEvent)

        // Verify expected state
        assertEquals(test.expectedState, sm.currentState, "Final state mismatch for: ${test.name}")

        // Verify end reason if expected
        if (test.expectedEndReason != null) {
            assertEquals(test.expectedEndReason, sm.endReason, "End reason mismatch for: ${test.name}")
        } else if (test.expectedState != SessionState.ENDED) {
            assertNull(sm.endReason, "End reason should be null for: ${test.name}")
        }
    }
}
