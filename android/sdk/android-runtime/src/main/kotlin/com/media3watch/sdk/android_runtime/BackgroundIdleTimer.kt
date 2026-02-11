package com.media3watch.sdk.android_runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages background idle timeout logic.
 * Starts a timer when app is backgrounded with inactive playback,
 * and emits BackgroundIdleTimeout event after BG_IDLE_END_TIMEOUT_MS.
 *
 * playbackActive = (isPlaying == true) OR (playWhenReady == true AND playbackState == BUFFERING)
 * Where BUFFERING is Media3's Player.STATE_BUFFERING (value 2)
 */
class BackgroundIdleTimer(
    private val scope: CoroutineScope,
    private val onTimeout: () -> Unit,
) {
    private var timerJob: Job? = null
    private var isInBackground = false

    /**
     * Notify that app went to background.
     * If playbackActive is false, starts the timer.
     */
    fun onAppBackgrounded(playbackActive: Boolean) {
        isInBackground = true
        if (!playbackActive) {
            startTimer()
        }
    }

    /**
     * Notify that app returned to foreground.
     * Cancels any running timer.
     */
    fun onAppForegrounded() {
        isInBackground = false
        cancelTimer()
    }

    /**
     * Notify that playback active state changed while in background.
     * If playback becomes active, cancel timer.
     * If playback becomes inactive, start/restart timer.
     */
    fun onPlaybackActiveChanged(playbackActive: Boolean) {
        if (!isInBackground) return

        if (playbackActive) {
            cancelTimer()
        } else {
            startTimer()
        }
    }

    /**
     * Explicitly cancel the timer (e.g., when session ends).
     */
    fun cancel() {
        cancelTimer()
    }

    private fun startTimer() {
        // Cancel any existing timer first (restart from 0)
        timerJob?.cancel()
        
        timerJob = scope.launch {
            delay(BG_IDLE_END_TIMEOUT_MS)
            onTimeout()
        }
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Check if timer is currently running.
     */
    fun isRunning(): Boolean = timerJob?.isActive == true
}

/**
 * Compute playbackActive state per spec:
 * playbackActive = (isPlaying == true) OR (playWhenReady == true AND playbackState == BUFFERING)
 */
fun computePlaybackActive(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int, // Media3 Player.STATE_* constants
): Boolean {
    // Media3 Player.STATE_BUFFERING = 2
    val STATE_BUFFERING = 2
    return isPlaying || (playWhenReady && playbackState == STATE_BUFFERING)
}
