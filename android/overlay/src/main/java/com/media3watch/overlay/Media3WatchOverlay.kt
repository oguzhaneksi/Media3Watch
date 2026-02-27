package com.media3watch.overlay

import android.app.Activity
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.media3watch.sdk.Media3WatchAnalytics
import com.media3watch.sdk.MetricsObserver
import com.media3watch.sdk.SessionSnapshot

/**
 * Defines the corner of the screen where the debug overlay widget is anchored.
 *
 * Used in [OverlayConfig.initialPosition] to control where the overlay initially appears.
 */
enum class OverlayPosition {
    /** Top-left corner of the screen. */
    TOP_START,
    /** Top-right corner of the screen. */
    TOP_END,
    /** Bottom-left corner of the screen. */
    BOTTOM_START,
    /** Bottom-right corner of the screen. */
    BOTTOM_END,
}

/**
 * Controls whether the debug overlay widget starts in its compact or full-detail view.
 *
 * Used in [OverlayConfig.initialState] to set the overlay's default presentation.
 */
enum class OverlayState {
    /** The overlay is shown as a compact widget, hiding the full metrics details. */
    COLLAPSED,
    /** The overlay is shown with all metrics details visible. */
    EXPANDED,
}

/**
 * Configuration options for a [Media3WatchOverlay] instance.
 *
 * @property initialPosition Corner of the screen where the overlay is initially placed.
 *   Defaults to [OverlayPosition.TOP_END].
 * @property initialState Whether the overlay starts collapsed or expanded.
 *   Defaults to [OverlayState.COLLAPSED].
 */
data class OverlayConfig(
    val initialPosition: OverlayPosition = OverlayPosition.TOP_END,
    val initialState: OverlayState = OverlayState.COLLAPSED,
)

/**
 * A debug overlay that displays live [Media3WatchAnalytics] metrics on top of your UI.
 *
 * `Media3WatchOverlay` observes a [Media3WatchAnalytics] instance and renders session
 * snapshots (buffering ratio, stall count, bitrate, etc.) in a draggable, toggleable
 * widget that floats above all other views.
 *
 * **This class is intended for debug builds only.** Add the overlay module via
 * `debugImplementation` in your app's Gradle file to ensure it is not included in
 * release builds:
 * ```kotlin
 * debugImplementation("com.media3watch:overlay:<version>")
 * ```
 *
 * Typical usage:
 * ```kotlin
 * class PlayerActivity : AppCompatActivity() {
 *     private val overlay = Media3WatchOverlay()
 *
 *     override fun onStart() {
 *         super.onStart()
 *         overlay.attach(analytics, this)
 *     }
 *
 *     override fun onStop() {
 *         overlay.detach()
 *         super.onStop()
 *     }
 * }
 * ```
 *
 * @param config Visual configuration for the overlay widget. Defaults to [OverlayConfig]
 *   with [OverlayPosition.TOP_END] and [OverlayState.COLLAPSED].
 */
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

    /**
     * Attaches the overlay to a specific [ViewGroup], making it a child of that container.
     *
     * The overlay view is added on top of all existing children of [host] and will observe
     * [analytics] for metric updates. Call [detach] when the overlay is no longer needed to
     * clean up the view and stop observing metrics.
     *
     * @param analytics The [Media3WatchAnalytics] instance whose metrics will be displayed.
     * @param host The [ViewGroup] to which the overlay view will be added.
     */
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

    /**
     * Convenience overload that attaches the overlay to the root content view of an [Activity].
     *
     * Equivalent to calling `attach(analytics, activity.findViewById(android.R.id.content))`.
     * This is the simplest integration point for most activities.
     *
     * @param analytics The [Media3WatchAnalytics] instance whose metrics will be displayed.
     * @param activity The [Activity] whose root content view will host the overlay.
     */
    fun attach(analytics: Media3WatchAnalytics, activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        attach(analytics, content)
    }

    /**
     * Detaches the overlay from the host view and stops observing analytics metrics.
     *
     * The overlay view is removed from its parent and all references are cleared. It is safe
     * to call this method even if [attach] has not been called. After calling [detach], the
     * overlay can be re-attached by calling [attach] again.
     */
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

    /** Makes the overlay widget visible if it has been hidden with [hide]. */
    fun show() {
        overlayView?.isVisible = true
    }

    /** Hides the overlay widget without detaching it. Call [show] to make it visible again. */
    fun hide() {
        overlayView?.isVisible = false
    }
}
