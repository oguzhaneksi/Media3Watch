package com.media3watch.sdk.android_runtime

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.media3watch.sdk.schema.PlaybackEvent

/**
 * Observes app lifecycle using ProcessLifecycleOwner.
 * Emits AppBackgrounded and AppForegrounded events.
 */
class AppLifecycleObserver(
    private val onEvent: (PlaybackEvent) -> Unit,
) : DefaultLifecycleObserver {

    private var isInitialized = false

    /**
     * Initialize lifecycle observation.
     * Must be called on main thread.
     */
    fun init(application: Application? = null) {
        if (isInitialized) return
        
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            isInitialized = true
        } catch (e: Exception) {
            // If ProcessLifecycleOwner is not available, fail gracefully
            // This can happen in test environments
            throw IllegalStateException("ProcessLifecycleOwner not available", e)
        }
    }

    /**
     * Remove lifecycle observation.
     */
    fun dispose() {
        if (!isInitialized) return
        
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        } catch (e: Exception) {
            // Already removed or not available
        }
        isInitialized = false
    }

    /**
     * Called when app goes to background (all activities stopped).
     */
    override fun onStop(owner: LifecycleOwner) {
        onEvent(PlaybackEvent.AppBackgrounded(System.currentTimeMillis()))
    }

    /**
     * Called when app returns to foreground (any activity started).
     */
    override fun onStart(owner: LifecycleOwner) {
        onEvent(PlaybackEvent.AppForegrounded(System.currentTimeMillis()))
    }
}
