package com.media3watch.sample.overlay

import android.app.Activity
import com.media3watch.overlay.Media3WatchOverlay
import com.media3watch.sdk.Media3WatchAnalytics

object OverlayInstaller {
    fun install(analytics: Media3WatchAnalytics, activity: Activity?): () -> Unit {
        val hostActivity = activity ?: return {}
        val overlay = Media3WatchOverlay()
        overlay.attach(analytics, hostActivity)
        overlay.show()
        return { overlay.detach() }
    }
}
