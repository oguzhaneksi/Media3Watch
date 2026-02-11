package com.media3watch.sdk.android_runtime

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.media3watch.sdk.schema.PlaybackEvent

/**
 * Observes app lifecycle events (foreground/background) using ProcessLifecycleOwner.
 *
 * When the app transitions between foreground and background, emits:
 * - [PlaybackEvent.AppBackgrounded] when app goes to background
 * - [PlaybackEvent.AppForegrounded] when app returns to foreground
 */
class AppLifecycleObserver(
    private val onEvent: (PlaybackEvent) -> Unit,
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        onEvent(PlaybackEvent.AppForegrounded(System.currentTimeMillis()))
    }

    override fun onStop(owner: LifecycleOwner) {
        onEvent(PlaybackEvent.AppBackgrounded(System.currentTimeMillis()))
    }
}
