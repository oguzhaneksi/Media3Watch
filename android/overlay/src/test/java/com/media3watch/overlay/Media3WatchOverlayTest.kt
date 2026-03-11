package com.media3watch.overlay

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.media3watch.sdk.Media3WatchAnalytics
import com.media3watch.sdk.model.SessionPlaybackState
import com.media3watch.sdk.model.SessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Media3WatchOverlayTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun overlayView_toggle_collapsedExpanded() {
        val view = OverlayView(
            context = context,
            config = OverlayConfig(initialState = OverlayState.COLLAPSED)
        )

        assertEquals(OverlayState.COLLAPSED, view.overlayStateForTest())
        view.performHandleClickForTest()
        assertEquals(OverlayState.EXPANDED, view.overlayStateForTest())
        view.performHandleClickForTest()
        assertEquals(OverlayState.COLLAPSED, view.overlayStateForTest())
    }

    @Test
    fun overlayView_updatesPillTextAndHealthColor_fromSnapshot() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity)
        activity.setContentView(host)

        val view = OverlayView(context = activity, config = OverlayConfig())
        host.addView(view)
        view.onSnapshotUpdated(
            snapshot(
                playbackState = SessionPlaybackState.PLAYING,
                startupTimeMs = 480L,
                rebufferCount = 2,
                rebufferRatio = 0.01f,
                errorCount = 0
            )
        )
        shadowOf(Looper.getMainLooper()).idle()

        val pill = view.pillTextForTest()
        assertTrue(pill.contains("PLAYING"))
        assertTrue(pill.contains("Start 480ms"))
        assertTrue(pill.contains("Reb 2"))
        assertTrue(pill.contains("Err 0"))
        assertEquals(0xFF2E7D32.toInt(), view.pillColorForTest())

        view.onSnapshotUpdated(snapshot(rebufferRatio = 0.03f, errorCount = 0))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0xFFF9A825.toInt(), view.pillColorForTest())

        view.onSnapshotUpdated(snapshot(rebufferRatio = 0.0f, errorCount = 1))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0xFFC62828.toInt(), view.pillColorForTest())
    }

    @Test
    fun overlay_attachAndDetach_addsAndRemovesOverlayView() {
        val analytics = Media3WatchAnalytics(context)
        val overlay = Media3WatchOverlay()
        val host = FrameLayout(context)

        overlay.attach(analytics, host)
        assertEquals(1, host.childCount)

        overlay.detach()
        assertEquals(0, host.childCount)
    }

    @Test
    fun overlayView_touchThrough_rootDoesNotConsumeEvents() {
        val view = OverlayView(context = context, config = OverlayConfig())
        val event = MotionEvent.obtain(
            SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_DOWN,
            10f,
            10f,
            0
        )

        assertFalse(view.onInterceptTouchEvent(event))
        assertFalse(view.onTouchEvent(event))
        event.recycle()
    }

    private fun snapshot(
        playbackState: SessionPlaybackState = SessionPlaybackState.PAUSED,
        startupTimeMs: Long? = null,
        rebufferCount: Int = 0,
        rebufferRatio: Float = 0f,
        errorCount: Int = 0
    ): SessionSnapshot {
        return SessionSnapshot(
            sessionId = "session-1",
            elapsedSessionTimeMs = 1_000L,
            playbackState = playbackState,
            isPlaying = playbackState == SessionPlaybackState.PLAYING,
            currentPositionMs = 2_000L,
            startupTimeMs = startupTimeMs,
            rebufferTimeMs = 0L,
            rebufferCount = rebufferCount,
            playTimeMs = 1_000L,
            rebufferRatio = rebufferRatio,
            totalDroppedFrames = 0L,
            totalSeekCount = 0,
            totalSeekTimeMs = 0L,
            meanVideoFormatBitrate = null,
            currentBitrate = null,
            errorCount = errorCount
        )
    }
}
