package com.media3watch.overlay

import android.app.Activity
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.media3watch.sdk.Media3WatchAnalytics
import com.media3watch.sdk.MetricsObserver
import com.media3watch.sdk.SessionSnapshot

enum class OverlayPosition {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}

enum class OverlayState {
    COLLAPSED,
    EXPANDED,
}

data class OverlayConfig(
    val initialPosition: OverlayPosition = OverlayPosition.TOP_END,
    val initialState: OverlayState = OverlayState.COLLAPSED,
)

class Media3WatchOverlay(
    private val config: OverlayConfig = OverlayConfig()
) {
    private var analytics: Media3WatchAnalytics? = null
    private var host: ViewGroup? = null
    private var overlayView: OverlayView? = null

    private val observer = object : MetricsObserver {
        override fun onSnapshotUpdated(snapshot: SessionSnapshot) {
            overlayView?.onSnapshotUpdated(snapshot)
        }

        override fun onSessionStarted(sessionId: String) {
            overlayView?.onSessionStarted(sessionId)
        }

        override fun onSessionEnded(sessionId: String, finalSnapshot: SessionSnapshot) {
            overlayView?.onSessionEnded(sessionId, finalSnapshot)
        }
    }

    fun attach(analytics: Media3WatchAnalytics, host: ViewGroup) {
        detach()

        val createdView = OverlayView(context = host.context, config = config)
        createdView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        ViewCompat.setOnApplyWindowInsetsListener(createdView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            (view as OverlayView).updateInsets(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        host.addView(createdView)
        ViewCompat.requestApplyInsets(createdView)

        this.host = host
        this.overlayView = createdView
        this.analytics = analytics

        analytics.addMetricsObserver(observer)
    }

    fun attach(analytics: Media3WatchAnalytics, activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        attach(analytics, content)
    }

    fun detach() {
        analytics?.removeMetricsObserver(observer)

        overlayView?.let { view ->
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
            host?.removeView(view)
        }

        analytics = null
        host = null
        overlayView = null
    }

    fun show() {
        overlayView?.isVisible = true
    }

    fun hide() {
        overlayView?.isVisible = false
    }
}
