package com.media3watch.sdk.android_runtime

import com.media3watch.sdk.schema.PlaybackEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Background idle timer for ending sessions after prolonged inactivity in the background.
 *
 * Per session-lifecycle.md §4 and §6:
 * - Background alone does NOT end a session (supports PiP, audio playback)
 * - Background + 2 minutes of idle (not playing) ends the session with BACKGROUND_IDLE_TIMEOUT
 * - playbackActive = isPlaying OR (playWhenReady AND playbackState == BUFFERING)
 *
 * Timer behavior:
 * - Starts on AppBackgrounded if playbackActive == false
 * - Cancels on AppForegrounded
 * - Cancels if playback becomes active while backgrounded
 * - Restarts if playback becomes inactive while still backgrounded
 * - Fires BackgroundIdleTimeout event after 2 minutes
 */
class BackgroundIdleTimer(
    private val scope: CoroutineScope,
    private val onTimeout: (PlaybackEvent.BackgroundIdleTimeout) -> Unit,
) {
    companion object {
        /**
         * Fixed background idle timeout: 2 minutes (not configurable in MVP).
         */
        const val BG_IDLE_END_TIMEOUT_MS = 120_000L
    }

    private var timerJob: Job? = null
    private var isInBackground = false

    /**
     * Starts the timer if app is in background and playback is not active.
     */
    fun start() {
        if (!isInBackground) return
        cancel()
        timerJob =
            scope.launch {
                delay(BG_IDLE_END_TIMEOUT_MS)
                onTimeout(PlaybackEvent.BackgroundIdleTimeout(System.currentTimeMillis()))
            }
    }

    /**
     * Cancels any running timer.
     */
    fun cancel() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Handles app backgrounded event.
     * If playbackActive is false, starts the timer.
     */
    fun onAppBackgrounded(playbackActive: Boolean) {
        isInBackground = true
        if (!playbackActive) {
            start()
        } else {
            cancel()
        }
    }

    /**
     * Handles app foregrounded event.
     * Cancels any running timer.
     */
    fun onAppForegrounded() {
        isInBackground = false
        cancel()
    }

    /**
     * Handles playback active state change while in background.
     * If playback becomes active, cancels timer.
     * If playback becomes inactive while still backgrounded, starts timer.
     */
    fun onPlaybackActiveChanged(playbackActive: Boolean) {
        if (!isInBackground) return

        if (playbackActive) {
            cancel()
        } else {
            start()
        }
    }
}
