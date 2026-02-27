package com.media3watch.sample.overlay

import android.app.Activity
import com.media3watch.sdk.Media3WatchAnalytics

object OverlayInstaller {
    fun install(analytics: Media3WatchAnalytics, activity: Activity?): () -> Unit {
        // no-op in release builds
        return {}
    }
}
